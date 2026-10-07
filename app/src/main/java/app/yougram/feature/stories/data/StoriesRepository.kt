package app.yougram.feature.stories.data

import app.yougram.core.telegram.TelegramClient
import app.yougram.core.telegram.getOrThrow
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputStoryContentPhoto
import dev.g000sha256.tdl.dto.Story
import dev.g000sha256.tdl.dto.StoryContentPhoto
import dev.g000sha256.tdl.dto.StoryContentVideo
import dev.g000sha256.tdl.dto.StoryInfo
import dev.g000sha256.tdl.dto.StoryPrivacySettingsEveryone
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** TDLib-backed stories: active stories, viewing and basic photo publishing. */
data class StoryRef(
    val chatId: Long,
    val info: StoryInfo,
    val title: String = "История",
    val avatarPath: String? = null,
    val unread: Boolean = true,
)

class StoriesRepository(private val telegram: TelegramClient) {
    suspend fun activeStories(chatIds: List<Long>): List<StoryRef> = withContext(Dispatchers.IO) {
        chatIds.distinct().mapNotNull { chatId ->
            runCatching {
                val active = telegram.client.getChatActiveStories(chatId).getOrThrow()
                val first = active.stories.firstOrNull { it.storyId > active.maxReadStoryId }
                val info = first ?: active.stories.lastOrNull()
                info?.let { StoryRef(chatId, it, unread = first != null) }
            }.getOrNull()
        }
    }

    suspend fun loadStory(chatId: Long, storyId: Int): Story =
        telegram.client.getStory(storyPosterChatId = chatId, storyId = storyId, onlyLocal = false).getOrThrow()

    suspend fun openStory(chatId: Long, storyId: Int) {
        telegram.client.openStory(storyPosterChatId = chatId, storyId = storyId).getOrThrow()
    }

    suspend fun downloadStoryMedia(story: Story, destination: File): String? = withContext(Dispatchers.IO) {
        val fileId = when (val content = story.content) {
            is StoryContentPhoto -> content.photo.sizes.maxByOrNull { it.width * it.height }?.photo?.id
            is StoryContentVideo -> content.video.video.id
            else -> null
        } ?: return@withContext null
        val result = telegram.client.downloadFile(fileId = fileId, priority = 32, offset = 0L, limit = 0L, synchronous = true)
        if (result !is TdlResult.Success) return@withContext null
        val path = result.result.local.path
        if (path.isBlank()) return@withContext null
        destination.parentFile?.mkdirs()
        File(path).copyTo(destination, overwrite = true)
        destination.absolutePath
    }

    suspend fun postPhoto(path: String, caption: String = "") =
        telegram.client.postStory(
            chatId = telegram.client.getMe().getOrThrow().id,
            content = InputStoryContentPhoto(photo = InputFileLocal(path = path), addedStickerFileIds = intArrayOf()),
            areas = null,
            caption = if (caption.isBlank()) null else dev.g000sha256.tdl.dto.FormattedText(caption, emptyArray()),
            privacySettings = StoryPrivacySettingsEveryone(longArrayOf()),
            albumIds = intArrayOf(),
            activePeriod = 86400,
            fromStoryFullId = null,
            isPostedToChatPage = false,
            protectContent = false,
        ).getOrThrow()
}
