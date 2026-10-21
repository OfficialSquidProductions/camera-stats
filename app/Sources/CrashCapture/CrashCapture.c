#include "CrashCapture.h"
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>

/* No allocation, Foundation, stack walking, file opening, locks or logging in a
   fatal handler. Preserve the native fatal disposition so OS crash reporting
   still works. The preopened file holds only a format byte and signal number. */
static volatile sig_atomic_t marker_fd = -1;
static const int fatal_signals[] = {SIGABRT, SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGTRAP};
static struct sigaction previous[6];
static int installed = 0;
static void record(unsigned char code) {
    const unsigned char marker[2] = {1, code};
    int saved_errno = errno;
    if (marker_fd >= 0) (void)write((int)marker_fd, marker, sizeof(marker));
    errno = saved_errno;
}
static void fatal_handler(int number) {
    record((unsigned char)number);
    /* SA_RESETHAND restored SIG_DFL; SA_NODEFER permits the fatal redelivery. */
    (void)kill(getpid(), number);
    _exit(128 + number);
}
int sr_crash_install(const char *path) {
    if (installed) return -1;
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (fd < 0) return -1;
    marker_fd = fd;
    struct sigaction action = {0};
    action.sa_handler = fatal_handler;
    action.sa_flags = SA_RESETHAND | SA_NODEFER;
    sigemptyset(&action.sa_mask);
    for (int i = 0; i < 6; i++) {
        if (sigaction(fatal_signals[i], &action, &previous[i]) != 0) {
            for (int j = 0; j < i; j++) sigaction(fatal_signals[j], &previous[j], 0);
            close(fd); marker_fd = -1; return -1;
        }
    }
    installed = 1;
    return 0;
}
void sr_crash_record_exception(void) { record(255); }
void sr_crash_uninstall(void) {
    if (!installed) return;
    for (int i = 0; i < 6; i++) sigaction(fatal_signals[i], &previous[i], 0);
    if (marker_fd >= 0) close((int)marker_fd);
    marker_fd = -1; installed = 0;
}
