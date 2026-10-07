package app.yougram.feature.profile.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.yougram.core.ui.component.CustomBannerImage
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.component.LocalOpenLink
import app.yougram.core.ui.component.LocalOwnCustomBanner
import app.yougram.core.ui.component.ProfileBanner
import app.yougram.core.ui.component.rememberFileBitmap
import app.yougram.core.ui.glass.SystemBarsGlass
import app.yougram.feature.badge.ui.LocalYougramBanners
import app.yougram.feature.badge.ui.YougramBadge
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.MediaItem
import app.yougram.feature.chat.data.MediaKind
import app.yougram.feature.chat.data.MessageItem
import app.yougram.feature.chat.data.ProfileDetails
import app.yougram.feature.chat.data.ProfileKind
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

private enum class ProfileTab(val label: String) {
    MEDIA("Медиа"),
    FILES("Файлы"),
    LINKS("Ссылки"),
    DELETED("Удалённые"),
}

private val UrlRegex = Regex("""(https?://|t\.me/)\S+""", RegexOption.IGNORE_CASE)

private val MediaKinds = setOf(MediaKind.PHOTO, MediaKind.VIDEO, MediaKind.ANIMATION, MediaKind.VIDEO_NOTE)

private class ProfileAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onLeft: () -> Unit,
    onCall: (userId: Long, video: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(ProfileTab.MEDIA) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showDossier by remember { mutableStateOf(false) }
    var pendingVideo by remember { mutableStateOf(false) }
    val callPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val userId = state.details?.id
        if (userId != null && result[Manifest.permission.RECORD_AUDIO] != false) onCall(userId, pendingVideo)
        else Toast.makeText(context, "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }
    val startCall: (Boolean) -> Unit = { video ->
        val userId = state.details?.id
        if (userId != null) {
            val needed = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (video) add(Manifest.permission.CAMERA)
            }.toTypedArray()
            if (needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                onCall(userId, video)
            } else {
                pendingVideo = video
                callPermissions.launch(needed)
            }
        }
    }

    LaunchedEffect(state.left) { if (state.left) onLeft() }
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.dismissError()
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val details = state.details
    val listState = rememberLazyListState()
    // Вход секций проигрывается один раз; при возврате прокруткой элементы появляются сразу.
    val entered = remember { mutableStateOf(false) }
    LaunchedEffect(details != null) {
        if (details != null) {
            delay(900)
            entered.value = true
        }
    }

    SystemBarsGlass(Modifier.fillMaxSize()) {
        if (details == null) {
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            val actions = actionsFor(details, context, onOpenChat, startCall, viewModel::toggleMute) { confirmLeave = true }
            val media = remember(state.shared) { state.shared.filter { it.media?.kind in MediaKinds } }
            val files = remember(state.shared) { state.shared.filter { it.media?.kind == MediaKind.DOCUMENT } }
            val links = remember(state.shared) { state.shared.filter { UrlRegex.containsMatchIn(it.text) } }

            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = topInset + 56.dp,
                    bottom = bottomInset + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Appear(0, entered.value) { ProfileHeader(details, viewModel, listState, entered.value) } }
                item { Appear(1, entered.value) { ActionsGroup(actions) } }
                item {
                    Appear(2, entered.value) {
                        InfoGroup(details, context, onDossier = { viewModel.loadDossier(); showDossier = true })
                    }
                }
                item { Appear(3, entered.value) { TabSwitcher(tab) { tab = it } } }
                when {
                    state.sharedLoading && tab != ProfileTab.DELETED -> item(key = "loading") {
                        Box(Modifier.animateItem().fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                    }
                    tab == ProfileTab.MEDIA -> {
                        if (media.isEmpty()) item(key = "empty") { EmptyHint(Modifier.animateItem()) }
                        items3(media) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                row.forEach { message ->
                                    MediaThumb(message.media!!, viewModel, Modifier.weight(1f).aspectRatio(1f))
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    tab == ProfileTab.FILES -> {
                        if (files.isEmpty()) item(key = "empty") { EmptyHint(Modifier.animateItem()) }
                        files.forEach { message -> item(key = "f${message.id}") { Box(Modifier.animateItem()) { FileRow(message.media!!, context) } } }
                    }
                    tab == ProfileTab.LINKS -> {
                        if (links.isEmpty()) item(key = "empty") { EmptyHint(Modifier.animateItem()) }
                        links.forEach { message -> item(key = "l${message.id}") { Box(Modifier.animateItem()) { LinkRow(message, context) } } }
                    }
                    else -> {
                        if (state.deleted.isEmpty()) item(key = "empty") { EmptyHint(Modifier.animateItem()) }
                        else item(key = "deleted") { Box(Modifier.animateItem()) { DeletedGroup(state.deleted) } }
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = topInset, start = 4.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }
    }

    if (showDossier) {
        DossierSheet(viewModel, onDismiss = { showDossier = false })
    }

    if (confirmLeave) {
        val title = state.details?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Покинуть «$title»?") },
            text = { Text("Вы перестанете получать сообщения из этого чата.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.leave()
                }) { Text("Покинуть") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Отмена") } },
        )
    }
}

/** Раскладывает список по три элемента в строку для сетки медиа внутри общего LazyColumn. */
private fun androidx.compose.foundation.lazy.LazyListScope.items3(
    list: List<MessageItem>,
    row: @Composable (List<MessageItem>) -> Unit,
) {
    val rows = list.chunked(3)
    rows.forEach { chunk -> item(key = "m${chunk.first().id}") { Box(Modifier.animateItem()) { row(chunk) } } }
}

private fun actionsFor(
    details: ProfileDetails,
    context: Context,
    onOpenChat: () -> Unit,
    onCall: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onLeave: () -> Unit,
): List<ProfileAction> {
    val dial = {
        val phone = details.phone
        if (phone == null) {
            Toast.makeText(context, "Номер телефона скрыт", Toast.LENGTH_SHORT).show()
        } else {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "Нет приложения для звонков", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val sound = ProfileAction(
        if (details.muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
        "Звук",
        onToggleMute,
    )
    val share = {
        details.link?.let { link ->
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://$link")
            context.startActivity(Intent.createChooser(send, null))
        } ?: Toast.makeText(context, "У канала нет публичной ссылки", Toast.LENGTH_SHORT).show()
    }
    return when (details.kind) {
        ProfileKind.USER, ProfileKind.BOT -> listOf(
            ProfileAction(Icons.Filled.ChatBubble, "Чат", onOpenChat),
            ProfileAction(Icons.Filled.Call, "Звонок") { if (details.kind == ProfileKind.BOT) dial() else onCall(false) },
            ProfileAction(Icons.Filled.Videocam, "Видео") { if (details.kind == ProfileKind.BOT) dial() else onCall(true) },
            sound,
        )
        ProfileKind.GROUP -> listOf(
            ProfileAction(Icons.Filled.ChatBubble, "Чат", onOpenChat),
            sound,
            ProfileAction(Icons.AutoMirrored.Filled.ExitToApp, "Покинуть", onLeave),
        )
        ProfileKind.CHANNEL -> listOf(
            sound,
            ProfileAction(Icons.Filled.Share, "Ссылка", share),
            ProfileAction(Icons.AutoMirrored.Filled.ExitToApp, "Покинуть", onLeave),
        )
        ProfileKind.OTHER -> listOf(ProfileAction(Icons.Filled.ChatBubble, "Чат", onOpenChat))
    }
}

@Composable
private fun ProfileHeader(details: ProfileDetails, viewModel: ProfileViewModel, listState: LazyListState, entered: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .animateContentSize()
            // Параллакс: при прокрутке шапка уходит медленнее и затухает.
            .graphicsLayer {
                val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 600f
                val progress = (offset / 500f).coerceIn(0f, 1f)
                alpha = 1f - progress * 0.85f
                scaleX = 1f - progress * 0.08f
                scaleY = 1f - progress * 0.08f
                translationY = offset * 0.35f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val banner = if (details.kind == ProfileKind.USER) LocalYougramBanners.current[details.id] else null
        val custom = LocalOwnCustomBanner.current?.takeIf { details.kind == ProfileKind.USER && it.userId == details.id }
        if (banner != null || custom != null) {
            // Баннер видят только пользователи Yougram; аватар наполовину заходит на него.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (custom != null) {
                    CustomBannerImage(custom.version, Modifier.fillMaxWidth().height(150.dp).fadeInOnce(500))
                } else if (banner != null) {
                    ProfileBanner(banner, Modifier.fillMaxWidth().height(150.dp).fadeInOnce(500))
                }
                Box(
                    Modifier
                        .padding(top = 90.dp)
                        .popIn(entered)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                ) {
                    FileAvatar(
                        title = details.title,
                        fileId = details.avatarFileId,
                        fileState = viewModel::fileState,
                        size = 100.dp,
                    )
                }
            }
        } else {
            Box(Modifier.popIn(entered)) {
                FileAvatar(
                    title = details.title,
                    fileId = details.avatarFileId,
                    fileState = viewModel::fileState,
                    size = 96.dp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                details.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            YougramBadge(details.chatId, Modifier.padding(start = 6.dp), size = 20.dp)
        }
        // Статус («в сети», «был(а) недавно») плавно сменяется без скачка.
        Crossfade(details.subtitle, label = "subtitle") { subtitle ->
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Действия — сегментированная группа строк, как в настройках. */
@Composable
private fun ActionsGroup(actions: List<ProfileAction>) {
    SettingGroup {
        actions.forEach { action ->
            item { SettingRow(action.label, icon = action.icon, onClick = action.onClick) }
        }
    }
}

@Composable
private fun InfoGroup(details: ProfileDetails, context: Context, onDossier: () -> Unit) {
    val isUser = details.kind == ProfileKind.USER || details.kind == ProfileKind.BOT
    val others = details.otherUsernames.joinToString(", ") { "@$it" }
    val rows = buildList {
        if (details.description.isNotEmpty()) {
            add(InfoRowData(details.description, if (isUser) "О себе" else "Описание", null, false))
        }
        details.phone?.let { add(InfoRowData(it, "Телефон", null, true)) }
        if (isUser) {
            details.username?.let {
                val label = "Имя пользователя" + if (others.isNotEmpty()) " · также $others" else ""
                add(InfoRowData("@$it", label, Icons.Filled.QrCode2, true))
            }
        } else {
            details.link?.let {
                val label = "Ссылка-приглашение" + if (others.isNotEmpty()) " · также $others" else ""
                add(InfoRowData(it, label, Icons.Filled.QrCode2, true))
            }
        }
        add(InfoRowData(details.id.toString(), "ID", null, true))
    }
    val showDossierRow = details.kind == ProfileKind.USER
    SectionLabel(if (isUser) "О пользователе" else "Информация")
    SettingGroup {
        rows.forEach { row ->
            item {
                SettingRow(
                    title = row.value,
                    subtitle = row.label,
                    onClick = if (row.copyable) ({ copyText(context, row.value) }) else null,
                    trailing = row.trailing?.let { icon ->
                        { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    },
                )
            }
        }
        if (showDossierRow) {
            item { SettingRow(title = "Досье", subtitle = "Всё, что известно клиенту об этом контакте", onClick = onDossier) }
        }
    }
}

private class InfoRowData(val value: String, val label: String, val trailing: ImageVector?, val copyable: Boolean)

/** Удалённые сообщения из архива шпиона. */
@Composable
private fun DeletedGroup(messages: List<MessageItem>) {
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    SettingGroup {
        messages.take(100).forEach { message ->
            item {
                SettingRow(
                    title = message.text.ifEmpty { message.summary }.ifEmpty { "Сообщение" },
                    subtitle = format.format(Date(message.date * 1000L)) + if (message.isOutgoing) " · вы" else "",
                )
            }
        }
    }
}

/** Переключатель вкладок: «пилюля» плавно переезжает на выбранную вкладку, цвет текста перетекает. */
@Composable
private fun TabSwitcher(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    val tabs = ProfileTab.entries
    val gap = 4.dp
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        BoxWithConstraints(Modifier.padding(4.dp)) {
            val tabWidth = (maxWidth - gap * (tabs.size - 1)) / tabs.size
            val pillOffset by animateDpAsState(
                targetValue = (tabWidth + gap) * selected.ordinal,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
                label = "tabPill",
            )
            Box(
                Modifier
                    .offset(x = pillOffset)
                    .width(tabWidth)
                    .height(40.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                tabs.forEach { tab ->
                    val textColor by animateColorAsState(
                        targetValue = if (tab == selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(220),
                        label = "tabText",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable { onSelect(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(tab.label, style = MaterialTheme.typography.labelLarge, color = textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text("Пока ничего нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MediaThumb(media: MediaItem, viewModel: ProfileViewModel, modifier: Modifier) {
    val previewId = media.previewFileId
    val preview by remember(previewId) {
        previewId?.let(viewModel::fileState) ?: flowOf(FileState())
    }.collectAsState(FileState())
    LaunchedEffect(previewId) { previewId?.let { viewModel.download(it, 8) } }
    val mini = remember(media.miniThumb) {
        media.miniThumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val bitmap = rememberFileBitmap(preview.path, 512)
    val bitmapAlpha by animateFloatAsState(if (bitmap != null) 1f else 0f, tween(300), label = "thumbAlpha")
    Box(
        modifier
            .clip4()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        mini?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        bitmap?.let { Image(it, null, Modifier.fillMaxSize().graphicsLayer { alpha = bitmapAlpha }, contentScale = ContentScale.Crop) }
        if (media.kind != MediaKind.PHOTO) {
            Box(
                Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Секция выезжает снизу с затуханием; [index] задаёт каскад. При [skip] показывается сразу. */
@Composable
private fun Appear(index: Int, skip: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (skip) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * 60L)
            progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 24.dp.toPx()
        },
    ) { content() }
}

/** Пружинное «вылетание» аватара из уменьшенного состояния. */
@Composable
private fun Modifier.popIn(skip: Boolean): Modifier {
    val scale = remember { Animatable(if (skip) 1f else 0.6f) }
    LaunchedEffect(Unit) {
        if (scale.value < 1f) scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        alpha = ((scale.value - 0.6f) / 0.25f).coerceIn(0f, 1f)
    }
}

/** Один раз плавно проявляет элемент при первом появлении. */
@Composable
private fun Modifier.fadeInOnce(durationMs: Int): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(durationMs)) }
    return this.graphicsLayer { this.alpha = alpha.value }
}

private fun Modifier.clip4(): Modifier = this.then(Modifier.clip(RoundedCornerShape(4.dp)))

@Composable
private fun FileRow(media: MediaItem, context: Context) {
    SettingGroup {
        item {
            SettingRow(
                title = media.name.ifEmpty { media.kind.label },
                subtitle = Formatter.formatShortFileSize(context, media.size),
                icon = Icons.Filled.Description,
            )
        }
    }
}

@Composable
private fun LinkRow(message: MessageItem, context: Context) {
    val openLink = LocalOpenLink.current
    val url = UrlRegex.find(message.text)?.value.orEmpty()
    val rest = message.text.replace(url, "").trim()
    SettingGroup {
        item {
            SettingRow(
                title = url,
                subtitle = rest.ifEmpty { null },
                onClick = { openLink(url) },
            )
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("", text))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    val full = if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(full)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения для открытия ссылки", Toast.LENGTH_SHORT).show()
    }
}