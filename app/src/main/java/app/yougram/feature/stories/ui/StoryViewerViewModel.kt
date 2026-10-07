package app.yougram.feature.stories.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.stories.data.ChatMeta
import app.yougram.feature.stories.data.StoriesRepository
import app.yougram.feature.stories.data.StoryItem
import app.yougram.feature.stories.data.StoryRef
import app.yougram.feature.stories.data.StoryViewer
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min

data class ViewerState(
    val ref: StoryRef,
    val index: Int,
    val total: Int,
    val storyId: Int,
    val key: String,
    val item: StoryItem?,
    val failed: Boolean,
)

data class ViewersState(
    val loading: Boolean = false,
    val list: List<StoryViewer> = emptyList(),
    val error: String? = null,
)

/** Просмотр историй: очередь авторов, переходы вперёд/назад, отметка просмотра, реакции, ответы, удаление. */
class StoryViewerViewModel(
    private val repo: StoriesRepository,
    private val chats: ChatRepository,
    startChatId: Long,
    startStoryId: Int,
) : ViewModel() {
    private data class Pos(val user: Int, val story: Int)

    private val _queue = MutableStateFlow(buildQueue(startChatId, startStoryId))
    private val _pos = MutableStateFlow(startPos(startChatId, startStoryId))
    private val items = MutableStateFlow<Map<String, StoryItem>>(emptyMap())
    private val failed = MutableStateFlow<Set<String>>(emptySet())
    private val meta = MutableStateFlow<Map<Long, ChatMeta>>(emptyMap())

    private val _viewers = MutableStateFlow(ViewersState())
    val viewers: StateFlow<ViewersState> = _viewers.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val state: StateFlow<ViewerState?> = combine(_queue, _pos, items, failed, meta) { q, p, loaded, bad, m ->
        val ref = q.getOrNull(p.user) ?: return@combine null
        val id = ref.storyIds.getOrNull(p.story) ?: return@combine null
        val key = "${ref.chatId}:$id"
        val info = m[ref.chatId]
        ViewerState(
            ref = ref.copy(title = info?.title ?: ref.title, avatarPath = info?.avatarPath ?: ref.avatarPath),
            index = p.story, total = ref.storyIds.size, storyId = id, key = key,
            item = loaded[key], failed = key in bad,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val currentKey = combine(_queue, _pos) { q, p ->
        q.getOrNull(p.user)?.let { r -> r.storyIds.getOrNull(p.story)?.let { r.chatId to it } }
    }.distinctUntilChanged()

    init {
        // Имя и аватар авторов: из списка чатов, иначе отдельным запросом.
        viewModelScope.launch {
            val known = chats.chats.value.associateBy { it.id }
            _queue.value.forEach { ref ->
                if (ref.isOwn) {
                    launch { repo.chatMeta(ref.chatId)?.let { m -> meta.update { it + (ref.chatId to m) } } }
                } else {
                    val chat = known[ref.chatId]
                    if (chat != null) meta.update { it + (ref.chatId to ChatMeta(chat.title, chat.avatarPath)) }
                    else launch { repo.chatMeta(ref.chatId)?.let { m -> meta.update { it + (ref.chatId to m) } } }
                }
            }
        }
        // Загрузка текущей истории, отметка просмотра и закрытие при уходе.
        viewModelScope.launch {
            currentKey.collectLatest { key ->
                if (key == null) return@collectLatest
                val (chatId, id) = key
                if (load(chatId, id) != null) repo.view(chatId, id)
                launch { peekNext()?.let { (c, s) -> load(c, s) } }
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { repo.close(chatId, id) }
                }
            }
        }
    }

    private fun buildQueue(chatId: Long, storyId: Int): List<StoryRef> {
        val base = repo.refs.value.filter { it.hasStories }
        return if (base.any { it.chatId == chatId }) base
        else base + StoryRef(chatId, storyIds = listOf(storyId), isOwn = false)
    }

    private fun startPos(chatId: Long, storyId: Int): Pos {
        val q = _queue.value
        val u = q.indexOfFirst { it.chatId == chatId }.coerceAtLeast(0)
        val s = q.getOrNull(u)?.storyIds?.indexOf(storyId)?.takeIf { it >= 0 } ?: 0
        return Pos(u, s)
    }

    private suspend fun load(chatId: Long, id: Int): StoryItem? {
        val key = "$chatId:$id"
        items.value[key]?.let { return it }
        return runCatching { repo.loadItem(chatId, id) }
            .onSuccess { item -> items.update { it + (key to item) } }
            .onFailure { failed.update { it + key } }
            .getOrNull()
    }

    private fun firstUnread(ref: StoryRef): Int =
        ref.storyIds.indexOfFirst { it > ref.maxReadId }.takeIf { it >= 0 } ?: 0

    private fun peekNext(): Pair<Long, Int>? {
        val p = _pos.value
        val q = _queue.value
        val ref = q.getOrNull(p.user) ?: return null
        ref.storyIds.getOrNull(p.story + 1)?.let { return ref.chatId to it }
        val nextRef = q.getOrNull(p.user + 1) ?: return null
        return nextRef.storyIds.getOrNull(firstUnread(nextRef))?.let { nextRef.chatId to it }
    }

    /** false — истории закончились. */
    fun next(): Boolean {
        val p = _pos.value
        val q = _queue.value
        val ref = q.getOrNull(p.user) ?: return false
        return when {
            p.story + 1 < ref.storyIds.size -> { _pos.value = Pos(p.user, p.story + 1); true }
            p.user + 1 < q.size -> { _pos.value = Pos(p.user + 1, firstUnread(q[p.user + 1])); true }
            else -> false
        }
    }

    /** false — это самая первая история (экран просто перезапускает прогресс). */
    fun prev(): Boolean {
        val p = _pos.value
        val q = _queue.value
        return when {
            p.story > 0 -> { _pos.value = Pos(p.user, p.story - 1); true }
            p.user > 0 -> { _pos.value = Pos(p.user - 1, q[p.user - 1].storyIds.lastIndex); true }
            else -> false
        }
    }

    fun react(emoji: String) {
        val s = state.value ?: return
        val item = s.item ?: return
        val remove = item.myReaction == emoji
        val updated = item.copy(myReaction = if (remove) null else emoji)
        items.update { it + (s.key to updated) }
        viewModelScope.launch {
            runCatching { repo.react(s.ref.chatId, s.storyId, if (remove) null else emoji) }.onFailure {
                items.update { m -> m + (s.key to item) }
                _message.value = "Не удалось поставить реакцию"
            }
        }
    }

    fun reply(text: String, onDone: (Boolean) -> Unit) {
        val s = state.value ?: return
        viewModelScope.launch {
            val ok = runCatching { repo.reply(s.ref.chatId, s.storyId, text.trim()) }.isSuccess
            if (!ok) _message.value = "Не удалось отправить ответ"
            onDone(ok)
        }
    }

    /** true — в очереди ещё есть истории, false — экран нужно закрыть. */
    fun deleteCurrent(): Boolean {
        val s = state.value ?: return false
        val p = _pos.value
        val q = _queue.value.toMutableList()
        val ref = q.getOrNull(p.user) ?: return false
        viewModelScope.launch { runCatching { repo.delete(ref.chatId, s.storyId) } }
        val ids = ref.storyIds - s.storyId
        if (ids.isEmpty()) {
            q.removeAt(p.user)
            if (q.isEmpty()) return false
            _queue.value = q
            val u = min(p.user, q.lastIndex)
            _pos.value = Pos(u, firstUnread(q[u]))
        } else {
            q[p.user] = ref.copy(storyIds = ids)
            _queue.value = q
            _pos.value = Pos(p.user, min(p.story, ids.lastIndex))
        }
        return true
    }

    fun loadViewers() {
        val s = state.value ?: return
        _viewers.value = ViewersState(loading = true)
        viewModelScope.launch {
            _viewers.value = runCatching { repo.viewers(s.storyId) }.fold(
                onSuccess = { ViewersState(list = it) },
                onFailure = { ViewersState(error = "Не удалось загрузить просмотры") },
            )
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(
            repo: StoriesRepository,
            chats: ChatRepository,
            chatId: Long,
            storyId: Int,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { StoryViewerViewModel(repo, chats, chatId, storyId) }
        }
    }
}