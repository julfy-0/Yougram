package app.yougram.data

import android.content.Context
import android.content.Intent
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AccountEntry(
    /** Имя папки TDLib: "tdlib" для основного, "account_<время>" для остальных. */
    val id: String,
    val userId: Long = 0L,
    val name: String = "",
    val phone: String = "",
    val username: String? = null,
    val avatarFileId: Int? = null,
) {
    /** Аккаунт ещё ни разу не входил в Telegram. */
    val isBlank: Boolean get() = userId == 0L
}

/**
 * Список аккаунтов и выбор активного. У каждого аккаунта своя база TDLib (папка [AccountEntry.id]).
 * TDLib-клиент создаётся один раз при старте процесса для активного аккаунта, поэтому любое
 * переключение или добавление сохраняет выбор и перезапускает процесс ([switchAndRestart], [addAndRestart]).
 */
class AccountManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("yougram_accounts", Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow(loadAccounts())
    val accounts: StateFlow<List<AccountEntry>> = _accounts.asStateFlow()

    private val _activeAccountId = MutableStateFlow(loadActiveId(_accounts.value))
    val activeAccountId: StateFlow<String> = _activeAccountId.asStateFlow()

    /** Делает аккаунт активным (вступит в силу после перезапуска). false — такого аккаунта нет или он уже активен. */
    fun switchAccount(id: String): Boolean {
        if (id == _activeAccountId.value || _accounts.value.none { it.id == id }) return false
        _activeAccountId.value = id
        prefs.edit().putString(KEY_ACTIVE_ID, id).commit()
        return true
    }

    /**
     * Создаёт пустой аккаунт и делает его активным; после перезапуска откроется экран входа.
     * Если уже есть аккаунт, в который так и не вошли, используется он, чтобы не плодить пустые.
     */
    fun addAccount(): String {
        val existing = _accounts.value.firstOrNull { it.isBlank && it.id != _activeAccountId.value }
        if (existing != null) {
            switchAccount(existing.id)
            return existing.id
        }
        val id = "account_${System.currentTimeMillis()}"
        updateAccounts { it + AccountEntry(id = id, name = "Новый аккаунт") }
        switchAccount(id)
        return id
    }

    fun switchAndRestart(id: String) {
        if (switchAccount(id)) restartApp()
    }

    fun addAndRestart() {
        addAccount()
        restartApp()
    }

    fun updateAccountDetails(
        id: String,
        userId: Long,
        name: String,
        phone: String,
        username: String?,
        avatarFileId: Int?,
    ) {
        updateAccounts { list ->
            list.map { acc ->
                if (acc.id == id) {
                    acc.copy(
                        userId = userId,
                        name = name.ifEmpty { "Аккаунт" },
                        phone = phone,
                        username = username,
                        avatarFileId = avatarFileId,
                    )
                } else acc
            }
        }
    }

    /** Убирает неактивный аккаунт из списка и стирает его локальные данные. Сеанс в Telegram остаётся (его можно закрыть в «Устройствах»). */
    fun removeAccount(id: String): Boolean {
        if (id == _activeAccountId.value || _accounts.value.size <= 1) return false
        updateAccounts { list -> list.filterNot { it.id == id } }
        deleteData(id)
        return true
    }

    /**
     * Вызывать после успешного выхода из активного аккаунта (TDLib уже удалил его базу).
     * Если есть другие аккаунты — этот убирается из списка и активным становится первый из оставшихся,
     * иначе запись остаётся пустой и после перезапуска откроется экран входа. Затем приложение перезапускается.
     */
    fun finishLogoutAndRestart() {
        val current = _activeAccountId.value
        val others = _accounts.value.filterNot { it.id == current }
        if (others.isNotEmpty()) {
            updateAccounts { list -> list.filterNot { it.id == current } }
            deleteData(current)
            val next = others.first().id
            _activeAccountId.value = next
            prefs.edit().putString(KEY_ACTIVE_ID, next).commit()
        } else {
            updateAccounts { list ->
                list.map { if (it.id == current) AccountEntry(id = current, name = "Новый аккаунт") else it }
            }
        }
        restartApp()
    }

    /** Перезапуск процесса: TDLib-клиент привязан к аккаунту, выбранному при старте. */
    fun restartApp() {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            appContext.startActivity(launchIntent)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                Process.killProcess(Process.myPid())
            }, 300)
        }
    }

    private fun deleteData(id: String) {
        runCatching { File(appContext.filesDir, id).deleteRecursively() }
        runCatching {
            File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "${id}_files").deleteRecursively()
        }
    }

    private fun updateAccounts(transform: (List<AccountEntry>) -> List<AccountEntry>) {
        val updated = transform(_accounts.value)
        _accounts.value = updated
        saveAccounts(updated)
    }

    private fun loadActiveId(list: List<AccountEntry>): String {
        val saved = prefs.getString(KEY_ACTIVE_ID, DEFAULT_ACCOUNT_ID) ?: DEFAULT_ACCOUNT_ID
        return if (list.any { it.id == saved }) saved else list.first().id
    }

    private fun loadAccounts(): List<AccountEntry> {
        val default = listOf(AccountEntry(id = DEFAULT_ACCOUNT_ID, name = "Основной аккаунт"))
        val raw = prefs.getString(KEY_ACCOUNTS_JSON, null)
        if (raw.isNullOrBlank()) return default
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                AccountEntry(
                    id = obj.getString("id"),
                    userId = obj.optLong("userId", 0L),
                    name = obj.optString("name", "Аккаунт"),
                    phone = obj.optString("phone", ""),
                    username = obj.optString("username", "").ifEmpty { null },
                    avatarFileId = obj.optInt("avatarFileId", 0).takeIf { it != 0 },
                )
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: default
    }

    private fun saveAccounts(list: List<AccountEntry>) {
        val array = JSONArray()
        list.forEach { acc ->
            array.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("userId", acc.userId)
                    put("name", acc.name)
                    put("phone", acc.phone)
                    put("username", acc.username ?: "")
                    put("avatarFileId", acc.avatarFileId ?: 0)
                },
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, array.toString()).commit()
    }

    companion object {
        const val DEFAULT_ACCOUNT_ID = "tdlib"
        private const val KEY_ACTIVE_ID = "active_account_id"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
    }
}