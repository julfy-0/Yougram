package app.yougram.feature.stories.data

import app.yougram.core.telegram.TelegramClient
import app.yougram.core.telegram.getOrThrow
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.ChatActiveStories
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputMessageReplyToStory
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.InputStoryContentPhoto
import dev.g000sha256.tdl.dto.InputStoryContentVideo
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.Story
import dev.g000sha256.tdl.dto.StoryContentPhoto
import dev.g000sha256.tdl.dto.StoryContentVideo
import dev.g000sha256.tdl.dto.StoryInteractionTypeView
import dev.g000sha256.tdl.dto.StoryListMain
import dev.g000sha256.tdl.dto.StoryPrivacySettings
import dev.g000sha256.tdl.dto.StoryPrivacySettingsCloseFriends
import dev.g000sha256.tdl.dto.StoryPrivacySettingsContacts
import dev.g000sha256.tdl.dto.StoryPrivacySettingsEveryone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Круг в ленте историй / элемент очереди просмотра: один автор и id его активных историй. */
data class StoryRef(
    val chatId: Long,
    val title: String = "",
    val avatarPath: String? = null,
    val storyIds: List<Int> = emptyList(),
    val maxReadId: Int = 0,
    val isOwn: Boolean = false,
    val order: Long = 0L,
) {
    val hasStories: Boolean get() = storyIds.isNotEmpty()
    val unread: Boolean get() = storyIds.any { it > maxReadId }

    /** Первая непросмотренная история, а если все просмотрены — первая. */
    val storyId: Int get() = storyIds.firstOrNull { it > maxReadId } ?: storyIds.firstOrNull() ?: 0
}

data class StoryItem(
    val id: Int,
    val date: Int,
    val isVideo: Boolean,
    val supported: Boolean,
    val caption: String,
    val durationSec: Double,
    val isOwn: Boolean,
    val canReply: Boolean,
    val canDelete: Boolean,
    val viewCount: Int,
    val reactionCount: Int,
    val myReaction: String?,
    val closeFriends: Boolean,
    val mediaPath: String?,
    val pending: Boolean,
)

data class StoryViewer(val name: String, val date: Int, val reaction: String?)

data class ChatMeta(val title: String, val avatarPath: String?)

enum class StoryAudience { Everyone, Contacts, CloseFriends }

/** TDLib-истории: лента, просмотр, реакции, ответы, публикация, удаление, просмотры. */
class StoriesRepository(
    private val telegram: TelegramClient,
    private val scope: CoroutineScope,
    private val ghost: () -> Boolean = { false },
) {
    private data class Active(val ids: List<Int>, val maxRead: Int, val order: Long)

    private val client get() = telegram.client
    private val active = MutableStateFlow<Map<Long, Active>>(emptyMap())
    private val _myId = MutableStateFlow(0L)
    private val metaCache = HashMap<Long, ChatMeta>()
    private var started = false

    /** Упорядоченная лента: свой круг первым, затем непросмотренные, затем по порядку TDLib. Title/avatar заполняет UI. */
    val refs: StateFlow<List<StoryRef>> = combine(active, _myId) { map, me ->
        val own = map[me]
        val ownRef = StoryRef(me, "Моя история", null, own?.ids.orEmpty(), own?.maxRead ?: 0, isOwn = true)
        val others = map.filterKeys { it != me }
            .map { (id, a) -> StoryRef(id, "", null, a.ids, a.maxRead, order = a.order) }
            .sortedWith(compareByDescending<StoryRef> { it.unread }.thenByDescending { it.order })
        listOf(ownRef) + others
    }.stateIn(scope, SharingStarted.Eagerly, listOf(StoryRef(0L, "Моя история", isOwn = true)))

    /** Подписка на живые обновления; идемпотентна. */
    fun start() {
        if (started) return
        started = true
        scope.launch { runCatching { _myId.value = client.getMe().getOrThrow().id } }
        scope.launch { client.chatActiveStoriesUpdates.collect { apply(it.activeStories) } }
    }

    private fun apply(a: ChatActiveStories) {
        active.update { m ->
            if (a.stories.isEmpty()) m - a.chatId
            else m + (a.chatId to Active(a.stories.map { it.storyId }, a.maxReadStoryId, a.order))
        }
    }

    suspend fun refresh(chatIds: List<Long>) {
        start()
        runCatching { client.loadActiveStories(storyList = StoryListMain()).getOrThrow() }
        val me = _myId.value
        coroutineScope {
            (listOf(me) + chatIds).filter { it != 0L }.distinct().chunked(12).forEach { chunk ->
                chunk.map { id ->
                    async { runCatching { apply(client.getChatActiveStories(chatId = id).getOrThrow()) } }
                }.awaitAll()
            }
        }
    }

    suspend fun chatMeta(chatId: Long): ChatMeta? = metaCache[chatId] ?: withContext(Dispatchers.IO) {
        runCatching {
            val chat = client.getChat(chatId = chatId).getOrThrow()
            val file = chat.photo?.small
            var path = file?.local?.path?.takeIf { it.isNotBlank() && file.local.isDownloadingCompleted }
            if (path == null && file != null) {
                val r = client.downloadFile(fileId = file.id, priority = 16, offset = 0L, limit = 0L, synchronous = true)
                if (r is TdlResult.Success) path = r.result.local.path.takeIf { it.isNotBlank() }
            }
            ChatMeta(if (chatId == _myId.value) "Моя история" else chat.title, path).also { metaCache[chatId] = it }
        }.getOrNull()
    }

    /** Загружает историю и скачивает её медиа (синхронно). */
    suspend fun loadItem(chatId: Long, storyId: Int): StoryItem = withContext(Dispatchers.IO) {
        val story = client.getStory(storyPosterChatId = chatId, storyId = storyId, onlyLocal = false).getOrThrow()
        map(story, download(story))
    }

    private suspend fun download(story: Story): String? {
        val fileId = when (val c = story.content) {
            is StoryContentPhoto -> c.photo.sizes.maxByOrNull { it.width * it.height }?.photo?.id
            is StoryContentVideo -> c.video.video.id
            else -> null
        } ?: return null
        val r = client.downloadFile(fileId = fileId, priority = 32, offset = 0L, limit = 0L, synchronous = true)
        return (r as? TdlResult.Success)?.result?.local?.path?.takeIf { it.isNotBlank() }
    }

    private fun map(s: Story, path: String?): StoryItem {
        val c = s.content
        val duration = (c as? StoryContentVideo)?.video?.duration?.toDouble() ?: 0.0
        return StoryItem(
            id = s.id,
            date = s.date,
            isVideo = c is StoryContentVideo,
            supported = c is StoryContentPhoto || c is StoryContentVideo,
            caption = s.caption.text,
            durationSec = duration,
            isOwn = s.canBeDeleted && s.posterChatId == _myId.value,
            canReply = s.canBeReplied,
            canDelete = s.canBeDeleted,
            viewCount = s.interactionInfo?.viewCount ?: 0,
            reactionCount = s.interactionInfo?.reactionCount ?: 0,
            myReaction = (s.chosenReactionType as? ReactionTypeEmoji)?.emoji,
            closeFriends = s.privacySettings is StoryPrivacySettingsCloseFriends,
            mediaPath = path,
            pending = s.isBeingPosted,
        )
    }

    /** Отметить просмотренной (в режиме призрака — нет). */
    suspend fun view(chatId: Long, storyId: Int) {
        if (ghost()) return
        runCatching { client.openStory(storyPosterChatId = chatId, storyId = storyId).getOrThrow() }
    }

    suspend fun close(chatId: Long, storyId: Int) {
        if (ghost()) return
        runCatching { client.closeStory(storyPosterChatId = chatId, storyId = storyId).getOrThrow() }
    }

    /** emoji == null — снять реакцию. */
    suspend fun react(chatId: Long, storyId: Int, emoji: String?) {
        client.setStoryReaction(
            storyPosterChatId = chatId,
            storyId = storyId,
            reactionType = emoji?.let { ReactionTypeEmoji(emoji = it) },
            updateRecentReactions = true,
        ).getOrThrow()
    }

    suspend fun reply(chatId: Long, storyId: Int, text: String) {
        client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = InputMessageReplyToStory(storyPosterChatId = chatId, storyId = storyId),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = emptyArray()),
                linkPreviewOptions = null,
                clearDraft = true,
            ),
        ).getOrThrow()
    }

    suspend fun delete(chatId: Long, storyId: Int) {
        client.deleteStory(storyPosterChatId = chatId, storyId = storyId).getOrThrow()
    }

    suspend fun viewers(storyId: Int): List<StoryViewer> = withContext(Dispatchers.IO) {
        val r = client.getStoryInteractions(
            storyId = storyId,
            query = "",
            onlyContacts = false,
            preferForwards = false,
            preferWithReaction = false,
            offset = "",
            limit = 100,
        ).getOrThrow()
        r.interactions.map { i ->
            val name = when (val a = i.actorId) {
                is MessageSenderUser -> client.getUser(userId = a.userId).getOrThrow()
                    .let { u -> listOf(u.firstName, u.lastName).filter { it.isNotBlank() }.joinToString(" ") }
                is MessageSenderChat -> client.getChat(chatId = a.chatId).getOrThrow().title
                else -> "—"
            }
            val reaction = ((i.type as? StoryInteractionTypeView)?.chosenReactionType as? ReactionTypeEmoji)?.emoji
            StoryViewer(name, i.interactionDate, reaction)
        }
    }

    private fun privacy(a: StoryAudience): StoryPrivacySettings = when (a) {
        StoryAudience.Everyone -> StoryPrivacySettingsEveryone(longArrayOf())
        StoryAudience.Contacts -> StoryPrivacySettingsContacts(longArrayOf())
        StoryAudience.CloseFriends -> StoryPrivacySettingsCloseFriends()
    }

    suspend fun post(
        path: String,
        isVideo: Boolean,
        durationSec: Double,
        caption: String,
        audience: StoryAudience,
        periodSec: Int,
        protect: Boolean,
    ) {
        val me = client.getMe().getOrThrow().id
        val content = if (isVideo) {
            InputStoryContentVideo(
                video = InputFileLocal(path = path),
                addedStickerFileIds = intArrayOf(),
                duration = durationSec,
                coverFrameTimestamp = 0.0,
                isAnimation = false,
            )
        } else {
            InputStoryContentPhoto(photo = InputFileLocal(path = path), addedStickerFileIds = intArrayOf())
        }
        client.postStory(
            chatId = me,
            content = content,
            areas = null,
            caption = caption.takeIf { it.isNotBlank() }?.let { FormattedText(it, emptyArray()) },
            privacySettings = privacy(audience),
            albumIds = intArrayOf(),
            activePeriod = periodSec,
            fromStoryFullId = null,
            isPostedToChatPage = false,
            protectContent = protect,
        ).getOrThrow()
        refresh(emptyList())
    }
}