package app.yougram.ui.chat

import app.yougram.feature.chat.comments.CommentsButton

import app.yougram.ui.LocalOpenLink
import app.yougram.ui.LocalOpenUsername
import app.yougram.ui.rememberLinkified
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import android.provider.OpenableColumns
import android.net.Uri
import android.content.pm.PackageManager
import android.content.Context
import android.Manifest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.yougram.ui.highlightMatches
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import app.yougram.data.ReactionItem
import app.yougram.data.ReplyPreview
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlin.reflect.KProperty
import app.yougram.ui.ChatWallpaper
import app.yougram.data.GlassSettings
import app.yougram.ui.glass.BackdropState
import app.yougram.ui.glass.LocalPlates
import app.yougram.data.EditRecord
import app.yougram.data.FileState
import app.yougram.data.MediaItem
import app.yougram.data.MediaKind
import app.yougram.data.SenderInfo
import app.yougram.ui.FileAvatar
import app.yougram.ui.rememberFileBitmap
import app.yougram.data.MessageFilters
import app.yougram.data.MessageItem
import app.yougram.data.SettingsRepository
import app.yougram.data.StickerItem
import app.yougram.ui.Avatar
import app.yougram.ui.YougramBadge
import app.yougram.ui.glass.backdropSource
import app.yougram.ui.glass.glass
import app.yougram.ui.glass.rememberBackdropState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val TopBarContentHeight = 64.dp
private val BubbleInner = 6.dp
private const val GroupGapSeconds = 300
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    settings: SettingsRepository,
    onBack: () -> Unit,
    /** В правой панели планшета у корневого чата кнопки «Назад» нет. */
    showBack: Boolean = true,
    /** Тап по шапке чата — профиль собеседника, группы или канала. */
    onOpenChatProfile: () -> Unit = {},
    /** Профиль автора сообщения по id чата. */
    onOpenProfile: (Long) -> Unit = {},
    /** Звонок собеседнику в личном чате: (userId, видео). */
    onCall: (Long, Boolean) -> Unit = { _, _ -> },
    /** Открыть комментарии к канальному посту. */
    onOpenComments: (Long, Long) -> Unit = { _, _ -> },
    /** Заголовок вместо названия чата (имя темы форума). */
    titleOverride: String? = null,
    /** Подзаголовок вместо стандартного (в комментариях — название канала). */
    subtitleOverride: String? = null,
    /** Режим комментариев к посту: у сообщений нет кнопки «Комментарии». */
    threadMode: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val senders by viewModel.senders.collectAsState()
    val replies by viewModel.replies.collectAsState()
    val glass by settings.glass.collectAsState()
    val chatPrefs by settings.chatPrefs.collectAsState()
    val filters by settings.filterPrefs.collectAsState()
    val powerSaving by settings.powerSaving.collectAsState()
    val appContext = LocalContext.current
    val lowRam = remember(appContext) {
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
    }
    // Размытие под сообщениями дорогое: на слабых устройствах, в энергосбережении и при сплошных пузырях его нет.
    val bubbleBlurOn = chatPrefs.bubbleBlur && chatPrefs.bubbleOpacity < 100 && !powerSaving && !lowRam
    LaunchedEffect(bubbleBlurOn) {
        if (bubbleBlurOn) {
            settings.armChatGuard()
            delay(2500) // чат успел отрисоваться без падения
            settings.disarmChatGuard()
        }
    }
    DisposableEffect(Unit) { onDispose { settings.disarmChatGuard() } }
    val backdrop = rememberBackdropState()
    // Отдельный источник размытия только для фона чата (обои), без самих сообщений.
    val bubbleBackdrop = rememberBackdropState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val inputHolder = remember { InputHolder() }
    var input by inputHolder
    var viewerMedia by remember { mutableStateOf<MediaItem?>(null) }
    var actionMessage by remember { mutableStateOf<MessageItem?>(null) }
    var replyTo by remember { mutableStateOf<MessageItem?>(null) }
    var editing by remember { mutableStateOf<MessageItem?>(null) }
    var forwardMessage by remember { mutableStateOf<MessageItem?>(null) }
    var deleteMessage by remember { mutableStateOf<MessageItem?>(null) }
    var editsDialog by remember { mutableStateOf<List<EditRecord>?>(null) }
    var emojiSheet by remember { mutableStateOf(false) }
    var attachmentTab by remember { mutableStateOf(0) } // 0 emoji, 1 stickers, 2 GIF
    var stickerQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var highlightId by remember { mutableStateOf<Long?>(null) }
    val search by viewModel.search.collectAsState()
    val jump by viewModel.pendingJump.collectAsState()
    var recording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }
    var videoMode by remember { mutableStateOf(false) }
    var videoRecorderOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }

    fun beginRecording() {
        AudioPlayback.stop()
        if (recorder.start()) recording = true
        else Toast.makeText(context, "Не удалось начать запись", Toast.LENGTH_SHORT).show()
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginRecording()
        else Toast.makeText(context, "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }
    val videoPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) videoRecorderOpen = true
        else Toast.makeText(context, "Нужен доступ к камере и микрофону", Toast.LENGTH_SHORT).show()
    }
    // Редактирование: фокус и клавиатура на поле, курсор в конце текста.
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var draftBeforeEdit by remember { mutableStateOf("") }
    LaunchedEffect(editing?.id) {
        if (editing != null) {
            delay(200) // меню сообщения ещё закрывается
            runCatching { inputFocus.requestFocus() }
            keyboard?.show()
        }
    }

    /** Выходит из режима редактирования и возвращает в поле то, что человек набирал до него. */
    fun stopEditing() {
        editing = null
        input = draftBeforeEdit
        draftBeforeEdit = ""
    }

    fun submit() {
        val e = editing
        if (e != null) {
            // Текст не менялся — Telegram ответил бы ошибкой «сообщение не изменено», поэтому просто выходим.
            if (input.trim() != e.text.trim()) viewModel.edit(e.id, input)
            stopEditing()
        } else {
            viewModel.send(input, replyTo?.id)
            replyTo = null
            input = ""
        }
    }
    var pendingCallVideo by remember { mutableStateOf(false) }
    val callPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] != false) onCall(viewModel.chatId, pendingCallVideo)
        else Toast.makeText(context, "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }
    fun startCall(video: Boolean) {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
        }.toTypedArray()
        if (needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            onCall(viewModel.chatId, video)
        } else {
            pendingCallVideo = video
            callPermissions.launch(needed)
        }
    }
    fun openVideoRecorder() {
        AudioPlayback.stop()
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            videoRecorderOpen = true
        } else {
            videoPermissions.launch(needed)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            copyToCache(context, uri)?.let { viewModel.sendDocument(it) }
                ?: Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
        }
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val isImage = context.contentResolver.getType(uri)?.startsWith("image/") == true
            val path = copyToCache(context, uri)
            when {
                path == null -> Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
                isImage -> viewModel.sendPhoto(path)
                else -> viewModel.sendDocument(path)
            }
        }
    }
    val stickerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            copyToCache(context, uri)?.let { path ->
                viewModel.sendStickerFile(path)
            } ?: Toast.makeText(context, "Не удалось прочитать стикер", Toast.LENGTH_SHORT).show()
        }
    }
    val gifPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            copyToCache(context, uri)?.let { path ->
                viewModel.sendAnimation(path)
            } ?: Toast.makeText(context, "Не удалось прочитать GIF", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(recording) {
        recordSeconds = 0
        while (recording) {
            delay(1000)
            recordSeconds++
        }
    }

    // Фильтры: скрытые сообщения просто не попадают в список (в данных остаются).
    val visibleMessages = remember(state.messages, filters, state.blockedUserIds) {
        state.messages.filterNot { MessageFilters.isHidden(it, filters, state.blockedUserIds) }
    }
    val visibleState = rememberUpdatedState(visibleMessages)

    fun closeSearch() {
        searchOpen = false
        searchText = ""
        highlightId = null
        viewModel.clearSearch()
    }

    BackHandler(enabled = searchOpen) { closeSearch() }

    // Переход к сообщению: из результатов поиска (в чате и глобального). Подсветка снимается сама,
    // если поиск в чате не открыт.
    LaunchedEffect(jump) {
        val id = jump ?: return@LaunchedEffect
        highlightId = id
        val list = withTimeoutOrNull(2000) {
            snapshotFlow { visibleState.value }.first { l -> l.any { it.id == id } }
        }
        val idx = list?.indexOfFirst { it.id == id } ?: -1
        if (idx >= 0) {
            listState.animateScrollToItem(idx, -(listState.layoutInfo.viewportSize.height / 3))
            if (!searchOpen) {
                scope.launch {
                    delay(2000)
                    if (highlightId == id) highlightId = null
                }
            }
        } else {
            scope.launch { snackbar.showSnackbar("Сообщение не найдено в истории") }
        }
        viewModel.consumeJump()
    }

    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Высота нижней панели меняется (клавиатура, многострочный ввод), поэтому измеряем её.
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    // Клавиатура поднимает панель ввода, а список должен остаться видимым над ней.
    val imeHeight = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() } + imeHeight

    // Голосовое не должно играть после выхода из чата.
    DisposableEffect(Unit) {
        onDispose {
            AudioPlayback.stop()
            recorder.cancel()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }
    // При появлении нового сообщения прокручиваем вниз (в reverseLayout это индекс 0).
    // Если чат открыт на конкретном сообщении (из поиска), стартовую прокрутку вниз пропускаем.
    var skipInitialScroll by remember { mutableStateOf(viewModel.initialMessageId != 0L) }
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        if (state.messages.isNotEmpty()) {
            if (skipInitialScroll) skipInitialScroll = false
            else if (!searchOpen) listState.animateScrollToItem(0)
        }
    }

    // reverseLayout: старые сообщения — в конце списка; у верхнего края просим следующую порцию.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 8
        }.collect { nearTop -> if (nearTop) viewModel.loadOlder() }
    }

    Box(Modifier.fillMaxSize()) {
        // Сообщения: прокручиваются под панелями, их размытая копия видна в панелях.
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (bubbleBlurOn) Modifier.backdropSource(bubbleBackdrop) else Modifier)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                if (chatPrefs.wallpaper != 0L) ChatWallpaper(chatPrefs.wallpaper, Modifier.fillMaxSize())
            }
            if (state.loading && state.messages.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topInset + TopBarContentHeight + 8.dp,
                        bottom = bottomBarHeight + 8.dp,
                    ),
                ) {
                    // Список в обратном порядке: index - 1 — более новое сообщение, index + 1 — более старое.
                    itemsIndexed(visibleMessages, key = { _, m -> m.id }) { index, message ->
                        val newer = visibleMessages.getOrNull(index - 1)
                        val older = visibleMessages.getOrNull(index + 1)
                        val joinedWithOlder = older != null && sameGroup(older, message)
                        val joinedWithNewer = newer != null && sameGroup(message, newer)
                        MessageBubble(
                            message = message,
                            joinedWithOlder = joinedWithOlder,
                            joinedWithNewer = joinedWithNewer,
                            viewModel = viewModel,
                            textSize = chatPrefs.textSize,
                            cornerRadius = chatPrefs.bubbleRadius.dp,
                            onOpenPhoto = { media -> viewerMedia = media },
                            deleted = message.id in state.deletedIds,
                            edits = state.edits[message.id].orEmpty(),
                            readAt = state.readAt[message.id],
                            onLongPress = { actionMessage = message },
                            onShowEdits = { editsDialog = state.edits[message.id] },
                            groupChat = state.isGroup || state.isChannel,
                            sender = message.senderKey?.let { senders[it] },
                            onOpenSender = { message.senderKey?.let { key -> viewModel.openSender(key, onOpenProfile) } },
                            reply = message.reply?.let { replies[it.messageId] },
                            onReplyClick = {
                                val target = message.reply?.messageId
                                val idx = visibleMessages.indexOfFirst { it.id == target }
                                scope.launch {
                                    if (idx >= 0) {
                                        listState.animateScrollToItem(idx)
                                        highlightId = target
                                        delay(1500)
                                        if (!searchOpen && highlightId == target) highlightId = null
                                    } else {
                                        snackbar.showSnackbar("Исходное сообщение не загружено")
                                    }
                                }
                            },
                            onReact = { emoji -> viewModel.react(message.id, emoji) },
                            onOpenComments = { onOpenComments(message.chatId, message.id) },
                            commentsEnabled = !threadMode,
                            bubbleOpacity = chatPrefs.bubbleOpacity / 100f,
                            bubbleBackdrop = if (bubbleBlurOn) bubbleBackdrop else null,
                            glassSettings = glass,
                            highlighted = message.id == highlightId,
                            searchQuery = if (searchOpen) search.query else "",
                        )
                    }
                    if (state.loadingOlder) {
                        item(key = "older-loader") {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }

        // Верхняя панель: назад, аватарка, название.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topInset + TopBarContentHeight)
                .glass(backdrop, glass, RectangleShape),
        ) {
            if (searchOpen) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Row(
                    Modifier
                        .padding(top = topInset)
                        .height(TopBarContentHeight)
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { closeSearch() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Закрыть поиск")
                    }
                    TextField(
                        value = searchText,
                        onValueChange = {
                            searchText = it
                            viewModel.search(it)
                        },
                        placeholder = { Text("Поиск в чате") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.searchOlder() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    val counter = when {
                        search.loading -> "…"
                        search.notFound -> "0"
                        search.index >= 0 -> "${search.index + 1}/${search.total}"
                        else -> ""
                    }
                    if (counter.isNotEmpty()) {
                        Text(
                            counter,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.searchOlder() }, enabled = search.ids.isNotEmpty()) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Предыдущее (старее)")
                    }
                    IconButton(onClick = { viewModel.searchNewer() }, enabled = search.index > 0) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Следующее (новее)")
                    }
                }
            } else {
                Row(
                    Modifier
                        .padding(top = topInset)
                        .height(TopBarContentHeight)
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .height(TopBarContentHeight)
                            .clickable(onClick = onOpenChatProfile),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileAvatar(title = state.title, fileId = state.avatarFileId, fileState = viewModel::fileState, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    titleOverride?.takeIf { it.isNotEmpty() } ?: state.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                YougramBadge(viewModel.chatId, Modifier.padding(start = 6.dp))
                            }
                            if ((subtitleOverride ?: state.subtitle).isNotEmpty()) {
                                Text(
                                    subtitleOverride ?: state.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.isOnline) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    IconButton(onClick = { searchOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Search, contentDescription = "Поиск в чате", modifier = Modifier.size(22.dp))
                    }
                    // Звонки доступны только в личных чатах (id чата = id пользователя).
                    if (!state.isGroup && !state.isChannel && viewModel.chatId > 0L) {
                        IconButton(onClick = { startCall(false) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Call, contentDescription = "Аудиозвонок", modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { startCall(true) }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Videocam, contentDescription = "Видеозвонок", modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        }

        // Нижняя панель: поле ввода поднимается над клавиатурой.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding(),
        ) {
            SnackbarHost(snackbar)
            if (!state.canSendMessages) {
                val restrictionText = if (state.isChannel) {
                    "В этом канале нельзя писать"
                } else {
                    "Вам запрещено писать здесь"
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeightPx = it.height }
                        .glass(backdrop, glass, RectangleShape)
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        )
                        .heightIn(min = 52.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = restrictionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val bannerMessage = editing ?: replyTo
                if (bannerMessage != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(backdrop, glass, RectangleShape)
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (editing != null) "Редактирование" else "Ответ",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                bannerMessage.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            if (editing != null) stopEditing()
                            replyTo = null
                        }) { Icon(Icons.Filled.Close, contentDescription = "Отмена") }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeightPx = it.height }
                        .glass(backdrop, glass, RectangleShape)
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Поле ввода-«таблетка».
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        shape = RoundedCornerShape(28.dp),
                        // Полупрозрачное поле: сквозь него просвечивают обои и размытый фон панели.
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = InputFieldAlpha),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (recording) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "%d:%02d".format(recordSeconds / 60, recordSeconds % 60),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "Отмена",
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            recorder.cancel()
                                            recording = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.AddCircleOutline,
                                    contentDescription = "Файл",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { filePicker.launch(arrayOf("*/*")) },
                                )
                                Spacer(Modifier.width(12.dp))
                                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (input.isEmpty()) {
                                        Text(
                                            "Сообщение",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    BasicTextField(
                                        value = inputHolder.value,
                                        onValueChange = { inputHolder.value = it },
                                        modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
                                        maxLines = 5,
                                        keyboardOptions = KeyboardOptions(
                                            imeAction = if (chatPrefs.enterToSend) ImeAction.Send else ImeAction.Default,
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSend = {
                                                if (input.isNotBlank()) submit()
                                            },
                                        ),
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    Icons.Filled.EmojiEmotions,
                                    contentDescription = "Эмодзи",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { attachmentTab = 0; emojiSheet = true },
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    Icons.Filled.Image,
                                    contentDescription = "Фото или видео",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                        },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Круглая кнопка: отправка (есть текст) / микрофон / камера для кружка.
                    // Тап — действие, долгое нажатие — переключение «голосовое ↔ кружок».
                    val canSend = input.isNotBlank()
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .pointerInput(canSend, recording, videoMode) {
                                detectTapGestures(
                                    onLongPress = { if (!canSend && !recording) videoMode = !videoMode },
                                    onTap = {
                                        when {
                                            canSend -> submit()
                                            recording -> {
                                                recording = false
                                                recorder.stop()?.let { (path, seconds) -> viewModel.sendVoice(path, seconds) }
                                            }
                                            videoMode -> openVideoRecorder()
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                                    PackageManager.PERMISSION_GRANTED -> beginRecording()
                                            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                )
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when {
                                    canSend || recording -> Icons.AutoMirrored.Filled.Send
                                    videoMode -> Icons.Filled.Videocam
                                    else -> Icons.Filled.Mic
                                },
                                contentDescription = when {
                                    canSend || recording -> "Отправить"
                                    videoMode -> "Видеосообщение"
                                    else -> "Голосовое сообщение"
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }

        // Меню эмодзи / стикеров / GIF: полупрозрачная панель с размытием фона чата.
        run {
            GlassPickerPanel(
                visible = emojiSheet,
                backdrop = backdrop,
                glass = glass,
                transparency = LocalPlates.current.picker,
                onDismiss = { emojiSheet = false },
            ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickerTabButton(
                    icon = "😀",
                    title = "Эмодзи",
                    selected = attachmentTab == 0,
                    onClick = { attachmentTab = 0 },
                    modifier = Modifier.weight(1f),
                )
                PickerTabButton(
                    icon = "🎨",
                    title = "Стикеры",
                    selected = attachmentTab == 1,
                    onClick = { attachmentTab = 1 },
                    modifier = Modifier.weight(1f),
                )
                PickerTabButton(
                    icon = "GIF",
                    title = "GIF",
                    selected = attachmentTab == 2,
                    onClick = { attachmentTab = 2 },
                    modifier = Modifier.weight(1f),
                )
            }
            when (attachmentTab) {
                0 -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(48.dp),
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(Emojis) { emoji ->
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).clickable { input += emoji },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 28.sp) }
                    }
                }
                1 -> StickerTab(
                    viewModel = viewModel,
                    query = stickerQuery,
                    onQueryChange = { stickerQuery = it },
                    onPickFile = { stickerPicker.launch(arrayOf("image/webp", "image/png")) },
                    onSent = { emojiSheet = false },
                )
                else -> GifTab(
                    viewModel = viewModel,
                    onPickFile = { gifPicker.launch(arrayOf("image/gif", "video/mp4")) },
                    onSent = { emojiSheet = false },
                )
            }
            }
        }
    }

    if (videoRecorderOpen) {
        VideoNoteRecorderDialog(
            onSend = { path, seconds, length -> viewModel.sendVideoNote(path, seconds, length) },
            onDismiss = { videoRecorderOpen = false },
        )
    }

    viewerMedia?.let { media ->
        PhotoViewer(media = media, viewModel = viewModel, onDismiss = { viewerMedia = null })
    }

    actionMessage?.let { message ->
        val sender = message.senderUserId
        var available by remember(message.id) { mutableStateOf(DefaultReactions) }
        LaunchedEffect(message.id) {
            viewModel.availableReactions(message.id).takeIf { it.isNotEmpty() }?.let { available = it }
        }
        MessageMenu(
            message = message,
            isRead = state.readAt[message.id] != null,
            canEdit = message.isOutgoing && message.media == null && message.call == null && message.id !in state.deletedIds,
            canSave = message.media?.kind.let { it == MediaKind.PHOTO || it == MediaKind.VIDEO || it == MediaKind.ANIMATION || it == MediaKind.STICKER || it == MediaKind.DOCUMENT },
            canShadowBan = sender != null && !message.isOutgoing,
            reactions = available,
            onDismiss = { actionMessage = null },
            onReact = { viewModel.react(message.id, it) },
            onReply = { if (editing != null) stopEditing(); replyTo = message },
            onSave = {
                val media = message.media ?: return@MessageMenu
                scope.launch {
                    val path = viewModel.fileState(media.fileId).first().path
                    if (path == null) {
                        viewModel.download(media.fileId)
                        snackbar.showSnackbar("Файл ещё загружается, попробуйте позже")
                    } else {
                        val ok = saveToGallery(context, path, media.name, media.mimeType, media.kind)
                        snackbar.showSnackbar(if (ok) "Сохранено в галерею" else "Не удалось сохранить")
                    }
                }
            },
            onForward = { forwardMessage = message },
            onPin = { viewModel.pin(message.id) },
            onEdit = {
                replyTo = null
                if (editing == null) draftBeforeEdit = input
                editing = message
                input = message.text
            },
            onDelete = { deleteMessage = message },
            onShadowBan = { sender?.let { viewModel.shadowBan(it) } },
        )
    }

    deleteMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteMessage = null },
            title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение будет удалено у всех, если это позволяют права в чате.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(message.id); deleteMessage = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteMessage = null }) { Text("Отмена") } },
        )
    }

    forwardMessage?.let { message ->
        val chatList by viewModel.chats.collectAsState()
        AlertDialog(
            onDismissRequest = { forwardMessage = null },
            title = { Text("Переслать в…") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    chatList.forEach { chat ->
                        Text(
                            chat.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.forward(message.id, chat.id)
                                    forwardMessage = null
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { forwardMessage = null }) { Text("Отмена") } },
        )
    }

    editsDialog?.let { records ->
        AlertDialog(
            onDismissRequest = { editsDialog = null },
            title = { Text("История правок") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    records.asReversed().forEach { record ->
                        Text(
                            formatTime(record.at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(record.oldText, modifier = Modifier.padding(bottom = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editsDialog = null }) { Text("Закрыть") } },
        )
    }
}

/** Два соседних сообщения — одна группа: тот же автор и интервал меньше пяти минут. */
private fun sameGroup(older: MessageItem, newer: MessageItem): Boolean =
    older.isOutgoing == newer.isOutgoing &&
            older.senderKey == newer.senderKey &&
            abs(newer.date - older.date) < GroupGapSeconds

private fun formatTime(date: Int): String =
    Instant.ofEpochSecond(date.toLong()).atZone(ZoneId.systemDefault()).format(TimeFormatter)

private val SenderColors = listOf(
    Color(0xFFE17076), Color(0xFFEDA86C), Color(0xFFA695E7), Color(0xFF7BC862),
    Color(0xFF6EC9CB), Color(0xFF65AADD), Color(0xFFEE7AAE),
)

private fun senderColor(key: Long): Color = SenderColors[Math.floorMod(key, SenderColors.size.toLong()).toInt()]

@Composable
private fun SenderName(key: Long, sender: SenderInfo?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(
            sender?.name ?: "…",
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = senderColor(key),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        YougramBadge(key, Modifier.padding(start = 4.dp), size = 14.dp)
        sender?.username?.let {
            Text(
                "  @$it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageItem,
    joinedWithOlder: Boolean,
    joinedWithNewer: Boolean,
    viewModel: ChatViewModel,
    textSize: Int,
    cornerRadius: Dp,
    onOpenPhoto: (MediaItem) -> Unit,
    deleted: Boolean,
    edits: List<EditRecord>,
    readAt: Int?,
    onLongPress: () -> Unit,
    onShowEdits: () -> Unit,
    groupChat: Boolean,
    sender: SenderInfo?,
    onOpenSender: () -> Unit,
    reply: ReplyPreview?,
    onReplyClick: () -> Unit,
    onReact: (String) -> Unit,
    onOpenComments: () -> Unit,
    commentsEnabled: Boolean,
    bubbleOpacity: Float,
    bubbleBackdrop: BackdropState?,
    glassSettings: GlassSettings,
    highlighted: Boolean,
    searchQuery: String,
) {
    val mine = message.isOutgoing
    val media = message.media
    val senderKey = message.senderKey
    // В группах у чужих сообщений показываем автора: аватарка и имя с тегом у первого сообщения серии.
    val showSenderUi = groupChat && !mine && senderKey != null
    val showName = showSenderUi && !joinedWithOlder
    val showAvatar = showSenderUi && !joinedWithOlder
    // Кружок показывается без пузыря.
    val bare = media?.kind == MediaKind.VIDEO_NOTE && message.text.isEmpty()
    // Кнопка комментариев прилегает к самому посту — полоса внизу пузыря.
    val withComments = commentsEnabled && message.hasComments && !bare

    LaunchedEffect(senderKey, showSenderUi) {
        if (showSenderUi && senderKey != null) viewModel.ensureSender(senderKey)
    }

    val replyRef = message.reply
    LaunchedEffect(replyRef) { if (replyRef != null) viewModel.ensureReply(replyRef) }

    // Углы со стороны автора у склеенных сообщений становятся мелкими.
    val inner = minOf(BubbleInner, cornerRadius)
    val top = if (joinedWithOlder) inner else cornerRadius
    val bottom = if (joinedWithNewer) inner else cornerRadius
    val shape = if (mine) {
        RoundedCornerShape(topStart = cornerRadius, topEnd = top, bottomEnd = bottom, bottomStart = cornerRadius)
    } else {
        RoundedCornerShape(topStart = top, topEnd = cornerRadius, bottomEnd = cornerRadius, bottomStart = bottom)
    }

    val baseColor = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val bubbleColor by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else baseColor,
        animationSpec = tween(250),
        label = "bubbleHighlight",
    )
    val matchColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (joinedWithOlder) 2.dp else 12.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (showSenderUi) {
            Box(Modifier.size(36.dp).clickable(enabled = showAvatar, onClick = onOpenSender)) {
                if (showAvatar) {
                    FileAvatar(
                        title = sender?.name.orEmpty().ifEmpty { "?" },
                        fileId = sender?.avatarFileId,
                        fileState = viewModel::fileState,
                        size = 36.dp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (bare && media != null) {
                Column(Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }) {
                    if (showName && senderKey != null) {
                        SenderName(senderKey, sender, onOpenSender, Modifier.padding(start = 4.dp, bottom = 4.dp))
                    }
                    MessageMedia(media, viewModel, onOpenPhoto)
                }
            } else {
                val glassModifier = if (bubbleBackdrop != null) {
                    Modifier.glass(bubbleBackdrop, glassSettings.copy(opacity = bubbleOpacity), shape, tint = bubbleColor)
                } else {
                    Modifier
                }
                Surface(
                    modifier = glassModifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
                    shape = shape,
                    color = if (bubbleBackdrop != null) Color.Transparent else bubbleColor.copy(alpha = bubbleOpacity),
                    contentColor = when {
                        highlighted -> MaterialTheme.colorScheme.onTertiaryContainer
                        mine -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Column(
                        Modifier
                            .widthIn(max = if (showSenderUi) 290.dp else 320.dp)
                            .then(if (withComments) Modifier.width(IntrinsicSize.Max) else Modifier),
                    ) {
                        if (showName && senderKey != null) {
                            SenderName(
                                senderKey, sender, onOpenSender,
                                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            )
                        }
                        if (replyRef != null) {
                            ReplyQuote(
                                preview = reply,
                                mine = mine,
                                onClick = onReplyClick,
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp),
                            )
                        }
                        if (media != null) {
                            Box(Modifier.padding(4.dp)) { MessageMedia(media, viewModel, onOpenPhoto) }
                        }
                        if (message.text.isNotEmpty()) {
                            val linked = rememberLinkified(
                                message.text,
                                if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                LocalOpenLink.current,
                                LocalOpenUsername.current,
                            )
                            val shown = remember(linked, searchQuery, matchColor) {
                                linked.highlightMatches(searchQuery, matchColor)
                            }
                            Text(
                                shown,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = textSize.sp,
                                    lineHeight = (textSize * 1.4f).sp,
                                ),
                            )
                        }
                        // Время у каждого сообщения внутри пузыря, справа внизу.
                        MessageFooter(
                            message = message,
                            mine = mine,
                            deleted = deleted,
                            edits = edits,
                            readAt = readAt,
                            onShowEdits = onShowEdits,
                            modifier = Modifier.align(Alignment.End).padding(start = 16.dp, end = 14.dp, top = 2.dp, bottom = if (withComments) 6.dp else 8.dp),
                        )
                        if (withComments) {
                            CommentsButton(
                                count = message.commentCount,
                                onClick = onOpenComments,
                                attached = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            // У кружка пузыря нет — время выводим под ним.
            if (bare) {
                MessageFooter(
                    message = message,
                    mine = mine,
                    deleted = deleted,
                    edits = edits,
                    readAt = readAt,
                    onShowEdits = onShowEdits,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (message.reactions.isNotEmpty()) {
                ReactionRow(message.reactions, onReact, Modifier.padding(top = 4.dp))
            }
            if (commentsEnabled && message.hasComments && bare) {
                CommentsButton(
                    count = message.commentCount,
                    onClick = onOpenComments,
                    attached = false,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/** Строка под сообщением: время, «прочитано», «изменено», «удалено». */
@Composable
private fun MessageFooter(
    message: MessageItem,
    mine: Boolean,
    deleted: Boolean,
    edits: List<EditRecord>,
    readAt: Int?,
    onShowEdits: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (edits.isNotEmpty()) {
            Text(
                "изменено · ",
                modifier = Modifier.clickable(onClick = onShowEdits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (deleted) {
            Text("удалено · ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        if (message.paidMessageStars > 0) {
            // Платное сообщение: отправитель заплатил звёздами.
            Icon(Icons.Filled.Star, contentDescription = "Платное сообщение", tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
            Text(" ${message.paidMessageStars} · ", style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Text(formatTime(message.date), style = MaterialTheme.typography.labelSmall, color = muted)
        if (mine) {
            Spacer(Modifier.width(4.dp))
            if (readAt != null) {
                Icon(
                    Icons.Filled.DoneAll,
                    contentDescription = "Прочитано",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    Icons.Filled.Done,
                    contentDescription = "Отправлено",
                    tint = muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Цитата оригинала над текстом ответа; тап прокручивает к оригиналу. */
@Composable
private fun ReplyQuote(
    preview: ReplyPreview?,
    mine: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    Row(
        modifier
            .widthIn(min = 120.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                preview?.author.orEmpty().ifEmpty { "Ответ" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preview?.text ?: "…",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Реакции чипами под сообщением; тап ставит или снимает свою. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionRow(
    reactions: List<ReactionItem>,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier.widthIn(max = 320.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { r ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (r.chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onReact(r.emoji) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.emoji, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    r.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PickerTabButton(
    icon: String,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = pickerChipColor(selected),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon)
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StickerPickerItem(sticker: StickerItem, viewModel: ChatViewModel, onClick: () -> Unit) {
    val state by viewModel.fileState(sticker.fileId).collectAsState(FileState())
    LaunchedEffect(sticker.fileId) { viewModel.download(sticker.fileId, 8) }
    val bitmap = rememberFileBitmap(state.path, 256)
    Box(
        Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit) }
            ?: CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

private val Emojis = ("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😛 😜 🤪 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 😶 😐 😑 😬 🙄 😯 😴 🤤 😷 🤒 🤕 🤢 🤮 🤧 😈 👿 💀 👻 👽 🤖 💩 " +
        "👍 👎 👌 ✌️ 🤞 🤟 🤘 👈 👉 👆 👇 ✋ 👋 👏 🙌 🙏 💪 🤝 ✍️ 💅 " +
        "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 🔥 ✨ ⭐ 🌟 💥 💯 ✅ ❌ ❓ ❗ " +
        "🎉 🎊 🎁 🎂 🍕 🍔 🍟 🍺 🍻 ☕ 🍷 ⚽ 🏀 🎮 🎵 🎧 📱 💻 📷 🚗 ✈️ 🌍 🌹 🌞 🌙 ⚡ 🌈").split(" ")

/** Копирует выбранный пользователем файл в кэш под его настоящим именем и возвращает путь (TDLib нужен обычный путь). */
private suspend fun copyToCache(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "file_${System.currentTimeMillis()}"
        val dir = File(context.cacheDir, "uploads/${System.currentTimeMillis()}").apply { mkdirs() }
        val file = File(dir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            ?: return@runCatching null
        file.absolutePath
    }.getOrNull()
}

/** Прозрачность «таблетки» поля ввода: 0 — полностью прозрачная, 1 — непрозрачная. */
private const val InputFieldAlpha = 0.5f

/**
 * Состояние поля ввода. Внутри — TextFieldValue (чтобы не ломать композицию клавиатуры при наборе),
 * снаружи — обычная строка: присваивание строки ставит курсор в конец.
 */
private class InputHolder {
    var value by mutableStateOf(TextFieldValue(""))
}

private operator fun InputHolder.getValue(thisRef: Any?, property: KProperty<*>): String = value.text

private operator fun InputHolder.setValue(thisRef: Any?, property: KProperty<*>, newText: String) {
    if (newText != value.text) value = TextFieldValue(newText, TextRange(newText.length))
}
