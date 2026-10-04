package app.yougram.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.data.ChatRepository
import app.yougram.data.EditRecord
import app.yougram.data.SettingsRepository
import app.yougram.data.FileState
import app.yougram.data.MessageItem
import app.yougram.data.SenderInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val title: String = "",
    /** Порядок: новые сообщения в начале списка (для LazyColumn с reverseLayout). */
    val messages: List<MessageItem> = emptyList(),
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val error: String? = null,
    val canSendMessages: Boolean = true,
    val isChannel: Boolean = false,
    val isGroup: Boolean = false,
    val avatarFileId: Int? = null,
    /** Сообщения, удалённые в Telegram, но сохранённые режимом шпиона. */
    val deletedIds: Set<Long> = emptySet(),
    /** История правок: id сообщения -> прежние версии текста (от старых к новым). */
    val edits: Map<Long, List<EditRecord>> = emptyMap(),
    /** Когда собеседник прочитал исходящее сообщение (unix, сек). */
    val readAt: Map<Long, Int> = emptyMap(),
    /** Пользователи из чёрного списка — нужны фильтру «скрывать от ЧС». */
    val blockedUserIds: Set<Long> = emptySet(),
)

class ChatViewModel(
    private val repository: ChatRepository,
    val chatId: Long,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _senders = MutableStateFlow<Map<Long, SenderInfo>>(emptyMap())

    /** Авторы сообщений (ключ — id пользователя или чата), подгружаются по мере показа. */
    val senders: StateFlow<Map<Long, SenderInfo>> = _senders.asStateFlow()
    private val requestedSenders = HashSet<Long>()

    fun ensureSender(key: Long) {
        if (!requestedSenders.add(key)) return
        viewModelScope.launch {
            repository.sender(key)?.let { info -> _senders.update { it + (key to info) } }
        }
    }

    /** Открывает профиль автора: для пользователя сначала создаёт личный чат. */
    fun openSender(key: Long, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(if (key > 0) repository.openPrivateChat(key) else key)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private var olderJob: Job? = null
    private var historyEnd = false

    /** Удалённые сообщения из архива шпиона; в список попадают по мере подгрузки истории. */
    private var deletedStore: List<MessageItem> = emptyList()

    private fun deletedFrom(lowBound: Long) = deletedStore.filter { it.id >= lowBound }

    /** Подгружает сообщения старше самого старого из загруженных. */
    fun loadOlder() {
        val s = _state.value
        if (olderJob?.isActive == true || historyEnd || s.loading || s.messages.isEmpty()) return
        val oldestId = s.messages.last().id
        olderJob = viewModelScope.launch {
            _state.update { it.copy(loadingOlder = true) }
            try {
                val older = repository.loadOlder(chatId, oldestId)
                if (older.isEmpty()) historyEnd = true
                val low = if (older.isEmpty()) 0L else older.minOf { it.id }
                _state.update { cur ->
                    val merged = (cur.messages + older.asReversed() + deletedFrom(low)).distinctBy { it.id }.sortedByDescending { it.id }
                    cur.copy(messages = merged, loadingOlder = false)
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingOlder = false, error = e.message) }
            }
        }
    }

    init {
        // Сначала подписываемся на новые сообщения, потом грузим историю — чтобы ничего не потерять.
        viewModelScope.launch {
            repository.incomingFor(chatId).collect { message ->
                _state.update { s ->
                    if (s.messages.any { it.id == message.id }) s
                    else s.copy(messages = listOf(message) + s.messages)
                }
                if (!message.isOutgoing) repository.markRead(chatId, listOf(message.id))
            }
        }
        viewModelScope.launch {
            repository.deletions.collect { event ->
                if (event.chatId != chatId) return@collect
                _state.update { s ->
                    s.copy(
                        messages = s.messages.filterNot { it.id in event.removedIds },
                        deletedIds = s.deletedIds + event.keptIds,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.contentUpdates.collect { event ->
                if (event.chatId != chatId) return@collect
                _state.update { s ->
                    s.copy(
                        messages = s.messages.map {
                            if (it.id == event.messageId) it.copy(text = event.text, media = event.media ?: it.media, summary = event.summary) else it
                        },
                        edits = if (event.oldText == null) s.edits else
                            s.edits + (event.messageId to (s.edits[event.messageId].orEmpty() + EditRecord(event.oldText, event.at))),
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.readEvents.collect { event ->
                if (event.chatId != chatId) return@collect
                _state.update { s ->
                    val fresh = s.messages
                        .filter { it.isOutgoing && it.id <= event.lastMessageId && it.id !in s.readAt }
                        .associate { it.id to event.at }
                    s.copy(readAt = s.readAt + fresh)
                }
            }
        }
        viewModelScope.launch {
            runCatching { repository.blockedUserIds() }.onSuccess { ids ->
                _state.update { it.copy(blockedUserIds = ids) }
            }
        }
        viewModelScope.launch {
            try {
                val chatInfo = repository.getChatInfo(chatId)
                val history = repository.loadHistory(chatId) // от старых к новым
                val snapshot = repository.spySnapshot(chatId)
                deletedStore = snapshot.deleted
                // Если история короткая, она загружена целиком — показываем все сохранённые удалённые.
                val low = if (history.size < 50) 0L else history.minOf { it.id }
                _state.update { s ->
                    val merged = (history.asReversed() + s.messages + deletedFrom(low)).distinctBy { it.id }.sortedByDescending { it.id }
                    s.copy(
                        title = chatInfo.title,
                        messages = merged,
                        loading = false,
                        canSendMessages = chatInfo.canSendMessages,
                        isChannel = chatInfo.isChannel,
                        isGroup = chatInfo.isGroup,
                        avatarFileId = chatInfo.avatarFileId,
                        deletedIds = s.deletedIds + deletedFrom(low).map { it.id },
                        edits = snapshot.edits + s.edits,
                        readAt = snapshot.readAt + s.readAt,
                    )
                }
                repository.markRead(chatId, history.filter { !it.isOutgoing }.map { it.id })
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.sendText(chatId, trimmed)
                // Режим призрака: сообщения читаем только когда пользователь сам что-то делает в чате.
                if (settings.ghost.value.enabled && settings.ghost.value.readOnAction) {
                    val unread = _state.value.messages.filter { !it.isOutgoing }.take(50).map { it.id }
                    repository.markRead(chatId, unread, force = true)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun sendPhoto(path: String) = sendMedia { repository.sendPhoto(chatId, path) }

    fun sendDocument(path: String) = sendMedia { repository.sendDocument(chatId, path) }

    fun sendVoice(path: String, duration: Int) = sendMedia { repository.sendVoice(chatId, path, duration) }

    fun sendVideoNote(path: String, duration: Int, length: Int) =
        sendMedia { repository.sendVideoNote(chatId, path, duration, length) }

    private fun sendMedia(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (settings.ghost.value.enabled && settings.ghost.value.readOnAction) {
                    val unread = _state.value.messages.filter { !it.isOutgoing }.take(50).map { it.id }
                    repository.markRead(chatId, unread, force = true)
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    /** Скрывает сообщения пользователя (теневой бан), имя берём из Telegram. */
    fun shadowBan(userId: Long) {
        viewModelScope.launch {
            settings.addShadowBan(userId, repository.userName(userId))
        }
    }

    fun fileState(fileId: Int): Flow<FileState> = repository.fileState(fileId)

    fun download(fileId: Int, priority: Int = 8) = repository.download(fileId, priority)

    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        fun factory(repository: ChatRepository, chatId: Long, settings: SettingsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(repository, chatId, settings) }
        }
    }
}