package app.yougram.feature.chat.comments.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yougram.feature.chat.comments.data.CommentsRepository
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageThreadInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SenderInfo(val name: String, val avatarPath: String?)

data class CommentsState(
    val loading: Boolean = true,
    val comments: List<Message> = emptyList(),
    val error: Boolean = false,
    val senders: Map<String, SenderInfo> = emptyMap(),
)

fun senderKey(m: Message): String = when (val s = m.senderId) {
    is MessageSenderUser -> "u${s.userId}"
    is MessageSenderChat -> "c${s.chatId}"
    else -> "?"
}

class CommentsViewModel(
    private val repo: CommentsRepository,
    private val channelChatId: Long,
    private val postId: Long,
    /** Имя и аватарка автора комментария. */
    private val resolve: suspend (Message) -> SenderInfo,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsState())
    val state: StateFlow<CommentsState> = _state.asStateFlow()
    private var info: MessageThreadInfo? = null

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = false)
            val i = repo.openThread(channelChatId, postId)
            if (i == null) {
                _state.value = _state.value.copy(loading = false, error = true)
                return@launch
            }
            info = i
            publish(i)
        }
    }

    fun send(text: String) {
        val i = info ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            if (repo.sendComment(i, text.trim())) publish(i)
        }
    }

    private suspend fun publish(i: MessageThreadInfo) {
        val list = repo.loadComments(i).reversed()
        val known = _state.value.senders.toMutableMap()
        list.distinctBy(::senderKey).forEach { m ->
            val key = senderKey(m)
            if (key !in known) known[key] = runCatching { resolve(m) }.getOrDefault(SenderInfo("", null))
        }
        _state.value = CommentsState(loading = false, comments = list, senders = known)
    }
}
