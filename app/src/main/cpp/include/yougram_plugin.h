/*
 * Yougram Plugin SDK (C ABI, API 2).
 *
 * Плагин — shared-библиотека (.so) с экспортом yougram_plugin_entry(). Интерфейс — чистый C.
 *
 * События (on_event, имя + JSON UTF-8):
 *   message_received / message_edited / message_deleted   — нужно разрешение "chat.read"
 *   timer    {id}                                          — после host->call("timer_start")
 *   command  {chat_id, name, args, reply_to}               — после host->call("register_command")
 *   action   {id, chat_id, message_id}                     — после host->call("register_action")
 *
 * Хуки (on_hook, нужно разрешение "hooks"). Возвращают новый JSON (malloc/strdup) или NULL = без изменений:
 *   before_send  {chat_id, text, reply_to}  ->  {"text":"..."}  или  {"cancel":true}
 *
 * host->call(ctx, method, args_json) возвращает JSON {"ok":true,"result":...} / {"ok":false,"error":"..."}.
 * Строку нужно освободить через host->free_string(). Методы и разрешения:
 *   chat.read   : get_me, get_chat{chat_id}, get_user{user_id}, get_message{chat_id,message_id}, mark_read{chat_id,message_ids[]}
 *   chat.send   : send_message{chat_id,text,reply_to?}, forward_message{to_chat_id,from_chat_id,message_id}
 *   chat.modify : edit_message{chat_id,message_id,text}, delete_message{chat_id,message_id}
 *   network     : http_request{url,method?,headers?{},body?} -> {status, body}
 *   hooks       : register_command{name,description?}
 *   ui          : register_action{id,title,where:"message_menu"}, toast{title,message}
 *   (без прав)  : storage_get{key}, storage_set{key,value}, storage_delete{key}, timer_start{id,interval_ms,repeat?}, timer_stop{id}
 *
 * Колбэки вызываются из служебного потока по одному. Не блокируйте его надолго.
 */
#ifndef YOUGRAM_PLUGIN_H
#define YOUGRAM_PLUGIN_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define YG_PLUGIN_API_VERSION 2u

#define YG_LOG_DEBUG 0
#define YG_LOG_INFO  1
#define YG_LOG_WARN  2
#define YG_LOG_ERROR 3

#define YG_EXPORT __attribute__((visibility("default")))

typedef struct YgHost {
    uint32_t api_version;
    void* ctx;
    void (*log)(void* ctx, int level, const char* message);
    void (*notify)(void* ctx, const char* title, const char* message);
    /* --- v2: проверяйте host->api_version >= 2 --- */
    char* (*call)(void* ctx, const char* method, const char* args_json);
    void (*free_string)(char* s);
} YgHost;

typedef struct YgPlugin {
    uint32_t api_version; /* 1 или 2 */
    void (*on_load)(const YgHost* host);
    void (*on_enable)(void);
    void (*on_disable)(void);
    void (*on_event)(const char* event, const char* json);
    /* --- v2 (читается только если api_version >= 2) --- */
    char* (*on_hook)(const char* hook, const char* json);
} YgPlugin;

typedef const YgPlugin* (*YgPluginEntryFn)(void);

YG_EXPORT const YgPlugin* yougram_plugin_entry(void);

#ifdef __cplusplus
}
#endif

#endif /* YOUGRAM_PLUGIN_H */