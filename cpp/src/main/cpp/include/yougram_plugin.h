/*
 * Yougram Plugin SDK 2.0 (C ABI, YG_PLUGIN_API_VERSION 2, minor 0).
 *
 * Плагин — shared-библиотека (.so) с экспортом yougram_plugin_entry(). Интерфейс — чистый C.
 * Пакет .ygplugin — zip: manifest.json + lib/<abi>/<entry>.
 *
 * ===================== manifest.json =====================
 *   {
 *     "id": "com.example.plugin",            // [A-Za-z0-9][A-Za-z0-9._-]{0,119}
 *     "name": "Мой плагин",
 *     "version": "1.0.0",
 *     "api": "2.0",                          // "1.0" тоже принимается
 *     "author": "...", "description": "...",
 *     "entry": "libplugin.so",
 *     "priority": 0,                         // -100..100, больше = раньше в цепочке хуков
 *     "permissions": ["chat.read", "chat.send", "chat.modify", "network", "hooks", "ui", "ipc"],
 *     "settings": [                          // экран настроек рисует приложение
 *       {"key":"greeting","type":"text","title":"Приветствие","default":"hi"},
 *       {"key":"loud","type":"switch","title":"Громко","default":false},
 *       {"key":"count","type":"number","title":"Сколько","default":3,"min":1,"max":10},
 *       {"key":"mode","type":"choice","title":"Режим","default":"a",
 *        "options":[{"value":"a","title":"А"},{"value":"b","title":"Б"}]}
 *     ]
 *   }
 *
 * ===================== События (on_event) =====================
 * Имя + JSON UTF-8. Вызываются из служебного потока по одному.
 *   message_received {message}                        — chat.read (включая свои исходящие, outgoing:true)
 *   message_sent     {message, old_message_id}        — chat.read, сообщение подтверждено сервером
 *   message_edited   {chat_id,message_id,text,type,content,media} — chat.read
 *   message_deleted  {chat_id,message_ids[]}          — chat.read
 *   timer            {id}                             — после timer_start
 *   command          {chat_id,name,args,reply_to}     — после register_command
 *   action           {id,chat_id,message_id}          — после register_action
 *   settings_changed {key,value}                      — пользователь изменил настройку плагина
 *   plugin_message   {from,payload}                   — другой плагин вызвал send_to_plugin
 *   http_response    {request_id,ok,status?,headers?,body?,error?} — после http_request_async
 *
 * message = {id,chat_id,sender_user_id,sender_chat_id,date,outgoing,text,type,content,media,
 *            reply_to_message_id,reactions[]}
 *
 * ===================== Хуки (on_hook, разрешение "hooks") =====================
 * Возвращают новый JSON (malloc/strdup, освобождает хост) или NULL = без изменений.
 * Результат плагина — вход следующего (порядок по priority). {"cancel":true} обрывает цепочку и отменяет действие.
 *   before_send {chat_id,text,reply_to}            -> {"text":"..."} | {"cancel":true}
 *   before_edit {chat_id,message_id,text}          -> {"text":"..."} | {"cancel":true}
 * Хук ограничен по времени (1.5 с): при превышении отправка идёт как есть.
 * Сообщения, отправленные самим плагином через host->call, мимо хуков не проходят.
 *
 * ===================== host->call(ctx, method, args_json) =====================
 * Возвращает JSON {"ok":true,"result":...} или {"ok":false,"error":"..."}.
 * Строку нужно освободить через host->free_string().
 *
 *  без разрешений:
 *    get_app_info{}                         -> {api,api_major,api_minor,app_version,sdk,abi,plugin_id,data_dir,
 *                                               language,permissions[],methods[]}
 *    storage_get{key} / storage_set{key,value} / storage_delete{key} / storage_keys{prefix?} / storage_clear{}
 *    settings_get{key} / settings_all{} / settings_set{key,value}
 *    timer_start{id,interval_ms,repeat?} / timer_stop{id}
 *
 *  chat.read:
 *    get_me{} / get_chat{chat_id} / get_user{user_id} / get_message{chat_id,message_id}
 *    get_chats{limit?,archived?} / get_chat_history{chat_id,from_message_id?,offset?,limit?,only_local?}
 *    search_messages{query,limit?} / mark_read{chat_id,message_ids[]}
 *
 *  chat.send:   (лимит: 30 изменяющих вызовов за 30 с на плагин)
 *    send_message{chat_id,text,reply_to?}
 *    send_photo{chat_id,path,caption?} / send_document{chat_id,path,caption?}   — path только внутри data_dir
 *    forward_message{to_chat_id,from_chat_id,message_id}
 *
 *  chat.modify: (тот же лимит)
 *    edit_message{chat_id,message_id,text} / delete_message{chat_id,message_id}
 *    react{chat_id,message_id,emoji,remove?} / pin_message{chat_id,message_id}
 *
 *  network:
 *    http_request{url,method?,headers?{},body?|body_base64?,timeout_ms?,max_bytes?,response?:"text"|"base64",
 *                 follow_redirects?}                     -> {status,headers{},body}   (блокирующий, до 25 с)
 *    http_request_async{request_id?, ...те же поля}      -> {request_id}; ответ придёт событием http_response
 *    http_download{url,name,method?,headers?{},timeout_ms?} -> {path,size,status}  (в data_dir/downloads, до 32 МБ)
 *
 *  hooks:
 *    register_command{name,description?,usage?,aliases?[]} / unregister_command{name}
 *
 *  ui:
 *    register_action{id,title,where?:"message_menu"|"chat_menu",icon?} / unregister_action{id}
 *    toast{title,message} / copy_to_clipboard{text}
 *
 *  ipc:
 *    send_to_plugin{plugin_id,payload}      -> событие plugin_message у целевого плагина
 *
 * Всё, что плагин зарегистрировал (команды, действия, таймеры, запросы), снимается при выключении/удалении.
 * Не блокируйте служебный поток надолго: для долгих запросов используйте http_request_async.
 */
#ifndef YOUGRAM_PLUGIN_H
#define YOUGRAM_PLUGIN_H

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

#define YG_PLUGIN_API_VERSION 2u
#define YG_PLUGIN_API_MINOR   0u

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
    /* --- v2.0: поля добавлены в конец, старые плагины их просто не видят --- */
    uint32_t api_minor;
    const char* plugin_id;  /* id из манифеста */
    const char* data_dir;   /* приватный каталог плагина, существует и доступен для записи */
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

/* ===================== Хелперы (необязательные, без STL) ===================== */

static inline void yg_log(const YgHost* h, int level, const char* msg) {
    if (h && h->log) h->log(h->ctx, level, msg);
}

/* Вызов метода хоста. Результат освободить через yg_free(). */
static inline char* yg_call(const YgHost* h, const char* method, const char* args_json) {
    if (!h || !h->call) return NULL;
    return h->call(h->ctx, method, args_json ? args_json : "{}");
}

static inline void yg_free(const YgHost* h, char* s) {
    if (h && h->free_string && s) h->free_string(s);
}

/* Есть ли "ok":true в ответе host->call. */
static inline int yg_ok(const char* response) {
    return response && strstr(response, "\"ok\":true") != NULL;
}

/*
 * Простой разбор плоского JSON: ищет "key": на любом уровне и возвращает указатель на значение.
 * Подходит для payload'ов событий. Строковое значение с текстом вида "key": внутри даст ложное срабатывание.
 */
static inline const char* yg_json_find(const char* json, const char* key) {
    char needle[96];
    size_t n = key ? strlen(key) : 0;
    if (!json || n == 0 || n > sizeof(needle) - 3) return NULL;
    needle[0] = '"';
    memcpy(needle + 1, key, n);
    needle[n + 1] = '"';
    needle[n + 2] = 0;
    const char* p = json;
    while ((p = strstr(p, needle)) != NULL) {
        const char* q = p + n + 2;
        while (*q == ' ' || *q == '\t' || *q == '\n' || *q == '\r') q++;
        if (*q == ':') {
            q++;
            while (*q == ' ' || *q == '\t' || *q == '\n' || *q == '\r') q++;
            return q;
        }
        p++;
    }
    return NULL;
}

static inline int64_t yg_json_get_int64(const char* json, const char* key, int64_t def) {
    const char* v = yg_json_find(json, key);
    if (!v || !((*v >= '0' && *v <= '9') || *v == '-')) return def;
    return (int64_t) strtoll(v, NULL, 10);
}

static inline int yg_json_get_bool(const char* json, const char* key, int def) {
    const char* v = yg_json_find(json, key);
    if (!v) return def;
    if (strncmp(v, "true", 4) == 0) return 1;
    if (strncmp(v, "false", 5) == 0) return 0;
    return def;
}

static inline int yg_hex4(const char* s, unsigned* out) {
    unsigned v = 0;
    for (int i = 0; i < 4; i++) {
        char c = s[i];
        v <<= 4;
        if (c >= '0' && c <= '9') v |= (unsigned) (c - '0');
        else if (c >= 'a' && c <= 'f') v |= (unsigned) (c - 'a' + 10);
        else if (c >= 'A' && c <= 'F') v |= (unsigned) (c - 'A' + 10);
        else return 0;
    }
    *out = v;
    return 1;
}

static inline size_t yg_put_utf8(char* dst, size_t pos, size_t cap, unsigned cp) {
    char tmp[4];
    size_t n;
    if (cp < 0x80) { tmp[0] = (char) cp; n = 1; }
    else if (cp < 0x800) { tmp[0] = (char) (0xC0 | (cp >> 6)); tmp[1] = (char) (0x80 | (cp & 0x3F)); n = 2; }
    else if (cp < 0x10000) {
        tmp[0] = (char) (0xE0 | (cp >> 12)); tmp[1] = (char) (0x80 | ((cp >> 6) & 0x3F));
        tmp[2] = (char) (0x80 | (cp & 0x3F)); n = 3;
    } else {
        tmp[0] = (char) (0xF0 | (cp >> 18)); tmp[1] = (char) (0x80 | ((cp >> 12) & 0x3F));
        tmp[2] = (char) (0x80 | ((cp >> 6) & 0x3F)); tmp[3] = (char) (0x80 | (cp & 0x3F)); n = 4;
    }
    if (pos + n >= cap) return pos;
    memcpy(dst + pos, tmp, n);
    return pos + n;
}

/* Копирует строковое значение в out (UTF-8, с разбором \n, \", \uXXXX). Возвращает длину или -1, если значения нет. */
static inline int yg_json_get_string(const char* json, const char* key, char* out, size_t cap) {
    const char* v = yg_json_find(json, key);
    if (!v || *v != '"' || cap == 0) return -1;
    size_t pos = 0;
    v++;
    while (*v && *v != '"') {
        if (*v == '\\' && v[1]) {
            v++;
            switch (*v) {
                case 'n': pos = yg_put_utf8(out, pos, cap, '\n'); v++; break;
                case 'r': pos = yg_put_utf8(out, pos, cap, '\r'); v++; break;
                case 't': pos = yg_put_utf8(out, pos, cap, '\t'); v++; break;
                case 'b': pos = yg_put_utf8(out, pos, cap, '\b'); v++; break;
                case 'f': pos = yg_put_utf8(out, pos, cap, '\f'); v++; break;
                case 'u': {
                    unsigned cp = 0;
                    if (!yg_hex4(v + 1, &cp)) { v++; break; }
                    v += 5;
                    if (cp >= 0xD800 && cp < 0xDC00 && v[0] == '\\' && v[1] == 'u') {
                        unsigned lo = 0;
                        if (yg_hex4(v + 2, &lo) && lo >= 0xDC00 && lo < 0xE000) {
                            cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                            v += 6;
                        }
                    }
                    pos = yg_put_utf8(out, pos, cap, cp);
                    break;
                }
                default: pos = yg_put_utf8(out, pos, cap, (unsigned char) *v); v++; break;
            }
        } else {
            if (pos + 1 >= cap) break;
            out[pos++] = *v++;
        }
    }
    out[pos] = 0;
    return (int) pos;
}

/* Экранирует строку для вставки в JSON (без кавычек). Возвращает длину или -1, если не поместилось. */
static inline int yg_json_escape(const char* src, char* dst, size_t cap) {
    size_t pos = 0;
    if (!src || cap == 0) return -1;
    for (; *src; src++) {
        unsigned char c = (unsigned char) *src;
        const char* rep = NULL;
        char buf[8];
        switch (c) {
            case '"': rep = "\\\""; break;
            case '\\': rep = "\\\\"; break;
            case '\n': rep = "\\n"; break;
            case '\r': rep = "\\r"; break;
            case '\t': rep = "\\t"; break;
            default:
                if (c < 0x20) {
                    static const char hex[] = "0123456789abcdef";
                    buf[0] = '\\'; buf[1] = 'u'; buf[2] = '0'; buf[3] = '0';
                    buf[4] = hex[c >> 4]; buf[5] = hex[c & 15]; buf[6] = 0;
                    rep = buf;
                }
        }
        if (rep) {
            size_t n = strlen(rep);
            if (pos + n + 1 > cap) return -1;
            memcpy(dst + pos, rep, n);
            pos += n;
        } else {
            if (pos + 2 > cap) return -1;
            dst[pos++] = (char) c;
        }
    }
    dst[pos] = 0;
    return (int) pos;
}

#ifdef __cplusplus
}
#endif

#endif /* YOUGRAM_PLUGIN_H */
