package app.yougram.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.feature.account.data.AccountInfo
import app.yougram.feature.account.data.AccountRepository
import app.yougram.feature.account.data.BirthdateInfo
import app.yougram.feature.account.data.BlockedItem
import app.yougram.feature.account.data.PersonalChatOption
import app.yougram.feature.account.data.PrivacyDetail
import app.yougram.feature.account.data.PrivacyKey
import app.yougram.feature.account.data.PrivacyLevel
import app.yougram.feature.account.data.SecurityInfo
import app.yougram.feature.account.data.SessionItem
import app.yougram.feature.account.data.StorageInfo
import app.yougram.feature.account.data.WebsiteItem
import app.yougram.feature.badge.data.YougramBadge
import app.yougram.feature.chat.data.ChatFolderItem
import app.yougram.feature.chat.data.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsState(
    val account: AccountInfo? = null,
    /** null — ещё не загружено. */
    val sessions: List<SessionItem>? = null,
    val privacy: Map<PrivacyKey, PrivacyLevel> = emptyMap(),
    val privacyDetail: Map<PrivacyKey, PrivacyDetail> = emptyMap(),
    val security: SecurityInfo = SecurityInfo(),
    /** null — ещё не загружено. */
    val blocked: List<BlockedItem>? = null,
    val websites: List<WebsiteItem>? = null,
    /** Код для смены почты отправлен, ждём ввод. */
    val emailCodeSent: Boolean = false,
    val storage: StorageInfo? = null,
    /** Каналы, которые можно выбрать личным; null — ещё не загружено. */
    val personalChats: List<PersonalChatOption>? = null,
    /** Код для смены номера отправлен, ждём ввод. */
    val phoneCodeSent: Boolean = false,
    val error: String? = null,
)

/** Состояние подэкранов настроек, которым нужен TDLib: аккаунт, устройства, конфиденциальность, память. */
class SettingsDetailsViewModel(
    private val account: AccountRepository,
    chats: ChatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    val folders: StateFlow<List<ChatFolderItem>> = chats.folders

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun loadAccount() = launchCatching {
        val info = account.loadAccount()
        _state.update { it.copy(account = info) }
    }

    fun saveName(firstName: String, lastName: String) = launchCatching {
        account.setName(firstName.trim(), lastName.trim())
        _state.update { it.copy(account = account.loadAccount()) }
    }

    fun saveUsername(username: String) = launchCatching {
        account.setUsername(username.trim().removePrefix("@"))
        _state.update { it.copy(account = account.loadAccount()) }
    }

    fun saveBio(bio: String) = launchCatching {
        // Текущий хвост (метка и баннер Yougram) читаем заново: он мог измениться после загрузки экрана.
        val tail = YougramBadge.tail(account.loadAccount().bio)
        account.setBio(bio.trim() + tail)
        _state.update { it.copy(account = account.loadAccount()) }
    }

    fun saveBirthdate(value: BirthdateInfo?) = launchCatching {
        account.setBirthdate(value)
        _state.update { it.copy(account = account.loadAccount()) }
    }

    fun requestPhoneChange(phone: String) = launchCatching {
        account.requestPhoneChange(phone)
        _state.update { it.copy(phoneCodeSent = true) }
    }

    fun confirmPhoneChange(code: String) = launchCatching {
        account.confirmPhoneChange(code)
        _state.update { it.copy(phoneCodeSent = false, account = account.loadAccount()) }
    }

    fun cancelPhoneChange() = _state.update { it.copy(phoneCodeSent = false) }

    fun loadPersonalChats() = launchCatching {
        val list = account.loadPersonalChats()
        _state.update { it.copy(personalChats = list) }
    }

    fun clearPersonalChats() = _state.update { it.copy(personalChats = null) }

    fun setPersonalChat(chatId: Long) = launchCatching {
        account.setPersonalChat(chatId)
        _state.update { it.copy(account = account.loadAccount()) }
    }

    /** После выхода TDLib закрыт и пересоздать клиент нельзя — [onDone] перезапускает приложение. */
    fun logOut(onDone: () -> Unit) = launchCatching {
        account.logOut()
        onDone()
    }

    fun loadSessions() = launchCatching {
        val list = account.loadSessions()
        _state.update { it.copy(sessions = list) }
    }

    fun linkDevice(link: String) = launchCatching {
        account.confirmQrLogin(link)
        _state.update { it.copy(sessions = account.loadSessions()) }
    }

    fun terminateSession(id: Long) = launchCatching {
        account.terminateSession(id)
        _state.update { it.copy(sessions = account.loadSessions()) }
    }

    fun terminateOtherSessions() = launchCatching {
        account.terminateOtherSessions()
        _state.update { it.copy(sessions = account.loadSessions()) }
    }

    fun loadPrivacy(keys: List<PrivacyKey> = PrivacyKey.entries) {
        keys.forEach { key ->
            viewModelScope.launch {
                runCatching { account.loadPrivacy(key) }.onSuccess { detail ->
                    _state.update {
                        it.copy(
                            privacy = it.privacy + (key to detail.level),
                            privacyDetail = it.privacyDetail + (key to detail),
                        )
                    }
                }
            }
        }
    }

    fun setPrivacy(key: PrivacyKey, level: PrivacyLevel) = launchCatching {
        account.setPrivacy(key, level)
        val detail = account.loadPrivacy(key)
        _state.update {
            it.copy(
                privacy = it.privacy + (key to detail.level),
                privacyDetail = it.privacyDetail + (key to detail),
            )
        }
    }

    fun loadSecurity() = launchCatching {
        val info = account.loadSecurity()
        _state.update { it.copy(security = info) }
    }

    fun setCloudPassword(old: String, new: String, hint: String) = launchCatching {
        account.setCloudPassword(old, new, hint)
        _state.update { it.copy(security = account.loadSecurity()) }
    }

    fun setAutoDelete(seconds: Int) = launchCatching {
        account.setAutoDelete(seconds)
        _state.update { it.copy(security = it.security.copy(autoDeleteSeconds = seconds)) }
    }

    fun setAccountTtl(days: Int) = launchCatching {
        account.setAccountTtl(days)
        _state.update { it.copy(security = it.security.copy(ttlDays = days)) }
    }

    fun requestLoginEmail(email: String) = launchCatching {
        account.requestLoginEmail(email)
        _state.update { it.copy(emailCodeSent = true) }
    }

    fun confirmLoginEmail(code: String) = launchCatching {
        account.confirmLoginEmail(code)
        _state.update { it.copy(emailCodeSent = false, security = account.loadSecurity()) }
    }

    fun cancelLoginEmail() = _state.update { it.copy(emailCodeSent = false) }

    fun setTopChats(enabled: Boolean) = launchCatching {
        _state.update { it.copy(security = it.security.copy(topChats = enabled)) }
        account.setTopChats(enabled)
    }

    fun clearPaymentData(onDone: () -> Unit) = launchCatching {
        account.clearPaymentData()
        onDone()
    }

    fun clearImportedContacts(onDone: () -> Unit) = launchCatching {
        account.clearImportedContacts()
        onDone()
    }

    fun loadBlocked() = launchCatching {
        val list = account.loadBlocked()
        _state.update { it.copy(blocked = list) }
    }

    fun unblock(item: BlockedItem) = launchCatching {
        account.unblock(item)
        val list = account.loadBlocked()
        _state.update { it.copy(blocked = list, security = it.security.copy(blocked = list.size)) }
    }

    fun loadWebsites() = launchCatching {
        val list = account.loadWebsites()
        _state.update { it.copy(websites = list) }
    }

    fun disconnectWebsite(id: Long) = launchCatching {
        account.disconnectWebsite(id)
        _state.update { it.copy(websites = account.loadWebsites()) }
    }

    fun disconnectAllWebsites() = launchCatching {
        account.disconnectAllWebsites()
        _state.update { it.copy(websites = emptyList()) }
    }

    fun loadStorage() = launchCatching {
        val info = account.loadStorage()
        _state.update { it.copy(storage = info) }
    }

    fun clearCache() = launchCatching {
        account.clearCache()
        _state.update { it.copy(storage = account.loadStorage()) }
    }

    companion object {
        fun factory(account: AccountRepository, chats: ChatRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { SettingsDetailsViewModel(account, chats) } }
    }
}