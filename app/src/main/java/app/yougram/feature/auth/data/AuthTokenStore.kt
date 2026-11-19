package app.yougram.feature.auth.data

import android.content.Context
import org.json.JSONArray

/**
 * Порт `AuthTokensHelper` из официального Telegram (org.telegram.messenger).
 *
 * При выходе из аккаунта TDLib присылает `updateOption("authentication_token")`. Эти токены нужно
 * передать в настройках следующего входа ([PhoneAuthSettingsFactory]): сервер узнаёт прежнее устройство
 * и может не слать код заново.
 *
 * Хранилище общее для всех аккаунтов приложения (как и в оригинале): токен, полученный при выходе из
 * одного аккаунта, отправляется при любом следующем входе. Максимум [MAX_TOKENS] штук (лимит TDLib).
 * В оригинале новые токены дописываются в конец и берутся первые 20; здесь новые идут первыми, чтобы
 * при переполнении отбрасывались самые старые.
 */
class AuthTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("yougram_auth_tokens", Context.MODE_PRIVATE)
    private val lock = Any()

    fun all(): List<String> = synchronized(lock) { read() }

    fun add(token: String) {
        if (token.isBlank()) return
        synchronized(lock) {
            val list = read().toMutableList()
            list.remove(token)
            list.add(0, token)
            write(list.take(MAX_TOKENS))
        }
    }

    fun clear() = synchronized(lock) { prefs.edit().remove(KEY_TOKENS).apply() }

    private fun read(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY_TOKENS, null) ?: "[]")
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun write(tokens: List<String>) {
        prefs.edit().putString(KEY_TOKENS, JSONArray(tokens).toString()).apply()
    }

    private companion object {
        const val KEY_TOKENS = "tokens"
        const val MAX_TOKENS = 20
    }
}
