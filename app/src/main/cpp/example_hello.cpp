// Пример C++ плагина: пишет в logcat жизненный цикл и приходящие события.
// Без STL: плагину не нужна libc++, достаточно libc и интерфейса из yougram_plugin.h.
#include <stdio.h>
#include <string.h>

#include "yougram_plugin.h"

namespace {

const YgHost* g_host = nullptr;

void log(int level, const char* text) {
    if (g_host) g_host->log(g_host->ctx, level, text);
}

void onLoad(const YgHost* host) {
    g_host = host;
    log(YG_LOG_INFO, "[Hello World] plugin loaded (C++)");
}

void onEnable() { log(YG_LOG_INFO, "[Hello World] plugin enabled"); }

void onDisable() { log(YG_LOG_INFO, "[Hello World] plugin disabled"); }

void onEvent(const char* event, const char* json) {
    char line[256];
    snprintf(line, sizeof(line), "event %s (%zu bytes)", event ? event : "?", json ? strlen(json) : static_cast<size_t>(0));
    log(YG_LOG_DEBUG, line);
}

const YgPlugin kPlugin = {YG_PLUGIN_API_VERSION, onLoad, onEnable, onDisable, onEvent};

}  // namespace

extern "C" YG_EXPORT const YgPlugin* yougram_plugin_entry(void) { return &kPlugin; }
