package app.yougram.feature.groupadmin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.feature.groupadmin.data.AdminLogEntry
import app.yougram.feature.groupadmin.data.ChatStatsResult
import app.yougram.feature.groupadmin.data.GroupAdminRepository
import app.yougram.feature.groupadmin.data.InviteLinkItem
import app.yougram.feature.groupadmin.data.JoinRequestItem
import app.yougram.feature.groupadmin.data.LinkLifetime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminTab(val title: String) {
    LOG("Журнал"),
    REQUESTS("Заявки"),
    LINKS("Ссылки"),
    STATS("Данные"),
    SETTINGS("Режим"),
}

data class GroupAdminState(
    val tab: AdminTab = AdminTab.LOG,
    val log: List<AdminLogEntry>? = null,
    val logEnd: Boolean = false,
    val logLoadingMore: Boolean = false,
    val requests: List<JoinRequestItem>? = null,
    val links: List<InviteLinkItem>? = null,
    val revokedLinks: List<InviteLinkItem> = emptyList(),
    /** null — медленный режим недоступен (канал или обычная группа). */
    val slowMode: Int? = null,
    val slowModeLoaded: Boolean = false,
    val autoDelete: Int = 0,
    val stats: ChatStatsResult? = null,
    val statsLoaded: Boolean = false,
    val statsError: String? = null,
    val error: String? = null,
)

/** Как runCatching, но отмену корутины не глотает (иначе закрытый экран продолжал бы «работать»). */
private inline fun <T> safeCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

class GroupAdminViewModel(
    private val repository: GroupAdminRepository,
    private val chatId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(GroupAdminState())
    val state: StateFlow<GroupAdminState> = _state.asStateFlow()

    val slowModeSteps get() = repository.slowModeSteps
    val autoDeleteSteps get() = repository.autoDeleteSteps

    init {
        loadLog()
    }

    fun selectTab(tab: AdminTab) {
        _state.update { it.copy(tab = tab) }
        when (tab) {
            AdminTab.LOG -> if (_state.value.log == null) loadLog()
            AdminTab.REQUESTS -> loadRequests()
            AdminTab.LINKS -> loadLinks()
            AdminTab.STATS -> if (!_state.value.statsLoaded) loadStats()
            AdminTab.SETTINGS -> if (!_state.value.slowModeLoaded) loadSlowMode()
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun fail(e: Throwable) = _state.update { it.copy(error = e.message ?: "Не удалось выполнить действие") }

    // ── Журнал ──

    private var logJob: kotlinx.coroutines.Job? = null

    fun loadLog() {
        if (logJob?.isActive == true) return
        logJob = viewModelScope.launch { fetchLog() }
    }

    private suspend fun fetchLog() {
        safeCall { repository.adminLog(chatId) }
            .onSuccess { page -> _state.update { it.copy(log = page, logEnd = page.size < 50) } }
            .onFailure { e -> _state.update { it.copy(log = emptyList(), logEnd = true, error = e.message) } }
    }

    fun loadMoreLog() {
        val current = _state.value
        val last = current.log?.lastOrNull() ?: return
        if (current.logEnd || current.logLoadingMore) return
        _state.update { it.copy(logLoadingMore = true) }
        viewModelScope.launch {
            safeCall { repository.adminLog(chatId, fromEventId = last.id) }
                .onSuccess { page ->
                    _state.update { it.copy(log = it.log.orEmpty() + page, logEnd = page.size < 50, logLoadingMore = false) }
                }
                .onFailure { e -> _state.update { it.copy(logLoadingMore = false, logEnd = true, error = e.message) } }
        }
    }

    // ── Заявки ──

    fun loadRequests() = viewModelScope.launch {
        safeCall { repository.joinRequests(chatId) }
            .onSuccess { list -> _state.update { it.copy(requests = list) } }
            .onFailure { e -> _state.update { it.copy(requests = emptyList(), error = e.message) } }
    }

    fun processRequest(userId: Long, approve: Boolean) = viewModelScope.launch {
        safeCall { repository.processJoinRequest(chatId, userId, approve) }
            .onSuccess { _state.update { s -> s.copy(requests = s.requests?.filterNot { it.userId == userId }) } }
            .onFailure(::fail)
    }

    fun processAllRequests(approve: Boolean) = viewModelScope.launch {
        val all = _state.value.requests.orEmpty()
        all.forEach { r ->
            safeCall { repository.processJoinRequest(chatId, r.userId, approve) }
                .onSuccess { _state.update { s -> s.copy(requests = s.requests?.filterNot { it.userId == r.userId }) } }
                .onFailure(::fail)
        }
    }

    // ── Ссылки ──

    fun loadLinks() = viewModelScope.launch {
        safeCall { repository.inviteLinks(chatId, revoked = false) to repository.inviteLinks(chatId, revoked = true) }
            .onSuccess { (active, revoked) -> _state.update { it.copy(links = active, revokedLinks = revoked) } }
            .onFailure { e -> _state.update { it.copy(links = emptyList(), error = e.message) } }
    }

    fun createLink(name: String, lifetime: LinkLifetime, memberLimit: Int, createsJoinRequest: Boolean) =
        viewModelScope.launch {
            safeCall { repository.createInviteLink(chatId, name, lifetime, memberLimit, createsJoinRequest) }
                .onSuccess { loadLinks() }
                .onFailure(::fail)
        }

    fun revokeLink(link: String) = viewModelScope.launch {
        safeCall { repository.revokeInviteLink(chatId, link) }.onSuccess { loadLinks() }.onFailure(::fail)
    }

    fun deleteRevokedLink(link: String) = viewModelScope.launch {
        safeCall { repository.deleteRevokedInviteLink(chatId, link) }.onSuccess { loadLinks() }.onFailure(::fail)
    }

    // ── Медленный режим ──

    private fun loadSlowMode() = viewModelScope.launch {
        val auto = safeCall { repository.autoDeleteTime(chatId) }.getOrDefault(0)
        safeCall { repository.slowModeDelay(chatId) }
            .onSuccess { delay -> _state.update { it.copy(slowMode = delay, autoDelete = auto, slowModeLoaded = true) } }
            .onFailure { e -> _state.update { it.copy(slowModeLoaded = true, error = e.message) } }
    }

    fun setSlowMode(seconds: Int) = viewModelScope.launch {
        val before = _state.value.slowMode
        _state.update { it.copy(slowMode = seconds) }
        safeCall { repository.setSlowModeDelay(chatId, seconds) }
            .onFailure { e -> _state.update { it.copy(slowMode = before, error = e.message) } }
    }

    private fun loadStats() = viewModelScope.launch {
        safeCall { repository.stats(chatId) }
            .onSuccess { r -> _state.update { it.copy(stats = r, statsLoaded = true, statsError = null) } }
            .onFailure { e -> _state.update { it.copy(statsLoaded = true, statsError = e.message ?: "Статистика недоступна") } }
    }

    fun setAutoDelete(seconds: Int) = viewModelScope.launch {
        val before = _state.value.autoDelete
        _state.update { it.copy(autoDelete = seconds) }
        safeCall { repository.setAutoDeleteTime(chatId, seconds) }
            .onFailure { e -> _state.update { it.copy(autoDelete = before, error = e.message) } }
    }

    companion object {
        fun factory(repository: GroupAdminRepository, chatId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { GroupAdminViewModel(repository, chatId) }
        }
    }
}
