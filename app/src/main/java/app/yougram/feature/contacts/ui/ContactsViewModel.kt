package app.yougram.feature.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.chat.data.ContactItem
import app.yougram.feature.chat.data.FileState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactsUiState(
    val contacts: List<ContactItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class ContactsViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(ContactsUiState())
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val list = repository.loadContacts().sortedBy { it.name.lowercase() }
                _state.update { it.copy(contacts = list, loading = false, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    /** Открывает личный чат с контактом и возвращает id чата в [onResult]. */
    fun openChat(userId: Long, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                onResult(repository.openPrivateChat(userId))
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun fileState(fileId: Int): Flow<FileState> = repository.fileState(fileId)

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ContactsViewModel(repository) }
        }
    }
}
