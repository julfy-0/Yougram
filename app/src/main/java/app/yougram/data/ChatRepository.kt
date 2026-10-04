package app.yougram.data

import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.CallDiscardReasonDeclined
import dev.g000sha256.tdl.dto.CallDiscardReasonMissed
import dev.g000sha256.tdl.dto.ChatListFolder
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatTypeBasicGroup
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageCall
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.User
import dev.g000sha256.tdl.dto.UserStatusOffline
import dev.g000sha256.tdl.dto.UserStatusOnline
import dev.g000sha256.tdl.dto.UserStatusRecently
import dev.g000sha256.tdl.dto.UserStatusLastWeek
import dev.g000sha256.tdl.dto.UserStatusLastMonth
import kotlinx.coroutines.CoroutineScope
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
import java.text.DateFormat
import java.util.Date
import dev.g000sha256.tdl.dto.File as TdFile

data class ChatInfo(
    val title: String,
    val canSendMessages: Boolean = true,
    val isChannel: Boolean = false,
    val isGroup: Boolean = false,
)

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

enum class MediaKind(val label: String) {
    PHOTO("Фото"),
    VIDEO("Видео"),
    ANIMATION("GIF"),
    DOCUMENT("Файл"),
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
)

/** Состояние скачивания файла TDLib. */
data class FileState(
    val path: String? = null,
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val active: Boolean = false,
)

/** Модель сообщения для UI. */
data class MessageItem(
    val id: Long,
    val chatId: Long,
    /** Текст сообщения или подпись к медиа. */
    val text: String,
    val isOutgoing: Boolean,
    val date: Int,
    val media: MediaItem? = null,
    /** Краткое описание для списка чатов: текст, а для медиа без подписи — тип. */
    val summary: String = text,
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

class ChatRepository(
    private val telegram: TelegramClient,
    private val scope: CoroutineScope,
) {
    private val client get() = telegram.client

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
                _newMessages.tryEmit(update.message.toItem())
            }
        }
    }

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

    suspend fun getChatInfo(chatId: Long): ChatInfo = runCatching {
        val chat = client.getChat(chatId = chatId).getOrThrow()
        val type = chat.type
        val isChannel = (type as? ChatTypeSupergroup)?.isChannel == true
        val isGroup = type is ChatTypeBasicGroup || (type is ChatTypeSupergroup && !type.isChannel)
        val canSend = chat.permissions.canSendBasicMessages
        ChatInfo(
            title = chat.title,
            canSendMessages = canSend,
            isChannel = isChannel,
            isGroup = isGroup,
        )
    }.getOrDefault(ChatInfo(title = getChatTitle(chatId)))

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
            val batch = client.getChatHistory(
                chatId = chatId,
                fromMessageId = from,
                offset = 0,
                limit = limit - collected.size + (if (from != 0L) 1 else 0),
                onlyLocal = false,
            ).getOrThrow().messages.orEmpty().filterNotNull()
            val fresh = batch.filter { it.id != fromMessageId && !collected.containsKey(it.id) }
            if (fresh.isEmpty()) break
            fresh.forEach { collected[it.id] = it.toItem() }
            from = batch.last().id
            attempts++
        }
        return collected.values.toList()
    }

    fun incomingFor(chatId: Long): Flow<MessageItem> = newMessages.filter { it.chatId == chatId }

    suspend fun sendText(chatId: Long, text: String) {
        client.sendMessage(
            chatId = chatId,
            topicId = null,
            replyTo = null,
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = emptyArray()),
                linkPreviewOptions = null,
                clearDraft = true,
            ),
        ).getOrThrow()
    }

    /** Отметить сообщения прочитанными, когда пользователь открыл чат. */
    suspend fun markRead(chatId: Long, messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        client.viewMessages(
            chatId = chatId,
            messageIds = messageIds.toLongArray(),
            source = null,
            forceRead = true,
        )
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

    /** Число активных сеансов («Устройства») или null, если получить не удалось. */
    suspend fun activeSessionsCount(): Int? =
        (client.getActiveSessions() as? TdlResult.Success)?.result?.sessions?.size

    /** Личный чат с поддержкой Telegram; возвращает id чата. */
    suspend fun openSupportChat(): Long =
        openPrivateChat(client.getSupportUser().getOrThrow().id)

    /** Открывает (создаёт при необходимости) личный чат и возвращает его id. */
    suspend fun openPrivateChat(userId: Long): Long =
        client.createPrivateChat(userId = userId, force = false).getOrThrow().id

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

    private suspend fun callChatInfo(chatId: Long): Pair<String, Int?>? {
        val chat = (client.getChat(chatId = chatId) as? TdlResult.Success)?.result ?: return null
        val avatar = chat.photo?.small
        if (avatar != null) {
            track(avatar)
            if (!avatar.local.isDownloadingCompleted) download(avatar.id, priority = 1)
        }
        return chat.title to avatar?.id
    }

    private fun User.toContact(): ContactItem {
        val avatar = profilePhoto?.small
        if (avatar != null) {
            track(avatar)
            if (!avatar.local.isDownloadingCompleted) download(avatar.id, priority = 1)
        }
        val st = status
        val text = when (st) {
            is UserStatusOnline -> "в сети"
            is UserStatusOffline ->
                "был(а) " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(st.wasOnline * 1000L))
            is UserStatusRecently -> "был(а) недавно"
            is UserStatusLastWeek -> "был(а) на этой неделе"
            is UserStatusLastMonth -> "был(а) в этом месяце"
            else -> "был(а) давно"
        }
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

    private fun Message.toItem(): MessageItem {
        val c = content
        val media = c.toMedia()
        val body = when (c) {
            is MessageText -> c.text.text
            is MessagePhoto -> c.caption.text
            is MessageVideo -> c.caption.text
            is MessageAnimation -> c.caption.text
            is MessageDocument -> c.caption.text
            // Остальные типы пока показываем заглушкой с названием типа.
            else -> "[${c::class.simpleName?.removePrefix("Message") ?: "Сообщение"}]"
        }
        val summary = when {
            media == null || body.isNotEmpty() -> body
            media.kind == MediaKind.DOCUMENT -> media.name.ifEmpty { media.kind.label }
            else -> media.kind.label
        }
        return MessageItem(
            id = id,
            chatId = chatId,
            text = body,
            isOutgoing = isOutgoing,
            date = date,
            media = media,
            summary = summary,
        )
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
        else -> null
    }
}