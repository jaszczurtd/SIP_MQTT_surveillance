#ifndef LINPHONE_CLIENT
#define LINPHONE_CLIENT

#include <linphone/core.h>
#include <signal.h>
#include <bctoolbox/defs.h>
#include <mediastreamer2/mscommon.h>
#include <sys/stat.h>
#include <unistd.h>
#include <pwd.h>
#include <stdbool.h>
#include <termios.h>

#define MAX_BUFFER_SIZE 256
#define MAX_INPUT_SIZE 64

#define AUTH_PATH ".sip_client_auth"

void sip_register(LinphoneCore *lc, const char *username, const char *password,
                    const char *domain);


#endif
