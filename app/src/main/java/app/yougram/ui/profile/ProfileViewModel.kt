package app.yougram.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.data.ChatRepository
import app.yougram.data.FileState
import app.yougram.data.MessageItem
import app.yougram.data.ProfileDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val details: ProfileDetails? = null,
    /** Последние сообщения чата, из них собираются вкладки «Медиа», «Файлы», «Ссылки». */
    val shared: List<MessageItem> = emptyList(),
    val sharedLoading: Boolean = true,
    val deleted: List<MessageItem> = emptyList(),
    val error: String? = null,
    val left: Boolean = false,
)

class ProfileViewModel(
    private val repository: ChatRepository,
    private val chatId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val details = repository.loadProfileDetails(chatId)
                _state.update { it.copy(loading = false, details = details) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
        viewModelScope.launch {
            val items = runCatching { repository.deletedMessages(chatId) }.getOrDefault(emptyList())
            _state.update { it.copy(deleted = items) }
        }
        viewModelScope.launch {
            val items = runCatching { repository.sharedMessages(chatId) }.getOrDefault(emptyList())
            _state.update { it.copy(shared = items, sharedLoading = false) }
        }
    }

    fun toggleMute() {
        val current = _state.value.details ?: return
        val target = !current.muted
        _state.update { it.copy(details = current.copy(muted = target)) }
        viewModelScope.launch {
            try {
                repository.setMuted(chatId, target)
            } catch (e: Exception) {
                _state.update { it.copy(details = current, error = e.message) }
            }
        }
    }

    fun leave() {
        viewModelScope.launch {
            try {
                repository.leaveChat(chatId)
                _state.update { it.copy(left = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun fileState(fileId: Int): Flow<FileState> = repository.fileState(fileId)

    fun download(fileId: Int, priority: Int = 8) = repository.download(fileId, priority)

    companion object {
        fun factory(repository: ChatRepository, chatId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfileViewModel(repository, chatId) }
        }
    }
}