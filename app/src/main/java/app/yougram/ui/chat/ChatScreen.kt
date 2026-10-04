package app.yougram.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
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
import app.yougram.data.MediaItem
import app.yougram.data.MessageItem
import app.yougram.data.SettingsRepository
import app.yougram.ui.Avatar
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

@Composable
fun ChatScreen(viewModel: ChatViewModel, settings: SettingsRepository, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val glass by settings.glass.collectAsState()
    val chatPrefs by settings.chatPrefs.collectAsState()
    val backdrop = rememberBackdropState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var input by remember { mutableStateOf("") }
    var viewerMedia by remember { mutableStateOf<MediaItem?>(null) }

    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Высота нижней панели меняется (клавиатура, многострочный ввод), поэтому измеряем её.
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    // Клавиатура поднимает панель ввода, а список должен остаться видимым над ней.
    val imeHeight = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() } + imeHeight

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
                    itemsIndexed(state.messages, key = { _, m -> m.id }) { index, message ->
                        val newer = state.messages.getOrNull(index - 1)
                        val older = state.messages.getOrNull(index + 1)
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
                Avatar(title = state.title, path = null, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                            // Вложения и эмодзи пока без действия (отправка медиа не реализована).
                            Icon(
                                Icons.Filled.AddCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
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
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Круглая кнопка: микрофон (пока без действия), а при наличии текста — отправка.
                    val canSend = input.isNotBlank()
                    Surface(
                        onClick = {
                            if (canSend) {
                                viewModel.send(input)
                                input = ""
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                                contentDescription = if (canSend) "Отправить" else "Голосовое сообщение",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    viewerMedia?.let { media ->
        PhotoViewer(media = media, viewModel = viewModel, onDismiss = { viewerMedia = null })
    }
}

/** Два соседних сообщения — одна группа: тот же автор и интервал меньше пяти минут. */
private fun sameGroup(older: MessageItem, newer: MessageItem): Boolean =
    older.isOutgoing == newer.isOutgoing && abs(newer.date - older.date) < GroupGapSeconds

private fun formatTime(date: Int): String =
    Instant.ofEpochSecond(date.toLong()).atZone(ZoneId.systemDefault()).format(TimeFormatter)

@Composable
private fun MessageBubble(
    message: MessageItem,
    joinedWithOlder: Boolean,
    joinedWithNewer: Boolean,
    viewModel: ChatViewModel,
    textSize: Int,
    cornerRadius: Dp,
    onOpenPhoto: (MediaItem) -> Unit,
) {
    val mine = message.isOutgoing
    val media = message.media

    // Углы со стороны автора у склеенных сообщений становятся мелкими.
    val inner = minOf(BubbleInner, cornerRadius)
    val top = if (joinedWithOlder) inner else cornerRadius
    val bottom = if (joinedWithNewer) inner else cornerRadius
    val shape = if (mine) {
        RoundedCornerShape(topStart = cornerRadius, topEnd = top, bottomEnd = bottom, bottomStart = cornerRadius)
    } else {
        RoundedCornerShape(topStart = top, topEnd = cornerRadius, bottomEnd = cornerRadius, bottomStart = bottom)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (joinedWithOlder) 2.dp else 12.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Surface(
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
            Column(Modifier.widthIn(max = 320.dp)) {
                if (media != null) {
                    Box(Modifier.padding(4.dp)) { MessageMedia(media, viewModel, onOpenPhoto) }
                }
                if (message.text.isNotEmpty()) {
                    Text(
                        message.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = textSize.sp,
                            lineHeight = (textSize * 1.4f).sp,
                        ),
                    )
                }
            }
        }
        // Время под последним сообщением группы, вне пузыря.
        if (!joinedWithNewer) {
            Text(
                formatTime(message.date),
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}