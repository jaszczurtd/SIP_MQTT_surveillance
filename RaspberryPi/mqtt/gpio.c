#include <stdio.h>
#include <string.h>
#include <mosquitto.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <stdlib.h>
#include <pwd.h>
#include <termios.h>
#include <sys/stat.h>
#include <signal.h>

#define GPIO_OFFSET 0x00200000
#define BLOCK_SIZE 4096

#define INP_GPIO(g)   *(gpio + ((g)/10)) &= ~(7 << (((g)%10)*3))
#define OUT_GPIO(g)   *(gpio + ((g)/10)) |=  (1 << (((g)%10)*3))
#define GPIO_SET      *(gpio + 7)
#define GPIO_CLR      *(gpio + 10)

#define MAX_LEN 128
#define AUTH_PATH ".mqtt_auth"

#define SWITCHES_TIMEOUT 60

void get_password(char *buf, size_t buflen) {
    struct termios oldt, newt;
    printf("password: ");
    fflush(stdout);

    tcgetattr(STDIN_FILENO, &oldt);
    newt = oldt; newt.c_lflag &= ~ECHO;
    tcsetattr(STDIN_FILENO, TCSANOW, &newt);

    fgets(buf, buflen, stdin);
    tcsetattr(STDIN_FILENO, TCSANOW, &oldt);
    printf("\n");

    buf[strcspn(buf, "\n")] = '\0'; // usuń \n
}

int read_or_prompt_credentials(char *username, char *password) {
    char path[256];
    snprintf(path, sizeof(path), "%s/%s", getenv("HOME"), AUTH_PATH);

    FILE *f = fopen(path, "r");
    if (f) {
        fgets(username, MAX_LEN, f);
        fgets(password, MAX_LEN, f);
        fclose(f);
        username[strcspn(username, "\n")] = '\0';
        password[strcspn(password, "\n")] = '\0';
        return 0;
    }

    // prompt
    printf("login: ");
    fgets(username, MAX_LEN, stdin);
    username[strcspn(username, "\n")] = '\0';

    get_password(password, MAX_LEN);

    f = fopen(path, "w");
    if (!f) {
        perror("fopen");
        return -1;
    }
    fprintf(f, "%s\n%s\n", username, password);
    fclose(f);
    chmod(path, 0600); // tylko dla właściciela

    return 0;
}


volatile unsigned *gpio = NULL;
volatile bool timeout_set = false;

// Funkcja obsługi sygnału alarmowego
void alarm_handler(int sig) {
    timeout_set = true;
}

void set_timeout(unsigned long time) {
    timeout_set = false;
    alarm(time);
}

void setup_gpio_mem() {
    int mem_fd = open("/dev/gpiomem", O_RDWR | O_SYNC);
    void *gpio_map = mmap(NULL, BLOCK_SIZE, PROT_READ | PROT_WRITE, MAP_SHARED, mem_fd, GPIO_OFFSET);
    close(mem_fd);
    gpio = (volatile unsigned *)gpio_map;
}

void set_gpio(int pin, int value) {
    INP_GPIO(pin); OUT_GPIO(pin);
    if (value)
        GPIO_SET = (1 << pin);
    else
        GPIO_CLR = (1 << pin);
}

void on_disconnect(struct mosquitto *mosq, void *userdata, int rc) {
    printf("disconnected! code: %d\n", rc);
    if (rc != 0) {
        printf("mosquitto is trying to reconnect...\n");
        mosquitto_reconnect(mosq);  // lub pozwól loop_forever() to zrobić
    }
}

void on_connect(struct mosquitto *mosq, void *userdata, int rc) {
    if (rc == 0) {
        printf("MQTT is connected\n");
        mosquitto_subscribe(mosq, NULL, "gpio/17", 0);
        mosquitto_subscribe(mosq, NULL, "gpio/27", 0);
    } else {
        printf("connection error: %d\n", rc);
    }
}


void on_message(struct mosquitto *mosq, void *userdata, const struct mosquitto_message *msg) {

    printf("received payload: %s for topic:%s\n", (char*)msg->payload, msg->topic);

    int pin = 0;
    if (strcmp(msg->topic, "gpio/17") == 0) pin = 17;
    if (strcmp(msg->topic, "gpio/27") == 0) pin = 27;

    if (pin) {
        if (strcasecmp(msg->payload, "on") == 0) {
            set_timeout(SWITCHES_TIMEOUT);
            set_gpio(pin, 1);
        } else if (strcasecmp(msg->payload, "off") == 0) {
            set_timeout(0);
            set_gpio(pin, 0);
        }
    }
}

int main() {
    
    printf("starting mosquitto listener...\n");

    char username[MAX_LEN], password[MAX_LEN];
    if (read_or_prompt_credentials(username, password) != 0) {
        fprintf(stderr, "cannot get credentials\n");
        exit(1);
    }

    setup_gpio_mem();
    mosquitto_lib_init();

    struct mosquitto *mosq = mosquitto_new(NULL, true, NULL);
    mosquitto_username_pw_set(mosq, username, password);

    mosquitto_connect_callback_set(mosq, on_connect);
    mosquitto_disconnect_callback_set(mosq, on_disconnect);

    mosquitto_reconnect_delay_set(mosq, 2, 10, true);  // min=2s, max=10s, exponential backoff

    if (mosquitto_connect(mosq, "10.8.0.1", 1883, 60) != MOSQ_ERR_SUCCESS) {
        fprintf(stderr, "Nie udało się połączyć z brokerem.\n");
        return 1;
    }

    mosquitto_message_callback_set(mosq, on_message);

    // Ustawienie obsługi sygnału alarmowego
    signal(SIGALRM, alarm_handler);
    
    // Uruchamiamy pętlę MQTT w tle
    mosquitto_loop_start(mosq);

    // Główna pętla sprawdzająca flagę
    while (1) {
        if(timeout_set) {
            printf("timeout for switches has been reached\n");
            set_timeout(0);
            
            set_gpio(17, 0);
            set_gpio(27, 0);

            // Publikacja stanu OFF z retain
            mosquitto_publish(mosq, NULL, "gpio/17", 3, "off", 1, true);
            mosquitto_publish(mosq, NULL, "gpio/27", 3, "off", 1, true);
        }

        sleep(1);
    }

    mosquitto_destroy(mosq);
    mosquitto_lib_cleanup();

    printf("exiting mosquitto listener...\n");

    return 0;
}

