#pragma once
// Заглушка вместо tgnet/FileLog: нативный код звонков использует только эти макросы.
#include <android/log.h>
#include <string>
#define DEBUG_FATAL(...) __android_log_print(ANDROID_LOG_FATAL, "tgvoip", __VA_ARGS__)
#define DEBUG_E(...)     __android_log_print(ANDROID_LOG_ERROR, "tgvoip", __VA_ARGS__)
#define DEBUG_W(...)     __android_log_print(ANDROID_LOG_WARN,  "tgvoip", __VA_ARGS__)
#define DEBUG_D(...)     ((void)0)
#define DEBUG_REF(...)   ((void)0)
#define DEBUG_DELREF(...) ((void)0)
