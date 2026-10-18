// Пример плагина SDK 2.0: команда /ping отвечает текстом из настроек плагина.
// Без STL: плагину не нужна libc++, достаточно libc и yougram_plugin.h.
#include <stdio.h>
#include <string.h>

#include "yougram_plugin.h"

namespace {

const YgHost* g_host = nullptr;

void log(int level, const char* text) { yg_log(g_host, level, text); }

void sendPong(long long chatId, long long replyTo) {
    // Текст ответа и режим «ответом на команду» задаёт пользователь в настройках плагина.
    char pong[256] = "pong";
    char* settings = yg_call(g_host, "settings_all", "{}");
    if (settings) {
        yg_json_get_string(settings, "pong_text", pong, sizeof(pong));
        int asReply = yg_json_get_bool(settings, "reply_to_command", 1);
        yg_free(g_host, settings);
        if (!asReply) replyTo = 0;
    }

    char escaped[600];
    if (yg_json_escape(pong, escaped, sizeof(escaped)) < 0) return;

    char args[800];
    snprintf(args, sizeof(args), "{\"chat_id\":%lld,\"text\":\"%s\",\"reply_to\":%lld}", chatId, escaped, replyTo);
    char* response = yg_call(g_host, "send_message", args);
    if (!yg_ok(response)) log(YG_LOG_WARN, response ? response : "send_message: нет ответа");
    yg_free(g_host, response);
}

void onLoad(const YgHost* host) {
    g_host = host;
    char line[200];
    snprintf(line, sizeof(line), "loaded: id=%s, host API %u.%u, data=%s",
             host->plugin_id ? host->plugin_id : "?", static_cast<unsigned>(host->api_version),
             static_cast<unsigned>(host->api_minor), host->data_dir ? host->data_dir : "?");
    log(YG_LOG_INFO, line);
}

void onEnable() {
    // Команды и действия регистрируются при каждом включении: при выключении хост их снимает.
    char* response = yg_call(g_host, "register_command",
                             "{\"name\":\"ping\",\"description\":\"Ответить pong\",\"usage\":\"/ping\"}");
    if (!yg_ok(response)) log(YG_LOG_ERROR, response ? response : "register_command: нет ответа");
    yg_free(g_host, response);
    log(YG_LOG_INFO, "enabled");
}

void onDisable() { log(YG_LOG_INFO, "disabled"); }

void onEvent(const char* event, const char* json) {
    if (!event || !json) return;
    if (strcmp(event, "command") == 0) {
        char name[40];
        if (yg_json_get_string(json, "name", name, sizeof(name)) < 0 || strcmp(name, "ping") != 0) return;
        sendPong(yg_json_get_int64(json, "chat_id", 0), yg_json_get_int64(json, "reply_to", 0));
    } else if (strcmp(event, "settings_changed") == 0) {
        char key[64];
        char line[128];
        if (yg_json_get_string(json, "key", key, sizeof(key)) < 0) return;
        snprintf(line, sizeof(line), "setting changed: %s", key);
        log(YG_LOG_DEBUG, line);
    }
}

// Хук не меняет текст: плагин только показывает, как он вызывается (NULL = «без изменений»).
char* onHook(const char*, const char*) { return nullptr; }

const YgPlugin kPlugin = {YG_PLUGIN_API_VERSION, onLoad, onEnable, onDisable, onEvent, onHook};

}  // namespace

extern "C" YG_EXPORT const YgPlugin* yougram_plugin_entry(void) { return &kPlugin; }
