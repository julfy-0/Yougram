package app.yougram.feature.chat.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageThreadInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommentsState(
    val loading: Boolean = true,
    val comments: List<Message> = emptyList(),
    val error: Boolean = false,
)

class CommentsViewModel(
    private val repo: CommentsRepository,
    private val channelChatId: Long,
    private val postId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsState())
    val state: StateFlow<CommentsState> = _state.asStateFlow()
    private var info: MessageThreadInfo? = null

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.value = CommentsState(loading = true)
            val i = repo.openThread(channelChatId, postId)
            if (i == null) {
                _state.value = CommentsState(loading = false, error = true)
                return@launch
            }
            info = i
            _state.value = CommentsState(loading = false, comments = repo.loadComments(i).reversed())
        }
    }

    fun send(text: String) {
        val i = info ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            if (repo.sendComment(i, text.trim())) {
                _state.value = _state.value.copy(comments = repo.loadComments(i).reversed())
            }
        }
    }
}
