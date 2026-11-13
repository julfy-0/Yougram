@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package app.yougram.feature.chat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.Morph
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.blur
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.animateFloatAsState
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import app.yougram.feature.chat.location.LocationDialog
import app.yougram.feature.chat.location.LocationMessage
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yougram.core.di.AppContainer
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.ui.Haptics
import app.yougram.core.ui.component.ChatWallpaper
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.core.ui.component.openExternally
import app.yougram.core.ui.component.rememberLinkified
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.glassTopBar
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.core.ui.rememberHaptics
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.foundation.lazy.LazyRow
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import app.yougram.core.i18n.Localization
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubble
import app.yougram.feature.chat.data.MediaItem
import app.yougram.feature.chat.data.MediaKind
import app.yougram.feature.chat.data.MessageItem
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val QuickReactions = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "👎")

/** Что сейчас делает поле ввода помимо обычной отправки. */
private enum class ComposerButtonKind { Voice, Round, Send, Confirm }

/** Общая высота элементов строки ввода: скрепка, поле и кнопка справа ровно в одну линию. */
private val ComposerHeight = 56.dp

private sealed interface ComposerMode {
    data object Idle : ComposerMode
    data class Reply(val message: MessageItem) : ComposerMode
    data class Edit(val message: MessageItem) : ComposerMode
}

/**
 * Экран чата в Material 3 Expressive.
 *
 * Верхняя панель — TopAppBar с аватаркой и статусом, пузыри — тональные контейнеры M3
 * (исходящие — primaryContainer, входящие — surfaceContainerHigh) с большим внешним
 * и малым «хвостовым» скруглением, поле ввода — pill TextField + FAB отправки/микрофона.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenChatProfile: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    container: AppContainer? = null,
    settings: SettingsRepository? = container?.settings,
    onCall: (Long, Boolean) -> Unit = { _, _ -> },
    onOpenComments: (Long, Long) -> Unit = { _, _ -> },
    showBack: Boolean = true,
    /** Свой заголовок/подзаголовок (например, «Комментарии» и число ответов). */
    titleOverride: String? = null,
    subtitleOverride: String? = null,
    /** Ветка комментариев: без звонков и без профиля чата в шапке. */
    threadMode: Boolean = false,
) {
    requireNotNull(settings) { "ChatScreen: нужен container или settings" }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val state by viewModel.state.collectAsState()
    // Секретный чат: пока он открыт, окна приложения защищены от скриншотов и записи экрана.
    val secretChat = state.isSecret
    DisposableEffect(secretChat) {
        if (secretChat) app.yougram.core.ui.SecureScreen.enter()
        onDispose { if (secretChat) app.yougram.core.ui.SecureScreen.leave() }
    }
    val prefs by settings.chatPrefs.collectAsState()
    val senders by viewModel.senders.collectAsState()
    val replies by viewModel.replies.collectAsState()
    val pendingJump by viewModel.pendingJump.collectAsState()
    val typingWatch by viewModel.typingWatch.collectAsState()

    val listState = rememberLazyListState()
    val entryCache = remember { HashMap<Long, ChatEntry>() }
    val entries = remember(state.messages) {
        state.messages.groupAlbums(entryCache).also { list ->
            entryCache.clear()
            list.forEach { entryCache[it.key] = it }
        }
    }

    var mode by remember { mutableStateOf<ComposerMode>(ComposerMode.Idle) }
    var draft by remember { mutableStateOf("") }
    // Подсказки хэштегов: начало «#тег» в конце черновика (после пробела или в начале строки).
    var hashtagTick by remember { mutableStateOf(0) }
    val hashPrefix = remember(draft) { trailingHashtagPrefix(draft) }
    val hashtagSuggestions by produceState(emptyList<String>(), hashPrefix, hashtagTick) {
        value = if (hashPrefix == null) emptyList() else {
            delay(120)
            runCatching { container?.chatRepository?.searchHashtags(hashPrefix) ?: emptyList() }.getOrDefault(emptyList())
                // Полностью набранный тег единственной подсказкой не показываем.
                .filterNot { it.equals(hashPrefix, ignoreCase = true) && hashPrefix.isNotEmpty() }
        }
    }
    val hashtagScope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<ChatEntry?>(null) }
    var menuAnchor by remember { mutableStateOf<MessageAnchor?>(null) }
    LaunchedEffect(menuFor) { if (menuFor == null) menuAnchor = null }
    var attachOpen by remember { mutableStateOf(false) }
    // Панель эмодзи / стикеров / GIF.
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerTab by remember { mutableStateOf(PickerTab.Emoji) }
    var stickerQuery by remember { mutableStateOf("") }
    BackHandler(enabled = pickerOpen || attachOpen) { pickerOpen = false; attachOpen = false }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var pollOpen by remember { mutableStateOf(false) }
    var locationOpen by remember { mutableStateOf(false) }
    val locationContext = LocalContext.current
    LaunchedEffect(Unit) { viewModel.ensureLiveLocation(locationContext) }
    var overflowOpen by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }
    var pendingMedia by remember { mutableStateOf<PendingMedia?>(null) }
    var stickerSet by remember { mutableLongStateOf(0L) }

    // ── Голосовые: зажатие микрофона, свайп вверх — закреп, влево — отмена ──
    val voiceRecorder = remember { VoiceRecorder(context) }
    var voiceHold by remember { mutableStateOf(HOLD_NONE) } // HOLD_ACTIVE при удержании, HOLD_LOCKED после свайпа вверх
    var voiceSeconds by remember { mutableIntStateOf(0) }
    // Голосовое, запущенное кнопкой в панели вложений: не привязано к пальцу, Pill сразу с кнопками.
    var voiceTapMode by remember { mutableStateOf(false) }
    val voiceDragging = voiceHold == HOLD_ACTIVE || voiceHold == HOLD_LOCKED || voiceHold == HOLD_CANCELLED

    // ── Кружки: полноэкранная запись (разрешения камеры и микрофона запрошены до показа) ──
    var roundOpen by remember { mutableStateOf(false) }
    var roundHold by remember { mutableStateOf(HOLD_NONE) } // зажатие кнопки кружка
    var roundAuto by remember { mutableStateOf(false) } // окно открыто зажатием: запись стартует сама
    val cameraPrefs by settings.camera.collectAsState()

    /** Завершение записи: `send` — отправить результат, иначе отменить и удалить файл. */
    val finishVoice = { send: Boolean ->
        if (voiceHold != HOLD_NONE) {
            val hold = voiceHold
            voiceHold = HOLD_NONE
            voiceTapMode = false
            if (hold != HOLD_CANCELLED && send) {
                voiceRecorder.stop()?.let { (path, seconds) ->
                    scope.launch { viewModel.sendVoice(path, seconds) }
                    haptics(Haptics.Kind.Confirm)
                }
            } else voiceRecorder.cancel()
        }
    }

    var startVoiceRef: () -> Unit = {}
    val voicePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // После выдачи разрешения пальца уже нет: пойдёт запись с Pill-кнопками (как из панели вложений).
            voiceTapMode = true
            startVoiceRef.invoke()
        } else {
            Toast.makeText(context, tr("Нужен доступ к микрофону"), Toast.LENGTH_SHORT).show()
        }
    }

    // Нажатие на микрофон: старт записи. Если запись уже идёт — ничего не делаем (Pill ею управляет).
    val startVoiceTap = {
        if (voiceHold == HOLD_NONE) {
            val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!mic) {
                voicePermissions.launch(Manifest.permission.RECORD_AUDIO)
            } else if (voiceRecorder.start()) {
                focusManager.clearFocus()
                keyboard?.hide()
                voiceSeconds = 0
                voiceHold = HOLD_ACTIVE
                haptics(Haptics.Kind.Tick)
            }
        }
    }
    startVoiceRef = startVoiceTap

    val roundPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] != false && result[Manifest.permission.CAMERA] != false) {
            roundOpen = true
        } else {
            Toast.makeText(context, tr("Нужны доступ к камере и микрофону"), Toast.LENGTH_SHORT).show()
        }
    }
    // Открыть запись кружка, сначала запросив CAMERA и RECORD_AUDIO.
    val openRound = {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val granted = needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        roundAuto = false
        roundHold = HOLD_NONE
        if (granted) roundOpen = true else roundPermissions.launch(needed)
    }
    // Зажатие кнопки кружка: сразу открываем окно и пишем. Если разрешений нет — просим, дальше обычное окно.
    val onRoundHoldStart = {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val granted = needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            focusManager.clearFocus()
            keyboard?.hide()
            roundAuto = true
            roundHold = HOLD_ACTIVE
            roundOpen = true
            haptics(Haptics.Kind.Tick)
        } else {
            roundAuto = false
            roundHold = HOLD_NONE
            roundPermissions.launch(needed)
        }
    }
    val onRoundHoldDrag = { dx: Float, dy: Float ->
        if (roundHold == HOLD_ACTIVE) {
            when {
                dy < -(48 * context.resources.displayMetrics.density) -> { roundHold = HOLD_LOCKED; haptics(Haptics.Kind.Confirm) }
                dx < -(96 * context.resources.displayMetrics.density) -> { roundHold = HOLD_CANCELLED; haptics(Haptics.Kind.Reject) }
            }
        }
    }
    val onRoundHoldRelease = {
        if (roundHold == HOLD_ACTIVE) roundHold = HOLD_RELEASED
    }

    LaunchedEffect(voiceDragging) { // таймер и автоотправка на пределе длины (60 с)
        if (voiceDragging) {
            while (voiceHold != HOLD_NONE && voiceHold != HOLD_CANCELLED) {
                kotlinx.coroutines.delay(1000)
                voiceSeconds++
                if (voiceSeconds >= 60) finishVoice(true)
            }
        }
    }

    val isDirect = !threadMode && !state.isGroup && !state.isChannel && !state.isSecret && viewModel.chatId > 0L
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }

    // Прыжок к сообщению (из поиска, ответа, закрепа).
    LaunchedEffect(pendingJump, entries) {
        val target = pendingJump ?: return@LaunchedEffect
        val index = entries.indexOfFirst { target in it.ids }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            viewModel.consumeJump()
        }
    }

    // Подгрузка старой истории, когда долистали до конца.
    LaunchedEffect(listState, entries.size) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= entries.size - 6
        }.collect { nearTop -> if (nearTop && entries.isNotEmpty()) viewModel.loadOlder() }
    }

    // Новое входящее: докручиваем вниз, только если пользователь уже внизу.
    LaunchedEffect(entries.firstOrNull()?.key) {
        if (atBottom || entries.firstOrNull()?.message?.isOutgoing == true) listState.animateScrollToItem(0)
    }

    // Файлы: можно выбрать сразу несколько — уходят вместе (альбомами документов по 10).
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val paths = uris.mapNotNull { copyToCache(context, it)?.path }
            if (paths.isNotEmpty()) viewModel.sendDocuments(paths)
        }
    }
    // Фото/скриншоты: одно — через редактор с подписью, несколько — сразу альбомом.
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            if (uris.size == 1) {
                val uri = uris.first()
                val copy = copyToCache(context, uri) ?: return@launch
                val mime = context.contentResolver.getType(uri).orEmpty()
                when {
                    mime == "image/gif" -> pendingMedia = PendingMedia(copy.path, PendingKind.GIF)
                    mime.startsWith("video/") -> pendingMedia = PendingMedia(copy.path, PendingKind.VIDEO)
                    else -> pendingMedia = PendingMedia(copy.path, PendingKind.PHOTO)
                }
            } else {
                val gifs = mutableListOf<String>()
                val photos = mutableListOf<String>()
                uris.forEach { uri ->
                    val path = copyToCache(context, uri)?.path ?: return@forEach
                    if (context.contentResolver.getType(uri) == "image/gif") gifs += path else photos += path
                }
                if (photos.isNotEmpty()) viewModel.sendPhotos(photos)
                if (gifs.isNotEmpty()) viewModel.sendAnimations(gifs)
            }
        }
    }

    val pickStickerFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val copy = copyToCache(context, uri) ?: return@launch
            viewModel.sendStickerFile(copy.path)
            pickerOpen = false
        }
    }

    val backdrop = rememberBackdropState()
    val glass = LocalGlass.current
    val density = LocalDensity.current

    // Пороги свайпов записи в пикселях: вверх — 48.dp (закреп), влево — 96.dp (отмена).
    val voiceLockPx = with(density) { 48.dp.toPx() }
    val voiceCancelPx = with(density) { 96.dp.toPx() }
    // Свайп во время записи: вверх — закрепить (палец можно убрать), влево — подготовить отмену.
    val onVoiceDrag = { dx: Float, dy: Float ->
        if (voiceHold == HOLD_ACTIVE) {
            when {
                dy < -voiceLockPx -> {
                    voiceHold = HOLD_LOCKED
                    haptics(Haptics.Kind.Confirm)
                }
                dx < -voiceCancelPx -> {
                    voiceHold = HOLD_CANCELLED
                    haptics(Haptics.Kind.Reject)
                }
            }
        }
    }
    // Палец отпущен: обычное удержание — отправить, закреплённое — продолжаем, отмена — скрыть и удалить.
    val onVoiceRelease = {
        when (voiceHold) {
            HOLD_ACTIVE -> finishVoice(true)
            HOLD_CANCELLED -> finishVoice(false)
            else -> Unit // после закрепа запись продолжается: завершает Pill или повторное нажатие микрофона
        }
    }
    var topBarPx by remember { mutableIntStateOf(0) }
    var bottomBarPx by remember { mutableIntStateOf(0) }
    // Высота выдвижных панелей (эмодзи / вложения): список едет вместе с ними через translationY, без перекомпоновки.
    var panelPx by remember { mutableIntStateOf(0) }

    // Размытие чата, пока открыто меню сообщения (внутри приложения — не зависит от системного blur окон).
    val menuBlur by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (menuFor != null) 24.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "menuBlur",
    )
    Box(Modifier.fillMaxSize().blur(menuBlur).background(MaterialTheme.colorScheme.surface).imePadding()) {
        // Источник размытия: фон, обои и список. Панели внутрь класть нельзя — они лежат сверху отдельными слоями.
        // Фон должен быть внутри источника (после backdropSource): иначе размытая копия прозрачная и под панелями виден чёткий список.
        Box(Modifier.fillMaxSize().backdropSource(backdrop).background(MaterialTheme.colorScheme.surface)) {
            if (prefs.wallpaper != 0L) ChatWallpaper(version = prefs.wallpaper, modifier = Modifier.fillMaxSize())

            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading && entries.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator(contained = true)
                        }

                    entries.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Text(
                                    "Здесь пока нет сообщений",
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                    else -> MessageList(
                        topPad = with(density) { topBarPx.toDp() },
                        bottomPad = with(density) { (bottomBarPx - panelPx).coerceAtLeast(0).toDp() },
                        bottomShift = { panelPx.toFloat() },
                        entries = entries,
                        listState = listState,
                        state = state,
                        textSize = prefs.textSize,
                        bubbleRadius = prefs.bubbleRadius,
                        viewModel = viewModel,
                        senders = senders,
                        replies = replies,
                        onOpenMedia = { items, index -> viewer = items to index },
                        // Автор-пользователь: сначала создаём личный чат, иначе кнопка «Чат» в профиле даёт «Chat not found».
                        onOpenProfile = { key -> viewModel.openSender(key, onOpenProfile) },
                        onOpenComments = onOpenComments,
                        onLongPress = { e, a -> menuAnchor = a; menuFor = e },
                        onReact = { id, emoji -> viewModel.react(id, emoji); haptics(Haptics.Kind.Click) },
                        onReplyTap = { viewModel.jumpTo(it) },
                        onSwipeReply = { mode = ComposerMode.Reply(it); haptics(Haptics.Kind.Tick) },
                    )
                }
            }

        }

        val chatBarTint = app.yougram.core.ui.glass.barTint()

        // Верхняя стеклянная панель (шапка + закреп): скруглена снизу.
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .glassTopBar(backdrop, glass, tint = chatBarTint)
                .onSizeChanged { topBarPx = it.height },
        ) {
            ChatTopBar(
                title = titleOverride ?: state.title,
                subtitle = subtitleOverride ?: state.typing ?: state.subtitle,
                typing = subtitleOverride == null && state.typing != null,
                online = state.isOnline,
                avatarFileId = state.avatarFileId,
                fileState = { viewModel.fileState(it) },
                showBack = showBack,
                onBack = onBack,
                onTitleClick = if (threadMode) ({}) else onOpenChatProfile,
                canCall = isDirect,
                onCall = { video -> onCall(viewModel.chatId, video) },
                overflowOpen = overflowOpen,
                onOverflow = { overflowOpen = it },
                onSearch = { /* поиск живёт в ChatViewModel.search(); панель поиска подключается отдельно */ },
            )

            if (state.pinned.isNotEmpty()) {
                PinnedBar(
                    text = state.pinned.first().text,
                    count = state.pinned.size,
                    onClick = { viewModel.jumpTo(state.pinned.first().id) },
                )
            }

        }

        // Нижняя стеклянная панель (поле ввода / вступить / только чтение): скруглена сверху.
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .glass(backdrop, glass, app.yougram.core.ui.glass.BottomBarShape, tint = chatBarTint)
                .onSizeChanged { bottomBarPx = it.height },
        ) {
            when {
                state.canJoin -> JoinBar(
                    channel = state.isChannel,
                    joining = state.joining,
                    onJoin = viewModel::join,
                )

                !state.canSendMessages -> ReadOnlyBar()

                else -> Column {
                    // Пилюля записи голосового: вместо поля ввода — индикатор, таймер и кнопки завершения.
                    AnimatedVisibility(visible = voiceDragging, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        VoiceRecordingBar(
                            seconds = voiceSeconds,
                            locked = voiceHold == HOLD_LOCKED || voiceHold == HOLD_CANCELLED,
                            tapMode = voiceTapMode,
                            cancelled = voiceHold == HOLD_CANCELLED,
                            onSend = { finishVoice(true) },
                            onCancel = { finishVoice(false) },
                        )
                    }

                    Composer(
                        mode = mode,
                        draft = draft,
                        onDraft = { draft = it },
                        hashtags = hashtagSuggestions,
                        onPickHashtag = { tag ->
                            val i = draft.lastIndexOf('#')
                            if (i >= 0) draft = draft.substring(0, i) + "#" + tag + " "
                        },
                        onForgetHashtag = { tag ->
                            hashtagScope.launch {
                                runCatching { container?.chatRepository?.removeRecentHashtag(tag) }
                                hashtagTick++
                            }
                        },
                        onCancelMode = {
                            if (mode is ComposerMode.Edit) draft = ""
                            mode = ComposerMode.Idle
                        },
                        paidStars = state.paidMessageStars,
                        pickerOpen = pickerOpen,
                        attachOpen = attachOpen,
                        voiceRecording = voiceDragging,
                        onVoicePress = startVoiceTap,
                        onVoiceDrag = onVoiceDrag,
                        onVoiceRelease = onVoiceRelease,
                        onRoundClick = openRound,
                        onRoundHoldStart = onRoundHoldStart,
                        onRoundHoldDrag = onRoundHoldDrag,
                        onRoundHoldRelease = onRoundHoldRelease,
                        onTogglePicker = {
                            if (pickerOpen) {
                                pickerOpen = false
                            } else {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                attachOpen = false
                                pickerOpen = true
                            }
                        },
                        onFieldFocus = { pickerOpen = false; attachOpen = false },
                        onAttach = {
                            if (attachOpen) {
                                attachOpen = false
                            } else {
                                focusManager.clearFocus()
                                keyboard?.hide()
                                pickerOpen = false
                                attachOpen = true
                            }
                        },
                        onSend = {
                            val text = draft.trim()
                            if (text.isEmpty()) return@Composer
                            when (val m = mode) {
                                is ComposerMode.Edit -> viewModel.edit(m.message.id, text)
                                is ComposerMode.Reply -> viewModel.send(text, replyToId = m.message.id)
                                ComposerMode.Idle -> viewModel.send(text)
                            }
                            haptics(Haptics.Kind.Confirm)
                            draft = ""
                            mode = ComposerMode.Idle
                        },
                    )

                    Column(Modifier.onSizeChanged { panelPx = it.height }) {
                        // Панель эмодзи / стикеров / GIF — часть той же стеклянной панели: общий фон, без отдельных углов и «ручки».
                        AnimatedVisibility(
                            visible = pickerOpen,
                            enter = expandVertically(
                                animationSpec = tween(420, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)),
                                expandFrom = Alignment.Top,
                            ) + slideInVertically(
                                animationSpec = tween(420, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)),
                            ) { it / 5 } + fadeIn(tween(280, delayMillis = 40)),
                            exit = shrinkVertically(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Top,
                            ) + fadeOut(tween(160)),
                        ) {
                            Column {
                                PickerTabs(selected = pickerTab, onSelect = { pickerTab = it })
                                // Фиксированная высота: панель не «прыгает» при смене вкладок; содержимое выезжает в сторону переключения.
                                AnimatedContent(
                                    targetState = pickerTab,
                                    modifier = Modifier.fillMaxWidth().height(368.dp).clipToBounds(),
                                    transitionSpec = {
                                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                        (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 500f)) { dir * it / 3 } + fadeIn(tween(200, delayMillis = 60)))
                                            .togetherWith(slideOutHorizontally(tween(180)) { -dir * it / 3 } + fadeOut(tween(120)))
                                    },
                                    label = "pickerTab",
                                ) { tab ->
                                    when (tab) {
                                        PickerTab.Emoji -> EmojiTab(onPick = { draft += it }, modifier = Modifier.fillMaxSize())
                                        PickerTab.Stickers -> StickerTab(
                                            viewModel = viewModel,
                                            query = stickerQuery,
                                            onQueryChange = { stickerQuery = it },
                                            onPickFile = { pickStickerFile.launch("image/*") },
                                            onSent = { pickerOpen = false },
                                        )
                                        PickerTab.Gif -> GifTab(
                                            viewModel = viewModel,
                                            onPickFile = { pickMedia.launch("image/gif") },
                                            onSent = { pickerOpen = false },
                                        )
                                    }
                                }
                                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                            }
                        }

                        // Панель вложений: та же анимация и тот же стиль кнопок, что у панели эмодзи.
                        AnimatedVisibility(
                            visible = attachOpen,
                            enter = expandVertically(
                                animationSpec = tween(420, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)),
                                expandFrom = Alignment.Top,
                            ) + slideInVertically(
                                animationSpec = tween(420, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)),
                            ) { it / 5 } + fadeIn(tween(280, delayMillis = 40)),
                            exit = shrinkVertically(
                                animationSpec = tween(260, easing = FastOutSlowInEasing),
                                shrinkTowards = Alignment.Top,
                            ) + fadeOut(tween(160)),
                        ) {
                            Column {
                                AttachActionsPanel(
                                    onFile = { attachOpen = false; pickFile.launch("*/*") },
                                    onMedia = { attachOpen = false; pickMedia.launch("image/*") },
                                    onPoll = { attachOpen = false; pollOpen = true },
                                    onLocation = { attachOpen = false; locationOpen = true },
                                    onVoice = {
                                        attachOpen = false
                                        voiceTapMode = true
                                        startVoiceTap()
                                    },
                                    onRound = { attachOpen = false; openRound() },
                                )
                                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                            }
                        }
                    }
                }
            }
        }

        ScrollDownFab(
            visible = !atBottom,
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = with(density) { bottomBarPx.toDp() } + 12.dp),
        )

        state.error?.let { err ->
            ErrorSnack(
                text = err,
                onDismiss = viewModel::dismissError,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { bottomBarPx.toDp() } + 12.dp),
            )
        }
    }

    // ───── диалоги и панели ─────
    if (roundOpen) {
        VideoNoteRecorderDialog(
            onSend = { path, seconds, length ->
                roundOpen = false
                roundHold = HOLD_NONE
                roundAuto = false
                scope.launch { viewModel.sendVideoNote(path, seconds, length) }
                haptics(Haptics.Kind.Confirm)
            },
            onDismiss = { roundOpen = false; roundHold = HOLD_NONE; roundAuto = false },
            autoStart = roundAuto,
            hold = roundHold,
            camera = cameraPrefs,
        )
    }

    if (locationOpen) {
        LocationDialog(
            onDismiss = { locationOpen = false },
            onSend = { lat, lon, accuracy, livePeriod ->
                locationOpen = false
                viewModel.sendLocation(lat, lon, accuracy, livePeriod)
                if (livePeriod > 0) viewModel.ensureLiveLocation(locationContext)
            },
        )
    }

    if (pollOpen) {
        PollDialog(
            onDismiss = { pollOpen = false },
            onSend = { q, options, anonymous, multiple ->
                pollOpen = false
                viewModel.sendPoll(q, options, anonymous, multiple)
            },
        )
    }

    pendingMedia?.let { pending ->
        MediaComposerDialog(
            pending = pending,
            onDismiss = { pendingMedia = null },
            onSend = { path, caption ->
                pendingMedia = null
                when (pending.kind) {
                    PendingKind.PHOTO -> viewModel.sendPhoto(path, caption)
                    PendingKind.GIF -> viewModel.sendAnimation(path, caption = caption)
                    PendingKind.VIDEO -> viewModel.sendDocument(path, caption)
                }
            },
        )
    }

    viewer?.let { (items, index) ->
        PhotoViewer(items = items, startIndex = index, viewModel = viewModel, onDismiss = { viewer = null })
    }

    if (stickerSet != 0L) StickerSetSheet(setId = stickerSet, viewModel = viewModel, onDismiss = { stickerSet = 0L })

    menuFor?.let { entry ->
        val m = entry.message
        val pinned = state.pinned.any { it.id == m.id }
        MessageMenu(
            message = m,
            isRead = (state.readAt[m.id] ?: 0) > 0,
            canEdit = m.isOutgoing && m.media == null && m.poll == null && m.location == null && !m.isService,
            canSave = m.media != null,
            canShadowBan = false,
            canWatchTyping = false,
            watchingTyping = false,
            reactions = QuickReactions,
            onDismiss = { menuFor = null },
            onReact = { viewModel.react(m.id, it); menuFor = null },
            onReply = { mode = ComposerMode.Reply(m); menuFor = null },
            onSave = { menuFor = null },
            onForward = { menuFor = null },
            isPinned = pinned,
            onPin = { if (pinned) viewModel.unpin(m.id) else viewModel.pin(m.id); menuFor = null },
            onEdit = { mode = ComposerMode.Edit(m); draft = m.text; menuFor = null },
            onDelete = { viewModel.delete(m.id); menuFor = null },
            onShadowBan = { menuFor = null },
            onToggleTypingWatch = { menuFor = null },
            onCopy = if (m.text.isNotEmpty()) ({ copyToClipboard(context, m.text); menuFor = null }) else null,
            onCopyLink = {
                menuFor = null
                scope.launch {
                    val link = viewModel.messageLink(m.id)
                    if (link != null) copyToClipboard(context, link)
                    else Toast.makeText(context, tr("Ссылка недоступна"), Toast.LENGTH_SHORT).show()
                }
            },
            chatId = viewModel.chatId,
            anchor = menuAnchor,
        )
    }
}

private fun copyToClipboard(context: android.content.Context, text: String) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
    cm?.setPrimaryClip(android.content.ClipData.newPlainText("YouGram", text))
}

@Composable
private fun ScrollDownFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(spring(stiffness = 600f)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = tr("Вниз")) }
    }
}

// ───────────────────────── Top bar ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String,
    typing: Boolean,
    online: Boolean,
    avatarFileId: Int?,
    fileState: (Int) -> kotlinx.coroutines.flow.Flow<app.yougram.feature.chat.data.FileState>,
    showBack: Boolean,
    onBack: () -> Unit,
    onTitleClick: () -> Unit,
    canCall: Boolean,
    onCall: (Boolean) -> Unit,
    overflowOpen: Boolean,
    onOverflow: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад"))
                }
            }
        },
        title = {
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(onClick = onTitleClick)
                    .padding(vertical = 4.dp, horizontal = if (showBack) 0.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileAvatar(title = title, fileId = avatarFileId, fileState = fileState, size = 40.dp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (typing || online) scheme.primary else scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        actions = {
            if (canCall) {
                IconButton(onClick = { onCall(false) }) { Icon(Icons.Filled.Call, tr("Позвонить")) }
                IconButton(onClick = { onCall(true) }) { Icon(Icons.Filled.Videocam, tr("Видеозвонок")) }
            }
            Box {
                IconButton(onClick = { onOverflow(true) }) { Icon(Icons.Filled.MoreVert, tr("Ещё")) }
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { onOverflow(false) },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("Поиск") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        onClick = { onOverflow(false); onSearch() },
                    )
                    DropdownMenuItem(
                        text = { Text("Профиль") },
                        onClick = { onOverflow(false); onTitleClick() },
                    )
                }
            }
        },
    )
}

@Composable
private fun PinnedBar(text: String, count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 28.dp).clip(CircleShape).background(scheme.primary))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (count > 1) tr("Закреплённое сообщение (%d)", count) else "Закреплённое сообщение",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.PushPin, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

// ───────────────────────── Message list ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageList(
    topPad: androidx.compose.ui.unit.Dp,
    bottomPad: androidx.compose.ui.unit.Dp,
    bottomShift: () -> Float,
    entries: List<ChatEntry>,
    listState: LazyListState,
    state: ChatUiState,
    textSize: Int,
    bubbleRadius: Int,
    viewModel: ChatViewModel,
    senders: Map<Long, app.yougram.feature.chat.data.SenderInfo>,
    replies: Map<Long, app.yougram.feature.chat.data.ReplyPreview>,
    onOpenMedia: (List<MediaItem>, Int) -> Unit,
    onOpenProfile: (Long) -> Unit,
    onOpenComments: (Long, Long) -> Unit,
    onLongPress: (ChatEntry, MessageAnchor) -> Unit,
    onReact: (Long, String) -> Unit,
    onReplyTap: (Long) -> Unit,
    onSwipeReply: (MessageItem) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val lowTier = rememberDeviceTier() == DeviceTier.Low
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize().graphicsLayer { translationY = -bottomShift() },
        // contentPadding физический и при reverseLayout не меняется местами: top — у верхней панели, bottom — у нижней.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 8.dp, end = 8.dp,
            top = topPad + 8.dp,
            bottom = bottomPad + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            count = entries.size,
            key = { entries[it].key },
            // Тип контента: Compose переиспользует разметку пузырей одного вида, а не пересобирает с нуля.
            contentType = {
                val e = entries[it]
                when {
                    e.message.isService -> 0
                    e.albumMedia.size > 1 -> 1
                    e.message.media != null -> 2
                    else -> 3
                }
            },
        ) { index ->
            val entry = entries[index]
            val older = entries.getOrNull(index + 1)      // reverseLayout: следующий в списке — более старый
            val newer = entries.getOrNull(index - 1)
            val m = entry.message

            // Разделитель даты показываем над первым сообщением дня (визуально выше → рисуется после в reverse).
            // animateItem: удалённое сообщение плавно гаснет, а соседние мягко сдвигаются на его место.
            Column(
                if (lowTier) Modifier else Modifier.animateItem(
                    fadeInSpec = motion.defaultEffectsSpec(),
                    placementSpec = motion.defaultSpatialSpec(),
                    fadeOutSpec = motion.fastEffectsSpec(),
                ),
            ) {
                if (older == null || !sameDay(older.message.date, m.date)) DateChip(m.date)

                if (m.isService) {
                    ServiceMessage(m.text)
                } else {
                    val groupedWithOlder = older != null && !older.message.isService &&
                            older.message.senderKey == m.senderKey && older.message.isOutgoing == m.isOutgoing &&
                            sameDay(older.message.date, m.date) && m.date - older.message.date < 300
                    val groupedWithNewer = newer != null && !newer.message.isService &&
                            newer.message.senderKey == m.senderKey && newer.message.isOutgoing == m.isOutgoing &&
                            sameDay(newer.message.date, m.date) && newer.message.date - m.date < 300

                    LaunchedEffect(m.senderKey) { m.senderKey?.let(viewModel::ensureSender) }
                    LaunchedEffect(m.reply) { m.reply?.let(viewModel::ensureReply) }

                    MessageRow(
                        entry = entry,
                        showSender = state.isGroup && !m.isOutgoing && !groupedWithOlder,
                        sender = m.senderKey?.let { senders[it] },
                        showAvatar = state.isGroup && !m.isOutgoing && !groupedWithNewer,
                        tailTop = groupedWithOlder,
                        tailBottom = groupedWithNewer,
                        edited = state.edits.containsKey(m.id),
                        read = (state.readAt[m.id] ?: 0) > 0,
                        deleted = m.id in state.deletedIds,
                        vanishing = m.id in state.vanishingIds,
                        textSize = textSize,
                        radius = bubbleRadius,
                        replyPreview = m.reply?.let { replies[it.messageId] },
                        viewModel = viewModel,
                        onOpenMedia = onOpenMedia,
                        onOpenProfile = onOpenProfile,
                        onOpenComments = onOpenComments,
                        onLongPress = { a -> onLongPress(entry, a) },
                        onReact = { emoji -> onReact(m.id, emoji) },
                        onReplyTap = { m.reply?.messageId?.let(onReplyTap) },
                        onDoubleTap = { onSwipeReply(m) },
                        onSwipeReply = { onSwipeReply(m) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    entry: ChatEntry,
    showSender: Boolean,
    sender: app.yougram.feature.chat.data.SenderInfo?,
    showAvatar: Boolean,
    tailTop: Boolean,
    tailBottom: Boolean,
    edited: Boolean,
    read: Boolean,
    deleted: Boolean,
    vanishing: Boolean,
    textSize: Int,
    radius: Int,
    replyPreview: app.yougram.feature.chat.data.ReplyPreview?,
    viewModel: ChatViewModel,
    onOpenMedia: (List<MediaItem>, Int) -> Unit,
    onOpenProfile: (Long) -> Unit,
    onOpenComments: (Long, Long) -> Unit,
    onLongPress: (MessageAnchor) -> Unit,
    onReact: (String) -> Unit,
    onReplyTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeReply: () -> Unit,
) {
    val m = entry.message
    val scheme = MaterialTheme.colorScheme
    val mine = m.isOutgoing
    val container = if (mine) scheme.primaryContainer else scheme.surfaceContainerHigh
    val content = if (mine) scheme.onPrimaryContainer else scheme.onSurface
    val muted = content.copy(alpha = 0.68f)

    val big = radius.dp
    val small = 6.dp

    // ── Свайп сообщения: быстрый ответ как в Telegram ──
    // Исходящее сдвигается влево, входящее — вправо: в сторону автора. Перешли порог — нацепился «реплай».
    val replyThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var swipeArmed by remember { mutableStateOf(false) }
    var swipeSettle by remember { mutableIntStateOf(0) }
    val swipeDir = if (mine) -1f else 1f
    // Плавно возвращаем пузырь на место после свайпа-ответа.
    LaunchedEffect(swipeSettle) {
        if (swipeSettle > 0 && swipeOffset != 0f) {
            val start = swipeOffset
            val startTime = System.currentTimeMillis()
            val durationMs = 220L
            while (true) {
                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(0)
                val t = (elapsed / durationMs.toFloat()).coerceIn(0f, 1f)
                val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                swipeOffset = start * (1f - eased)
                if (t >= 1f) {
                    swipeOffset = 0f
                    break
                }
                delay(16)
            }
            swipeArmed = false
        }
    }

    // «Хвост» — малое скругление у нижнего угла со стороны автора; внутри серии — малое с обеих сторон.
    val shape: Shape = if (mine) {
        RoundedCornerShape(
            topStart = big, bottomStart = big,
            topEnd = if (tailTop) small else big,
            bottomEnd = small,
        ).let { if (m.hasComments) RoundedCornerShape(topStart = big, topEnd = if (tailTop) small else big, bottomStart = small, bottomEnd = small) else it }
    } else {
        RoundedCornerShape(
            topEnd = big, bottomEnd = big,
            topStart = if (tailTop) small else big,
            bottomStart = small,
        ).let { if (m.hasComments) RoundedCornerShape(topEnd = big, topStart = if (tailTop) small else big, bottomEnd = small, bottomStart = small) else it }
    }

    // Растворение при удалении: пузырь чуть уменьшается к стороне автора, гаснет и уезжает за край.
    val vanish by animateFloatAsState(
        targetValue = if (vanishing) 1f else 0f,
        animationSpec = tween(ChatViewModel.VANISH_MS, easing = FastOutSlowInEasing),
        label = "vanish",
    )
    // Снимок сообщения для меню: при долгом тапе оно рисуется чётко поверх размытого фона.
    val captureLayer = rememberGraphicsLayer()
    val coordsRef = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    var capturing by remember { mutableStateOf(false) }
    val captureScope = rememberCoroutineScope()
    val fireLongPress: () -> Unit = {
        captureScope.launch {
            // Слой пишется только на время снимка: в обычной отрисовке лишней работы нет.
            capturing = true
            withFrameNanos { }
            withFrameNanos { }
            val bmp = runCatching { captureLayer.toImageBitmap() }.getOrNull()
            capturing = false
            val pos = coordsRef[0]?.takeIf { it.isAttached }?.positionOnScreen()
            onLongPress(
                MessageAnchor(
                    pos ?: androidx.compose.ui.geometry.Offset.Zero,
                    captureLayer.size,
                    if (pos != null) bmp else null,
                ),
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (vanish > 0f) {
                    alpha = 1f - vanish
                    val k = 1f - 0.12f * vanish
                    scaleX = k
                    scaleY = k
                    transformOrigin = TransformOrigin(if (mine) 1f else 0f, 0.5f)
                    translationX = (if (mine) 1f else -1f) * 24.dp.toPx() * vanish
                }
            }
            .padding(top = if (tailTop) 0.dp else 6.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine && showAvatar && sender != null) {
            FileAvatar(
                title = sender.name,
                fileId = sender.avatarFileId,
                fileState = { viewModel.fileState(it) },
                size = 32.dp,
                modifier = Modifier.clip(CircleShape).combinedClickable(onClick = { onOpenProfile(sender.key) }),
            )
            Spacer(Modifier.size(6.dp))
        } else if (!mine && viewModel.state.value.isGroup) {
            Spacer(Modifier.size(38.dp))
        }

        Column(
            Modifier
                .widthIn(max = 320.dp)
                .width(IntrinsicSize.Max)
                .onGloballyPositioned { coordsRef[0] = it }
                .drawWithContent {
                    if (capturing) {
                        captureLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(captureLayer)
                    } else {
                        drawContent()
                    }
                },
            verticalArrangement = Arrangement.spacedBy(if (m.hasComments) 0.dp else 2.dp),
        ) {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                // Подсказка за пузырём: стрелка реплая проявляется по мере свайпа.
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    contentDescription = tr("Ответить"),
                    tint = scheme.primary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            val t = (swipeOffset / replyThreshold).coerceIn(0f, 1f)
                            alpha = t
                            scaleX = 0.6f + 0.4f * t
                            scaleY = 0.6f + 0.4f * t
                        },
                )
                Surface(
                    color = if (deleted) scheme.errorContainer.copy(alpha = 0.6f) else container,
                    contentColor = content,
                    shape = shape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset((swipeOffset * swipeDir).roundToInt(), 0) }
                        .clip(shape)
                        .longPressFirst(m.id, fireLongPress)
                        .combinedClickable(onClick = {}, onDoubleClick = onDoubleTap)
                        .pointerInput(m.id, swipeDir) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    swipeSettle++
                                    if (swipeArmed) {
                                        swipeArmed = false
                                        onSwipeReply()
                                    }
                                },
                                onDragCancel = { swipeSettle++ },
                            ) { change, amount ->
                                change.consume()
                                swipeOffset = (swipeOffset + amount * swipeDir).coerceAtLeast(0f)
                                swipeArmed = swipeArmed || swipeOffset >= replyThreshold
                            }
                        },
                ) {
                    Column(Modifier.padding(top = 6.dp, bottom = if (m.hasComments) 2.dp else 6.dp, start = 4.dp, end = 4.dp)) {
                        if (showSender && sender != null) {
                            Text(
                                sender.name,
                                modifier = Modifier.padding(horizontal = 8.dp).combinedClickable(onClick = { onOpenProfile(sender.key) }),
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        m.forward?.let { f ->
                            Text(
                                tr("Переслано от %s", f.hiddenName.ifEmpty { f.signature.ifEmpty { tr("неизвестного") } }),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.tertiary,
                            )
                        }

                        if (m.reply != null) {
                            ReplyQuote(
                                author = replyPreview?.author.orEmpty(),
                                text = replyPreview?.text ?: "…",
                                onClick = onReplyTap,
                                tint = if (mine) scheme.primary else scheme.secondary,
                                background = content.copy(alpha = 0.08f),
                            )
                        }

                        // Медиа
                        if (entry.albumMedia.size > 1) {
                            Box(Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(18.dp))) {
                                AlbumMedia(entry.albumMedia, viewModel) { clicked ->
                                    onOpenMedia(entry.albumMedia, entry.albumMedia.indexOf(clicked).coerceAtLeast(0))
                                }
                            }
                        } else {
                            m.media?.let { media ->
                                val rounded = media.kind == MediaKind.PHOTO || media.kind == MediaKind.VIDEO ||
                                        media.kind == MediaKind.ANIMATION
                                Box(Modifier.padding(horizontal = 2.dp).let { if (rounded) it.clip(RoundedCornerShape(18.dp)) else it }) {
                                    MessageMedia(media, viewModel, onOpenPhoto = { onOpenMedia(listOf(it), 0) })
                                }
                            }
                        }

                        m.location?.let { loc ->
                            LocationMessage(
                                item = loc,
                                mine = mine,
                                onStopLive = { viewModel.stopLiveLocation(m.id) },
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                            )
                        }

                        m.poll?.let {
                            PollMessage(
                                poll = it,
                                messageId = m.id,
                                viewModel = viewModel,
                                canStop = mine,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        // Текст + время в одну строку, если помещается
                        val context = LocalContext.current
                        val annotated = rememberLinkified(
                            text = m.text,
                            linkColor = scheme.primary,
                            onOpen = { openExternally(context, it) },
                            emojis = m.emojis,
                            links = m.links,
                        )
                        if (m.text.isNotEmpty()) {
                            Column(Modifier.padding(horizontal = 8.dp)) {
                                ClickableText(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyLarge.copy(color = content, fontSize = textSize.sp),
                                    onClick = { offset ->
                                        annotated.getStringAnnotations(start = offset, end = offset).firstOrNull()
                                            ?.let { openExternally(context, it.item) }
                                    },
                                )
                            }
                        }

                        if (m.buttons.isNotEmpty()) {
                            InlineKeyboard(
                                message = m,
                                viewModel = viewModel,
                                mine = mine,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                            )
                        }

                        // Реакции слева и время справа — в одной строке, без пустоты рядом со временем.
                        Row(
                            Modifier.fillMaxWidth().padding(end = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (m.reactions.isNotEmpty()) {
                                Box(Modifier.weight(1f)) {
                                    ReactionsRow(m, onReact, contentColor = content, chosenColor = scheme.primary)
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            Row(
                                Modifier.padding(start = 6.dp, bottom = if (m.reactions.isNotEmpty()) 6.dp else 0.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (edited) Text(tr("изм."), style = MaterialTheme.typography.labelSmall, color = muted)
                                if (edited) Spacer(Modifier.size(4.dp))
                                Text(timeOf(m.date), style = MaterialTheme.typography.labelSmall, color = muted)
                                if (mine) {
                                    Spacer(Modifier.size(4.dp))
                                    Icon(
                                        if (read) Icons.Filled.DoneAll else Icons.Filled.Done,
                                        contentDescription = null,
                                        tint = if (read) scheme.primary else muted,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (m.hasComments) {
                // Кнопка комментариев — продолжение сообщения: тот же цвет, соединённая группа (малые внутренние углы, крупные внешние).
                val commentsShape = if (mine) {
                    RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = small)
                } else {
                    RoundedCornerShape(topStart = small, topEnd = small, bottomStart = small, bottomEnd = big)
                }
                Surface(
                    onClick = { onOpenComments(m.chatId, m.id) },
                    color = container,
                    contentColor = content,
                    shape = commentsShape,
                    modifier = Modifier.fillMaxWidth().longPressFirst(m.id, fireLongPress),
                ) {
                    Row(
                        Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            if (m.commentCount > 0) {
                                "${m.commentCount} " + Localization.pluralForm(m.commentCount, tr("комментарий"), tr("комментария"), tr("комментариев"))
                            } else tr("Оставить комментарий"),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyQuote(author: String, text: String, onClick: () -> Unit, tint: Color, background: Color) {
    Row(
        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .combinedClickable(onClick = onClick)
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 3.dp, height = 36.dp).background(tint))
        Spacer(Modifier.size(8.dp))
        Column(Modifier.padding(vertical = 4.dp)) {
            if (author.isNotEmpty()) {
                Text(author, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReactionsRow(m: MessageItem, onReact: (String) -> Unit, contentColor: Color, chosenColor: Color) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        m.reactions.forEach { r ->
            val bg = if (r.chosen) chosenColor.copy(alpha = 0.22f) else contentColor.copy(alpha = 0.10f)
            Row(
                Modifier.clip(CircleShape).background(bg)
                    .combinedClickable(onClick = { onReact(r.emoji) })
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (r.customEmojiId == 0L) BasicText(r.emoji, style = MaterialTheme.typography.labelLarge)
                else Icon(Icons.Filled.AddReaction, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(4.dp))
                Text(
                    r.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (r.chosen) chosenColor else contentColor,
                    fontWeight = if (r.chosen) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun DateChip(date: Int) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f)) {
            Text(
                dayLabel(date),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun ServiceMessage(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ───────────────────────── Composer ─────────────────────────

/** Начало хэштега в конце [draft] (текст после «#», возможно пустой); null, если в конце нет набираемого тега. */
private fun trailingHashtagPrefix(draft: String): String? {
    val i = draft.lastIndexOf('#')
    if (i < 0) return null
    if (i > 0 && !draft[i - 1].isWhitespace()) return null
    val prefix = draft.substring(i + 1)
    return prefix.takeIf { p -> p.length <= 64 && p.all { it.isLetterOrDigit() || it == '_' } }
}

@Composable
private fun Composer(
    mode: ComposerMode,
    draft: String,
    onDraft: (String) -> Unit,
    hashtags: List<String> = emptyList(),
    onPickHashtag: (String) -> Unit = {},
    onForgetHashtag: (String) -> Unit = {},
    onCancelMode: () -> Unit,
    paidStars: Long,
    pickerOpen: Boolean,
    attachOpen: Boolean = false,
    voiceRecording: Boolean = false,
    onVoicePress: () -> Unit = {},
    onVoiceDrag: (Float, Float) -> Unit = { _, _ -> },
    onVoiceRelease: () -> Unit = {},
    onRoundClick: () -> Unit = {},
    onRoundHoldStart: () -> Unit = {},
    onRoundHoldDrag: (Float, Float) -> Unit = { _, _ -> },
    onRoundHoldRelease: () -> Unit = {},
    onTogglePicker: () -> Unit,
    onFieldFocus: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val hasText = draft.isNotBlank()
    var videoMode by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
            // Жестовая навигация (tappableElement = 0) не добавляет отступ: расстояние от кнопок до низа панели равно боковому.
            .let {
                if (imeVisible || pickerOpen || attachOpen) it
                else it.padding(bottom = WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding())
            },
    ) {
        Column {
            // Подсказки хэштегов; долгое нажатие убирает тег из недавних.
            AnimatedVisibility(
                visible = hashtags.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(hashtags, key = { it }) { tag ->
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.clip(CircleShape)
                                .combinedClickable(onClick = { onPickHashtag(tag) }, onLongClick = { onForgetHashtag(tag) }),
                        ) {
                            Text(
                                "#$tag",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = mode !is ComposerMode.Idle,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                val (icon, label, preview) = when (mode) {
                    is ComposerMode.Reply -> Triple(Icons.AutoMirrored.Filled.Reply, "Ответ", mode.message.summary)
                    is ComposerMode.Edit -> Triple(Icons.Filled.Edit, "Редактирование", mode.message.text)
                    ComposerMode.Idle -> Triple(Icons.AutoMirrored.Filled.Reply, "", "")
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                        Text(preview, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onCancelMode) { Icon(Icons.Filled.Close, tr("Отмена")) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 26.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                FilledIconButton(
                    onClick = onAttach,
                    modifier = Modifier.size(ComposerHeight),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.AttachFile, tr("Прикрепить"))
                }

                Spacer(Modifier.size(8.dp))

                TextField(
                    value = draft,
                    onValueChange = onDraft,
                    modifier = Modifier.weight(1f).heightIn(min = ComposerHeight).onFocusChanged { if (it.isFocused) onFieldFocus() },
                    placeholder = {
                        Text(if (paidStars > 0) tr("Сообщение · %d ★", paidStars) else "Сообщение")
                    },
                    shape = RoundedCornerShape(28.dp),
                    maxLines = 6,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(alpha = 0.5f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.5f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                        focusedLeadingIconColor = Color.White.copy(alpha = 0.7f),
                        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    leadingIcon = {
                        // Иконка смещена правее без изменения раскладки: текст остаётся на месте.
                        IconButton(onClick = onTogglePicker, modifier = Modifier.offset(x = 3.dp)) {
                            AnimatedContent(
                                targetState = pickerOpen,
                                transitionSpec = {
                                    (fadeIn(tween(160, delayMillis = 70)) + scaleIn(spring(0.6f, 500f), initialScale = 0.5f))
                                        .togetherWith(fadeOut(tween(70)) + scaleOut(tween(70), targetScale = 0.5f))
                                        .using(SizeTransform(clip = false))
                                },
                                label = "pickerToggleIcon",
                            ) { open ->
                                Icon(
                                    if (open) Icons.Filled.Keyboard else Icons.Filled.EmojiEmotions,
                                    tr(if (open) "Клавиатура" else "Эмодзи"),
                                )
                            }
                        }
                    },
                )

                Spacer(Modifier.size(8.dp))

                // Отправить / голосовое / кружок. Переключение голосовое ↔ кружок — как у тайлов быстрых настроек:
                // круг превращается в скруглённый квадрат (пружина), цвет перетекает, иконка гаснет и появляется заново.
                val kind = when {
                    voiceRecording -> ComposerButtonKind.Voice // во время записи кнопка ничего не активирует: завершает Pill
                    hasText && mode is ComposerMode.Edit -> ComposerButtonKind.Confirm
                    hasText -> ComposerButtonKind.Send
                    videoMode -> ComposerButtonKind.Round
                    else -> ComposerButtonKind.Voice
                }
                // Форма кнопки: у голосового и кружка одна и та же («печенье»), отправка — круг.
                // Голосовое ↔ кружок форму не меняют: кнопка просто быстро проворачивается (spin ниже).
                val shapeOf = { k: ComposerButtonKind ->
                    when (k) {
                        ComposerButtonKind.Voice, ComposerButtonKind.Round -> MaterialShapes.Cookie9Sided
                        else -> MaterialShapes.Circle
                    }
                }
                // Поворот при смене голосовое ↔ кружок: два оборота (720°) всегда в одну сторону.
                val spin = remember { Animatable(0f) }
                val lastKind = remember { object { var value = kind } }
                LaunchedEffect(kind) {
                    val prev = lastKind.value
                    lastKind.value = kind
                    val micOrRound = { k: ComposerButtonKind -> k == ComposerButtonKind.Voice || k == ComposerButtonKind.Round }
                    if (prev != kind && micOrRound(prev) && micOrRound(kind)) {
                        spin.snapTo(0f)
                        spin.animateTo(720f, tween(480, easing = FastOutSlowInEasing))
                        spin.snapTo(0f)
                    }
                }
                var shapeFrom by remember { mutableStateOf(shapeOf(kind)) }
                var shapeTo by remember { mutableStateOf(shapeOf(kind)) }
                val shapeProgress = remember { Animatable(1f) }
                LaunchedEffect(kind) {
                    val target = shapeOf(kind)
                    if (target !== shapeTo) {
                        shapeFrom = shapeTo
                        shapeTo = target
                        shapeProgress.snapTo(0f)
                        shapeProgress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 700f))
                    }
                }
                val morph = remember(shapeFrom, shapeTo) { Morph(shapeFrom.normalized(), shapeTo.normalized()) }
                val buttonShape = MorphShape(morph, shapeProgress.value)
                val container by animateColorAsState(
                    targetValue = scheme.primary,
                    animationSpec = tween(140),
                    label = "composerContainer",
                )
                val onContainer by animateColorAsState(
                    targetValue = scheme.onPrimary,
                    animationSpec = tween(140),
                    label = "composerContent",
                )

                // Жест голосового: тап — переключить на кружок, удержание ≥280 мс — запись,
                // свайп вверх во время записи — закреп, влево — отмена, отпустил — отправка.
                // pointerInput ловит всё удержание целиком: onClick у кнопки в режиме Voice отключён.
                val pressV by rememberUpdatedState(onVoicePress)
                val dragV by rememberUpdatedState(onVoiceDrag)
                val releaseV by rememberUpdatedState(onVoiceRelease)
                val pressR by rememberUpdatedState(onRoundHoldStart)
                val dragR by rememberUpdatedState(onRoundHoldDrag)
                val releaseR by rememberUpdatedState(onRoundHoldRelease)
                val holdGesture = if (kind == ComposerButtonKind.Voice || kind == ComposerButtonKind.Round) {
                    val isRound = kind == ComposerButtonKind.Round
                    Modifier.pointerInput(isRound) {
                        awaitEachGesture {
                            // requireUnconsumed = false: внутренний clickable IconButton успевает поглотить нажатие.
                            awaitFirstDown(requireUnconsumed = false)
                            val pressStart = System.currentTimeMillis()
                            var recording = false
                            var totalX = 0f
                            var totalY = 0f
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                if (!recording && System.currentTimeMillis() - pressStart >= 280) {
                                    recording = true
                                    if (isRound) pressR() else pressV()
                                    haptics(Haptics.Kind.Tick)
                                }
                                if (recording) {
                                    event.changes.firstOrNull()?.let {
                                        val delta = it.positionChange()
                                        totalX += delta.x
                                        totalY += delta.y
                                        // Пороги заданы для смещения от точки нажатия, поэтому передаём накопленное.
                                        if (isRound) dragR(totalX, totalY) else dragV(totalX, totalY)
                                    }
                                }
                            }
                            if (recording) {
                                if (isRound) releaseR() else releaseV()
                            } else videoMode = !videoMode // короткий тап: микрофон ↔ кружок
                        }
                    }
                } else Modifier

                FilledIconButton(
                    onClick = {
                        when (kind) {
                            // Голосовое и кружок живут в holdGesture: тап = смена режима, удержание = запись.
                            ComposerButtonKind.Round, ComposerButtonKind.Voice -> {}
                            else -> {
                                if (hasText) onSend() else videoMode = !videoMode
                            }
                        }
                    },
                    modifier = Modifier.size(ComposerHeight).graphicsLayer { rotationZ = spin.value }.then(holdGesture),
                    shape = buttonShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = container,
                        contentColor = onContainer,
                    ),
                ) {
                    // Fade-through: старая иконка быстро гаснет, новая появляется чуть позже, без сдвига.
                    AnimatedContent(
                        targetState = kind,
                        transitionSpec = {
                            (fadeIn(tween(140, delayMillis = 100)) + scaleIn(tween(140, delayMillis = 100), initialScale = 0.78f))
                                .togetherWith(fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.78f))
                                .using(SizeTransform(clip = false))
                        },
                        label = "composerIcon",
                    ) { k ->
                        Icon(
                            when (k) {
                                ComposerButtonKind.Confirm -> Icons.Filled.Check
                                ComposerButtonKind.Send -> Icons.AutoMirrored.Filled.Send
                                ComposerButtonKind.Round -> Icons.Filled.Videocam
                                ComposerButtonKind.Voice -> Icons.Filled.Mic
                            },
                            contentDescription = tr(
                                when (k) {
                                    ComposerButtonKind.Confirm, ComposerButtonKind.Send -> "Отправить"
                                    ComposerButtonKind.Round -> "Видеосообщение"
                                    ComposerButtonKind.Voice -> "Голосовое сообщение"
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinBar(channel: Boolean, joining: Boolean, onJoin: () -> Unit) {
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            FilledTonalButton(onClick = onJoin, enabled = !joining) {
                Text(if (channel) "Подписаться" else "Вступить")
            }
        }
    }
}

/** Пилюля записи голосового: пульсирующий индикатор, таймер и подсказка про свайпы. */
@Composable
private fun VoiceRecordingBar(
    seconds: Int,
    locked: Boolean,
    tapMode: Boolean,
    cancelled: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "voicePulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "voiceDotAlpha",
    )
    val showButtons = locked || tapMode

    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Красный индикатор: пульсирует, в режиме отмены — статичный приглушённый.
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .alpha(if (cancelled) 0.4f else dotAlpha)
                    .background(if (cancelled) scheme.errorContainer else scheme.error),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                when {
                    cancelled -> tr("Отменяю…")
                    locked -> tr("Запись закреплена")
                    else -> tr("Вверх — закрепить, влево — отмена")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showButtons) {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = tr("Отмена"),
                        tint = scheme.error,
                    )
                }
                FilledIconButton(
                    onClick = onSend,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primary),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr("Отправить"), tint = scheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyBar() {
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Писать сообщения здесь нельзя",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorSnack(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(3500)
        onDismiss()
    }
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

// ───────────────────────── Helpers ─────────────────────────

private fun sameDay(a: Int, b: Int): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a * 1000L }
    val cb = Calendar.getInstance().apply { timeInMillis = b * 1000L }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun timeOf(date: Int): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(date * 1000L))

private fun dayLabel(date: Int): String {
    val now = Calendar.getInstance()
    val d = Calendar.getInstance().apply { timeInMillis = date * 1000L }
    val sameYear = now.get(Calendar.YEAR) == d.get(Calendar.YEAR)
    val diff = now.get(Calendar.DAY_OF_YEAR) - d.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && diff == 0 -> tr("Сегодня")
        sameYear && diff == 1 -> tr("Вчера")
        else -> SimpleDateFormat(if (sameYear) "d MMMM" else "d MMMM yyyy", Locale.getDefault()).format(d.time)
    }
}

/** Копирует выбранный через SAF файл в кэш приложения: TDLib принимает только обычные пути. */
private suspend fun copyToCache(context: android.content.Context, uri: Uri): File? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: "file_${System.currentTimeMillis()}"
            val dir = File(context.cacheDir, "outgoing").apply { mkdirs() }
            val out = File(dir, "${System.currentTimeMillis()}_$name")
            context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            out.takeIf { it.exists() && it.length() > 0 }
        }.getOrNull()
    }

/** Shape из [Morph] (полигоны нормализованы в единичный квадрат): контур на [progress] масштабируется под размер. */
private class MorphShape(private val morph: Morph, private val progress: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path()
        val w = size.width
        val h = size.height
        var first = true
        for (c in morph.asCubics(progress)) {
            if (first) {
                path.moveTo(c.anchor0X * w, c.anchor0Y * h)
                first = false
            }
            path.cubicTo(c.control0X * w, c.control0Y * h, c.control1X * w, c.control1Y * h, c.anchor1X * w, c.anchor1Y * h)
        }
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}