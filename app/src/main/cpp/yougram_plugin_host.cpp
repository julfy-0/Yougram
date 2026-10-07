// JNI-хост нативных плагинов Yougram (API 1–2): dlopen(), события, хуки, вызовы хоста из плагина.
// Без STL. Все строки-payload ходят через JNI как byte[] (чистый UTF-8, эмодзи не ломаются).
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "yougram_plugin.h"

#define TAG "YougramPlugins"

namespace {

    struct Plugin {
        char* id;
        void* handle;
        const YgPlugin* api;
        YgHost host;
        bool enabled;
        Plugin* next;
    };

    pthread_mutex_t g_mutex = PTHREAD_MUTEX_INITIALIZER;
    Plugin* g_plugins = nullptr;

    JavaVM* g_vm = nullptr;
    jobject g_manager = nullptr;
    jmethodID g_notify = nullptr;
    jmethodID g_call = nullptr;

    struct Lock {
        Lock() { pthread_mutex_lock(&g_mutex); }
        ~Lock() { pthread_mutex_unlock(&g_mutex); }
    };

// Для ASCII-строк (id, имена событий).
    char* dupUtf(JNIEnv* env, jstring s) {
        if (!s) return strdup("");
        const char* chars = env->GetStringUTFChars(s, nullptr);
        char* out = strdup(chars ? chars : "");
        if (chars) env->ReleaseStringUTFChars(s, chars);
        return out ? out : strdup("");
    }

    char* dupBytes(JNIEnv* env, jbyteArray a) {
        jsize n = a ? env->GetArrayLength(a) : 0;
        char* out = static_cast<char*>(malloc(static_cast<size_t>(n) + 1));
        if (!out) return strdup("");
        if (n > 0) env->GetByteArrayRegion(a, 0, n, reinterpret_cast<jbyte*>(out));
        out[n] = 0;
        return out;
    }

    jbyteArray newBytes(JNIEnv* env, const char* s) {
        size_t n = s ? strlen(s) : 0;
        jbyteArray a = env->NewByteArray(static_cast<jsize>(n));
        if (a && n) env->SetByteArrayRegion(a, 0, static_cast<jsize>(n), reinterpret_cast<const jbyte*>(s));
        return a;
    }

    jstring fail(JNIEnv* env, const char* fmt, ...) {
        char buf[512];
        va_list args;
        va_start(args, fmt);
        vsnprintf(buf, sizeof(buf), fmt, args);
        va_end(args);
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", buf);
        return env->NewStringUTF(buf);
    }

    Plugin* findLocked(const char* id) {
        for (Plugin* p = g_plugins; p; p = p->next) {
            if (strcmp(p->id, id) == 0) return p;
        }
        return nullptr;
    }

// allowed: "*" — все; иначе список вида ",id1,id2,".
    bool isAllowed(const char* allowed, const char* id) {
        if (strcmp(allowed, "*") == 0) return true;
        char needle[160];
        snprintf(needle, sizeof(needle), ",%s,", id);
        return strstr(allowed, needle) != nullptr;
    }

    bool attach(JNIEnv** env, bool* attached) {
        *attached = false;
        if (!g_vm) return false;
        if (g_vm->GetEnv(reinterpret_cast<void**>(env), JNI_VERSION_1_6) == JNI_OK) return true;
        if (g_vm->AttachCurrentThread(env, nullptr) != JNI_OK) return false;
        *attached = true;
        return true;
    }

    void hostLog(void* ctx, int level, const char* message) {
        auto* p = static_cast<Plugin*>(ctx);
        int prio = level <= YG_LOG_DEBUG  ? ANDROID_LOG_DEBUG
                                          : level == YG_LOG_INFO ? ANDROID_LOG_INFO
                                                                 : level == YG_LOG_WARN ? ANDROID_LOG_WARN
                                                                                        : ANDROID_LOG_ERROR;
        __android_log_print(prio, TAG, "[%s] %s", p->id, message ? message : "");
    }

    void hostNotify(void* ctx, const char* title, const char* message) {
        auto* p = static_cast<Plugin*>(ctx);
        if (!g_manager || !g_notify) return;
        JNIEnv* env = nullptr;
        bool attached = false;
        if (!attach(&env, &attached)) return;
        jstring a = env->NewStringUTF(p->id);
        jbyteArray b = newBytes(env, title);
        jbyteArray c = newBytes(env, message);
        env->CallVoidMethod(g_manager, g_notify, a, b, c);
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(a);
        env->DeleteLocalRef(b);
        env->DeleteLocalRef(c);
        if (attached) g_vm->DetachCurrentThread();
    }

    char* hostCall(void* ctx, const char* method, const char* args) {
        auto* p = static_cast<Plugin*>(ctx);
        static const char kUnavailable[] = "{\"ok\":false,\"error\":\"host unavailable\"}";
        if (!g_manager || !g_call) return strdup(kUnavailable);
        JNIEnv* env = nullptr;
        bool attached = false;
        if (!attach(&env, &attached)) return strdup(kUnavailable);
        jstring a = env->NewStringUTF(p->id);
        jstring b = env->NewStringUTF(method ? method : "");
        jbyteArray c = newBytes(env, args && *args ? args : "{}");
        auto result = static_cast<jbyteArray>(env->CallObjectMethod(g_manager, g_call, a, b, c));
        char* out = nullptr;
        if (env->ExceptionCheck()) env->ExceptionClear();
        else if (result) out = dupBytes(env, result);
        env->DeleteLocalRef(a);
        env->DeleteLocalRef(b);
        env->DeleteLocalRef(c);
        if (result) env->DeleteLocalRef(result);
        if (attached) g_vm->DetachCurrentThread();
        return out ? out : strdup(kUnavailable);
    }

    void hostFreeString(char* s) { free(s); }

    void disableLocked(Plugin* p) {
        if (!p->enabled) return;
        p->enabled = false;
        if (p->api->on_disable) p->api->on_disable();
    }

    void unloadLocked(const char* id) {
        Plugin** link = &g_plugins;
        while (*link) {
            Plugin* p = *link;
            if (strcmp(p->id, id) == 0) {
                *link = p->next;
                disableLocked(p);
                dlclose(p->handle);
                free(p->id);
                free(p);
                return;
            }
            link = &p->next;
        }
    }

}  // namespace

extern "C" {

JNIEXPORT void JNICALL Java_app_yougram_plugin_NativePluginManager_nativeInit(JNIEnv* env, jobject thiz) {
    env->GetJavaVM(&g_vm);
    if (g_manager) env->DeleteGlobalRef(g_manager);
    g_manager = env->NewGlobalRef(thiz);
    jclass cls = env->GetObjectClass(thiz);
    g_notify = env->GetMethodID(cls, "onNativeNotify", "(Ljava/lang/String;[B[B)V");
    g_call = env->GetMethodID(cls, "onNativeCall", "(Ljava/lang/String;Ljava/lang/String;[B)[B");
}

JNIEXPORT jstring JNICALL Java_app_yougram_plugin_NativePluginManager_nativeLoad(
        JNIEnv* env, jobject, jstring jid, jstring jpath) {
    char* id = dupUtf(env, jid);
    char* path = dupUtf(env, jpath);
    jstring result = nullptr;
    {
        Lock lock;
        unloadLocked(id);

        dlerror();
        void* handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            const char* e = dlerror();
            result = fail(env, "dlopen: %s", e ? e : "неизвестная ошибка");
        } else {
            auto entry = reinterpret_cast<YgPluginEntryFn>(dlsym(handle, "yougram_plugin_entry"));
            const YgPlugin* api = entry ? entry() : nullptr;
            if (!entry) {
                dlclose(handle);
                result = fail(env, "В библиотеке нет экспорта yougram_plugin_entry");
            } else if (!api) {
                dlclose(handle);
                result = fail(env, "yougram_plugin_entry вернула null");
            } else if (api->api_version < 1 || api->api_version > YG_PLUGIN_API_VERSION) {
                dlclose(handle);
                result = fail(env, "Версия SDK плагина %u не поддерживается (нужна 1..%u)",
                              static_cast<unsigned>(api->api_version), static_cast<unsigned>(YG_PLUGIN_API_VERSION));
            } else {
                auto* plugin = static_cast<Plugin*>(calloc(1, sizeof(Plugin)));
                if (!plugin) {
                    dlclose(handle);
                    result = fail(env, "Не хватило памяти");
                } else {
                    plugin->id = strdup(id);
                    plugin->handle = handle;
                    plugin->api = api;
                    plugin->host.api_version = YG_PLUGIN_API_VERSION;
                    plugin->host.ctx = plugin;
                    plugin->host.log = hostLog;
                    plugin->host.notify = hostNotify;
                    plugin->host.call = hostCall;
                    plugin->host.free_string = hostFreeString;
                    plugin->enabled = false;
                    plugin->next = g_plugins;
                    g_plugins = plugin;
                    if (api->on_load) api->on_load(&plugin->host);
                }
            }
        }
    }
    free(id);
    free(path);
    return result;
}

JNIEXPORT jstring JNICALL Java_app_yougram_plugin_NativePluginManager_nativeSetEnabled(
        JNIEnv* env, jobject, jstring jid, jboolean enabled) {
    char* id = dupUtf(env, jid);
    jstring result = nullptr;
    {
        Lock lock;
        Plugin* p = findLocked(id);
        if (!p) {
            result = fail(env, "Плагин не загружен");
        } else if (enabled) {
            if (!p->enabled) {
                p->enabled = true;
                if (p->api->on_enable) p->api->on_enable();
            }
        } else {
            disableLocked(p);
        }
    }
    free(id);
    return result;
}

JNIEXPORT void JNICALL Java_app_yougram_plugin_NativePluginManager_nativeUnload(JNIEnv* env, jobject, jstring jid) {
    char* id = dupUtf(env, jid);
    {
        Lock lock;
        unloadLocked(id);
    }
    free(id);
}

JNIEXPORT void JNICALL Java_app_yougram_plugin_NativePluginManager_nativeEmit(
        JNIEnv* env, jobject, jstring jevent, jbyteArray jjson, jstring jallowed) {
    char* event = dupUtf(env, jevent);
    char* json = dupBytes(env, jjson);
    char* allowed = dupUtf(env, jallowed);
    {
        Lock lock;
        for (Plugin* p = g_plugins; p; p = p->next) {
            if (!p->enabled || !p->api->on_event || !isAllowed(allowed, p->id)) continue;
            p->api->on_event(event, json);
        }
    }
    free(event);
    free(json);
    free(allowed);
}

// Цепочка хуков: результат одного плагина — вход следующего. null — никто ничего не изменил.
JNIEXPORT jbyteArray JNICALL Java_app_yougram_plugin_NativePluginManager_nativeHook(
        JNIEnv* env, jobject, jstring jhook, jbyteArray jjson, jstring jallowed) {
    char* hook = dupUtf(env, jhook);
    char* json = dupBytes(env, jjson);
    char* allowed = dupUtf(env, jallowed);
    char* current = json;
    {
        Lock lock;
        for (Plugin* p = g_plugins; p; p = p->next) {
            if (!p->enabled || p->api->api_version < 2 || !p->api->on_hook || !isAllowed(allowed, p->id)) continue;
            char* out = p->api->on_hook(hook, current);
            if (!out) continue;
            if (current != json) free(current);
            current = out;
            if (strstr(out, "\"cancel\":true")) break;
        }
    }
    jbyteArray result = current != json ? newBytes(env, current) : nullptr;
    if (current != json) free(current);
    free(json);
    free(hook);
    free(allowed);
    return result;
}

}  // extern "C"