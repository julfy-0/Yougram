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
import app.yougram.data.ReplyPreview
import app.yougram.data.ReplyRef
import app.yougram.data.SenderInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/** Состояние поиска внутри чата. */
data class ChatSearchState(
    val query: String = "",
    /** Найденные сообщения: от новых к старым. */
    val ids: List<Long> = emptyList(),
    val total: Int = 0,
    /** Позиция текущего результата в [ids]; -1, если результата нет. */
    val index: Int = -1,
    val loading: Boolean = false,
    val notFound: Boolean = false,
    val nextFrom: Long = 0L,
)

class ChatViewModel(
    private val repository: ChatRepository,
    val chatId: Long,
    private val settings: SettingsRepository,
    /** Если не 0 — после загрузки истории прокрутить к этому сообщению (переход из глобального поиска). */
    val initialMessageId: Long = 0L,
) : ViewModel() {

    private val _search = MutableStateFlow(ChatSearchState())
    val search: StateFlow<ChatSearchState> = _search.asStateFlow()

    private val _jump = MutableStateFlow<Long?>(null)
    /** Сообщение, к которому нужно прокрутить; экран сбрасывает его через [consumeJump]. */
    val pendingJump: StateFlow<Long?> = _jump.asStateFlow()

    fun consumeJump() {
        _jump.value = null
    }

    private var searchJob: Job? = null
    private var moreJob: Job? = null
    private var jumpJob: Job? = null

    /** Новый запрос поиска по чату (с небольшой задержкой, пока пользователь печатает). */
    fun search(query: String) {
        searchJob?.cancel()
        moreJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _search.value = ChatSearchState()
            return
        }
        _search.value = ChatSearchState(query = q, loading = true)
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                val page = repository.searchInChat(chatId, q)
                _search.value = ChatSearchState(
                    query = q,
                    ids = page.ids,
                    total = maxOf(page.total, page.ids.size),
                    index = if (page.ids.isEmpty()) -1 else 0,
                    notFound = page.ids.isEmpty(),
                    nextFrom = page.nextFromMessageId,
                )
                page.ids.firstOrNull()?.let { jumpTo(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.update { it.copy(loading = false) }
                _state.update { it.copy(error = e.message ?: "Не удалось выполнить поиск") }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        moreJob?.cancel()
        _search.value = ChatSearchState()
    }

    /** К более старому результату (стрелка вверх). */
    fun searchOlder() {
        val s = _search.value
        if (s.ids.isEmpty()) return
        val next = s.index + 1
        if (next < s.ids.size) {
            selectResult(next)
            if (next >= s.ids.size - 3) loadMoreResults(selectNext = false)
        } else {
            loadMoreResults(selectNext = true)
        }
    }

    /** К более новому результату (стрелка вниз). */
    fun searchNewer() {
        val s = _search.value
        if (s.index > 0) selectResult(s.index - 1)
    }

    private fun selectResult(index: Int) {
        val id = _search.value.ids.getOrNull(index) ?: return
        _search.update { it.copy(index = index) }
        jumpTo(id)
    }

    private fun loadMoreResults(selectNext: Boolean) {
        val s = _search.value
        if (moreJob?.isActive == true || s.nextFrom == 0L || s.query.isEmpty()) return
        val q = s.query
        moreJob = viewModelScope.launch {
            try {
                val page = repository.searchInChat(chatId, q, fromMessageId = s.nextFrom)
                if (_search.value.query != q) return@launch
                _search.update { cur ->
                    cur.copy(
                        ids = (cur.ids + page.ids).distinct(),
                        total = maxOf(cur.total, cur.ids.size + page.ids.size),
                        nextFrom = page.nextFromMessageId,
                    )
                }
                if (selectNext) {
                    val cur = _search.value
                    if (cur.index + 1 < cur.ids.size) selectResult(cur.index + 1)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Не удалось загрузить результаты") }
            }
        }
    }

    /** Догружает историю до сообщения [messageId] и просит экран прокрутить к нему. */
    private fun jumpTo(messageId: Long) {
        jumpJob?.cancel()
        jumpJob = viewModelScope.launch {
            if (ensureLoaded(messageId)) _jump.value = messageId
            else _state.update { it.copy(error = "Сообщение слишком далеко в истории") }
        }
    }

    /** Подгружает старые сообщения, пока нужное не окажется в списке (не больше 30 порций по 100). */
    private suspend fun ensureLoaded(messageId: Long): Boolean {
        _state.first { !it.loading }
        repeat(MAX_JUMP_ROUNDS) {
            val s = _state.value
            if (s.messages.any { it.id == messageId }) return true
            if (historyEnd || s.messages.isEmpty() || s.messages.last().id < messageId) return false
            try {
                if (!fetchOlder(100)) return false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return false
            }
        }
        return _state.value.messages.any { it.id == messageId }
    }

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _senders = MutableStateFlow<Map<Long, SenderInfo>>(emptyMap())

    /** Авторы сообщений (ключ — id пользователя или чата), подгружаются по мере показа. */
    val senders: StateFlow<Map<Long, SenderInfo>> = _senders.asStateFlow()
    private val requestedSenders = HashSet<Long>()

    private val _replies = MutableStateFlow<Map<Long, ReplyPreview>>(emptyMap())

    /** Превью оригиналов для цитат: id сообщения-оригинала -> автор и текст. */
    val replies: StateFlow<Map<Long, ReplyPreview>> = _replies.asStateFlow()
    private val requestedReplies = HashSet<Long>()

    fun ensureReply(ref: ReplyRef) {
        if (!requestedReplies.add(ref.messageId)) return
        viewModelScope.launch {
            val preview = repository.replyPreview(ref)
            _replies.update { it + (ref.messageId to preview) }
        }
    }

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

    private val olderMutex = Mutex()

    /** Подгружает порцию сообщений старше самого старого из загруженных; false — загружать нечего. */
    private suspend fun fetchOlder(limit: Int): Boolean = olderMutex.withLock {
        val s = _state.value
        if (historyEnd || s.loading || s.messages.isEmpty()) return@withLock false
        val oldestId = s.messages.last().id
        _state.update { it.copy(loadingOlder = true) }
        try {
            val older = repository.loadOlder(chatId, oldestId, limit)
            if (older.isEmpty()) historyEnd = true
            val low = if (older.isEmpty()) 0L else older.minOf { it.id }
            _state.update { cur ->
                val merged = (cur.messages + older.asReversed() + deletedFrom(low)).distinctBy { it.id }.sortedByDescending { it.id }
                cur.copy(messages = merged)
            }
            older.isNotEmpty()
        } finally {
            _state.update { it.copy(loadingOlder = false) }
        }
    }

    /** Подгружает сообщения старше самого старого из загруженных. */
    fun loadOlder() {
        val s = _state.value
        if (olderJob?.isActive == true || historyEnd || s.loading || s.messages.isEmpty()) return
        olderJob = viewModelScope.launch {
            try {
                fetchOlder(40)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
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
            repository.reactionEvents.collect { event ->
                if (event.chatId != chatId) return@collect
                _state.update { s ->
                    s.copy(
                        messages = s.messages.map {
                            if (it.id == event.messageId) it.copy(reactions = event.reactions) else it
                        },
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
                if (initialMessageId != 0L) {
                    // Даём списку примениться и закончить стартовую прокрутку вниз.
                    delay(250)
                    jumpTo(initialMessageId)
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** Чаты для выбора адресата при пересылке. */
    val chats get() = repository.chats

    private fun action(notice: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (notice != null) _state.update { it.copy(error = notice) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Не удалось выполнить действие") }
            }
        }
    }

    /** Тап по реакции: если она уже моя — снимаем, иначе ставим. */
    fun react(messageId: Long, emoji: String) {
        val mine = _state.value.messages.firstOrNull { it.id == messageId }
            ?.reactions?.any { it.emoji == emoji && it.chosen } == true
        action { repository.react(chatId, messageId, emoji, remove = mine) }
    }

    /** Эмодзи-реакции, доступные для сообщения; пустой список — использовать набор по умолчанию. */
    suspend fun availableReactions(messageId: Long): List<String> =
        repository.availableReactions(chatId, messageId)

    fun edit(messageId: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) action { repository.editText(chatId, messageId, trimmed) }
    }

    fun delete(messageId: Long) = action { repository.deleteMessage(chatId, messageId) }

    fun pin(messageId: Long) = action("Сообщение закреплено") { repository.pinMessage(chatId, messageId) }

    fun forward(messageId: Long, toChatId: Long) =
        action("Сообщение переслано") { repository.forwardMessage(toChatId, chatId, messageId) }

    fun send(text: String, replyToId: Long? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.sendText(chatId, trimmed, replyToId)
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
        private const val MAX_JUMP_ROUNDS = 30

        fun factory(
            repository: ChatRepository,
            chatId: Long,
            settings: SettingsRepository,
            initialMessageId: Long = 0L,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(repository, chatId, settings, initialMessageId) }
        }
    }
}