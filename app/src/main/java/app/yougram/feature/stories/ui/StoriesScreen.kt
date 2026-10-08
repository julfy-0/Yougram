package app.yougram.feature.stories.ui

import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import app.yougram.core.ui.component.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.yougram.core.ui.component.Avatar
import app.yougram.core.ui.component.rememberFileBitmap
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.feature.stories.data.StoryRef
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val QuickReactions = listOf("❤️", "🔥", "👍", "😂", "😮", "😢", "🎉", "👎")

/** Лента историй для верхней панели: «Моя история» и круги авторов; непросмотренные — с цветным кольцом. */
@Composable
fun StoriesBar(
    stories: List<StoryRef>,
    onOpen: (Long, Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(stories, key = { if (it.isOwn) "own" else "u${it.chatId}" }) { ref ->
            StoryTile(
                title = ref.title.ifBlank { "История" },
                avatarPath = ref.avatarPath,
                unread = ref.unread,
                hasStories = ref.hasStories,
                own = ref.isOwn,
                onClick = {
                    if (ref.isOwn && !ref.hasStories) onAdd() else onOpen(ref.chatId, ref.storyId)
                },
                onAdd = onAdd,
            )
        }
    }
}

@Composable
private fun StoryTile(
    title: String,
    avatarPath: String?,
    unread: Boolean,
    hasStories: Boolean,
    own: Boolean,
    onClick: () -> Unit,
    onAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ring = when {
        unread -> Brush.sweepGradient(listOf(scheme.primary, scheme.tertiary, scheme.primary))
        hasStories -> SolidColor(scheme.onSurface.copy(alpha = if (own) 0.55f else 0.25f))
        else -> SolidColor(Color.Transparent)
    }
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().then(if (hasStories) Modifier.border(2.5.dp, ring, CircleShape) else Modifier).padding(5.dp),
                contentAlignment = Alignment.Center,
            ) { Avatar(title = title, path = avatarPath, size = 48.dp) }
            if (own) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape)
                        .background(scheme.primary).border(1.5.dp, scheme.surface, CircleShape)
                        .clickable(onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, "Опубликовать историю", tint = scheme.onPrimary, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ago(date: Int): String {
    val d = (System.currentTimeMillis() / 1000 - date).coerceAtLeast(0)
    return when {
        d < 60 -> "только что"
        d < 3600 -> "${d / 60} мин. назад"
        d < 86400 -> "${d / 3600} ч. назад"
        else -> "${d / 86400} дн. назад"
    }
}

/**
 * Полноэкранный просмотр историй: полосы прогресса, тап вперёд/назад, удержание — пауза,
 * свайп вниз — закрыть, реакции, ответ, для своих историй — просмотры и удаление.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoriesScreen(viewModel: StoryViewerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val st by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = { if (!closing) { closing = true; onBack() } }
    BackHandler { close() }

    LaunchedEffect(message) {
        message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.consumeMessage() }
    }

    val s = st
    if (s == null) {
        LaunchedEffect(Unit) { close() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val item = s.item
    var held by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewersOpen by remember { mutableStateOf(false) }
    var replyFocused by remember { mutableStateOf(false) }
    var reply by remember(s.key) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var videoMs by remember(s.key) { mutableIntStateOf(0) }
    var dragY by remember { mutableStateOf(0f) }
    val progress = remember(s.key) { Animatable(0f) }
    // Фон для размытия под полем ответа: записывается из слоя с медиа истории.
    val backdrop = rememberBackdropState()
    val glass = LocalGlass.current
    val replyShape = RoundedCornerShape(24.dp)

    val ready = item != null && (!item.supported || item.mediaPath != null)
    val holding = held || menuOpen || confirmDelete || viewersOpen || replyFocused || !ready
    val durationMs = when {
        item == null -> 5000
        item.isVideo -> if (videoMs > 0) videoMs else (item.durationSec * 1000).toInt().coerceAtLeast(3000)
        else -> 5000
    }

    LaunchedEffect(s.key, ready, holding, durationMs) {
        if (!ready || holding) return@LaunchedEffect
        val left = ((1f - progress.value) * durationMs).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(left, easing = LinearEasing))
        if (!viewModel.next()) close()
    }
    // Не поддерживаемый/битый тип — пропускаем через секунду-две.
    LaunchedEffect(s.key, s.failed, item?.supported) {
        if (s.failed || item?.supported == false) {
            kotlinx.coroutines.delay(1500)
            if (!viewModel.next()) close()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragY
                alpha = (1f - dragY / 900f).coerceIn(0.3f, 1f)
            }
            .background(Color.Black),
    ) {
        // Медиа.
        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
            if (item != null && item.supported && item.mediaPath != null) {
                if (item.isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(item.mediaPath)
                                setOnPreparedListener { mp -> mp.isLooping = false; videoMs = mp.duration }
                            }
                        },
                        update = { v ->
                            if (holding) { if (v.isPlaying) v.pause() } else if (!v.isPlaying) v.start()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val bitmap = rememberFileBitmap(item.mediaPath, 1920)
                    if (bitmap != null) {
                        Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
            }
        }
        if (!ready && !s.failed) {
            LoadingIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }
        if (s.failed) {
            Text("История недоступна", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else if (item?.supported == false) {
            Text("Этот тип истории пока не поддерживается", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }

        // Жесты: тап слева — назад, иначе вперёд; удержание — пауза; свайп вниз — закрыть.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(s.key) {
                    detectTapGestures(
                        onPress = { held = true; tryAwaitRelease(); held = false },
                        onTap = { pos ->
                            if (replyFocused) { focus.clearFocus(); return@detectTapGestures }
                            if (pos.x < size.width * 0.33f) {
                                if (!viewModel.prev()) scope.launch { progress.snapTo(0f) }
                            } else if (!viewModel.next()) close()
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { held = true },
                        onDragEnd = {
                            held = false
                            if (dragY > 220f) close() else dragY = 0f
                        },
                        onDragCancel = { held = false; dragY = 0f },
                        onVerticalDrag = { _, dy -> dragY = (dragY + dy).coerceAtLeast(0f) },
                    )
                },
        )

        // Верх: прогресс + шапка.
        Column(
            Modifier.fillMaxWidth().statusBarsPadding()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (i in 0 until s.total) {
                    Box(
                        Modifier.weight(1f).height(2.5.dp).clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                            .drawBehind {
                                val f = when {
                                    i < s.index -> 1f
                                    i == s.index -> progress.value
                                    else -> 0f
                                }
                                drawRect(Color.White, size = Size(size.width * f, size.height))
                            },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(title = s.ref.title.ifBlank { "?" }, path = s.ref.avatarPath, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.ref.title.ifBlank { "История" },
                        color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item != null) {
                        val sub = buildString {
                            append(if (item.pending) "публикуется…" else ago(item.date))
                            if (item.closeFriends) append(" · близкие друзья")
                        }
                        Text(sub, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (item?.canDelete == true) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Меню", tint = Color.White) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Удалить") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menuOpen = false; confirmDelete = true },
                            )
                        }
                    }
                }
                IconButton(onClick = close) { Icon(Icons.Default.Close, "Закрыть", tint = Color.White) }
            }
        }

        // Низ: подпись + (просмотры | ответ и реакции).
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                .navigationBarsPadding().imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (item != null && item.caption.isNotBlank()) {
                var expanded by remember(s.key) { mutableStateOf(false) }
                Text(
                    item.caption,
                    color = Color.White,
                    maxLines = if (expanded) 20 else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(bottom = 10.dp),
                )
            }
            if (item != null && item.isOwn) {
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .clickable { viewersOpen = true; viewModel.loadViewers() }
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Visibility, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${item.viewCount}", color = Color.White)
                    if (item.reactionCount > 0) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF4D6D), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${item.reactionCount}", color = Color.White)
                    }
                }
            } else if (item != null && item.canReply) {
                if (replyFocused) {
                    LazyRow(
                        Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(QuickReactions) { e ->
                            Box(
                                Modifier.size(42.dp).clip(CircleShape)
                                    .background(if (item.myReaction == e) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.14f))
                                    .clickable { viewModel.react(e) },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 22.sp) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = reply,
                        onValueChange = { reply = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Color.White),
                        maxLines = 4,
                        modifier = Modifier.weight(1f).onFocusChanged { replyFocused = it.isFocused },
                        decorationBox = { inner ->
                            Box(
                                Modifier
                                    .glass(backdrop, glass, replyShape, tint = Color.Black)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                if (reply.isEmpty()) Text("Ответить на историю…", color = Color.White.copy(alpha = 0.6f))
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    if (reply.isNotBlank()) {
                        IconButton(
                            enabled = !sending,
                            onClick = {
                                sending = true
                                viewModel.reply(reply) { ok ->
                                    sending = false
                                    if (ok) {
                                        reply = ""
                                        focus.clearFocus()
                                        Toast.makeText(context, "Ответ отправлен", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = Color.White) }
                    } else {
                        IconButton(onClick = { viewModel.react("❤️") }) {
                            val liked = item.myReaction != null
                            Icon(
                                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                "Реакция",
                                tint = if (liked) Color(0xFFFF4D6D) else Color.White,
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить историю?") },
            text = { Text("Она исчезнет у всех, кто её видит.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (!viewModel.deleteCurrent()) close()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    if (viewersOpen) {
        val viewers by viewModel.viewers.collectAsState()
        ModalBottomSheet(onDismissRequest = { viewersOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "Просмотры",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                when {
                    viewers.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                    viewers.error != null -> Text(viewers.error!!, modifier = Modifier.padding(20.dp))
                    viewers.list.isEmpty() -> Text("Пока никто не посмотрел", modifier = Modifier.padding(20.dp))
                    else -> LazyColumn {
                        items(viewers.list) { v ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(title = v.name.ifBlank { "?" }, path = null, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        ago(v.date),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                v.reaction?.let { Text(it, fontSize = 22.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}