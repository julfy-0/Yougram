package app.yougram.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.data.ChatRepository
import app.yougram.data.FileState
import app.yougram.data.MessageItem
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
)

class ChatViewModel(
    private val repository: ChatRepository,
    private val chatId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var olderJob: Job? = null
    private var historyEnd = false

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
                _state.update { cur ->
                    val merged = (cur.messages + older.asReversed()).distinctBy { it.id }.sortedByDescending { it.id }
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
            try {
                val chatInfo = repository.getChatInfo(chatId)
                val history = repository.loadHistory(chatId) // от старых к новым
                _state.update { s ->
                    val merged = (history.asReversed() + s.messages).distinctBy { it.id }.sortedByDescending { it.id }
                    s.copy(
                        title = chatInfo.title,
                        messages = merged,
                        loading = false,
                        canSendMessages = chatInfo.canSendMessages,
                        isChannel = chatInfo.isChannel,
                        isGroup = chatInfo.isGroup,
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
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun fileState(fileId: Int): Flow<FileState> = repository.fileState(fileId)

    fun download(fileId: Int, priority: Int = 8) = repository.download(fileId, priority)

    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        fun factory(repository: ChatRepository, chatId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(repository, chatId) }
        }
    }
}