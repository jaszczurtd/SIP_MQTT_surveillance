# Makefile for mqtt_gpio_client

CC = gcc
CFLAGS = -Wall -O2
LDFLAGS = -lmosquitto
TARGET = mqtt_gpio
SRC = gpio.c

all: $(TARGET)

$(TARGET): $(SRC)
	$(CC) $(CFLAGS) -o $(TARGET) $(SRC) $(LDFLAGS)

clean:
	rm -f $(TARGET)
