package app.yougram.data

import android.content.Context
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.BlockListMain
import dev.g000sha256.tdl.dto.CallDiscardReasonDeclined
import dev.g000sha256.tdl.dto.CallDiscardReasonMissed
import dev.g000sha256.tdl.dto.ChatListFolder
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatMemberStatus
import dev.g000sha256.tdl.dto.ChatMemberStatusAdministrator
import dev.g000sha256.tdl.dto.ChatMemberStatusBanned
import dev.g000sha256.tdl.dto.ChatMemberStatusCreator
import dev.g000sha256.tdl.dto.ChatMemberStatusLeft
import dev.g000sha256.tdl.dto.ChatMemberStatusMember
import dev.g000sha256.tdl.dto.ChatMemberStatusRestricted
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import dev.g000sha256.tdl.dto.ChatNotificationSettings
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.MessageTopic
import dev.g000sha256.tdl.dto.MessageTopicForum
import dev.g000sha256.tdl.dto.MessageTopicThread
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputChatPhotoStatic
import dev.g000sha256.tdl.dto.InputDocument
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.InputAnimation
import dev.g000sha256.tdl.dto.InputMessageContent
import dev.g000sha256.tdl.dto.InputMessageDocument
import dev.g000sha256.tdl.dto.InputMessageAnimation
import dev.g000sha256.tdl.dto.InputMessageSticker
import dev.g000sha256.tdl.dto.InputFileId
import dev.g000sha256.tdl.dto.StickerTypeRegular
import dev.g000sha256.tdl.dto.Sticker as TdSticker
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.InputMessageReplyToMessage
import dev.g000sha256.tdl.dto.ReactionTypeEmoji
import dev.g000sha256.tdl.dto.InputMessagePhoto
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.InputMessageVideoNote
import dev.g000sha256.tdl.dto.InputMessageVoiceNote
import dev.g000sha256.tdl.dto.InputPhoto
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageCall
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageInteractionInfo
import dev.g000sha256.tdl.dto.MessageReplyToMessage
import dev.g000sha256.tdl.dto.MessagePaidMedia
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.PaidMediaPhoto
import dev.g000sha256.tdl.dto.PaidMediaPreview
import dev.g000sha256.tdl.dto.PaidMediaVideo
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.SearchMessagesFilterEmpty
import dev.g000sha256.tdl.dto.User
import dev.g000sha256.tdl.dto.UserStatusLastMonth
import dev.g000sha256.tdl.dto.UserStatusLastWeek
import dev.g000sha256.tdl.dto.UserStatusOffline
import dev.g000sha256.tdl.dto.UserStatusOnline
import dev.g000sha256.tdl.dto.UserStatusRecently
import dev.g000sha256.tdl.dto.UserTypeBot
import app.yougram.plugin.NativePluginManager
import app.yougram.plugin.PluginMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import dev.g000sha256.tdl.dto.File as TdFile

data class ChatInfo(
    val title: String,
    val canSendMessages: Boolean = true,
    val isChannel: Boolean = false,
    val isGroup: Boolean = false,
    val avatarFileId: Int? = null,
)

/** Вид чата для уведомлений и их фильтров. */
enum class ChatKind { PRIVATE, GROUP, CHANNEL }

/** Модель чата для UI. */
data class ChatItem(
    val id: Long,
    val title: String,
    val lastMessage: String,
    val lastMessageDate: Int,
    val unreadCount: Int,
    /** Порядок в главном списке; больше = выше. */
    val order: Long,
    /** Путь к скачанному файлу аватарки или null, если аватарки нет / она ещё грузится. */
    val avatarPath: String? = null,
    /** Порядок в папках: id папки -> позиция; 0 или отсутствие — чата в папке нет. */
    val folderOrders: Map<Int, Long> = emptyMap(),
)

/** Папка с чатами. */
data class ChatFolderItem(val id: Int, val title: String)

/** Чат, контакт или публичный чат в результатах поиска. */
data class SearchChatHit(
    val chatId: Long,
    val title: String,
    /** @username, «Канал» или «Группа»; может быть пустым. */
    val subtitle: String,
    val avatarFileId: Int?,
)

/** Результаты поиска людей и чатов: свои чаты, контакты и публичные чаты по всему Telegram. */
data class PeopleSearch(
    val chats: List<SearchChatHit>,
    val contacts: List<SearchChatHit>,
    val global: List<SearchChatHit>,
)

/** Найденное сообщение в глобальном поиске. */
data class SearchMessageHit(
    val chatId: Long,
    val messageId: Long,
    val chatTitle: String,
    val avatarFileId: Int?,
    /** «Вы» или имя автора в группе; пусто в личных чатах и каналах. */
    val author: String,
    val text: String,
    val date: Int,
)

/** Страница глобального поиска сообщений; пустой [nextOffset] — результатов больше нет. */
data class MessageSearchPage(val hits: List<SearchMessageHit>, val nextOffset: String, val total: Int)

/** Страница поиска внутри чата: id сообщений от новых к старым; [nextFromMessageId] == 0 — конец. */
data class ChatSearchPage(val ids: List<Long>, val nextFromMessageId: Long, val total: Int)

enum class MediaKind(val label: String) {
    PHOTO("Фото"),
    VIDEO("Видео"),
    ANIMATION("GIF"),
    STICKER("Стикер"),
    DOCUMENT("Файл"),
    VOICE("Голосовое сообщение"),
    VIDEO_NOTE("Видеосообщение"),
}

/** Описание медиа в сообщении. Пути к файлам берутся отдельно через [ChatRepository.fileState]. */
data class MediaItem(
    val kind: MediaKind,
    /** Основной файл (для фото — самый большой размер). */
    val fileId: Int,
    /** Файл превью (размер фото / thumbnail видео) или null. */
    val previewFileId: Int?,
    val width: Int,
    val height: Int,
    val name: String,
    val mimeType: String,
    val size: Long,
    /** Крошечный JPEG для размытой заглушки, пока превью не скачалось. */
    val miniThumb: ByteArray?,
    /** Длительность в секундах (голосовые и кружки). */
    val duration: Int = 0,
    /** Сжатая форма волны голосового (5 бит на отсчёт) или null. */
    val waveform: ByteArray? = null,
    /** Платное медиа, которое ещё не куплено: файла нет, есть только размытая заглушка. */
    val locked: Boolean = false,
    /** Сколько звёзд стоит платное медиа (для заблокированного — цена открытия). */
    val paidStars: Long = 0L,
)

/** Стикер для панели выбора: файл TDLib уже можно скачать и отправить обратно по id. */
enum class StickerFmt { STATIC, TGS, WEBM }

data class StickerItem(
    val fileId: Int,
    val width: Int,
    val height: Int,
    val emoji: String,
    val thumbFileId: Int? = null,
    val format: StickerFmt = StickerFmt.STATIC,
)

data class StickerSetItem(val id: Long, val title: String, val cover: StickerItem?)

data class GifItem(val fileId: Int, val thumbFileId: Int?, val width: Int, val height: Int, val duration: Int)

/** Состояние панели вложений. */
data class StickerPanelState(
    val items: List<StickerItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/** Состояние скачивания файла TDLib. */
data class FileState(
    val path: String? = null,
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val active: Boolean = false,
)

/** Ссылка на сообщение, на которое отвечают. */
data class ReplyRef(val chatId: Long, val messageId: Long)

/** Превью оригинала для цитаты в пузыре. */
data class ReplyPreview(val author: String, val text: String)

/** Реакция под сообщением: сколько поставили и ставил ли я. */
data class ReactionItem(val emoji: String, val count: Int, val chosen: Boolean)

/** Реакции сообщения изменились (updateMessageInteractionInfo). */
data class ReactionEvent(val chatId: Long, val messageId: Long, val reactions: List<ReactionItem>)

/** Модель сообщения для UI. */
data class MessageItem(
    val id: Long,
    val chatId: Long,
    /** Текст сообщения или подпись к медиа. */
    val text: String,
    val isOutgoing: Boolean,
    val date: Int,
    val media: MediaItem? = null,
    val call: CallItem? = null,
    /** Краткое описание для списка чатов: текст, а для медиа без подписи — тип. */
    val summary: String = text,
    /** Автор-пользователь (для фильтров и теневого бана); null — канал/анонимный админ. */
    val senderUserId: Long? = null,
    /** Автор-чат (канал или анонимный админ группы); null — автор пользователь. */
    val senderChatId: Long? = null,
    /** Тема форума, в которой написано сообщение; 0 — вне тем. */
    val topicId: Int = 0,
    /** Сообщение, на которое это — ответ; null, если не ответ. */
    val reply: ReplyRef? = null,
    /** Реакции под сообщением. */
    val reactions: List<ReactionItem> = emptyList(),
    /** Количество комментариев/ответов в discussion thread. */
    val commentCount: Int = 0,
    /** Для сообщения существует discussion thread, доступный для комментариев. */
    val hasComments: Boolean = false,
    /** Ветка комментариев, в которой написано сообщение; 0 — вне ветки. */
    val threadId: Long = 0L,
    /** Сколько звёзд отправитель заплатил за это сообщение (платные сообщения); 0 — обычное. */
    val paidMessageStars: Long = 0L,
) {
    /** Ключ автора: id пользователя (>0) или id чата (<0). */
    val senderKey: Long? get() = senderUserId ?: senderChatId
}

/** Тема форума для списка тем. */
data class ForumTopicItem(
    val id: Int,
    val name: String,
    /** Цвет значка темы (RGB). */
    val color: Int,
    val isGeneral: Boolean,
    val isClosed: Boolean,
    val isPinned: Boolean,
    val unread: Int,
    val lastSender: String,
    val lastText: String,
    val lastDate: Int,
)

/** Автор сообщения для шапки в группах. */
data class SenderInfo(val key: Long, val name: String, val username: String?, val avatarFileId: Int?)

enum class ProfileKind { USER, BOT, GROUP, CHANNEL, OTHER }

/** Данные экрана профиля пользователя, группы или канала. */
data class ProfileDetails(
    val kind: ProfileKind,
    val chatId: Long,
    val title: String,
    val subtitle: String,
    val description: String,
    val phone: String?,
    /** Основной username без @. */
    val username: String?,
    val otherUsernames: List<String>,
    /** Ссылка вида t.me/... для групп и каналов. */
    val link: String?,
    val id: Long,
    val avatarFileId: Int?,
    val muted: Boolean = false,
)

/** Досье на пользователя: только то, что Telegram и так отдаёт клиенту о собеседнике. */
data class UserDossier(
    val id: Long,
    val name: String,
    val usernames: List<String>,
    val phone: String?,
    val bio: String,
    val isContact: Boolean,
    val isMutualContact: Boolean,
    val isPremium: Boolean,
    val isBot: Boolean,
    val isYougram: Boolean,
    val commonGroupsCount: Int,
    val commonGroups: List<String>,
    val deletedMessages: Int,
)

/** Контакт для UI. Аватарка берётся по [avatarFileId] через [ChatRepository.fileState]. */
data class ContactItem(
    val id: Long,
    val name: String,
    val statusText: String,
    val online: Boolean,
    val avatarFileId: Int?,
)

/** Профиль текущего пользователя для шапки настроек. */
data class ProfileItem(
    val id: Long,
    val name: String,
    val phone: String,
    val username: String?,
    val avatarFileId: Int?,
)

enum class CallKind { OUTGOING, INCOMING, MISSED, DECLINED }

data class CallItem(
    val messageId: Long,
    val chatId: Long,
    val title: String,
    val avatarFileId: Int?,
    val isOutgoing: Boolean,
    val isVideo: Boolean,
    val kind: CallKind,
    /** Длительность в секундах; 0 — звонок не состоялся. */
    val duration: Int,
    val date: Int,
)

/** Страница истории звонков; пустой [nextOffset] — больше страниц нет. */
data class CallPage(val items: List<CallItem>, val nextOffset: String)

/** Сообщения удалены: [keptIds] сохранены в архиве шпиона, [removedIds] нужно убрать из списка. */
data class DeleteEvent(val chatId: Long, val keptIds: Set<Long>, val removedIds: Set<Long>)

/** Содержимое сообщения изменилось; [oldText] не null, если прежний текст добавлен в историю правок. */
data class ContentEvent(
    val chatId: Long,
    val messageId: Long,
    val text: String,
    val media: MediaItem?,
    val summary: String,
    val oldText: String?,
    val at: Int,
)

/** Собеседник прочитал исходящие сообщения до [lastMessageId] включительно. */
data class ReadEvent(val chatId: Long, val lastMessageId: Long, val at: Int)

/** Данные архива шпиона для открытого чата. */
data class SpySnapshot(
    val deleted: List<MessageItem>,
    val edits: Map<Long, List<EditRecord>>,
    val readAt: Map<Long, Int>,
)

class ChatRepository(
    private val telegram: TelegramClient,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val spy: SpyStore,
    private val context: Context,
    private val plugins: NativePluginManager? = null,
) {
    private val client get() = telegram.client

    private data class ChatMeta(val isBot: Boolean, val isChannel: Boolean)
    private data class PendingSave(val chatId: Long, val name: String)

    private val metaCache = ConcurrentHashMap<Long, ChatMeta>()
    private val kindCache = ConcurrentHashMap<Long, ChatKind>()

    /** Сообщения, которые пользователь удаляет сам: шпион не должен оставлять их как «удалённые». */
    private val selfDeleting = ConcurrentHashMap.newKeySet<Long>()
    private val senderCache = ConcurrentHashMap<Long, SenderInfo>()
    private val pendingSaves = ConcurrentHashMap<Int, PendingSave>()

    private val _deletions = MutableSharedFlow<DeleteEvent>(extraBufferCapacity = 64)
    val deletions: SharedFlow<DeleteEvent> = _deletions.asSharedFlow()

    private val _contentUpdates = MutableSharedFlow<ContentEvent>(extraBufferCapacity = 64)
    val contentUpdates: SharedFlow<ContentEvent> = _contentUpdates.asSharedFlow()

    private val _readEvents = MutableSharedFlow<ReadEvent>(extraBufferCapacity = 64)
    val readEvents: SharedFlow<ReadEvent> = _readEvents.asSharedFlow()

    private val _reactionEvents = MutableSharedFlow<ReactionEvent>(extraBufferCapacity = 64)
    val reactionEvents: SharedFlow<ReactionEvent> = _reactionEvents.asSharedFlow()

    private val _yougramUsers = MutableStateFlow<Set<Long>>(emptySet())
    /** Пользователи, у которых найдена метка Yougram. */
    val yougramUsers: StateFlow<Set<Long>> = _yougramUsers.asStateFlow()

    private val _goldUsers = MutableStateFlow<Set<Long>>(YougramBadge.GOLD_USER_IDS)
    /** Пользователи с золотым значком помощника проекта. */
    val goldUsers: StateFlow<Set<Long>> = _goldUsers.asStateFlow()

    private val _creatorUsers = MutableStateFlow<Set<Long>>(setOf(YougramBadge.CREATOR_USER_ID))
    /** Пользователи с синим значком создателя Yougram. */
    val creatorUsers: StateFlow<Set<Long>> = _creatorUsers.asStateFlow()

    private val _ownUserId = MutableStateFlow(0L)
    /** Id текущего пользователя (0, пока не известен). */
    val ownUserId: StateFlow<Long> = _ownUserId.asStateFlow()

    private val _banners = MutableStateFlow<Map<Long, YougramBanner>>(emptyMap())
    /** Баннеры профилей пользователей Yougram (userId -> баннер). */
    val banners: StateFlow<Map<Long, YougramBanner>> = _banners.asStateFlow()

    /** Обновляет метку и баннер пользователя по его актуальному bio. */
    private fun noteBio(userId: Long, bio: String) {
        val marked = YougramBadge.hasMarker(bio)
        val isGold = YougramBadge.isGoldUser(userId, bio)
        val isCreator = YougramBadge.isCreatorUser(userId, bio)
        _yougramUsers.update { if (marked) it + userId else it - userId }
        _goldUsers.update { if (isGold) it + userId else it - userId }
        _creatorUsers.update { if (isCreator) it + userId else it - userId }
        val banner = if (marked) YougramBadge.bannerOf(bio) else null
        _banners.update { if (banner != null) it + (userId to banner) else it - userId }
    }

    private val badgeChecked = ConcurrentHashMap.newKeySet<Long>()
    private val badgeLimiter = Semaphore(3)

    /** Лениво проверяет bio пользователя на метку; результат кэшируется. */
    fun checkBadge(userId: Long) {
        if (userId <= 0L || _yougramUsers.value.contains(userId) || !badgeChecked.add(userId)) return
        scope.launch {
            badgeLimiter.withPermit {
                val full = when (val result = client.getUserFullInfo(userId = userId)) {
                    is TdlResult.Success -> result.result
                    is TdlResult.Failure -> null
                }
                if (full == null) {
                    badgeChecked.remove(userId) // не получилось — попробуем позже
                    return@withPermit
                }
                noteBio(userId, full.bio?.text.orEmpty())
            }
        }
    }

    /**
     * Приводит хвост bio текущего пользователя к нужному виду: метка Yougram и, если задан, баннер
     * (или ничего, если [enabled] выключен). Возвращает true, если после записи bio совпало с желаемым.
     */
    suspend fun syncOwnBadge(enabled: Boolean, banner: YougramBanner? = null): Boolean {
        val me = client.getMe().getOrThrow()
        _ownUserId.value = me.id
        val bio = client.getUserFullInfo(userId = me.id).getOrThrow().bio?.text.orEmpty()
        val desired = if (enabled) YougramBadge.MARKER + banner?.encode().orEmpty() else ""
        if (YougramBadge.tail(bio) != desired) {
            val newBio = YougramBadge.strip(bio) + desired
            if (newBio.length > YougramBadge.BIO_LIMIT) return false
            client.setBio(bio = newBio).getOrThrow()
        }
        val after = client.getUserFullInfo(userId = me.id).getOrThrow().bio?.text.orEmpty()
        noteBio(me.id, after)
        return YougramBadge.tail(after) == desired
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun now(): Int = (System.currentTimeMillis() / 1000).toInt()

    /**
     * Собственное состояние чата. DTO из TDLib — не data-классы (у них нет copy()),
     * поэтому храним нужные поля сами и обновляем их по апдейтам.
     */
    private data class ChatState(
        val id: Long,
        val title: String,
        val lastMessage: MessageItem?,
        val unreadCount: Int,
        /** Позиция в главном списке; 0 — чата в списке нет. */
        val order: Long,
        val avatarFileId: Int? = null,
        val avatarPath: String? = null,
        /** Позиции в папках: id папки -> order. */
        val folderOrders: Map<Int, Long> = emptyMap(),
    )

    private val chatStates = MutableStateFlow<Map<Long, ChatState>>(emptyMap())

    /** Состояния всех файлов, которые встречались в сообщениях: fileId -> состояние. */
    private val fileStates = MutableStateFlow<Map<Int, FileState>>(emptyMap())

    private val _chats = MutableStateFlow<List<ChatItem>>(emptyList())
    val chats: StateFlow<List<ChatItem>> = _chats.asStateFlow()

    private val _folders = MutableStateFlow<List<ChatFolderItem>>(emptyList())
    val folders: StateFlow<List<ChatFolderItem>> = _folders.asStateFlow()

    private val _newMessages = MutableSharedFlow<MessageItem>(extraBufferCapacity = 64)
    val newMessages: SharedFlow<MessageItem> = _newMessages.asSharedFlow()

    /** Подписки нужно оформить до первого запроса, поэтому вызывается из AppContainer.start(). */
    fun start() {
        scope.launch {
            client.chatFoldersUpdates.collect { update ->
                _folders.value = update.chatFolders.orEmpty().filterNotNull().map {
                    ChatFolderItem(id = it.id, title = it.name.text.text)
                }
            }
        }
        scope.launch {
            client.newChatUpdates.collect { update ->
                val chat = update.chat
                val order = chat.positions.firstOrNull { it.list is ChatListMain }?.order ?: 0L
                val folderOrders = chat.positions
                    .mapNotNull { p -> (p.list as? ChatListFolder)?.let { it.chatFolderId to p.order } }
                    .toMap()
                val avatar = chat.photo?.small
                val avatarDone = avatar?.local?.isDownloadingCompleted == true
                val state = ChatState(
                    id = chat.id,
                    title = chat.title,
                    lastMessage = chat.lastMessage?.toItem(),
                    unreadCount = chat.unreadCount,
                    order = order,
                    avatarFileId = avatar?.id,
                    avatarPath = if (avatarDone) avatar?.local?.path?.takeIf { it.isNotEmpty() } else null,
                    folderOrders = folderOrders,
                )
                chatStates.update { it + (chat.id to state) }
                if (avatar != null && !avatarDone) downloadAvatar(avatar.id)
                publish()
            }
        }
        scope.launch {
            client.chatPhotoUpdates.collect { update ->
                val avatar = update.photo?.small
                val avatarDone = avatar?.local?.isDownloadingCompleted == true
                chatStates.update { map ->
                    val state = map[update.chatId] ?: return@update map
                    map + (update.chatId to state.copy(
                        avatarFileId = avatar?.id,
                        avatarPath = if (avatarDone) avatar?.local?.path?.takeIf { it.isNotEmpty() } else null,
                    ))
                }
                if (avatar != null && !avatarDone) downloadAvatar(avatar.id)
                publish()
            }
        }
        scope.launch {
            // Любое обновление файла: сначала запоминаем состояние (для медиа), затем обрабатываем аватарки.
            client.fileUpdates.collect { update ->
                val file = update.file
                track(file)
                if (!file.local.isDownloadingCompleted) return@collect
                val path = file.local.path.takeIf { it.isNotEmpty() } ?: return@collect
                pendingSaves.remove(file.id)?.let { copyAttachment(path, it) }
                var changed = false
                chatStates.update { map ->
                    map.mapValues { (_, state) ->
                        if (state.avatarFileId == file.id && state.avatarPath != path) {
                            changed = true
                            state.copy(avatarPath = path)
                        } else state
                    }
                }
                if (changed) publish()
            }
        }
        scope.launch {
            client.chatLastMessageUpdates.collect { update ->
                chatStates.update { map ->
                    val state = map[update.chatId] ?: return@update map
                    val order = update.positions.firstOrNull { it.list is ChatListMain }?.order ?: state.order
                    val folderOrders = update.positions
                        .mapNotNull { p -> (p.list as? ChatListFolder)?.let { it.chatFolderId to p.order } }
                        .toMap()
                    map + (update.chatId to state.copy(
                        lastMessage = update.lastMessage?.toItem(),
                        order = order,
                        folderOrders = folderOrders,
                    ))
                }
                publish()
            }
        }
        scope.launch {
            client.chatPositionUpdates.collect { update ->
                val list = update.position.list
                if (list is ChatListMain || list is ChatListFolder) {
                    chatStates.update { map ->
                        val state = map[update.chatId] ?: return@update map
                        val new = when (list) {
                            is ChatListMain -> state.copy(order = update.position.order)
                            is ChatListFolder -> state.copy(
                                folderOrders = state.folderOrders + (list.chatFolderId to update.position.order),
                            )
                            else -> state
                        }
                        map + (update.chatId to new)
                    }
                    publish()
                }
            }
        }
        scope.launch {
            client.chatReadInboxUpdates.collect { update ->
                chatStates.update { map ->
                    val state = map[update.chatId] ?: return@update map
                    map + (update.chatId to state.copy(unreadCount = update.unreadCount))
                }
                publish()
            }
        }
        scope.launch {
            client.newMessageUpdates.collect { update ->
                val item = update.message.toItem()
                _newMessages.tryEmit(item)
                plugins?.emitMessageReceived(PluginMessage.from(item, update.message.content))
                onSpyMessage(item)
            }
        }
        scope.launch {
            client.messageInteractionInfoUpdates.collect { update ->
                _reactionEvents.tryEmit(
                    ReactionEvent(update.chatId, update.messageId, update.interactionInfo.toReactions()),
                )
            }
        }
        // Режим шпиона: удаление, правки, прочтение, онлайн.
        scope.launch {
            client.deleteMessagesUpdates.collect { update ->
                // fromCache — сообщения лишь выгружены из памяти TDLib, а не удалены.
                if (!update.isPermanent || update.fromCache) return@collect
                val ids = update.messageIds.toList()
                val own = ids.filter { selfDeleting.remove(it) }.toSet()
                val rest = ids.filterNot { it in own }
                val kept = if (settings.spyPrefs.value.saveDeleted && rest.isNotEmpty()) {
                    io { spy.markDeleted(update.chatId, rest, now()) }
                } else emptySet()
                _deletions.tryEmit(DeleteEvent(update.chatId, kept, ids.toSet() - kept))
                plugins?.emitMessageDeleted(update.chatId, ids)
            }
        }
        scope.launch {
            client.messageContentUpdates.collect { update ->
                val content = update.newContent
                val text = bodyOf(content, isOutgoing = false)
                val media = content.toMedia()
                val summary = summaryOf(text, media)
                val at = now()
                val old = io {
                    spy.recordEdit(update.chatId, update.messageId, text, summary, at, settings.spyPrefs.value.saveEdits)
                }
                _contentUpdates.tryEmit(ContentEvent(update.chatId, update.messageId, text, media, summary, old, at))
                plugins?.events?.emit("message_edited", linkedMapOf(
                    "chat_id" to update.chatId,
                    "message_id" to update.messageId,
                    "text" to text,
                    "type" to content.javaClass.simpleName.removePrefix("Message"),
                    "content" to app.yougram.plugin.TdObjectMapper.toMap(content),
                    "media" to media?.let { m -> linkedMapOf("kind" to m.kind.name, "file_id" to m.fileId, "preview_file_id" to m.previewFileId, "width" to m.width, "height" to m.height, "name" to m.name, "mime_type" to m.mimeType, "size" to m.size, "duration" to m.duration) },
                ))
            }
        }
        scope.launch {
            client.chatReadOutboxUpdates.collect { update ->
                if (!settings.spyPrefs.value.saveReadDate) return@collect
                val at = now()
                io { spy.markRead(update.chatId, update.lastReadOutboxMessageId, at) }
                _readEvents.tryEmit(ReadEvent(update.chatId, update.lastReadOutboxMessageId, at))
            }
        }
        scope.launch {
            client.userStatusUpdates.collect { update ->
                if (!settings.spyPrefs.value.saveLastOnline) return@collect
                when (val status = update.status) {
                    is UserStatusOnline -> io { spy.setLastSeen(update.userId, now()) }
                    is UserStatusOffline -> io { spy.setLastSeen(update.userId, status.wasOnline) }
                    else -> Unit
                }
            }
        }
    }

    /** Реакция шпиона на новое сообщение: архив, «последний онлайн», сохранение вложений. */
    private suspend fun onSpyMessage(item: MessageItem) {
        val p = settings.spyPrefs.value
        val sender = item.senderUserId
        if (p.saveLastOnline && !item.isOutgoing && sender != null) {
            io { spy.setLastSeen(sender, item.date) }
        }
        archive(listOf(item))
        maybeSaveAttachment(item)
    }

    private suspend fun archive(items: List<MessageItem>) {
        val p = settings.spyPrefs.value
        if (items.isEmpty() || !(p.saveDeleted || p.saveEdits || p.saveReadDate)) return
        if (!p.saveInBots && chatMeta(items.first().chatId).isBot) return
        io { spy.upsert(items) }
    }

    private suspend fun chatMeta(chatId: Long): ChatMeta {
        metaCache[chatId]?.let { return it }
        val meta = runCatching {
            val type = client.getChat(chatId = chatId).getOrThrow().type
            val bot = type is ChatTypePrivate &&
                    client.getUser(userId = type.userId).getOrThrow().type is UserTypeBot
            ChatMeta(isBot = bot, isChannel = type is ChatTypeSupergroup && type.isChannel)
        }.getOrNull() ?: return ChatMeta(isBot = false, isChannel = false)
        metaCache[chatId] = meta
        return meta
    }

    private suspend fun maybeSaveAttachment(item: MessageItem) {
        val p = settings.spyPrefs.value
        val media = item.media ?: return
        if (!p.saveAttachments || item.isOutgoing) return
        if (media.size > MAX_AUTO_ATTACHMENT) return
        val limit = p.maxFolderBytes
        if (limit != null && media.size > limit) return
        val meta = chatMeta(item.chatId)
        if (meta.isBot && !p.saveInBots) return
        if (meta.isChannel && !p.attachmentsChannels) return
        val save = PendingSave(item.chatId, attachmentName(item, media))
        pendingSaves[media.fileId] = save
        val result = client.downloadFile(
            fileId = media.fileId,
            priority = 2,
            offset = 0L,
            limit = 0L,
            synchronous = false,
        )
        if (result is TdlResult.Success) {
            track(result.result)
            val local = result.result.local
            // Файл уже лежит в кэше TDLib — updateFile не придёт, копируем сразу.
            if (local.isDownloadingCompleted && local.path.isNotEmpty() && pendingSaves.remove(media.fileId) != null) {
                copyAttachment(local.path, save)
            }
        } else {
            pendingSaves.remove(media.fileId)
        }
    }

    private fun attachmentName(item: MessageItem, media: MediaItem): String {
        val ext = when (media.mimeType) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "video/mp4" -> ".mp4"
            else -> ""
        }
        val base = media.name.ifEmpty { "${media.kind.name.lowercase()}_${item.id}$ext" }
        return "${item.id}_$base".replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun attachmentsRoot(): File {
        val folder = settings.spyPrefs.value.folderName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { SpyPrefs.DEFAULT_FOLDER }
        return File(context.getExternalFilesDir(null) ?: context.filesDir, folder)
    }

    private suspend fun copyAttachment(path: String, save: PendingSave) {
        io {
            runCatching {
                val src = File(path)
                if (!src.exists()) return@runCatching
                val root = attachmentsRoot()
                val dir = File(root, save.chatId.toString()).apply { mkdirs() }
                val dst = File(dir, save.name)
                if (!dst.exists()) src.copyTo(dst)
                pruneAttachments(root)
            }
        }
    }

    /** Если папка вложений больше лимита — удаляет самые старые файлы. */
    private fun pruneAttachments(root: File) {
        val limit = settings.spyPrefs.value.maxFolderBytes ?: return
        val files = root.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toMutableList()
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= limit) break
            total -= f.length()
            f.delete()
        }
    }

    /** Сохранённые удалённые сообщения, правки и время прочтения чата. */
    suspend fun spySnapshot(chatId: Long): SpySnapshot = io {
        SpySnapshot(
            deleted = spy.deleted(chatId).map {
                MessageItem(
                    id = it.id,
                    chatId = it.chatId,
                    text = it.text.ifEmpty { it.summary },
                    isOutgoing = it.outgoing,
                    date = it.date,
                    summary = it.summary,
                    senderUserId = it.senderId.takeIf { id -> id != 0L },
                )
            },
            edits = spy.edits(chatId),
            readAt = spy.readTimes(chatId),
        )
    }

    /** Пользователи из чёрного списка (для фильтра «скрывать от пользователей в ЧС»). */
    suspend fun blockedUserIds(): Set<Long> =
        client.getBlockedMessageSenders(blockList = BlockListMain(), offset = 0, limit = 100).getOrThrow()
            .senders.mapNotNull { (it as? MessageSenderUser)?.userId }.toSet()

    suspend fun userName(userId: Long): String = runCatching {
        client.getUser(userId = userId).getOrThrow().let { "${it.firstName} ${it.lastName}".trim() }
    }.getOrNull().orEmpty().ifEmpty { "ID $userId" }

    /** Запускает фоновую загрузку аватарки; по готовности придёт updateFile. */
    private fun downloadAvatar(fileId: Int) {
        scope.launch {
            client.downloadFile(
                fileId = fileId,
                priority = 1,
                offset = 0L,
                limit = 0L,
                synchronous = false,
            )
        }
    }

    /** Поток состояния файла (путь, прогресс) для UI. */
    fun fileState(fileId: Int): Flow<FileState> =
        fileStates.map { it[fileId] ?: FileState() }.distinctUntilChanged()

    suspend fun getLastReadOutboxMessageId(chatId: Long): Long = runCatching {
        client.getChat(chatId = chatId).getOrThrow().lastReadOutboxMessageId
    }.getOrDefault(0L)

    /** Запускает загрузку файла; прогресс и путь придут через [fileState]. */
    fun download(fileId: Int, priority: Int = 8) {
        scope.launch {
            val result = client.downloadFile(
                fileId = fileId,
                priority = priority,
                offset = 0L,
                limit = 0L,
                synchronous = false,
            )
            if (result is TdlResult.Success) track(result.result)
        }
    }

    /** Запоминает состояние файла; не затирает уже известный путь устаревшими данными из старого сообщения. */
    private fun track(file: TdFile?) {
        if (file == null) return
        val new = FileState(
            path = if (file.local.isDownloadingCompleted) file.local.path.takeIf { it.isNotEmpty() } else null,
            downloaded = file.local.downloadedSize,
            total = sizeOf(file),
            active = file.local.isDownloadingActive,
        )
        fileStates.update { map ->
            val old = map[file.id]
            if (old == new || (new.path == null && old?.path != null)) map else map + (file.id to new)
        }
    }

    private fun sizeOf(file: TdFile): Long = maxOf(file.size, file.expectedSize)

    /**
     * Просит TDLib подгрузить следующую порцию списка чатов (главного или папки [folderId]);
     * результаты придут через updateNewChat / updateChatPosition.
     * Возвращает false, когда все чаты списка загружены (TDLib отвечает ошибкой 404).
     */
    suspend fun loadChats(folderId: Int? = null, limit: Int = 50): Boolean {
        val list = if (folderId == null) ChatListMain() else ChatListFolder(chatFolderId = folderId)
        val result = client.loadChats(chatList = list, limit = limit)
        if (result is TdlResult.Failure) {
            if (result.code == 404) return false
            throw TelegramException(result.code, result.message)
        }
        return true
    }

    suspend fun getChatTitle(chatId: Long): String =
        chatStates.value[chatId]?.title ?: client.getChat(chatId = chatId).getOrThrow().title

    suspend fun getChatInfo(chatId: Long, viaThread: Boolean = false): ChatInfo = runCatching {
        val chat = client.getChat(chatId = chatId).getOrThrow()
        val type = chat.type
        val isChannel = (type as? ChatTypeSupergroup)?.isChannel == true
        val isGroup = type is ChatTypeBasicGroup || (type is ChatTypeSupergroup && !type.isChannel)
        // chat.permissions — это права «по умолчанию» для всех участников, а не ваши собственные.
        // В канале писать могут только создатель и админы с правом публикации, в группе — с учётом
        // вашего статуса (админ, ограничен, вышел, забанен).
        val canSend = when (type) {
            is ChatTypeSupergroup -> {
                val status = (client.getSupergroup(supergroupId = type.supergroupId) as? TdlResult.Success)?.result?.status
                canWriteAs(status, type.isChannel, chat.permissions.canSendBasicMessages, viaThread)
            }
            is ChatTypeBasicGroup -> {
                val status = (client.getBasicGroup(basicGroupId = type.basicGroupId) as? TdlResult.Success)?.result?.status
                canWriteAs(status, false, chat.permissions.canSendBasicMessages)
            }
            else -> chat.permissions.canSendBasicMessages
        }
        val avatar = chat.photo?.small
        avatar?.let { prepareAvatar(it) }
        ChatInfo(
            title = chat.title,
            canSendMessages = canSend,
            isChannel = isChannel,
            isGroup = isGroup,
            avatarFileId = avatar?.id,
        )
    }.getOrDefault(ChatInfo(title = getChatTitle(chatId)))

    private fun canWriteAs(status: ChatMemberStatus?, isChannel: Boolean, defaultCanSend: Boolean, viaThread: Boolean = false): Boolean = when (status) {
        null -> !isChannel && defaultCanSend
        is ChatMemberStatusCreator -> status.isMember
        is ChatMemberStatusAdministrator -> !isChannel || status.rights.canPostMessages
        is ChatMemberStatusMember -> !isChannel && defaultCanSend
        is ChatMemberStatusRestricted -> status.isMember && !isChannel && status.permissions.canSendBasicMessages
        // В комментариях к посту писать можно и не состоя в группе обсуждения.
        is ChatMemberStatusLeft -> viaThread && defaultCanSend
        is ChatMemberStatusBanned -> false
        else -> false
    }

    // ---------- Поиск ----------

    private suspend fun chatHit(chatId: Long): SearchChatHit? {
        val chat = (client.getChat(chatId = chatId) as? TdlResult.Success)?.result ?: return null
        val avatar = chat.photo?.small
        avatar?.let { prepareAvatar(it) }
        val subtitle = when (val type = chat.type) {
            is ChatTypePrivate -> (client.getUser(userId = type.userId) as? TdlResult.Success)?.result
                ?.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" }.orEmpty()
            is ChatTypeSupergroup -> {
                val group = (client.getSupergroup(supergroupId = type.supergroupId) as? TdlResult.Success)?.result
                group?.usernames?.activeUsernames?.firstOrNull()?.let { "@$it" }
                    ?: if (type.isChannel) "Канал" else "Группа"
            }
            is ChatTypeBasicGroup -> "Группа"
            else -> ""
        }
        return SearchChatHit(chatId = chatId, title = chat.title, subtitle = subtitle, avatarFileId = avatar?.id)
    }

    private suspend fun chatHits(ids: List<Long>): List<SearchChatHit> = coroutineScope {
        ids.distinct().map { id -> async { chatHit(id) } }.awaitAll().filterNotNull()
    }

    /** Быстрая часть поиска: уже известные чаты (searchChats) и контакты (searchContacts). */
    suspend fun searchPeopleLocal(query: String): PeopleSearch = coroutineScope {
        val q = query.trim()
        val chatIds = async {
            runCatching { client.searchChats(query = q, typeFilter = null, limit = 20).getOrThrow().chatIds.toList() }
                .getOrDefault(emptyList())
        }
        val contactIds = async {
            runCatching { client.searchContacts(query = q, limit = 20).getOrThrow().userIds.toList() }
                .getOrDefault(emptyList())
        }
        val chats = chatHits(chatIds.await())
        val contacts = chatHits(contactIds.await().mapNotNull { runCatching { openPrivateChat(it) }.getOrNull() })
        PeopleSearch(chats = chats, contacts = contacts, global = emptyList())
    }

    /** Серверная часть поиска: свои чаты через сервер (searchChatsOnServer) и публичные чаты по всему Telegram. */
    suspend fun searchPeopleRemote(query: String): PeopleSearch = coroutineScope {
        val q = query.trim()
        val serverIds = async {
            runCatching { client.searchChatsOnServer(query = q, typeFilter = null, limit = 20).getOrThrow().chatIds.toList() }
                .getOrDefault(emptyList())
        }
        val publicIds = async {
            runCatching { client.searchPublicChats(query = q, typeFilter = null).getOrThrow().chatIds.toList() }
                .getOrDefault(emptyList())
        }
        PeopleSearch(chats = chatHits(serverIds.await()), contacts = emptyList(), global = chatHits(publicIds.await()))
    }

    /** Поиск сообщений по всем чатам; для первой страницы передайте пустой [offset]. */
    suspend fun searchMessages(query: String, offset: String = "", limit: Int = 30): MessageSearchPage {
        val found = client.searchMessages(
            chatList = ChatListMain(),
            query = query.trim(),
            offset = offset,
            limit = limit,
            filter = SearchMessagesFilterEmpty(),
            chatTypeFilter = null,
            minDate = 0,
            maxDate = 0,
        ).getOrThrow()
        val hits = coroutineScope {
            found.messages.orEmpty().filterNotNull().map { message ->
                async {
                    val item = message.toItem()
                    val chat = (client.getChat(chatId = message.chatId) as? TdlResult.Success)?.result
                    val avatar = chat?.photo?.small
                    avatar?.let { prepareAvatar(it) }
                    val author = when {
                        item.isOutgoing -> "Вы"
                        message.chatId < 0 && item.senderUserId != null -> sender(item.senderUserId)?.name.orEmpty()
                        else -> ""
                    }
                    SearchMessageHit(
                        chatId = message.chatId,
                        messageId = message.id,
                        chatTitle = chat?.title.orEmpty(),
                        avatarFileId = avatar?.id,
                        author = author,
                        text = item.summary.ifEmpty { "Сообщение" },
                        date = message.date,
                    )
                }
            }.awaitAll()
        }
        return MessageSearchPage(hits = hits, nextOffset = found.nextOffset, total = found.totalCount)
    }

    /**
     * Поиск внутри чата (searchChatMessages): id от новых к старым. Для следующей страницы передайте
     * [fromMessageId] из [ChatSearchPage.nextFromMessageId]. TDLib может вернуть пустую порцию,
     * хотя результаты есть, поэтому пустые ответы с продолжением повторяем.
     */
    suspend fun searchInChat(chatId: Long, query: String, fromMessageId: Long = 0L, limit: Int = 50): ChatSearchPage {
        var from = fromMessageId
        var attempts = 0
        while (true) {
            val found = client.searchChatMessages(
                chatId = chatId,
                topicId = null,
                query = query.trim(),
                senderId = null,
                fromMessageId = from,
                offset = 0,
                limit = limit,
                filter = SearchMessagesFilterEmpty(),
            ).getOrThrow()
            val ids = found.messages.orEmpty().filterNotNull().map { it.id }
            attempts++
            if (ids.isNotEmpty() || found.nextFromMessageId == 0L || attempts >= 3) {
                return ChatSearchPage(ids = ids, nextFromMessageId = found.nextFromMessageId, total = found.totalCount)
            }
            from = found.nextFromMessageId
        }
    }

    /** Последние сообщения чата в порядке от старых к новым. */
    suspend fun loadHistory(chatId: Long, limit: Int = 50): List<MessageItem> =
        fetchHistory(chatId, fromMessageId = 0L, limit = limit).sortedBy { it.id }

    /** Сообщения старше [beforeMessageId] в порядке от старых к новым; пустой список — история закончилась. */
    suspend fun loadOlder(chatId: Long, beforeMessageId: Long, limit: Int = 40): List<MessageItem> =
        fetchHistory(chatId, fromMessageId = beforeMessageId, limit = limit)
            .filter { it.id < beforeMessageId }
            .sortedBy { it.id }

    private suspend fun fetchHistory(chatId: Long, fromMessageId: Long, limit: Int): List<MessageItem> {
        // TDLib может сначала вернуть неполный локальный кусок — повторяем, пока не наберётся нужное количество.
        // При offset = 0 сообщение fromMessageId входит в ответ, поэтому дубли отсекаем по id.
        var from = fromMessageId
        val collected = LinkedHashMap<Long, MessageItem>()
        var attempts = 0
        while (collected.size < limit && attempts < 3) {
            val pageLimit = limit - collected.size + (if (from != 0L) 1 else 0)
            val topic = topicOf(chatId)
            val thread = threadOf(chatId)
            val page = when {
                thread != 0L -> client.getMessageThreadHistory(chatId = chatId, messageId = thread, fromMessageId = from, offset = 0, limit = pageLimit)
                topic != 0 -> client.getForumTopicHistory(chatId = chatId, forumTopicId = topic, fromMessageId = from, offset = 0, limit = pageLimit)
                else -> client.getChatHistory(chatId = chatId, fromMessageId = from, offset = 0, limit = pageLimit, onlyLocal = false)
            }
            val batch = page.getOrThrow().messages.orEmpty().filterNotNull()
            val fresh = batch.filter { it.id != fromMessageId && !collected.containsKey(it.id) }
            if (fresh.isEmpty()) break
            fresh.forEach { collected[it.id] = it.toItem() }
            from = batch.last().id
            attempts++
        }
        archive(collected.values.toList())
        return collected.values.toList()
    }

    fun incomingFor(chatId: Long): Flow<MessageItem> = newMessages.filter {
        val thread = threadOf(chatId)
        it.chatId == chatId && when {
            thread != 0L -> it.threadId == thread || it.reply?.messageId == thread
            else -> topicOf(chatId) == 0 || it.topicId == topicOf(chatId)
        }
    }

    // ---- Форумы (группы с темами) ----

    private val forumCache = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()
    private val activeTopics = java.util.concurrent.ConcurrentHashMap<Long, Int>()

    /** Тема, открытая сейчас в чате [chatId] (0 — без темы): в неё идут отправка и загрузка истории. */
    fun setActiveTopic(chatId: Long, topicId: Int) {
        if (topicId == 0) activeTopics.remove(chatId) else activeTopics[chatId] = topicId
    }

    private fun topicOf(chatId: Long): Int = activeTopics[chatId] ?: 0

    private val activeThreads = ConcurrentHashMap<Long, Long>()

    /** Ветка комментариев, открытая сейчас в чате [chatId] (0 — без ветки): в неё идут отправка и загрузка истории. */
    fun setActiveThread(chatId: Long, threadId: Long) {
        if (threadId == 0L) activeThreads.remove(chatId) else activeThreads[chatId] = threadId
    }

    private fun threadOf(chatId: Long): Long = activeThreads[chatId] ?: 0L

    /** Чат обсуждения и id ветки для комментариев к посту; null — комментарии недоступны. */
    suspend fun openCommentThread(channelChatId: Long, postId: Long): Pair<Long, Long>? = runCatching {
        val info = client.getMessageThread(chatId = channelChatId, messageId = postId).getOrThrow()
        info.chatId to info.messageThreadId
    }.getOrNull()

    private fun threadReply(chatId: Long): InputMessageReplyToMessage? =
        threadOf(chatId).takeIf { it != 0L }?.let {
            InputMessageReplyToMessage(messageId = it, quote = null, checklistTaskId = 0, pollOptionId = "")
        }

    private fun topicParam(chatId: Long): MessageTopic? {
        val thread = threadOf(chatId)
        if (thread != 0L) return MessageTopicThread(messageThreadId = thread)
        return topicOf(chatId).takeIf { it != 0 }?.let { MessageTopicForum(forumTopicId = it) }
    }

    suspend fun isForum(chatId: Long): Boolean {
        forumCache[chatId]?.let { return it }
        val forum = runCatching {
            val type = client.getChat(chatId = chatId).getOrThrow().type
            type is ChatTypeSupergroup && client.getSupergroup(supergroupId = type.supergroupId).getOrThrow().isForum
        }.getOrNull() ?: return false
        forumCache[chatId] = forum
        return forum
    }

    suspend fun forumTopicName(chatId: Long, topicId: Int): String =
        runCatching { client.getForumTopic(chatId = chatId, forumTopicId = topicId).getOrThrow().info.name }.getOrDefault("")

    suspend fun loadForumTopics(chatId: Long): List<ForumTopicItem> {
        val result = client.getForumTopics(
            chatId = chatId,
            query = "",
            offsetDate = 0,
            offsetMessageId = 0L,
            offsetForumTopicId = 0,
            limit = 100,
        ).getOrThrow()
        return result.topics.orEmpty().filterNotNull().map { t ->
            val last = t.lastMessage?.toItem()
            ForumTopicItem(
                id = t.info.forumTopicId,
                name = t.info.name,
                color = t.info.icon.color,
                isGeneral = t.info.isGeneral,
                isClosed = t.info.isClosed,
                isPinned = t.isPinned,
                unread = t.unreadCount,
                lastSender = last?.senderKey?.let { runCatching { sender(it)?.name }.getOrNull() }.orEmpty(),
                lastText = last?.summary.orEmpty(),
                lastDate = last?.date ?: 0,
            )
        }
    }

    suspend fun sendText(chatId: Long, rawText: String, replyToId: Long? = null, fromPlugin: Boolean = false) {
        // Плагинные отправки идут мимо хука, иначе плагин зациклится на собственных сообщениях.
        val text = if (fromPlugin) rawText else plugins?.beforeSend(chatId, rawText, replyToId) ?: return
        client.sendMessage(
            chatId = chatId,
            topicId = topicParam(chatId),
            replyTo = replyToId?.let { InputMessageReplyToMessage(messageId = it, quote = null, checklistTaskId = 0, pollOptionId = "") }
                ?: threadReply(chatId),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = emptyArray()),
                linkPreviewOptions = null,
                clearDraft = true,
            ),
        ).getOrThrow()
    }

    /**
     * Отметить сообщения прочитанными, когда пользователь открыл чат.
     * В режиме призрака ничего не отправляем, если только [force] (чтение при действии) не задан.
     */
    suspend fun markRead(chatId: Long, messageIds: List<Long>, force: Boolean = false) {
        if (messageIds.isEmpty()) return
        if (settings.ghost.value.enabled && !force) return
        client.viewMessages(
            chatId = chatId,
            messageIds = messageIds.toLongArray(),
            source = null,
            forceRead = true,
        )
    }

    private fun prepareAvatar(file: TdFile) {
        track(file)
        if (!file.local.isDownloadingCompleted) download(file.id, priority = 1)
    }

    /** Автор сообщения по ключу: id пользователя (>0) или чата (<0). Результат кэшируется. */
    suspend fun sender(key: Long): SenderInfo? {
        senderCache[key]?.let { return it }
        val info = runCatching {
            if (key > 0) {
                val user = client.getUser(userId = key).getOrThrow()
                val avatar = user.profilePhoto?.small
                avatar?.let { prepareAvatar(it) }
                SenderInfo(
                    key = key,
                    name = "${user.firstName} ${user.lastName}".trim().ifEmpty { "Без имени" },
                    username = user.usernames?.activeUsernames?.firstOrNull(),
                    avatarFileId = avatar?.id,
                )
            } else {
                val chat = client.getChat(chatId = key).getOrThrow()
                val avatar = chat.photo?.small
                avatar?.let { prepareAvatar(it) }
                SenderInfo(key = key, name = chat.title, username = null, avatarFileId = avatar?.id)
            }
        }.getOrNull() ?: return null
        senderCache[key] = info
        return info
    }

    /** Досье для личного чата [chatId]; данные берутся из TDLib и локального архива, внешних запросов нет. */
    suspend fun loadDossier(chatId: Long): UserDossier {
        val chat = client.getChat(chatId = chatId).getOrThrow()
        val type = chat.type as? ChatTypePrivate ?: error("Досье доступно только для личных чатов")
        val user = client.getUser(userId = type.userId).getOrThrow()
        val full = (client.getUserFullInfo(userId = type.userId) as? TdlResult.Success)?.result
        val bio = full?.bio?.text.orEmpty()
        val groupIds = runCatching {
            client.getGroupsInCommon(userId = user.id, offsetChatId = 0L, limit = 30).getOrThrow().chatIds.toList()
        }.getOrDefault(emptyList())
        val groups = groupIds.mapNotNull { id ->
            (client.getChat(chatId = id) as? TdlResult.Success)?.result?.title
        }
        return UserDossier(
            id = user.id,
            name = "${user.firstName} ${user.lastName}".trim(),
            usernames = user.usernames?.activeUsernames?.toList().orEmpty(),
            phone = user.phoneNumber.takeIf { it.isNotEmpty() }?.let { "+$it" },
            bio = YougramBadge.strip(bio),
            isContact = user.isContact,
            isMutualContact = user.isMutualContact,
            isPremium = user.isPremium,
            isBot = user.type is UserTypeBot,
            isYougram = YougramBadge.hasMarker(bio),
            commonGroupsCount = maxOf(full?.groupInCommonCount ?: 0, groups.size),
            commonGroups = groups,
            deletedMessages = runCatching { deletedMessages(chatId).size }.getOrDefault(0),
        )
    }

    /** Данные для экрана профиля чата [chatId] (пользователь, группа или канал). */
    suspend fun loadProfileDetails(chatId: Long): ProfileDetails {
        val chat = client.getChat(chatId = chatId).getOrThrow()
        val big = chat.photo?.big
        big?.let { prepareAvatar(it) }
        val base = when (val type = chat.type) {
            is ChatTypePrivate -> {
                val user = client.getUser(userId = type.userId).getOrThrow()
                val full = (client.getUserFullInfo(userId = type.userId) as? TdlResult.Success)?.result
                val names = user.usernames?.activeUsernames?.toList().orEmpty()
                val bot = user.type is UserTypeBot
                if (!bot && full != null) noteBio(user.id, full.bio?.text.orEmpty())
                ProfileDetails(
                    kind = if (bot) ProfileKind.BOT else ProfileKind.USER,
                    chatId = chatId,
                    title = chat.title,
                    subtitle = if (bot) "бот" else statusText(user),
                    description = YougramBadge.strip(full?.bio?.text.orEmpty()),
                    phone = user.phoneNumber.takeIf { it.isNotEmpty() }?.let { "+$it" },
                    username = names.firstOrNull(),
                    otherUsernames = names.drop(1),
                    link = null,
                    id = user.id,
                    avatarFileId = big?.id,
                )
            }
            is ChatTypeSupergroup -> {
                val group = client.getSupergroup(supergroupId = type.supergroupId).getOrThrow()
                val full = (client.getSupergroupFullInfo(supergroupId = type.supergroupId) as? TdlResult.Success)?.result
                val names = group.usernames?.activeUsernames?.toList().orEmpty()
                val count = maxOf(full?.memberCount ?: 0, group.memberCount)
                ProfileDetails(
                    kind = if (type.isChannel) ProfileKind.CHANNEL else ProfileKind.GROUP,
                    chatId = chatId,
                    title = chat.title,
                    subtitle = membersText(count, type.isChannel),
                    description = full?.description.orEmpty(),
                    phone = null,
                    username = names.firstOrNull(),
                    otherUsernames = names.drop(1),
                    link = names.firstOrNull()?.let { "t.me/$it" }
                        ?: full?.inviteLink?.inviteLink?.removePrefix("https://"),
                    id = chatId,
                    avatarFileId = big?.id,
                )
            }
            is ChatTypeBasicGroup -> {
                val group = client.getBasicGroup(basicGroupId = type.basicGroupId).getOrThrow()
                val full = (client.getBasicGroupFullInfo(basicGroupId = type.basicGroupId) as? TdlResult.Success)?.result
                ProfileDetails(
                    kind = ProfileKind.GROUP,
                    chatId = chatId,
                    title = chat.title,
                    subtitle = membersText(group.memberCount, false),
                    description = full?.description.orEmpty(),
                    phone = null,
                    username = null,
                    otherUsernames = emptyList(),
                    link = full?.inviteLink?.inviteLink?.removePrefix("https://"),
                    id = chatId,
                    avatarFileId = big?.id,
                )
            }
            else -> ProfileDetails(
                kind = ProfileKind.OTHER,
                chatId = chatId,
                title = chat.title,
                subtitle = "",
                description = "",
                phone = null,
                username = null,
                otherUsernames = emptyList(),
                link = null,
                id = chatId,
                avatarFileId = big?.id,
            )
        }
        return base.copy(muted = chat.notificationSettings.muteFor > 0)
    }

    /** Последние сообщения чата для вкладок профиля (медиа, файлы, ссылки); фильтрация — на стороне UI. */
    suspend fun sharedMessages(chatId: Long, limit: Int = 200): List<MessageItem> =
        fetchHistory(chatId, fromMessageId = 0L, limit = limit).sortedByDescending { it.id }

    /** Ставит реакцию или, если [remove], снимает свою. */
    suspend fun react(chatId: Long, messageId: Long, emoji: String, remove: Boolean = false) {
        val type = ReactionTypeEmoji(emoji = emoji)
        if (remove) {
            client.removeMessageReaction(chatId = chatId, messageId = messageId, reactionType = type).getOrThrow()
        } else {
            client.addMessageReaction(
                chatId = chatId,
                messageId = messageId,
                reactionType = type,
                isBig = false,
                updateRecentReactions = true,
            ).getOrThrow()
        }
    }

    /** Эмодзи-реакции, доступные для этого сообщения; пустой список — не удалось получить. */
    suspend fun availableReactions(chatId: Long, messageId: Long): List<String> = runCatching {
        val r = client.getMessageAvailableReactions(chatId = chatId, messageId = messageId, rowSize = 8).getOrThrow()
        (r.topReactions.orEmpty().filterNotNull() +
                r.recentReactions.orEmpty().filterNotNull() +
                r.popularReactions.orEmpty().filterNotNull())
            .filter { !it.needsPremium }
            .mapNotNull { (it.type as? ReactionTypeEmoji)?.emoji }
            .distinct()
    }.getOrDefault(emptyList())

    /** Вид чата (личный, группа, канал); при ошибке считается личным. */
    suspend fun chatKind(chatId: Long): ChatKind {
        kindCache[chatId]?.let { return it }
        val kind = runCatching {
            when (val type = client.getChat(chatId = chatId).getOrThrow().type) {
                is ChatTypeBasicGroup -> ChatKind.GROUP
                is ChatTypeSupergroup -> if (type.isChannel) ChatKind.CHANNEL else ChatKind.GROUP
                else -> ChatKind.PRIVATE
            }
        }.getOrNull() ?: return ChatKind.PRIVATE
        kindCache[chatId] = kind
        return kind
    }

    /** Для уведомлений: имя автора и краткий текст сообщения. */
    suspend fun describeForNotification(message: Message): Pair<String, String> {
        val item = message.toItem()
        val name = item.senderKey?.let { sender(it)?.name }.orEmpty()
        return name to item.summary.ifEmpty { "Сообщение" }
    }

    /** Автор и текст оригинала для цитаты в пузыре. */
    suspend fun replyPreview(ref: ReplyRef): ReplyPreview = runCatching {
        val item = client.getMessage(chatId = ref.chatId, messageId = ref.messageId).getOrThrow().toItem()
        val author = if (item.isOutgoing) "Вы" else item.senderKey?.let { sender(it)?.name }.orEmpty()
        ReplyPreview(author, item.summary.ifEmpty { "Сообщение" })
    }.getOrDefault(ReplyPreview("", "Сообщение недоступно"))

    suspend fun editText(chatId: Long, messageId: Long, text: String) {
        client.editMessageText(
            chatId = chatId,
            messageId = messageId,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = emptyArray()),
                linkPreviewOptions = null,
                clearDraft = false,
            ),
        ).getOrThrow()
    }

    /** Удаляет сообщение у всех (в личных чатах и там, где позволяют права). */
    suspend fun deleteMessage(chatId: Long, messageId: Long) {
        selfDeleting.add(messageId)
        val result = client.deleteMessages(chatId = chatId, messageIds = longArrayOf(messageId), revoke = true)
        if (result is dev.g000sha256.tdl.TdlResult.Failure) selfDeleting.remove(messageId)
        result.getOrThrow()
    }

    suspend fun pinMessage(chatId: Long, messageId: Long) {
        client.pinChatMessage(
            chatId = chatId,
            messageId = messageId,
            disableNotification = false,
            onlyForSelf = false,
        ).getOrThrow()
    }

    suspend fun forwardMessage(toChatId: Long, fromChatId: Long, messageId: Long) {
        client.forwardMessages(
            chatId = toChatId,
            topicId = null,
            fromChatId = fromChatId,
            messageIds = longArrayOf(messageId),
            options = null,
            sendCopy = false,
            removeCaption = false,
        ).getOrThrow()
    }

    private suspend fun sendContent(chatId: Long, content: InputMessageContent) {
        client.sendMessage(
            chatId = chatId,
            topicId = topicParam(chatId),
            replyTo = threadReply(chatId),
            options = null,
            replyMarkup = null,
            inputMessageContent = content,
        ).getOrThrow()
    }

    /** Отправляет GIF/MP4-анимацию как нативное animation-сообщение Telegram. */
    suspend fun sendAnimation(chatId: Long, path: String, width: Int = 0, height: Int = 0, duration: Int = 0) = sendContent(
        chatId,
        InputMessageAnimation(
            animation = InputAnimation(
                animation = InputFileLocal(path = path),
                thumbnail = null,
                addedStickerFileIds = IntArray(0),
                duration = duration.coerceAtLeast(0),
                width = width.coerceAtLeast(0),
                height = height.coerceAtLeast(0),
            ),
            caption = null,
            showCaptionAboveMedia = false,
            hasSpoiler = false,
        ),
    )

    /** Отправляет уже известный TDLib-стикер по его file id. */
    suspend fun sendSticker(chatId: Long, fileId: Int, width: Int, height: Int, emoji: String) = sendContent(
        chatId,
        InputMessageSticker(
            sticker = InputFileId(id = fileId),
            thumbnail = null,
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            emoji = emoji,
        ),
    )

    /** Отправляет локальный WEBP/PNG как нативный Telegram-стикер. */
    suspend fun sendStickerFile(chatId: Long, path: String, width: Int = 512, height: Int = 512, emoji: String = "🙂") = sendContent(
        chatId,
        InputMessageSticker(
            sticker = InputFileLocal(path = path),
            thumbnail = null,
            width = width.coerceIn(1, 512),
            height = height.coerceIn(1, 512),
            emoji = emoji,
        ),
    )

    private fun mapSticker(s: TdSticker): StickerItem {
        track(s.sticker)
        track(s.thumbnail?.file)
        return StickerItem(
            fileId = s.sticker.id,
            width = s.width,
            height = s.height,
            emoji = s.emoji,
            thumbFileId = s.thumbnail?.file?.id,
            format = when (s.format::class.simpleName) {
                "StickerFormatTgs" -> StickerFmt.TGS
                "StickerFormatWebm" -> StickerFmt.WEBM
                else -> StickerFmt.STATIC
            },
        )
    }

    /** Поиск стикеров по эмодзи/запросу. */
    suspend fun loadStickers(query: String = "", limit: Int = 100, chatId: Long = 0L): List<StickerItem> = withContext(Dispatchers.IO) {
        client.getStickers(
            stickerType = StickerTypeRegular(),
            query = query,
            limit = limit.coerceIn(1, 100),
            chatId = chatId,
        ).getOrThrow().stickers.orEmpty().filterNotNull().map(::mapSticker)
    }

    /** Установленные наборы стикеров пользователя. */
    suspend fun loadStickerSets(): List<StickerSetItem> = withContext(Dispatchers.IO) {
        client.getInstalledStickerSets(stickerType = StickerTypeRegular()).getOrThrow()
            .sets.orEmpty().filterNotNull().map { info ->
                StickerSetItem(
                    id = info.id,
                    title = info.title,
                    cover = info.covers.orEmpty().filterNotNull().firstOrNull()?.let(::mapSticker),
                )
            }
    }

    suspend fun loadStickerSetStickers(setId: Long): List<StickerItem> = withContext(Dispatchers.IO) {
        client.getStickerSet(setId = setId).getOrThrow().stickers.orEmpty().filterNotNull().map(::mapSticker)
    }

    suspend fun loadRecentStickers(): List<StickerItem> = withContext(Dispatchers.IO) {
        val recent = client.getRecentStickers(isAttached = false).getOrThrow().stickers.orEmpty().filterNotNull()
        val favorite = runCatching { client.getFavoriteStickers().getOrThrow().stickers.orEmpty().filterNotNull() }
            .getOrDefault(emptyList())
        (favorite + recent).distinctBy { it.sticker.id }.map(::mapSticker)
    }

    /** Сохранённые GIF пользователя из Telegram. */
    suspend fun loadSavedGifs(): List<GifItem> = withContext(Dispatchers.IO) {
        client.getSavedAnimations().getOrThrow().animations.orEmpty().filterNotNull().map { a ->
            track(a.animation)
            track(a.thumbnail?.file)
            GifItem(
                fileId = a.animation.id,
                thumbFileId = a.thumbnail?.file?.id,
                width = a.width,
                height = a.height,
                duration = a.duration,
            )
        }
    }

    suspend fun sendAnimationById(chatId: Long, gif: GifItem) = sendContent(
        chatId,
        InputMessageAnimation(
            animation = InputAnimation(
                animation = InputFileId(id = gif.fileId),
                thumbnail = null,
                addedStickerFileIds = IntArray(0),
                duration = gif.duration.coerceAtLeast(0),
                width = gif.width.coerceAtLeast(0),
                height = gif.height.coerceAtLeast(0),
            ),
            caption = null,
            showCaptionAboveMedia = false,
            hasSpoiler = false,
        ),
    )

    /** Отправляет фото из локального файла. */
    suspend fun sendPhoto(chatId: Long, path: String) = sendContent(
        chatId,
        InputMessagePhoto(
            photo = InputPhoto(
                photo = InputFileLocal(path = path),
                thumbnail = null,
                video = null,
                addedStickerFileIds = IntArray(0),
                width = 0,
                height = 0,
            ),
            caption = null,
            showCaptionAboveMedia = false,
            selfDestructType = null,
            hasSpoiler = false,
        ),
    )

    /** Отправляет любой файл документом (так же уходят видео из галереи). */
    suspend fun sendDocument(chatId: Long, path: String) = sendContent(
        chatId,
        InputMessageDocument(
            document = InputDocument(
                document = InputFileLocal(path = path),
                thumbnail = null,
                disableContentTypeDetection = false,
            ),
            caption = null,
        ),
    )

    /** Отправляет голосовое сообщение (ogg/opus) длительностью [duration] секунд. */
    suspend fun sendVoice(chatId: Long, path: String, duration: Int) = sendContent(
        chatId,
        InputMessageVoiceNote(
            voiceNote = InputFileLocal(path = path),
            duration = duration,
            waveform = ByteArray(0),
            caption = null,
            selfDestructType = null,
        ),
    )

    /** Отправляет видеосообщение-кружок (квадратный mp4, [length] — диаметр в пикселях). */
    suspend fun sendVideoNote(chatId: Long, path: String, duration: Int, length: Int) = sendContent(
        chatId,
        InputMessageVideoNote(
            videoNote = InputFileLocal(path = path),
            thumbnail = null,
            duration = duration,
            length = length,
            selfDestructType = null,
        ),
    )

    /** Включает или выключает уведомления чата; остальные настройки уведомлений сохраняются. */
    suspend fun setMuted(chatId: Long, muted: Boolean) {
        val cur = client.getChat(chatId = chatId).getOrThrow().notificationSettings
        client.setChatNotificationSettings(
            chatId = chatId,
            notificationSettings = ChatNotificationSettings(
                useDefaultMuteFor = false,
                muteFor = if (muted) MUTE_FOREVER else 0,
                useDefaultSound = cur.useDefaultSound,
                soundId = cur.soundId,
                useDefaultShowPreview = cur.useDefaultShowPreview,
                showPreview = cur.showPreview,
                useDefaultMuteStories = cur.useDefaultMuteStories,
                muteStories = cur.muteStories,
                useDefaultStorySound = cur.useDefaultStorySound,
                storySoundId = cur.storySoundId,
                useDefaultShowStoryPoster = cur.useDefaultShowStoryPoster,
                showStoryPoster = cur.showStoryPoster,
                useDefaultDisablePinnedMessageNotifications = cur.useDefaultDisablePinnedMessageNotifications,
                disablePinnedMessageNotifications = cur.disablePinnedMessageNotifications,
                useDefaultDisableMentionNotifications = cur.useDefaultDisableMentionNotifications,
                disableMentionNotifications = cur.disableMentionNotifications,
            ),
        ).getOrThrow()
    }

    /** Удалённые в Telegram сообщения чата, сохранённые архивом шпиона (от новых к старым). */
    suspend fun deletedMessages(chatId: Long): List<MessageItem> =
        spySnapshot(chatId).deleted.sortedByDescending { it.id }

    /** Ставит новую аватарку текущему пользователю из локального файла и возвращает обновлённый профиль. */
    suspend fun setProfilePhoto(path: String): ProfileItem {
        client.setProfilePhoto(
            photo = InputChatPhotoStatic(photo = InputFileLocal(path = path)),
            isPublic = false,
        ).getOrThrow()
        return loadProfile()
    }

    /** Выйти из группы или канала. */
    suspend fun leaveChat(chatId: Long) {
        client.leaveChat(chatId = chatId).getOrThrow()
    }

    private fun membersText(count: Int, channel: Boolean): String {
        val word = if (channel) plural(count, "подписчик", "подписчика", "подписчиков")
        else plural(count, "участник", "участника", "участников")
        return "%,d %s".format(count, word)
    }

    private fun plural(n: Int, one: String, few: String, many: String): String {
        val m100 = n % 100
        val m10 = n % 10
        return when {
            m100 in 11..14 -> many
            m10 == 1 -> one
            m10 in 2..4 -> few
            else -> many
        }
    }

    /** Все контакты пользователя. */
    suspend fun loadContacts(): List<ContactItem> {
        val ids = client.getContacts().getOrThrow().userIds.toList()
        return coroutineScope {
            ids.map { id ->
                async {
                    (client.getUser(userId = id) as? TdlResult.Success)?.result?.toContact()
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Текущий пользователь. */
    suspend fun loadProfile(): ProfileItem {
        val me = client.getMe().getOrThrow()
        val avatar = me.profilePhoto?.big ?: me.profilePhoto?.small
        if (avatar != null) {
            track(avatar)
            if (!avatar.local.isDownloadingCompleted) download(avatar.id, priority = 1)
        }
        return ProfileItem(
            id = me.id,
            name = "${me.firstName} ${me.lastName}".trim(),
            phone = if (me.phoneNumber.isEmpty()) "" else "+${me.phoneNumber}",
            username = me.usernames?.activeUsernames?.firstOrNull(),
            avatarFileId = avatar?.id,
        )
    }

    /** true, если у текущего пользователя подключён Telegram Premium. */
    suspend fun isPremium(): Boolean = runCatching { client.getMe().getOrThrow().isPremium }.getOrDefault(false)

    /** Число активных сеансов («Устройства») или null, если получить не удалось. */
    suspend fun activeSessionsCount(): Int? =
        (client.getActiveSessions() as? TdlResult.Success)?.result?.sessions?.size

    /** Личный чат с поддержкой Telegram; возвращает id чата. */
    suspend fun openSupportChat(): Long =
        openPrivateChat(client.getSupportUser().getOrThrow().id)

    /** Открывает (создаёт при необходимости) личный чат и возвращает его id. */
    suspend fun openPrivateChat(userId: Long): Long =
        client.createPrivateChat(userId = userId, force = false).getOrThrow().id

    /** Ищет пользователя или публичный чат по @username; null — такого нет. */
    suspend fun resolveUsername(username: String): Long? =
        client.searchPublicChat(username.trim().removePrefix("@")).okOrNull()?.id

    /** Страница истории звонков. Для первой страницы передайте пустой [offset]. */
    suspend fun loadCalls(offset: String, onlyMissed: Boolean, limit: Int = 40): CallPage {
        val found = client.searchCallMessages(
            offset = offset,
            limit = limit,
            onlyMissed = onlyMissed,
        ).getOrThrow()
        val chatCache = HashMap<Long, Pair<String, Int?>?>()
        val items = found.messages.orEmpty().filterNotNull().mapNotNull { message ->
            val call = message.content as? MessageCall ?: return@mapNotNull null
            val info = chatCache.getOrPut(message.chatId) { callChatInfo(message.chatId) }
            val reason = call.discardReason
            val kind = when {
                reason is CallDiscardReasonMissed -> CallKind.MISSED
                reason is CallDiscardReasonDeclined -> CallKind.DECLINED
                message.isOutgoing -> CallKind.OUTGOING
                else -> CallKind.INCOMING
            }
            CallItem(
                messageId = message.id,
                chatId = message.chatId,
                title = info?.first.orEmpty(),
                avatarFileId = info?.second,
                isOutgoing = message.isOutgoing,
                isVideo = call.isVideo,
                kind = kind,
                duration = call.duration,
                date = message.date,
            )
        }
        return CallPage(items, found.nextOffset.orEmpty())
    }

    /** Имя и id аватарки пользователя для карточки звонка. */
    suspend fun userCardInfo(userId: Long): Pair<String, Int?> {
        val user = client.getUser(userId = userId).getOrThrow()
        val avatar = user.profilePhoto?.small
        if (avatar != null) {
            track(avatar)
            if (!avatar.local.isDownloadingCompleted) download(avatar.id, priority = 1)
        }
        return "${user.firstName} ${user.lastName}".trim() to avatar?.id
    }

    private suspend fun callChatInfo(chatId: Long): Pair<String, Int?>? {
        val chat = (client.getChat(chatId = chatId) as? TdlResult.Success)?.result ?: return null
        val avatar = chat.photo?.small
        if (avatar != null) {
            track(avatar)
            if (!avatar.local.isDownloadingCompleted) download(avatar.id, priority = 1)
        }
        return chat.title to avatar?.id
    }

    /** Текст статуса пользователя; для скрытого статуса добавляется последний известный онлайн из архива шпиона. */
    private suspend fun statusText(user: User): String {
        val st = user.status
        val text0 = when (st) {
            is UserStatusOnline -> "в сети"
            is UserStatusOffline ->
                "был(а) " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(st.wasOnline * 1000L))
            is UserStatusRecently -> "был(а) недавно"
            is UserStatusLastWeek -> "был(а) на этой неделе"
            is UserStatusLastMonth -> "был(а) в этом месяце"
            else -> "был(а) давно"
        }
        val approx = if (settings.spyPrefs.value.saveLastOnline && st !is UserStatusOnline && st !is UserStatusOffline) {
            io { spy.lastSeen(user.id) }
        } else null
        return if (approx != null) {
            "$text0 (≈ " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(approx * 1000L)) + ")"
        } else text0
    }

    private suspend fun User.toContact(): ContactItem {
        val avatar = profilePhoto?.small
        if (avatar != null) {
            track(avatar)
            if (!avatar.local.isDownloadingCompleted) download(avatar.id, priority = 1)
        }
        val st = status
        val text = statusText(this)
        return ContactItem(
            id = id,
            name = "$firstName $lastName".trim().ifEmpty { "Без имени" },
            statusText = text,
            online = st is UserStatusOnline,
            avatarFileId = avatar?.id,
        )
    }

    private fun publish() {
        _chats.value = chatStates.value.values
            .filter { it.order != 0L }
            .map { state ->
                ChatItem(
                    id = state.id,
                    title = state.title,
                    lastMessage = state.lastMessage?.summary.orEmpty(),
                    lastMessageDate = state.lastMessage?.date ?: 0,
                    unreadCount = state.unreadCount,
                    order = state.order,
                    avatarPath = state.avatarPath,
                    folderOrders = state.folderOrders,
                )
            }
            .sortedByDescending { it.order }
    }

    private fun bodyOf(c: MessageContent, isOutgoing: Boolean): String = when (c) {
        is MessageText -> c.text.text
        is MessagePhoto -> c.caption.text
        is MessageVideo -> c.caption.text
        is MessageAnimation -> c.caption.text
        is MessageSticker -> ""
        is MessageDocument -> c.caption.text
        is MessageVoiceNote -> c.caption.text
        is MessagePaidMedia -> c.caption.text
        is MessageVideoNote -> ""
        is MessageCall -> formatCallMessage(c, isOutgoing)
        else -> typeLabel(c)
    }

    private fun formatCallMessage(call: MessageCall, isOutgoing: Boolean): String {
        val reason = call.discardReason
        val typeStr = if (call.isVideo) "видеозвонок" else "звонок"
        val dur = if (call.duration > 0) " (${formatCallDuration(call.duration)})" else ""
        return when {
            reason is CallDiscardReasonMissed -> "Пропущенный $typeStr"
            reason is CallDiscardReasonDeclined -> "Отклонённый $typeStr"
            isOutgoing -> if (call.duration > 0) "Исходящий $typeStr$dur" else "Исходящий $typeStr (без ответа)"
            else -> "Входящий $typeStr$dur"
        }
    }

    private fun formatCallDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Человекочитаемое название типа сообщения без отдельной поддержки в UI. */
    private fun typeLabel(c: MessageContent): String {
        val name = c::class.simpleName?.removePrefix("Message") ?: return "[Сообщение]"
        val label = when (name) {
            "Sticker" -> "Стикер"
            "Audio" -> "Аудио"
            "Voice", "VoiceNote" -> "Голосовое сообщение"
            "Location" -> "Геопозиция"
            "Venue" -> "Место"
            "Contact" -> "Контакт"
            "Poll" -> "Опрос"
            "Dice" -> "Кубик"
            "Game" -> "Игра"
            "Invoice" -> "Счёт"
            "Story" -> "История"
            "Call" -> "Звонок"
            "PinMessage" -> "Сообщение закреплено"
            "ChatJoinByLink", "ChatJoinByRequest", "ChatAddMembers" -> "Новый участник"
            "ChatDeleteMember" -> "Участник вышел"
            "ChatChangeTitle" -> "Название изменено"
            "ChatChangePhoto" -> "Фото изменено"
            "ChatDeletePhoto" -> "Фото удалено"
            "BasicGroupChatCreate", "SupergroupChatCreate" -> "Чат создан"
            "ContactRegistered" -> "Присоединился к Telegram"
            "PaidMessagesRefunded" -> "Звёзды за сообщения возвращены"
            "PaidMessagePriceChanged" -> "Цена сообщений изменена"
            "Gift" -> "Подарок"
            "GiftedStars" -> "Подарок: звёзды"
            "GiftedPremium" -> "Подарок: Premium"
            "Unsupported" -> "Это сообщение не поддерживается"
            else -> "Сообщение"
        }
        return "[$label]"
    }

    private fun summaryOf(body: String, media: MediaItem?): String = when {
        media != null && media.locked && body.isEmpty() -> "⭐ ${media.paidStars} · Платное медиа"
        media == null || body.isNotEmpty() -> body
        media.kind == MediaKind.DOCUMENT -> media.name.ifEmpty { media.kind.label }
        else -> media.kind.label
    }

    private fun MessageInteractionInfo?.toReactions(): List<ReactionItem> =
        this?.reactions?.reactions.orEmpty().filterNotNull().mapNotNull { r ->
            val type = r.type as? ReactionTypeEmoji ?: return@mapNotNull null
            ReactionItem(type.emoji, r.totalCount, r.isChosen)
        }

    private fun Message.toItem(): MessageItem {
        val c = content
        val media = c.toMedia()
        val callItem = (c as? MessageCall)?.let { call ->
            val reason = call.discardReason
            val kind = when {
                reason is CallDiscardReasonMissed -> CallKind.MISSED
                reason is CallDiscardReasonDeclined -> CallKind.DECLINED
                isOutgoing -> CallKind.OUTGOING
                else -> CallKind.INCOMING
            }
            CallItem(
                messageId = id,
                chatId = chatId,
                title = "",
                avatarFileId = null,
                isOutgoing = isOutgoing,
                isVideo = call.isVideo,
                kind = kind,
                duration = call.duration,
                date = date,
            )
        }
        val body = bodyOf(c, isOutgoing)
        return MessageItem(
            id = id,
            chatId = chatId,
            text = body,
            isOutgoing = isOutgoing,
            date = date,
            media = media,
            call = callItem,
            summary = summaryOf(body, media),
            senderUserId = (senderId as? MessageSenderUser)?.userId,
            senderChatId = (senderId as? MessageSenderChat)?.chatId,
            topicId = (topicId as? MessageTopicForum)?.forumTopicId ?: 0,
            reply = (replyTo as? MessageReplyToMessage)
                ?.takeIf { it.messageId != 0L }
                ?.let { ReplyRef(it.chatId, it.messageId) },
            reactions = interactionInfo.toReactions(),
            commentCount = if (isChannelPost) interactionInfo?.replyInfo?.replyCount ?: 0 else 0,
            hasComments = isChannelPost && interactionInfo?.replyInfo != null,
            threadId = (topicId as? MessageTopicThread)?.messageThreadId ?: 0L,
            paidMessageStars = paidMessageStarCount,
        )
    }

    private companion object {
        /** Автосохранение вложений пропускает файлы крупнее 100 МБ, чтобы не съедать трафик. */
        const val MAX_AUTO_ATTACHMENT = 100L shl 20

        /** TDLib считает мьют на 366 дней и больше бессрочным. */
        const val MUTE_FOREVER = 400 * 24 * 3600
    }

    /** Разбирает медиа-контент и заодно запоминает состояния его файлов. */
    private fun MessageContent.toMedia(): MediaItem? = when (this) {
        is MessagePhoto -> {
            val sizes = photo.sizes.orEmpty().filterNotNull()
            val full = sizes.maxByOrNull { it.width.toLong() * it.height }
            val preview = sizes.firstOrNull { maxOf(it.width, it.height) >= 600 } ?: full
            if (full == null) null else {
                track(full.photo)
                track(preview?.photo)
                MediaItem(
                    kind = MediaKind.PHOTO,
                    fileId = full.photo.id,
                    previewFileId = preview?.photo?.id,
                    width = full.width,
                    height = full.height,
                    name = "",
                    mimeType = "image/jpeg",
                    size = sizeOf(full.photo),
                    miniThumb = photo.minithumbnail?.data,
                )
            }
        }
        is MessageVideo -> {
            track(video.video)
            track(video.thumbnail?.file)
            MediaItem(
                kind = MediaKind.VIDEO,
                fileId = video.video.id,
                previewFileId = video.thumbnail?.file?.id,
                width = video.width,
                height = video.height,
                name = video.fileName,
                mimeType = video.mimeType,
                size = sizeOf(video.video),
                miniThumb = video.minithumbnail?.data,
            )
        }
        is MessageAnimation -> {
            track(animation.animation)
            track(animation.thumbnail?.file)
            MediaItem(
                kind = MediaKind.ANIMATION,
                fileId = animation.animation.id,
                previewFileId = animation.thumbnail?.file?.id,
                width = animation.width,
                height = animation.height,
                name = animation.fileName,
                mimeType = animation.mimeType,
                size = sizeOf(animation.animation),
                miniThumb = animation.minithumbnail?.data,
            )
        }
        is MessageSticker -> {
            track(sticker.sticker)
            track(sticker.thumbnail?.file)
            MediaItem(
                kind = MediaKind.STICKER,
                fileId = sticker.sticker.id,
                previewFileId = sticker.thumbnail?.file?.id ?: sticker.sticker.id,
                width = sticker.width,
                height = sticker.height,
                name = "sticker",
                mimeType = when (sticker.format::class.simpleName) {
                    "StickerFormatTgs" -> "application/x-tgsticker"
                    "StickerFormatWebm" -> "video/webm"
                    else -> "image/webp"
                },
                size = sizeOf(sticker.sticker),
                miniThumb = null,
            )
        }
        is MessagePaidMedia -> when (val first = media.orEmpty().filterNotNull().firstOrNull()) {
            // Уже открытое платное медиа показываем как обычное фото/видео.
            is PaidMediaPhoto -> {
                val sizes = first.photo.sizes.orEmpty().filterNotNull()
                val full = sizes.maxByOrNull { it.width.toLong() * it.height }
                val preview = sizes.firstOrNull { maxOf(it.width, it.height) >= 600 } ?: full
                if (full == null) null else {
                    track(full.photo)
                    track(preview?.photo)
                    MediaItem(
                        kind = MediaKind.PHOTO,
                        fileId = full.photo.id,
                        previewFileId = preview?.photo?.id,
                        width = full.width,
                        height = full.height,
                        name = "",
                        mimeType = "image/jpeg",
                        size = sizeOf(full.photo),
                        miniThumb = first.photo.minithumbnail?.data,
                        paidStars = starCount,
                    )
                }
            }
            is PaidMediaVideo -> {
                track(first.video.video)
                track(first.video.thumbnail?.file)
                MediaItem(
                    kind = MediaKind.VIDEO,
                    fileId = first.video.video.id,
                    previewFileId = first.video.thumbnail?.file?.id,
                    width = first.video.width,
                    height = first.video.height,
                    name = first.video.fileName,
                    mimeType = first.video.mimeType,
                    size = sizeOf(first.video.video),
                    miniThumb = first.video.minithumbnail?.data,
                    paidStars = starCount,
                )
            }
            // Ещё не куплено: от медиа есть только размеры и крошечная заглушка.
            is PaidMediaPreview -> MediaItem(
                kind = if (first.duration > 0) MediaKind.VIDEO else MediaKind.PHOTO,
                fileId = 0,
                previewFileId = null,
                width = first.width,
                height = first.height,
                name = "",
                mimeType = "",
                size = 0L,
                miniThumb = first.minithumbnail?.data,
                duration = first.duration,
                locked = true,
                paidStars = starCount,
            )
            else -> null
        }
        is MessageDocument -> {
            track(document.document)
            MediaItem(
                kind = MediaKind.DOCUMENT,
                fileId = document.document.id,
                previewFileId = null,
                width = 0,
                height = 0,
                name = document.fileName,
                mimeType = document.mimeType,
                size = sizeOf(document.document),
                miniThumb = null,
            )
        }
        is MessageVoiceNote -> {
            track(voiceNote.voice)
            MediaItem(
                kind = MediaKind.VOICE,
                fileId = voiceNote.voice.id,
                previewFileId = null,
                width = 0,
                height = 0,
                name = "",
                mimeType = voiceNote.mimeType,
                size = sizeOf(voiceNote.voice),
                miniThumb = null,
                duration = voiceNote.duration,
                waveform = voiceNote.waveform,
            )
        }
        is MessageVideoNote -> {
            track(videoNote.video)
            track(videoNote.thumbnail?.file)
            MediaItem(
                kind = MediaKind.VIDEO_NOTE,
                fileId = videoNote.video.id,
                previewFileId = videoNote.thumbnail?.file?.id,
                width = videoNote.length,
                height = videoNote.length,
                name = "",
                mimeType = "video/mp4",
                size = sizeOf(videoNote.video),
                miniThumb = videoNote.minithumbnail?.data,
                duration = videoNote.duration,
            )
        }
        else -> null
    }
}