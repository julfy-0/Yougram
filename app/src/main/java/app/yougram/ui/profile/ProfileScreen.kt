package app.yougram.ui.profile

import app.yougram.ui.glass.SystemBarsGlass
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Videocam
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.data.FileState
import app.yougram.ui.YougramBadge
import app.yougram.data.MediaItem
import app.yougram.data.MediaKind
import app.yougram.data.MessageItem
import app.yougram.data.ProfileDetails
import app.yougram.data.ProfileKind
import app.yougram.ui.FileAvatar
import app.yougram.ui.rememberFileBitmap
import app.yougram.ui.settings.SectionLabel
import app.yougram.ui.settings.SettingGroup
import app.yougram.ui.settings.SettingRow
import java.text.DateFormat
import java.util.Date
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
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = topInset + 56.dp,
                    bottom = bottomInset + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { ProfileHeader(details, viewModel) }
                item { ActionsGroup(actions) }
                item { InfoGroup(details, context) }
                item { TabSwitcher(tab) { tab = it } }
                when {
                    state.sharedLoading && tab != ProfileTab.DELETED -> item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                    }
                    tab == ProfileTab.MEDIA -> {
                        if (media.isEmpty()) item { EmptyHint() }
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
                        if (files.isEmpty()) item { EmptyHint() }
                        files.forEach { message -> item(key = "f${message.id}") { FileRow(message.media!!, context) } }
                    }
                    tab == ProfileTab.LINKS -> {
                        if (links.isEmpty()) item { EmptyHint() }
                        links.forEach { message -> item(key = "l${message.id}") { LinkRow(message, context) } }
                    }
                    else -> {
                        if (state.deleted.isEmpty()) item { EmptyHint() }
                        else item(key = "deleted") { DeletedGroup(state.deleted) }
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
    rows.forEach { chunk -> item(key = "m${chunk.first().id}") { row(chunk) } }
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
private fun ProfileHeader(details: ProfileDetails, viewModel: ProfileViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileAvatar(
            title = details.title,
            fileId = details.avatarFileId,
            fileState = viewModel::fileState,
            size = 96.dp,
        )
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
        if (details.subtitle.isNotEmpty()) {
            Text(
                details.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
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
private fun InfoGroup(details: ProfileDetails, context: Context) {
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

@Composable
private fun TabSwitcher(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ProfileTab.entries.forEach { tab ->
                val active = tab == selected
                Surface(
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(tab.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
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
    Box(
        modifier
            .clip4()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        mini?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
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
    val url = UrlRegex.find(message.text)?.value.orEmpty()
    val rest = message.text.replace(url, "").trim()
    SettingGroup {
        item {
            SettingRow(
                title = url,
                subtitle = rest.ifEmpty { null },
                onClick = { openUrl(context, url) },
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