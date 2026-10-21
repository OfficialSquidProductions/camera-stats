#ifndef SHOT_REPORTER_CRASH_CAPTURE_H
#define SHOT_REPORTER_CRASH_CAPTURE_H
int sr_crash_install(const char *path);
void sr_crash_uninstall(void);
void sr_crash_record_exception(void);
#endif
