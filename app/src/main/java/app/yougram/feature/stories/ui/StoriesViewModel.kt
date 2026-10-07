package app.yougram.feature.stories.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.feature.chat.data.ChatItem
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.stories.data.ChatMeta
import app.yougram.feature.stories.data.StoriesRepository
import app.yougram.feature.stories.data.StoryRef
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Лента историй для списка чатов. Всегда содержит «Моя история» первой, даже если историй нет. */
class StoriesViewModel(
    private val repository: StoriesRepository,
    private val chats: ChatRepository,
) : ViewModel() {
    private val meta = MutableStateFlow<Map<Long, ChatMeta>>(emptyMap())

    val stories: StateFlow<List<StoryRef>> =
        combine(repository.refs, chats.chats, meta) { refs, chatList, extra ->
            val byId = chatList.associateBy(ChatItem::id)
            refs.map { ref ->
                val chat = byId[ref.chatId]
                val m = extra[ref.chatId]
                ref.copy(
                    title = if (ref.isOwn) "Моя история" else chat?.title ?: m?.title ?: ref.title,
                    avatarPath = chat?.avatarPath ?: m?.avatarPath,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.refs.value)

    init {
        repository.start()
        // Для авторов, которых нет в списке чатов (или для себя), подтягиваем имя и аватар отдельно.
        viewModelScope.launch {
            repository.refs.collect { refs ->
                val known = chats.chats.value.map(ChatItem::id).toSet()
                refs.filter { it.chatId != 0L && (it.isOwn || it.chatId !in known) && it.chatId !in meta.value }
                    .forEach { ref ->
                        launch { repository.chatMeta(ref.chatId)?.let { m -> meta.update { it + (ref.chatId to m) } } }
                    }
            }
        }
        viewModelScope.launch {
            repository.refresh(emptyList())
            chats.chats.first { it.isNotEmpty() }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh(chats.chats.value.take(60).map(ChatItem::id)) }
    }

    companion object {
        fun factory(repository: StoriesRepository, chats: ChatRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { StoriesViewModel(repository, chats) }
        }
    }
}