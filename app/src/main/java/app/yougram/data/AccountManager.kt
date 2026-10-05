package app.yougram.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AccountEntry(
    val id: String, // e.g. "tdlib" or "account_12345678"
    val userId: Long = 0L,
    val name: String = "",
    val phone: String = "",
    val username: String? = null,
    val avatarFileId: Int? = null,
)

class AccountManager(context: Context) {
    private val prefs = context.getSharedPreferences("yougram_accounts", Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow(loadAccounts())
    val accounts: StateFlow<List<AccountEntry>> = _accounts.asStateFlow()

    private val _activeAccountId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_ID, DEFAULT_ACCOUNT_ID) ?: DEFAULT_ACCOUNT_ID,
    )
    val activeAccountId: StateFlow<String> = _activeAccountId.asStateFlow()

    fun switchAccount(id: String) {
        if (id == _activeAccountId.value) return
        _activeAccountId.value = id
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun createNewAccount(): String {
        val newId = "account_${System.currentTimeMillis()}"
        val newEntry = AccountEntry(id = newId, name = "Новый аккаунт")
        updateAccounts { it + newEntry }
        switchAccount(newId)
        return newId
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

    fun removeAccount(id: String) {
        val current = _accounts.value
        if (current.size <= 1) return // Не удаляем единственный аккаунт
        val updated = current.filterNot { it.id == id }
        updateAccounts { updated }
        if (_activeAccountId.value == id) {
            val fallback = updated.firstOrNull()?.id ?: DEFAULT_ACCOUNT_ID
            switchAccount(fallback)
        }
    }

    private fun updateAccounts(transform: (List<AccountEntry>) -> List<AccountEntry>) {
        val updated = transform(_accounts.value)
        _accounts.value = updated
        saveAccounts(updated)
    }

    private fun loadAccounts(): List<AccountEntry> {
        val raw = prefs.getString(KEY_ACCOUNTS_JSON, null)
        if (raw.isNullOrBlank()) {
            return listOf(AccountEntry(id = DEFAULT_ACCOUNT_ID, name = "Основной аккаунт"))
        }
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
        }.getOrDefault(listOf(AccountEntry(id = DEFAULT_ACCOUNT_ID, name = "Основной аккаунт")))
    }

    private fun saveAccounts(list: List<AccountEntry>) {
        val array = JSONArray()
        list.forEach { acc ->
            val obj = JSONObject().apply {
                put("id", acc.id)
                put("userId", acc.userId)
                put("name", acc.name)
                put("phone", acc.phone)
                put("username", acc.username ?: "")
                put("avatarFileId", acc.avatarFileId ?: 0)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, array.toString()).apply()
    }

    companion object {
        const val DEFAULT_ACCOUNT_ID = "tdlib"
        private const val KEY_ACTIVE_ID = "active_account_id"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
    }
}
