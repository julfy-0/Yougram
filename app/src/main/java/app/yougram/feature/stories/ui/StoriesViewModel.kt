package app.yougram.feature.stories.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.feature.chat.data.ChatItem
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.stories.data.StoriesRepository
import app.yougram.feature.stories.data.StoryRef
import dev.g000sha256.tdl.dto.Story
import dev.g000sha256.tdl.dto.StoryInfo
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StoriesViewModel(
    private val repository: StoriesRepository,
    private val chats: ChatRepository,
) : ViewModel() {
    private val _stories = MutableStateFlow<List<StoryRef>>(emptyList())
    val stories: StateFlow<List<StoryRef>> = _stories.asStateFlow()
    private val _story = MutableStateFlow<Story?>(null)
    val story: StateFlow<Story?> = _story.asStateFlow()
    private val _mediaPath = MutableStateFlow<String?>(null)
    val mediaPath: StateFlow<String?> = _mediaPath.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Список чатов при создании VM обычно ещё пуст — ждём первую порцию и только потом ищем истории.
    init { viewModelScope.launch { chats.chats.first { it.isNotEmpty() }; refresh() } }

    fun refresh() = viewModelScope.launch {
        val ids = chats.chats.value.take(60).map(ChatItem::id)
        _stories.value = repository.activeStories(ids)
            .map { ref ->
                val chat = chats.chats.value.firstOrNull { it.id == ref.chatId }
                ref.copy(title = chat?.title ?: "История", avatarPath = chat?.avatarPath)
            }
            // Непросмотренные — первыми.
            .sortedByDescending { it.unread }
    }

    fun open(ref: StoryRef) = viewModelScope.launch {
        // chatId == 0 — «Моя история»: открывается экран публикации.
        if (ref.chatId == 0L) return@launch
        runCatching {
            _story.value = repository.loadStory(ref.chatId, ref.info.storyId)
            val file = File.createTempFile("yougram_story_", ".jpg")
            _mediaPath.value = repository.downloadStoryMedia(_story.value!!, file)
            repository.openStory(ref.chatId, ref.info.storyId)
        }.onFailure { _error.value = it.message ?: "Не удалось открыть историю" }
    }

    fun close() { _story.value = null; _mediaPath.value = null }

    fun publishPhoto(path: String, caption: String = "") = viewModelScope.launch {
        runCatching { repository.postPhoto(path, caption); refresh() }
            .onFailure { _error.value = it.message ?: "Не удалось опубликовать историю" }
    }

    companion object {
        fun factory(repository: StoriesRepository, chats: ChatRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { StoriesViewModel(repository, chats) }
        }
    }
}
