package app.yougram.feature.chat.ui

import app.yougram.core.ui.theme.LocalChatBackground
import android.Manifest
import android.location.Location
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import app.yougram.core.ui.component.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.yougram.core.settings.GlassSettings
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.ui.component.Avatar
import app.yougram.core.ui.component.ChatWallpaper
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.component.LocalOpenLink
import app.yougram.core.ui.component.LocalOpenUsername
import app.yougram.core.ui.component.highlightMatches
import app.yougram.core.ui.component.rememberFileBitmap
import app.yougram.core.ui.component.rememberLinkified
import app.yougram.core.ui.glass.BackdropState
import app.yougram.core.ui.glass.LocalPlates
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.feature.badge.ui.YougramBadge
import app.yougram.feature.chat.comments.ui.CommentsButton
import app.yougram.feature.chat.data.EditRecord
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.MediaItem
import app.yougram.feature.chat.data.MediaKind
import app.yougram.feature.chat.data.MessageFilters
import app.yougram.feature.chat.data.MessageItem
import app.yougram.feature.chat.data.ReactionItem
import app.yougram.feature.chat.data.ReplyPreview
import app.yougram.feature.chat.data.SenderInfo
import app.yougram.feature.chat.data.StickerItem
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.reflect.KProperty
import app.yougram.core.settings.DraftStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private val TopBarContentHeight = 64.dp
/** Угол со стороны автора у сообщений, склеенных в серию. */
private val BubbleInner = 8.dp
/** «Хвостик»: острый угол у последнего сообщения серии. */
private val BubbleTail = 3.dp
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
    // Секретный чат: запрещаем скриншоты и запись экрана (FLAG_SECURE) во всех окнах чата.
    androidx.compose.runtime.DisposableEffect(state.isSecret) {
        if (state.isSecret) app.yougram.core.ui.SecureScreen.enter()
        onDispose { if (state.isSecret) app.yougram.core.ui.SecureScreen.leave() }
    }
    app.yougram.core.ui.SecureWindowEffect()
    val senders by viewModel.senders.collectAsState()
    val replies by viewModel.replies.collectAsState()
    val glass by settings.glass.collectAsState()
    val chatPrefs by settings.chatPrefs.collectAsState()
    val filters by settings.filterPrefs.collectAsState()
    val powerSaving by settings.powerSaving.collectAsState()
    val appContext = LocalContext.current
    val lowRam = remember(appContext) {
        app.yougram.core.ui.DeviceProfile.tier(appContext) == app.yougram.core.ui.DeviceTier.Low
    }
    // Пружинное движение Expressive; на слабых устройствах — короткие tween.
    val motion = MaterialTheme.motionScheme
    val fadeSpec: FiniteAnimationSpec<Float> = if (lowRam) tween(150) else motion.defaultEffectsSpec()
    val fastScaleSpec: FiniteAnimationSpec<Float> = if (lowRam) tween(150) else motion.fastSpatialSpec()
    val slideSpec: FiniteAnimationSpec<IntOffset> = if (lowRam) tween(220) else motion.defaultSpatialSpec()
    val cornerSpec: FiniteAnimationSpec<Dp> = if (lowRam) snap() else motion.fastSpatialSpec()
    val colorSpec: FiniteAnimationSpec<Color> = if (lowRam) snap() else motion.defaultEffectsSpec()
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
    val draftContext = LocalContext.current
    val inputHolder = remember {
        InputHolder().also { h ->
            val saved = DraftStore.get(draftContext, viewModel.chatId, viewModel.topicId, viewModel.threadId)
            h.value = TextFieldValue(saved, TextRange(saved.length))
        }
    }
    var input by inputHolder
    var commandsOpen by remember { mutableStateOf(false) }
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
    // Запись зажатием: recordLocked — голосовая закреплена свайпом вверх; videoHold/videoAuto — то же для кружка.
    var recordLocked by remember { mutableStateOf(false) }
    var videoHold by remember { mutableStateOf(HOLD_NONE) }
    var videoAuto by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { VoiceRecorder(context) }

    fun beginRecording() {
        AudioPlayback.stop()
        if (recorder.start()) recording = true
        else Toast.makeText(context, "Не удалось начать запись", Toast.LENGTH_SHORT).show()
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recordLocked = true // палец уже отпущен: запись идёт, как после закрепления
            beginRecording()
        } else Toast.makeText(context, "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }
    val videoPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            videoAuto = false
            videoHold = HOLD_NONE
            videoRecorderOpen = true
        }
        else Toast.makeText(context, "Нужен доступ к камере и микрофону", Toast.LENGTH_SHORT).show()
    }
    // Редактирование: фокус и клавиатура на поле, курсор в конце текста.
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var draftBeforeEdit by remember { mutableStateOf("") }
    // Черновик: сохраняем набранное (кроме режима редактирования), при выходе из чата — сразу.
    val latestDraft by rememberUpdatedState(if (editing == null) input else null)
    LaunchedEffect(input, editing == null) {
        if (editing == null) {
            delay(400)
            DraftStore.set(draftContext, viewModel.chatId, viewModel.topicId, viewModel.threadId, input)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            latestDraft?.let { DraftStore.set(draftContext, viewModel.chatId, viewModel.topicId, viewModel.threadId, it) }
        }
    }
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
    /** hold = true: запись кружка стартует сразу и идёт, пока палец зажат. Возвращает true, если окно открыто. */
    fun openVideoRecorder(hold: Boolean = false): Boolean {
        AudioPlayback.stop()
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        return if (needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            videoAuto = hold
            videoHold = if (hold) HOLD_ACTIVE else HOLD_NONE
            videoRecorderOpen = true
            true
        } else {
            videoAuto = false
            videoHold = HOLD_NONE
            videoPermissions.launch(needed)
            false
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            copyToCache(context, uri)?.let { viewModel.sendDocument(it) }
                ?: Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
        }
    }
    // «Сохранить в файлы»: путь скачанного файла ждёт, пока пользователь выберет место в проводнике.
    var exportPath by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val path = exportPath
        exportPath = null
        if (uri != null && path != null) scope.launch {
            val ok = exportToUri(context, path, uri)
            snackbar.showSnackbar(if (ok) "Файл сохранён" else "Не удалось сохранить")
        }
    }
    // Меню «+», опрос и отправка геопозиции (с подтверждением перед отправкой).
    var attachOpen by remember { mutableStateOf(false) }
    var pollOpen by remember { mutableStateOf(false) }
    var pendingLocation by remember { mutableStateOf<Location?>(null) }
    fun fetchLocation() {
        Toast.makeText(context, "Определяем местоположение…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val loc = currentLocation(context)
            if (loc != null) pendingLocation = loc
            else Toast.makeText(context, "Не удалось определить местоположение", Toast.LENGTH_SHORT).show()
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) fetchLocation()
        else Toast.makeText(context, "Нет доступа к геолокации", Toast.LENGTH_SHORT).show()
    }
    // Выбранное медиа сначала попадает в экран подготовки (подпись, поворот, обрезка), и только потом уходит.
    var composing by remember { mutableStateOf<PendingMedia?>(null) }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val path = copyToCache(context, uri)
            if (path == null) {
                Toast.makeText(context, "Не удалось прочитать файл", Toast.LENGTH_SHORT).show()
            } else {
                composing = PendingMedia(
                    path,
                    when {
                        mime == "image/gif" -> PendingKind.GIF
                        mime.startsWith("image/") -> PendingKind.PHOTO
                        else -> PendingKind.VIDEO
                    },
                )
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
                composing = PendingMedia(path, PendingKind.GIF)
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
    var unreadNew by remember { mutableStateOf(0) }
    var lastNewestId by remember { mutableStateOf<Long?>(null) }
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val showScrollDown by remember {
        derivedStateOf { listState.firstVisibleItemIndex >= 20 || (unreadNew > 0 && listState.firstVisibleItemIndex > 0) }
    }
    LaunchedEffect(atBottom) { if (atBottom) unreadNew = 0 }
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        val newest = state.messages.firstOrNull() ?: return@LaunchedEffect
        val prevId = lastNewestId
        lastNewestId = newest.id
        if (skipInitialScroll) {
            skipInitialScroll = false
            return@LaunchedEffect
        }
        if (prevId == null) {
            if (!searchOpen) listState.scrollToItem(0)
            return@LaunchedEffect
        }
        // Индекс <= 2: пользователь у самого низа (после вставки список мог сдвинуться на 1–2 позиции).
        val nearBottom = listState.firstVisibleItemIndex <= 2
        if (newest.isOutgoing || (nearBottom && chatPrefs.autoScrollNew)) {
            if (!searchOpen) listState.animateScrollToItem(0)
        } else {
            val prevIndex = state.messages.indexOfFirst { it.id == prevId }
            val fresh = if (prevIndex < 0) listOf(newest) else state.messages.take(prevIndex)
            unreadNew += fresh.count { !it.isOutgoing }.coerceAtLeast(if (newest.isOutgoing) 0 else 1)
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

    // Список команд бота для подсказки (по кнопке-меню или при вводе «/»).
    val typedCommand = input.startsWith("/") && !input.contains(' ') && !input.contains('\n')
    val shownCommands = when {
        state.botCommands.isEmpty() -> emptyList()
        typedCommand -> state.botCommands.filter { ("/" + it.first).lowercase().startsWith(input.lowercase()) }
        commandsOpen && input.isEmpty() -> state.botCommands
        else -> emptyList()
    }

    // Меню «+», меню сообщения и запись кружка: пока они открыты, весь экран чата плавно размывается
    // (сами они — отдельные окна, остаются чёткими).
    val overlayOpen = attachOpen || actionMessage != null || videoRecorderOpen
    val attachBlur by animateDpAsState(
        if (overlayOpen && !lowRam && !powerSaving) 16.dp else 0.dp,
        if (lowRam) tween(120) else tween(220),
        label = "attachBlur",
    )
    Box(Modifier.fillMaxSize().then(if (attachBlur > 0.dp) Modifier.blur(attachBlur) else Modifier)) {
        // Сообщения: прокручиваются под панелями, их размытая копия видна в панелях.
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .background(LocalChatBackground.current),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (bubbleBlurOn) Modifier.backdropSource(bubbleBackdrop) else Modifier)
                    .background(LocalChatBackground.current),
            ) {
                if (chatPrefs.wallpaper != 0L) ChatWallpaper(chatPrefs.wallpaper, Modifier.fillMaxSize())
            }
            if (state.loading && state.messages.isEmpty()) {
                LoadingIndicator(Modifier.align(Alignment.Center))
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
                            ownBubbleColor = chatPrefs.ownBubbleColor.takeIf { it != 0L }?.let { Color(it) },
                            otherBubbleColor = chatPrefs.otherBubbleColor.takeIf { it != 0L }?.let { Color(it) },
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
                                LoadingIndicator(Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        // Подсказки команд бота: по кнопке-меню или при вводе «/». Стеклянная панель с размытием фона чата;
        // появляется из-под поля ввода и уезжает обратно, строки списка плавно перестраиваются при фильтрации.
        run {
            // Последний непустой список: пока идёт анимация закрытия, содержимое не должно пропадать.
            var lastShown by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
            if (shownCommands.isNotEmpty()) lastShown = shownCommands
            val plates = LocalPlates.current
            // Радиус размытия — общий, из настроек внешнего вида (как у остальных панелей); прозрачность своя.
            val commandsGlass = remember(glass.blurRadius, glass.blurType, plates.commands) {
                glass.copy(opacity = (1f - plates.commands).coerceIn(0f, 1f))
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = shownCommands.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomBarHeight + 4.dp)
                    .fillMaxWidth(),
                enter = fadeIn(fadeSpec) +
                        scaleIn(
                            fastScaleSpec,
                            initialScale = 0.88f,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.1f, 1f),
                        ) +
                        slideInVertically(slideSpec) { it / 4 },
                exit = fadeOut(fadeSpec) +
                        scaleOut(
                            fastScaleSpec,
                            targetScale = 0.92f,
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.1f, 1f),
                        ) +
                        slideOutVertically(slideSpec) { it / 5 },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .glass(
                            backdrop,
                            commandsGlass,
                            MaterialTheme.shapes.large,
                            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                ) {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        itemsIndexed(lastShown, key = { _, cmd -> cmd.first }) { _, cmd ->
                            Column(
                                Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clickable {
                                        if (typedCommand) {
                                            input = "/" + cmd.first + " "
                                        } else {
                                            viewModel.send("/" + cmd.first)
                                        }
                                        commandsOpen = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text("/" + cmd.first, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                if (cmd.second.isNotBlank()) {
                                    Text(
                                        cmd.second,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Кнопка «вниз» со счётчиком новых сообщений.
        androidx.compose.animation.AnimatedVisibility(
            visible = showScrollDown,
            enter = fadeIn(fadeSpec) + scaleIn(fastScaleSpec, initialScale = 0.6f),
            exit = fadeOut(fadeSpec) + scaleOut(fastScaleSpec, targetScale = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomBarHeight + 16.dp),
        ) {
            androidx.compose.material3.BadgedBox(
                badge = {
                    if (unreadNew > 0) {
                        androidx.compose.material3.Badge { Text(if (unreadNew > 99) "99+" else unreadNew.toString()) }
                    }
                },
            ) {
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (listState.firstVisibleItemIndex > 30) listState.scrollToItem(0) else listState.animateScrollToItem(0)
                            unreadNew = 0
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Вниз")
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
                            val headerSubtitle = state.typing ?: subtitleOverride ?: state.subtitle
                            if (headerSubtitle.isNotEmpty()) {
                                Text(
                                    headerSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        state.typing != null -> MaterialTheme.colorScheme.primary
                                        state.isOnline -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
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
                    if (state.canJoin) {
                        TextButton(
                            onClick = { viewModel.join() },
                            enabled = !state.joining,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.joining) {
                                LoadingIndicator(Modifier.size(18.dp))
                            } else {
                                Text(
                                    if (state.isChannel) "Подписаться" else "Вступить в группу",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    } else {
                        Text(
                            text = restrictionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                val bannerMessage = editing ?: replyTo
                if (bannerMessage != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .glass(backdrop, glass, RectangleShape)
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Row(
                                Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (editing != null) "Редактирование" else "Ответ",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        bannerMessage.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                    )
                                }
                                IconButton(onClick = {
                                    if (editing != null) stopEditing()
                                    replyTo = null
                                }) { Icon(Icons.Filled.Close, contentDescription = "Отмена") }
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeightPx = it.height }
                        // В комментариях общей панели нет: размытие у самого поля ввода.
                        .then(if (threadMode) Modifier else Modifier.glass(backdrop, glass, RectangleShape))
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Поле ввода-«таблетка».
                    val pillShape = MaterialTheme.shapes.largeIncreased
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .then(
                                if (threadMode) {
                                    Modifier.glass(
                                        backdrop,
                                        glass,
                                        pillShape,
                                        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        shape = pillShape,
                        // Полупрозрачное поле: сквозь него просвечивают обои и размытый фон панели.
                        // В комментариях фон поля рисует само стекло (размытие + подкраска).
                        color = if (threadMode) {
                            androidx.compose.ui.graphics.Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = InputFieldAlpha)
                        },
                        border = null,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (recording) {
                                Box(Modifier.padding(start = 12.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "%d:%02d".format(recordSeconds / 60, recordSeconds % 60),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (recordLocked) {
                                    Text(
                                        "Отмена",
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                recorder.cancel()
                                                recording = false
                                                recordLocked = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                } else {
                                    Text(
                                        "← отмена   ↑ закрепить",
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            } else {
                                if (state.botCommands.isNotEmpty()) {
                                    InputAction(Icons.Filled.Menu, "Команды бота", MaterialTheme.colorScheme.primary) {
                                        commandsOpen = !commandsOpen
                                    }
                                }
                                Box {
                                    InputAction(Icons.Filled.AddCircleOutline, "Прикрепить", MaterialTheme.colorScheme.onSurfaceVariant) {
                                        attachOpen = true
                                    }
                                    AttachMenu(
                                        expanded = attachOpen,
                                        onDismiss = { attachOpen = false },
                                        onFile = { filePicker.launch(arrayOf("*/*")) },
                                        onMedia = {
                                            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                        },
                                        onPoll = { pollOpen = true },
                                        onLocation = {
                                            val granted = listOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                            ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                                            if (granted) fetchLocation() else locationPermission.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                                ),
                                            )
                                        },
                                        onVoice = {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                                PackageManager.PERMISSION_GRANTED
                                            ) beginRecording() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                        onRound = { openVideoRecorder() },
                                    )
                                }
                                Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
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
                                InputAction(Icons.Filled.EmojiEmotions, "Эмодзи", MaterialTheme.colorScheme.onSurfaceVariant) {
                                    attachmentTab = 0
                                    emojiSheet = true
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Круглая кнопка: отправка (есть текст) / микрофон / камера для кружка.
                    // Тап — переключение «голосовое ↔ кружок» (если нет текста), зажатие — запись:
                    // отпустить — отправить, свайп вверх — закрепить (можно отпустить), свайп влево — отмена.
                    val canSend = input.isNotBlank()
                    val sendActive = canSend || recording
                    // Морфинг: «квадрат со скруглением» (микрофон/камера) становится кругом (отправка).
                    val sendCorner by animateDpAsState(if (sendActive) 28.dp else 16.dp, cornerSpec, label = "sendCorner")
                    val sendContainer by animateColorAsState(
                        if (sendActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        colorSpec,
                        label = "sendContainer",
                    )
                    val sendContent by animateColorAsState(
                        if (sendActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        colorSpec,
                        label = "sendContent",
                    )
                    // Жест не перезапускается при смене состояния (ключ Unit), актуальные значения читаем через State.
                    val canSendNow by rememberUpdatedState(canSend)
                    val recordingNow by rememberUpdatedState(recording)
                    val videoModeNow by rememberUpdatedState(videoMode)
                    val haptic = LocalHapticFeedback.current
                    val onSubmit by rememberUpdatedState({ submit() })
                    val finishVoice by rememberUpdatedState({
                        recording = false
                        recordLocked = false
                        recorder.stop()?.let { (path, seconds) -> viewModel.sendVoice(path, seconds) }
                        Unit
                    })
                    val startHold by rememberUpdatedState<(Boolean) -> Boolean>({ video ->
                        if (video) {
                            openVideoRecorder(hold = true)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            recordLocked = false
                            beginRecording()
                            recording
                        } else {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            false
                        }
                    })
                    val lockHold by rememberUpdatedState<(Boolean) -> Unit>({ video ->
                        if (video) videoHold = HOLD_LOCKED else recordLocked = true
                    })
                    val cancelHold by rememberUpdatedState<(Boolean) -> Unit>({ video ->
                        if (video) {
                            videoHold = HOLD_CANCELLED
                        } else {
                            recorder.cancel()
                            recording = false
                            recordLocked = false
                        }
                    })
                    val releaseHold by rememberUpdatedState<(Boolean) -> Unit>({ video ->
                        if (video) videoHold = HOLD_RELEASED else finishVoice()
                    })
                    // Смена режима голосовое ↔ кружок: кнопка коротко «дрожит» в стороны и даёт вибрацию (как плитки быстрых настроек).
                    val wiggle = remember { Animatable(0f) }
                    var modeSeen by remember { mutableStateOf(false) }
                    LaunchedEffect(videoMode) {
                        if (!modeSeen) {
                            modeSeen = true
                            return@LaunchedEffect
                        }
                        haptic.performHapticFeedback(
                            if (videoMode) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                        )
                        if (!lowRam) {
                            wiggle.snapTo(0f)
                            wiggle.animateTo(
                                targetValue = 0f,
                                animationSpec = keyframes {
                                    durationMillis = 320
                                    0f at 0
                                    -1f at 50
                                    1f at 110
                                    -0.7f at 170
                                    0.5f at 225
                                    -0.25f at 275
                                    0f at 320
                                },
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer { translationX = wiggle.value * 3.dp.toPx() }
                            .pointerInput(Unit) {
                                val lockDistance = 64.dp.toPx()
                                val cancelDistance = 96.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (canSendNow) {
                                        if (waitForUpOrCancellation() != null) onSubmit()
                                        return@awaitEachGesture
                                    }
                                    if (recordingNow) { // закреплённая запись: тап — отправить
                                        if (waitForUpOrCancellation() != null) finishVoice()
                                        return@awaitEachGesture
                                    }
                                    var tapped = false
                                    val completed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        tapped = waitForUpOrCancellation() != null
                                    }
                                    if (completed != null) { // короткий тап: микрофон ↔ кружок
                                        if (tapped) videoMode = !videoMode
                                        return@awaitEachGesture
                                    }
                                    val video = videoModeNow
                                    if (!startHold(video)) return@awaitEachGesture
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    var locked = false
                                    var cancelled = false
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        val dx = change.position.x - down.position.x
                                        val dy = change.position.y - down.position.y
                                        if (dy < -lockDistance) {
                                            locked = true
                                            lockHold(video)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            change.consume()
                                            break
                                        } else if (dx < -cancelDistance) {
                                            cancelled = true
                                            cancelHold(video)
                                            change.consume()
                                            break
                                        }
                                        change.consume()
                                    }
                                    if (!locked && !cancelled) releaseHold(video)
                                }
                            },
                        shape = RoundedCornerShape(sendCorner.coerceAtLeast(0.dp)),
                        color = sendContainer,
                        contentColor = sendContent,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AnimatedContent(
                                targetState = when {
                                    sendActive -> 0
                                    videoMode -> 1
                                    else -> 2
                                },
                                transitionSpec = {
                                    (fadeIn(fadeSpec) + scaleIn(fastScaleSpec, initialScale = 0.6f)) togetherWith
                                            (fadeOut(fadeSpec) + scaleOut(fastScaleSpec, targetScale = 0.6f))
                                },
                                label = "sendIcon",
                            ) { kind ->
                                Icon(
                                    when (kind) {
                                        0 -> Icons.AutoMirrored.Filled.Send
                                        1 -> Icons.Filled.Videocam
                                        else -> Icons.Filled.Mic
                                    },
                                    contentDescription = when (kind) {
                                        0 -> "Отправить"
                                        1 -> "Видеосообщение"
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
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    PickerTabButton(
                        icon = "😀",
                        title = "Эмодзи",
                        selected = attachmentTab == 0,
                        onClick = { attachmentTab = 0 },
                        modifier = Modifier.weight(1f),
                        first = true,
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
                        last = true,
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

    if (pollOpen) {
        PollDialog(
            onDismiss = { pollOpen = false },
            onSend = { question, options, anonymous, multiple -> viewModel.sendPoll(question, options, anonymous, multiple) },
        )
    }

    composing?.let { p ->
        MediaComposerDialog(
            pending = p,
            onDismiss = { composing = null },
            onSend = { path, caption ->
                composing = null
                when (p.kind) {
                    PendingKind.PHOTO -> viewModel.sendPhoto(path, caption)
                    PendingKind.GIF -> viewModel.sendAnimation(path, caption = caption)
                    PendingKind.VIDEO -> viewModel.sendDocument(path, caption)
                }
            },
        )
    }
    pendingLocation?.let { loc ->
        AlertDialog(
            onDismissRequest = { pendingLocation = null },
            title = { Text("Отправить геопозицию?") },
            text = { Text("%.5f, %.5f (точность ~%d м)".format(loc.latitude, loc.longitude, loc.accuracy.toInt())) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.sendLocation(loc.latitude, loc.longitude, loc.accuracy.toDouble())
                    pendingLocation = null
                }) { Text("Отправить") }
            },
            dismissButton = { TextButton(onClick = { pendingLocation = null }) { Text("Отмена") } },
        )
    }

    if (videoRecorderOpen) {
        VideoNoteRecorderDialog(
            onSend = { path, seconds, length -> viewModel.sendVideoNote(path, seconds, length) },
            onDismiss = {
                videoRecorderOpen = false
                videoHold = HOLD_NONE
                videoAuto = false
            },
            autoStart = videoAuto,
            hold = videoHold,
        )
    }

    viewerMedia?.let { media ->
        // Все фото загруженной части чата от старых к новым; открытое фото — стартовая страница.
        val gallery = visibleMessages.asReversed()
            .mapNotNull { it.media }
            .filter { it.kind == MediaKind.PHOTO && !it.locked }
        val start = gallery.indexOfFirst { it.fileId == media.fileId }
        PhotoViewer(
            items = if (start >= 0) gallery else listOf(media),
            startIndex = start.coerceAtLeast(0),
            viewModel = viewModel,
            onDismiss = { viewerMedia = null },
        )
    }

    actionMessage?.let { message ->
        val sender = message.senderUserId
        val tw by viewModel.typingWatch.collectAsState()
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
            canWatchTyping = state.isGroup && sender != null && !message.isOutgoing,
            watchingTyping = sender != null && tw.watches(viewModel.chatId, sender),
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
            onExport = {
                val media = message.media ?: return@MessageMenu
                scope.launch {
                    val path = viewModel.fileState(media.fileId).first().path
                    if (path == null) {
                        viewModel.download(media.fileId)
                        snackbar.showSnackbar("Файл ещё загружается, попробуйте позже")
                    } else {
                        exportPath = path
                        exportLauncher.launch(media.name.ifBlank { File(path).name })
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
            onToggleTypingWatch = { sender?.let { viewModel.toggleTypingWatch(it) } },
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
    ownBubbleColor: Color?,
    otherBubbleColor: Color?,
    bubbleOpacity: Float,
    bubbleBackdrop: BackdropState?,
    glassSettings: GlassSettings,
    highlighted: Boolean,
    searchQuery: String,
) {
    val mine = message.isOutgoing
    // Текст своих сообщений: белый в тёмной теме, в светлой как раньше.
    val customBg = if (mine) ownBubbleColor else otherBubbleColor
    val readableOnCustom = customBg?.let { if (it.luminance() > 0.5f) Color.Black else Color.White }
    val ownText = readableOnCustom
        ?: MaterialTheme.colorScheme.onPrimaryContainer
    val media = message.media
    val senderKey = message.senderKey
    // В группах у чужих сообщений показываем автора: аватарка и имя с тегом у первого сообщения серии.
    val showSenderUi = groupChat && !mine && senderKey != null
    val showName = showSenderUi && !joinedWithOlder
    val showAvatar = showSenderUi && !joinedWithOlder
    // Кружок и стикер показываются без пузыря.
    val bare = (media?.kind == MediaKind.VIDEO_NOTE || media?.kind == MediaKind.STICKER) && message.text.isEmpty()
    // Кнопка комментариев прилегает к самому посту — полоса внизу пузыря.
    val withComments = commentsEnabled && message.hasComments && !bare

    LaunchedEffect(senderKey, showSenderUi) {
        if (showSenderUi && senderKey != null) viewModel.ensureSender(senderKey)
    }

    val replyRef = message.reply
    LaunchedEffect(replyRef) { if (replyRef != null) viewModel.ensureReply(replyRef) }

    // Expressive-группировка: у склеенных сообщений углы со стороны автора мелкие, у последнего в серии — «хвостик».
    // Когда приходит новое сообщение, углы предыдущего плавно «перетекают».
    val motion = MaterialTheme.motionScheme
    val lowTier = app.yougram.core.ui.rememberDeviceTier() == app.yougram.core.ui.DeviceTier.Low
    val cornerSpec: FiniteAnimationSpec<Dp> = if (lowTier) snap() else motion.fastSpatialSpec()
    val colorSpec: FiniteAnimationSpec<Color> = if (lowTier) tween(250) else motion.defaultEffectsSpec()
    val inner = minOf(BubbleInner, cornerRadius)
    val tail = minOf(BubbleTail, cornerRadius)
    val authorTop by animateDpAsState(if (joinedWithOlder) inner else cornerRadius, cornerSpec, label = "bubbleTop")
    val authorBottom by animateDpAsState(if (joinedWithNewer) inner else tail, cornerSpec, label = "bubbleBottom")
    val top = authorTop.coerceAtLeast(0.dp)
    val bottom = authorBottom.coerceAtLeast(0.dp)
    val shape = if (mine) {
        RoundedCornerShape(topStart = cornerRadius, topEnd = top, bottomEnd = bottom, bottomStart = cornerRadius)
    } else {
        RoundedCornerShape(topStart = top, topEnd = cornerRadius, bottomEnd = cornerRadius, bottomStart = bottom)
    }

    // Свои сообщения — primaryContainer, чужие — surfaceContainerHigh; свой цвет из настроек важнее.
    val baseColor = customBg ?: if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val bubbleColor by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else baseColor,
        animationSpec = colorSpec,
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
            Box(
                Modifier
                    .size(36.dp)
                    .then(
                        if (showAvatar) {
                            Modifier.semantics { contentDescription = "Профиль: ${sender?.name.orEmpty().ifEmpty { "отправитель" }}" }
                        } else {
                            Modifier
                        },
                    )
                    .clickable(enabled = showAvatar, role = Role.Button, onClick = onOpenSender),
            ) {
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
                        mine || readableOnCustom != null -> ownText
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Column(
                        Modifier
                            .widthIn(max = if (showSenderUi) 290.dp else 320.dp)
                            .then(if (withComments) Modifier.width(IntrinsicSize.Max) else Modifier)
                            // Подпись переносится по ширине медиа, а не растягивает пузырь шире картинки.
                            .then(
                                if (media != null && message.text.isNotEmpty() &&
                                    (media.kind == MediaKind.PHOTO || media.kind == MediaKind.VIDEO || media.kind == MediaKind.ANIMATION)
                                ) Modifier.width(268.dp) else Modifier,
                            ),
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
                                when {
                                    mine -> ownText
                                    customBg != null -> app.yougram.core.ui.theme.ensureContrast(MaterialTheme.colorScheme.primary, customBg, 4.5f)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                LocalOpenLink.current,
                                LocalOpenUsername.current,
                                message.emojis,
                            )
                            val emojiSize = (textSize * 1.25f).sp
                            val inline = rememberEmojiInlineContent(message.emojis, viewModel, emojiSize, viewModel.animateEmoji)
                            val shown = remember(linked, searchQuery, matchColor) {
                                linked.highlightMatches(searchQuery, matchColor)
                            }
                            Text(
                                shown,
                                inlineContent = inline,
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
                        if (message.buttons.isNotEmpty()) {
                            InlineKeyboard(message, viewModel, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp))
                        }
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
    // Цвет считаем от цвета содержимого пузыря, чтобы время и галочки читались на любом фоне.
    val content = LocalContentColor.current
    val muted = content.copy(alpha = 0.7f)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (edits.isNotEmpty()) {
            Text(
                "изменено · ",
                modifier = Modifier.clickable(onClick = onShowEdits),
                style = MaterialTheme.typography.labelSmall,
                color = if (mine) content else MaterialTheme.colorScheme.primary,
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
                    tint = if (mine) content else MaterialTheme.colorScheme.primary,
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
            .clip(MaterialTheme.shapes.small)
            .background(accent.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(accent),
        )
        Column(Modifier.padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
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
        val lowTier = app.yougram.core.ui.rememberDeviceTier() == app.yougram.core.ui.DeviceTier.Low
        val motion = MaterialTheme.motionScheme
        val cornerSpec: FiniteAnimationSpec<Dp> = if (lowTier) snap() else motion.fastSpatialSpec()
        val colorSpec: FiniteAnimationSpec<Color> = if (lowTier) snap() else motion.defaultEffectsSpec()
        reactions.forEach { r ->
            key(r.emoji) {
                // Выбранная реакция — акцентная «таблетка», остальные — квадрат со скруглением.
                val container by animateColorAsState(
                    if (r.chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    colorSpec,
                    label = "reactionContainer",
                )
                val corner by animateDpAsState(if (r.chosen) 16.dp else 10.dp, cornerSpec, label = "reactionCorner")
                val content = if (r.chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Row(
                    Modifier
                        .heightIn(min = 32.dp)
                        .clip(RoundedCornerShape(corner.coerceAtLeast(0.dp)))
                        .background(container)
                        .clickable { onReact(r.emoji) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.emoji, fontSize = 14.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        r.count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = content,
                    )
                }
            }
        }
    }
}

/** Кнопка вкладки в группе: внутренние углы мелкие, у выбранной — круглые (форма «перетекает»). */
@Composable
private fun PickerTabButton(
    icon: String,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    last: Boolean = false,
) {
    val lowTier = app.yougram.core.ui.rememberDeviceTier() == app.yougram.core.ui.DeviceTier.Low
    val cornerSpec: FiniteAnimationSpec<Dp> = if (lowTier) snap() else MaterialTheme.motionScheme.fastSpatialSpec()
    val inner by animateDpAsState(if (selected) 24.dp else 8.dp, cornerSpec, label = "tabInner")
    val outer = 24.dp
    val i = inner.coerceAtLeast(0.dp)
    val shape = RoundedCornerShape(
        topStart = if (first) outer else i,
        bottomStart = if (first) outer else i,
        topEnd = if (last) outer else i,
        bottomEnd = if (last) outer else i,
    )
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        shape = shape,
        color = pickerChipColor(selected),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon)
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Кнопка-иконка внутри поля ввода: круглая область нажатия 40 dp, описание для TalkBack. */
@Composable
private fun InputAction(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
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
            ?: LoadingIndicator(Modifier.size(24.dp))
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