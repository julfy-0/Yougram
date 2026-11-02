@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package app.yougram.feature.chat.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.core.ui.rememberHaptics
import androidx.compose.runtime.mutableIntStateOf
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
    val prefs by settings.chatPrefs.collectAsState()
    val senders by viewModel.senders.collectAsState()
    val replies by viewModel.replies.collectAsState()
    val pendingJump by viewModel.pendingJump.collectAsState()
    val typingWatch by viewModel.typingWatch.collectAsState()

    val listState = rememberLazyListState()
    val entries = remember(state.messages) { state.messages.groupAlbums() }

    var mode by remember { mutableStateOf<ComposerMode>(ComposerMode.Idle) }
    var draft by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<ChatEntry?>(null) }
    var attachOpen by remember { mutableStateOf(false) }
    // Панель эмодзи / стикеров / GIF.
    var pickerOpen by remember { mutableStateOf(false) }
    var pickerTab by remember { mutableStateOf(PickerTab.Emoji) }
    var stickerQuery by remember { mutableStateOf("") }
    BackHandler(enabled = pickerOpen || attachOpen) { pickerOpen = false; attachOpen = false }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var pollOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }
    var pendingMedia by remember { mutableStateOf<PendingMedia?>(null) }
    var stickerSet by remember { mutableLongStateOf(0L) }

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

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val copy = copyToCache(context, uri) ?: return@launch
            viewModel.sendDocument(copy.path)
        }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val copy = copyToCache(context, uri) ?: return@launch
            val mime = context.contentResolver.getType(uri).orEmpty()
            when {
                mime == "image/gif" -> pendingMedia = PendingMedia(copy.path, PendingKind.GIF)
                mime.startsWith("video/") -> pendingMedia = PendingMedia(copy.path, PendingKind.VIDEO)
                else -> pendingMedia = PendingMedia(copy.path, PendingKind.PHOTO)
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
    var topBarPx by remember { mutableIntStateOf(0) }
    var bottomBarPx by remember { mutableIntStateOf(0) }
    // Высота выдвижных панелей (эмодзи / вложения): список едет вместе с ними через translationY, без перекомпоновки.
    var panelPx by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
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
                        onOpenProfile = onOpenProfile,
                        onOpenComments = onOpenComments,
                        onLongPress = { menuFor = it },
                        onReact = { id, emoji -> viewModel.react(id, emoji); haptics(Haptics.Kind.Click) },
                        onReplyTap = { viewModel.jumpTo(it) },
                        onSwipeReply = { mode = ComposerMode.Reply(it); haptics(Haptics.Kind.Tick) },
                    )
                }
            }

        }

        // Верхняя стеклянная панель (шапка + закреп).
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .glass(backdrop, glass, RectangleShape)
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

        // Нижняя стеклянная панель (поле ввода / вступить / только чтение).
        Box(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .glass(backdrop, glass, RectangleShape)
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
                    Composer(
                        mode = mode,
                        draft = draft,
                        onDraft = { draft = it },
                        onCancelMode = {
                            if (mode is ComposerMode.Edit) draft = ""
                            mode = ComposerMode.Idle
                        },
                        paidStars = state.paidMessageStars,
                        pickerOpen = pickerOpen,
                        attachOpen = attachOpen,
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
                                    onLocation = { attachOpen = false },
                                    onVoice = { attachOpen = false },
                                    onRound = { attachOpen = false },
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
            canEdit = m.isOutgoing && m.media == null && m.poll == null && !m.isService,
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
            chatId = viewModel.chatId,
        )
    }
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
    onLongPress: (ChatEntry) -> Unit,
    onReact: (Long, String) -> Unit,
    onReplyTap: (Long) -> Unit,
    onSwipeReply: (MessageItem) -> Unit,
) {
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize().graphicsLayer { translationY = -bottomShift() },
        // reverseLayout: «top» списка — это низ экрана, поэтому отступы поменяны местами в терминах контента.
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 8.dp, end = 8.dp,
            top = bottomPad + 8.dp,
            bottom = topPad + 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(entries.size, key = { entries[it].key }) { index ->
            val entry = entries[index]
            val older = entries.getOrNull(index + 1)      // reverseLayout: следующий в списке — более старый
            val newer = entries.getOrNull(index - 1)
            val m = entry.message

            // Разделитель даты показываем над первым сообщением дня (визуально выше → рисуется после в reverse).
            Column {
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
                        textSize = textSize,
                        radius = bubbleRadius,
                        replyPreview = m.reply?.let { replies[it.messageId] },
                        viewModel = viewModel,
                        onOpenMedia = onOpenMedia,
                        onOpenProfile = onOpenProfile,
                        onOpenComments = onOpenComments,
                        onLongPress = { onLongPress(entry) },
                        onReact = { emoji -> onReact(m.id, emoji) },
                        onReplyTap = { m.reply?.messageId?.let(onReplyTap) },
                        onDoubleTap = { onSwipeReply(m) },
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
    textSize: Int,
    radius: Int,
    replyPreview: app.yougram.feature.chat.data.ReplyPreview?,
    viewModel: ChatViewModel,
    onOpenMedia: (List<MediaItem>, Int) -> Unit,
    onOpenProfile: (Long) -> Unit,
    onOpenComments: (Long, Long) -> Unit,
    onLongPress: () -> Unit,
    onReact: (String) -> Unit,
    onReplyTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val m = entry.message
    val scheme = MaterialTheme.colorScheme
    val mine = m.isOutgoing
    val container = if (mine) scheme.primaryContainer else scheme.surfaceContainerHigh
    val content = if (mine) scheme.onPrimaryContainer else scheme.onSurface
    val muted = content.copy(alpha = 0.68f)

    val big = radius.dp
    val small = 6.dp
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

    Row(
        Modifier.fillMaxWidth().padding(top = if (tailTop) 0.dp else 6.dp),
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
            Modifier.widthIn(max = 320.dp).width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Surface(
                color = if (deleted) scheme.errorContainer.copy(alpha = 0.6f) else container,
                contentColor = content,
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress, onDoubleClick = onDoubleTap),
            ) {
                Column(Modifier.padding(vertical = 6.dp, horizontal = 4.dp)) {
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

                    if (m.reactions.isNotEmpty()) {
                        ReactionsRow(m, onReact, contentColor = content, chosenColor = scheme.primary)
                    }

                    // Мета-строка
                    Row(
                        Modifier.align(Alignment.End).padding(end = 8.dp, top = 2.dp),
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
                    modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun Composer(
    mode: ComposerMode,
    draft: String,
    onDraft: (String) -> Unit,
    onCancelMode: () -> Unit,
    paidStars: Long,
    pickerOpen: Boolean,
    attachOpen: Boolean = false,
    onTogglePicker: () -> Unit,
    onFieldFocus: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hasText = draft.isNotBlank()
    var videoMode by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
            .let { if (imeVisible || pickerOpen || attachOpen) it else it.windowInsetsPadding(WindowInsets.navigationBars) },
    ) {
        Column {
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
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
                        IconButton(onClick = onTogglePicker) {
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
                    hasText && mode is ComposerMode.Edit -> ComposerButtonKind.Confirm
                    hasText -> ComposerButtonKind.Send
                    videoMode -> ComposerButtonKind.Round
                    else -> ComposerButtonKind.Voice
                }
                // Форма кнопки: голосовое — «печенье» с 12 волнами, кружок — скруглённый квадрат, отправка — круг.
                // Между формами кнопка плавно перетекает (Morph) на пружине.
                val shapeOf = { k: ComposerButtonKind ->
                    when (k) {
                        ComposerButtonKind.Voice -> MaterialShapes.Cookie9Sided
                        ComposerButtonKind.Round -> MaterialShapes.Square
                        else -> MaterialShapes.Circle
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
                        shapeProgress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
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

                FilledIconButton(
                    onClick = { if (hasText) onSend() else videoMode = !videoMode },
                    modifier = Modifier.size(ComposerHeight),
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
                            (fadeIn(tween(160, delayMillis = 90)) + scaleIn(tween(160, delayMillis = 90), initialScale = 0.78f))
                                .togetherWith(fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.78f))
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