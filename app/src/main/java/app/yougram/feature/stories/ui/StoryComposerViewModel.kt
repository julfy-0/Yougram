package app.yougram.feature.stories.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.feature.stories.data.StoriesRepository
import app.yougram.feature.stories.data.StoryAudience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Подготовленный к публикации файл истории. */
data class PreparedMedia(
    val path: String,
    val isVideo: Boolean,
    val durationSec: Double,
)

/** Публикация истории: отправка подготовленного файла с подписью, аудиторией и сроком. */
class StoryComposerViewModel(private val repo: StoriesRepository) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun publish(
        media: PreparedMedia,
        caption: String,
        audience: StoryAudience,
        periodHours: Int,
        protect: Boolean,
        onDone: () -> Unit,
    ) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            runCatching {
                repo.post(
                    path = media.path,
                    isVideo = media.isVideo,
                    durationSec = media.durationSec,
                    caption = caption.trim(),
                    audience = audience,
                    periodSec = periodHours * 3600,
                    protect = protect,
                )
            }.onSuccess { onDone() }
                .onFailure { _error.value = it.message?.takeIf(String::isNotBlank) ?: "Не удалось опубликовать историю" }
            _busy.value = false
        }
    }

    fun clearError() { _error.value = null }

    companion object {
        fun factory(repo: StoriesRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { StoryComposerViewModel(repo) }
        }
    }
}