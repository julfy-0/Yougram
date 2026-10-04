package app.yougram.ui.chat

import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import app.yougram.ui.ChatWallpaper
import app.yougram.data.EditRecord
import app.yougram.data.MediaItem
import app.yougram.data.MediaKind
import app.yougram.data.SenderInfo
import app.yougram.ui.FileAvatar
import app.yougram.data.MessageFilters
import app.yougram.data.MessageItem
import app.yougram.data.SettingsRepository
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
    /** Тап по шапке чата — профиль собеседника, группы или канала. */
    onOpenChatProfile: () -> Unit = {},
    /** Профиль автора сообщения по id чата. */
    onOpenProfile: (Long) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val senders by viewModel.senders.collectAsState()
    val glass by settings.glass.collectAsState()
    val chatPrefs by settings.chatPrefs.collectAsState()
    val filters by settings.filterPrefs.collectAsState()
    val backdrop = rememberBackdropState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }
    var viewerMedia by remember { mutableStateOf<MediaItem?>(null) }
    var actionMessage by remember { mutableStateOf<MessageItem?>(null) }
    var editsDialog by remember { mutableStateOf<List<EditRecord>?>(null) }
    var emojiSheet by remember { mutableStateOf(false) }
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
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(0)
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
            if (chatPrefs.wallpaper != 0L) ChatWallpaper(chatPrefs.wallpaper, Modifier.fillMaxSize())
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
            Row(
                Modifier
                    .padding(top = topInset)
                    .height(TopBarContentHeight)
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    YougramBadge(viewModel.chatId, Modifier.padding(start = 6.dp))
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
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                        value = input,
                                        onValueChange = { input = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 5,
                                        keyboardOptions = KeyboardOptions(
                                            imeAction = if (chatPrefs.enterToSend) ImeAction.Send else ImeAction.Default,
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onSend = {
                                                if (input.isNotBlank()) {
                                                    viewModel.send(input)
                                                    input = ""
                                                }
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
                                        .clickable { emojiSheet = true },
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
                                            canSend -> {
                                                viewModel.send(input)
                                                input = ""
                                            }
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
    }

    if (videoRecorderOpen) {
        VideoNoteRecorderDialog(
            onSend = { path, seconds, length -> viewModel.sendVideoNote(path, seconds, length) },
            onDismiss = { videoRecorderOpen = false },
        )
    }

    if (emojiSheet) {
        ModalBottomSheet(onDismissRequest = { emojiSheet = false }) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(48.dp),
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                items(Emojis) { emoji ->
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).clickable { input += emoji },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 28.sp) }
                }
            }
        }
    }

    viewerMedia?.let { media ->
        PhotoViewer(media = media, viewModel = viewModel, onDismiss = { viewerMedia = null })
    }

    actionMessage?.let { message ->
        val sender = message.senderUserId
        AlertDialog(
            onDismissRequest = { actionMessage = null },
            title = { Text("Сообщение") },
            text = {
                Text(
                    if (sender != null && !message.isOutgoing) {
                        "Скрывать все сообщения этого пользователя во всех чатах? Отменить можно в разделе " +
                                "«Фильтры сообщений» → «Теневой бан»."
                    } else {
                        "Для этого сообщения нет доступных действий."
                    },
                )
            },
            confirmButton = {
                if (sender != null && !message.isOutgoing) {
                    TextButton(onClick = {
                        viewModel.shadowBan(sender)
                        actionMessage = null
                    }) { Text("Теневой бан") }
                }
            },
            dismissButton = { TextButton(onClick = { actionMessage = null }) { Text("Закрыть") } },
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

    LaunchedEffect(senderKey, showSenderUi) {
        if (showSenderUi && senderKey != null) viewModel.ensureSender(senderKey)
    }

    // Углы со стороны автора у склеенных сообщений становятся мелкими.
    val inner = minOf(BubbleInner, cornerRadius)
    val top = if (joinedWithOlder) inner else cornerRadius
    val bottom = if (joinedWithNewer) inner else cornerRadius
    val shape = if (mine) {
        RoundedCornerShape(topStart = cornerRadius, topEnd = top, bottomEnd = bottom, bottomStart = cornerRadius)
    } else {
        RoundedCornerShape(topStart = top, topEnd = cornerRadius, bottomEnd = cornerRadius, bottomStart = bottom)
    }

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
                Surface(
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
                    shape = shape,
                    color = if (mine) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (mine) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Column(Modifier.widthIn(max = if (showSenderUi) 290.dp else 320.dp)) {
                        if (showName && senderKey != null) {
                            SenderName(
                                senderKey, sender, onOpenSender,
                                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            )
                        }
                        if (media != null) {
                            Box(Modifier.padding(4.dp)) { MessageMedia(media, viewModel, onOpenPhoto) }
                        }
                        if (message.text.isNotEmpty()) {
                            Text(
                                message.text,
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
                            modifier = Modifier.align(Alignment.End).padding(start = 16.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
                        )
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
        Text(formatTime(message.date), style = MaterialTheme.typography.labelSmall, color = muted)
        if (mine && readAt != null) {
            Text(" · прочитано ${formatTime(readAt)}", style = MaterialTheme.typography.labelSmall, color = muted)
        }
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