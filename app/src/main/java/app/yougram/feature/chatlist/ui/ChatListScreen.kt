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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatListViewModel(private val repository: ChatRepository) : ViewModel() {

    val chats: StateFlow<List<ChatItem>> = repository.chats
    val folders: StateFlow<List<ChatFolderItem>> = repository.folders

    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    /** Выбранные долгим нажатием чаты; пусто — режим выбора выключен. */
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    fun toggleSelected(chatId: Long) = _selected.update { if (chatId in it) it - chatId else it + chatId }

    fun clearSelection() { _selected.value = emptySet() }

    private fun selectedChats() = chats.value.filter { it.id in _selected.value }

    private fun forSelected(action: suspend (ChatItem) -> Unit) {
        val items = selectedChats()
        clearSelection()
        viewModelScope.launch {
            items.forEach { item ->
                try {
                    action(item)
                } catch (e: Exception) {
                    _error.value = e.message
                }
            }
        }
    }

    /** Если все выбранные уже без звука — включает звук, иначе выключает. */
    fun muteSelected() {
        val mute = !selectedChats().all { it.muted }
        forSelected { repository.setMuted(it.id, mute) }
    }

    fun archiveSelected() = forSelected { repository.setChatArchived(it.id, true) }

    fun deleteSelected() = forSelected { repository.removeChat(it.id) }

    /** Если все выбранные закреплены — открепляет, иначе закрепляет (в текущей папке). */
    fun pinSelected() {
        val key = _selectedFolder.value
        val pin = !selectedChats().all { (key ?: 0) in it.pinnedLists }
        forSelected { repository.setPinned(it.id, key, pin) }
    }

    /** Прочитать все непрочитанные чаты текущей вкладки («Все» или выбранной папки). */
    fun markAllRead() {
        val key = _selectedFolder.value?.takeIf { id -> folders.value.any { it.id == id } }
        val ids = chats.value
            .filter { c ->
                c.unreadCount > 0 &&
                        if (key == null) c.archiveOrder == 0L else (c.folderOrders[key] ?: 0L) != 0L
            }
            .map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.markChatsRead(ids)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

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
        clearSelection()
        _selectedFolder.value = id
        loadMore()
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