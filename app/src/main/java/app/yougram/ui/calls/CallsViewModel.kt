package app.yougram.ui.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.data.CallItem
import app.yougram.data.ChatRepository
import app.yougram.data.FileState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallsUiState(
    val calls: List<CallItem> = emptyList(),
    val onlyMissed: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

class CallsViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(CallsUiState())
    val state: StateFlow<CallsUiState> = _state.asStateFlow()

    private var nextOffset = ""
    private var endReached = false
    private var job: Job? = null

    init {
        reload()
    }

    fun setOnlyMissed(value: Boolean) {
        if (_state.value.onlyMissed == value) return
        _state.update { it.copy(onlyMissed = value, calls = emptyList(), loading = true, error = null) }
        reload()
    }

    private fun reload() {
        job?.cancel()
        nextOffset = ""
        endReached = false
        loadPage()
    }

    /** Подгружает следующую страницу; безопасно вызывать часто. */
    fun loadMore() {
        if (job?.isActive == true || endReached) return
        loadPage()
    }

    private fun loadPage() {
        val missed = _state.value.onlyMissed
        job = viewModelScope.launch {
            try {
                val page = repository.loadCalls(offset = nextOffset, onlyMissed = missed)
                nextOffset = page.nextOffset
                if (page.nextOffset.isEmpty() || page.items.isEmpty()) endReached = true
                _state.update { s ->
                    s.copy(
                        calls = (s.calls + page.items).distinctBy { it.messageId },
                        loading = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                endReached = true
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun fileState(fileId: Int): Flow<FileState> = repository.fileState(fileId)

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CallsViewModel(repository) }
        }
    }
}
