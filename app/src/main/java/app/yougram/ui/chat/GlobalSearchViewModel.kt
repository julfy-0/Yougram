package app.yougram.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.data.ChatRepository
import app.yougram.data.PeopleSearch
import app.yougram.data.SearchChatHit
import app.yougram.data.SearchMessageHit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GlobalSearchState(
    val query: String = "",
    val chats: List<SearchChatHit> = emptyList(),
    val contacts: List<SearchChatHit> = emptyList(),
    val global: List<SearchChatHit> = emptyList(),
    val messages: List<SearchMessageHit> = emptyList(),
    val loadingPeople: Boolean = false,
    val loadingMessages: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = chats.isEmpty() && contacts.isEmpty() && global.isEmpty() && messages.isEmpty()
}

/** Глобальный поиск в списке чатов: чаты, контакты, публичные чаты и сообщения. */
class GlobalSearchViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _state = MutableStateFlow(GlobalSearchState())
    val state: StateFlow<GlobalSearchState> = _state.asStateFlow()

    private var job: Job? = null
    private var moreJob: Job? = null
    private var nextOffset = ""

    private var local: PeopleSearch? = null
    private var remote: PeopleSearch? = null

    fun search(query: String) {
        val q = query.trim()
        if (q == _state.value.query) return
        job?.cancel()
        moreJob?.cancel()
        nextOffset = ""
        local = null
        remote = null
        if (q.isEmpty()) {
            _state.value = GlobalSearchState()
            return
        }
        _state.value = GlobalSearchState(query = q, loadingPeople = true, loadingMessages = true)
        job = viewModelScope.launch {
            delay(300)
            coroutineScope {
                launch { runPeople(q) }
                launch { runMessages(q) }
            }
        }
    }

    private suspend fun runPeople(q: String) {
        // Сначала быстрый локальный поиск, затем серверный: результаты появляются по мере готовности.
        try {
            local = repository.searchPeopleLocal(q)
            publishPeople(q, done = false)
            remote = repository.searchPeopleRemote(q)
            publishPeople(q, done = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { if (it.query == q) it.copy(loadingPeople = false, error = e.message) else it }
        }
    }

    private fun publishPeople(q: String, done: Boolean) {
        val l = local
        val r = remote
        val chats = ((l?.chats.orEmpty()) + (r?.chats.orEmpty())).distinctBy { it.chatId }
        val chatIds = chats.map { it.chatId }.toSet()
        val contacts = l?.contacts.orEmpty().filter { it.chatId !in chatIds }.distinctBy { it.chatId }
        val taken = chatIds + contacts.map { it.chatId }
        val global = r?.global.orEmpty().filter { it.chatId !in taken }.distinctBy { it.chatId }
        _state.update {
            if (it.query != q) it
            else it.copy(chats = chats, contacts = contacts, global = global, loadingPeople = !done)
        }
    }

    private suspend fun runMessages(q: String) {
        try {
            val page = repository.searchMessages(q)
            nextOffset = page.nextOffset
            _state.update {
                if (it.query != q) it
                else it.copy(messages = page.hits, loadingMessages = false, canLoadMore = page.nextOffset.isNotEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { if (it.query == q) it.copy(loadingMessages = false, error = e.message) else it }
        }
    }

    /** Подгружает следующую страницу найденных сообщений; безопасно вызывать часто. */
    fun loadMoreMessages() {
        val s = _state.value
        if (moreJob?.isActive == true || nextOffset.isEmpty() || s.query.isEmpty() || s.loadingMessages) return
        val q = s.query
        val offset = nextOffset
        moreJob = viewModelScope.launch {
            _state.update { it.copy(loadingMessages = true) }
            try {
                val page = repository.searchMessages(q, offset)
                if (_state.value.query != q) return@launch
                nextOffset = page.nextOffset
                _state.update {
                    it.copy(
                        messages = (it.messages + page.hits).distinctBy { hit -> hit.chatId to hit.messageId },
                        loadingMessages = false,
                        canLoadMore = page.nextOffset.isNotEmpty(),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMessages = false, error = e.message) }
            }
        }
    }

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { GlobalSearchViewModel(repository) }
        }
    }
}