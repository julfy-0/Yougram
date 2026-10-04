package app.yougram.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.data.ChatFolderItem
import app.yougram.data.ChatItem
import app.yougram.data.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(private val repository: ChatRepository) : ViewModel() {

    val chats: StateFlow<List<ChatItem>> = repository.chats
    val folders: StateFlow<List<ChatFolderItem>> = repository.folders

    private val _selectedFolder = MutableStateFlow<Int?>(null)
    /** id выбранной папки; null — вкладка «Все». */
    val selectedFolder: StateFlow<Int?> = _selectedFolder.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Состояние подгрузки ведётся отдельно для главного списка (ключ null) и для каждой папки.
    private val loading = HashSet<Int?>()
    private val allLoaded = HashSet<Int?>()

    init {
        loadMore()
    }

    fun selectFolder(id: Int?) {
        _selectedFolder.value = id
        loadMore()
    }

    /** Подгружает следующую порцию текущего списка чатов; безопасно вызывать часто. */
    fun loadMore() {
        val key = _selectedFolder.value?.takeIf { id -> folders.value.any { it.id == id } }
        if (key in loading || key in allLoaded) return
        loading += key
        viewModelScope.launch {
            try {
                if (!repository.loadChats(folderId = key)) allLoaded += key
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                loading -= key
            }
        }
    }

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatListViewModel(repository) }
        }
    }
}