package app.yougram.feature.chatlist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.feature.chat.data.ChatFolderItem
import app.yougram.feature.chat.data.ChatItem
import app.yougram.feature.chat.data.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(private val repository: ChatRepository) : ViewModel() {

    val chats: StateFlow<List<ChatItem>> = repository.chats
    val folders: StateFlow<List<ChatFolderItem>> = repository.folders
    val archivedChats: StateFlow<List<ChatItem>> = repository.archivedChats

    private val _archiveOpen = MutableStateFlow(false)
    /** true — вместо главного списка показан архив. */
    val archiveOpen: StateFlow<Boolean> = _archiveOpen.asStateFlow()

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
        _archiveOpen.value = false
        _selectedFolder.value = id
        loadMore()
    }

    fun openArchive() {
        _archiveOpen.value = true
        loadMore()
    }

    fun closeArchive() {
        _archiveOpen.value = false
    }

    fun setArchived(chatId: Long, archived: Boolean) {
        viewModelScope.launch {
            try {
                repository.setChatArchived(chatId, archived)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /** Подгружает следующую порцию текущего списка чатов; безопасно вызывать часто. */
    fun loadMore() {
        val archive = _archiveOpen.value
        val key = if (archive) ARCHIVE_KEY else _selectedFolder.value?.takeIf { id -> folders.value.any { it.id == id } }
        if (key in loading || key in allLoaded) return
        loading += key
        viewModelScope.launch {
            try {
                if (!repository.loadChats(folderId = key.takeIf { !archive }, archive = archive)) allLoaded += key
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                loading -= key
            }
        }
    }

    companion object {
        private const val ARCHIVE_KEY = Int.MIN_VALUE

        fun factory(repository: ChatRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatListViewModel(repository) }
        }
    }
}