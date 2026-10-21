@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.profile.ui

import app.yougram.core.i18n.tr
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yougram.core.ui.component.Avatar
import app.yougram.core.ui.component.Button
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.core.ui.component.TextButton
import app.yougram.feature.chat.data.GroupPerms
import app.yougram.feature.chat.data.ProfileDetails
import app.yougram.feature.chat.data.ProfileKind
import app.yougram.feature.chat.data.UsernameCheck
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EditUsernameRegex = Regex("^[A-Za-z][A-Za-z0-9_]{4,31}$")
private const val EditUsernameHint = "От 5 до 32 символов: латиница, цифры и «_», первая — буква"

@Composable
private fun editFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

/** Редактирование группы или канала: название, описание, фото и (для создателя) публичный адрес. */
@Composable
fun EditChatDialog(
    details: ProfileDetails,
    viewModel: ProfileViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val channel = details.kind == ProfileKind.CHANNEL
    val self = details.isSelf
    val descriptionLimit = if (self) 55 else 255
    val scheme = MaterialTheme.colorScheme
    val cookie = MaterialShapes.Cookie9Sided.toShape()

    var title by remember { mutableStateOf(details.title) }
    var description by remember { mutableStateOf(details.description) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val originalUsername = details.username.orEmpty()
    var isPublic by remember { mutableStateOf(originalUsername.isNotEmpty()) }
    var username by remember { mutableStateOf(originalUsername) }
    var usernameCheck by remember { mutableStateOf<UsernameCheck?>(null) }

    var perms by remember { mutableStateOf<GroupPerms?>(null) }
    var origPerms by remember { mutableStateOf<GroupPerms?>(null) }
    var link by remember { mutableStateOf(details.link) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isGroup = details.kind == ProfileKind.GROUP
    LaunchedEffect(Unit) {
        if (isGroup) viewModel.loadPerms()?.let { perms = it; origPerms = it }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val file = File(context.cacheDir, "chat_photo_${System.currentTimeMillis()}.jpg")
            val ok = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                } != null
            }.getOrDefault(false)
            if (ok) {
                photoPath?.let { File(it).delete() }
                photoPath = file.absolutePath
                removePhoto = false
            } else {
                Toast.makeText(context, tr("Не удалось прочитать изображение"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val effectiveUsername = if (isPublic) username else ""
    val usernameChanged = details.canSetUsername && effectiveUsername != originalUsername
    val usernameValid = EditUsernameRegex.matches(username)
    LaunchedEffect(username, isPublic) {
        usernameCheck = null
        if (details.canSetUsername && isPublic && username != originalUsername && usernameValid) {
            delay(500)
            usernameCheck = viewModel.checkUsername(username)
        }
    }

    val usernameOk = !details.canSetUsername || !isPublic || username == originalUsername ||
            usernameCheck == UsernameCheck.Ok
    val permsChanged = perms != origPerms
    val changed = permsChanged || title.trim() != details.title || description.trim() != details.description ||
            photoPath != null || removePhoto || usernameChanged
    val canSave = title.isNotBlank() && !busy && changed && usernameOk &&
            !(details.canSetUsername && isPublic && !usernameValid)

    val close = {
        photoPath?.let { File(it).delete() }
        onDismiss()
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (channel) "Удалить канал?" else "Удалить группу?") },
            text = { Text("Чат и вся переписка будут удалены для всех участников. Это нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteChat()
                    onDismiss()
                }) { Text("Удалить", color = scheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    Dialog(
        onDismissRequest = { if (!busy) close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) {
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = top, start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (!busy) close() }) {
                        Icon(Icons.Filled.Close, contentDescription = tr("Закрыть"))
                    }
                    Text(
                        if (self) "Изменить профиль" else if (channel) "Изменить канал" else "Изменить группу",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(120.dp)) {
                            Box(
                                Modifier
                                    .size(120.dp)
                                    .clip(cookie)
                                    .background(scheme.primaryContainer)
                                    .clickable {
                                        picker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    photoPath != null -> Avatar(title = title, path = photoPath, size = 120.dp, shape = cookie)
                                    !removePhoto && details.avatarFileId != null -> FileAvatar(
                                        title = title,
                                        fileId = details.avatarFileId,
                                        fileState = viewModel::fileState,
                                        size = 120.dp,
                                        shape = cookie,
                                    )
                                    else -> Icon(
                                        Icons.Filled.CameraAlt,
                                        contentDescription = tr("Выбрать фото"),
                                        modifier = Modifier.size(40.dp),
                                        tint = scheme.onPrimaryContainer,
                                    )
                                }
                            }
                            val hasPhoto = photoPath != null || (!removePhoto && details.avatarFileId != null)
                            if (hasPhoto && !self) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(scheme.secondaryContainer)
                                        .clickable {
                                            photoPath?.let { File(it).delete() }
                                            photoPath = null
                                            removePhoto = true
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = tr("Убрать фото"),
                                        modifier = Modifier.size(20.dp),
                                        tint = scheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }

                    SettingGroup {
                        item {
                            TextField(
                                value = title,
                                onValueChange = { if (it.length <= 128) title = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(if (self) "Имя и фамилия" else if (channel) "Название канала" else "Название группы") },
                                colors = editFieldColors(),
                            )
                        }
                        item {
                            TextField(
                                value = description,
                                onValueChange = { if (it.length <= descriptionLimit) description = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 5,
                                label = { Text(if (self) "О себе" else "Описание") },
                                supportingText = { Text("${description.length}/$descriptionLimit") },
                                colors = editFieldColors(),
                            )
                        }
                    }

                    if (details.canSetUsername) {
                        SectionLabel(if (channel) "Тип канала" else "Тип группы")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !isPublic,
                                onClick = { isPublic = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Приватный") }
                            SegmentedButton(
                                selected = isPublic,
                                onClick = { isPublic = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Публичный") }
                        }
                        if (isPublic) {
                            val status = when {
                                username.isEmpty() || !usernameValid -> EditUsernameHint
                                username == originalUsername -> "Текущий адрес"
                                usernameCheck == null -> "Проверяем адрес…"
                                usernameCheck == UsernameCheck.Ok -> "Адрес свободен"
                                usernameCheck == UsernameCheck.Occupied -> "Адрес уже занят"
                                usernameCheck == UsernameCheck.Invalid -> "Недопустимый адрес"
                                usernameCheck == UsernameCheck.TooMany ->
                                    "Слишком много публичных чатов, сделайте чат приватным"
                                else -> "Этот адрес сейчас недоступен"
                            }
                            val bad = username.isNotEmpty() && username != originalUsername &&
                                    (!usernameValid || (usernameCheck != null && usernameCheck != UsernameCheck.Ok))
                            SettingGroup {
                                item {
                                    TextField(
                                        value = username,
                                        onValueChange = { v ->
                                            username = v
                                                .filter { (it.isLetterOrDigit() && it.code < 128) || it == '_' }
                                                .take(32)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        prefix = { Text("t.me/") },
                                        label = { Text("Адрес") },
                                        isError = bad,
                                        supportingText = { Text(status) },
                                        colors = editFieldColors(),
                                    )
                                }
                            }
                        }
                    }
                    perms?.let { p ->
                        SectionLabel("Что разрешено участникам")
                        SettingGroup {
                            item { PermRow("Отправлять сообщения", p.sendMessages) { perms = p.copy(sendMessages = it) } }
                            item { PermRow("Отправлять опросы", p.sendPolls) { perms = p.copy(sendPolls = it) } }
                            item { PermRow("Стикеры, GIF и боты", p.sendOther) { perms = p.copy(sendOther = it) } }
                            item { PermRow("Превью ссылок", p.linkPreviews) { perms = p.copy(linkPreviews = it) } }
                            item { PermRow("Добавлять участников", p.inviteUsers) { perms = p.copy(inviteUsers = it) } }
                            item { PermRow("Закреплять сообщения", p.pinMessages) { perms = p.copy(pinMessages = it) } }
                            item { PermRow("Менять информацию", p.changeInfo) { perms = p.copy(changeInfo = it) } }
                        }
                    }

                    link?.let { l ->
                        SectionLabel("Пригласительная ссылка")
                        SettingGroup {
                            item {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text("https://$l", style = MaterialTheme.typography.bodyLarge)
                                    Row {
                                        TextButton(onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("link", "https://$l"))
                                            Toast.makeText(context, tr("Ссылка скопирована"), Toast.LENGTH_SHORT).show()
                                        }) { Text("Копировать") }
                                        if (details.username == null) {
                                            TextButton(onClick = {
                                                scope.launch {
                                                    runCatching { viewModel.revokeLink() }
                                                        .onSuccess { link = it }
                                                        .onFailure {
                                                            Toast.makeText(context, tr("Не удалось обновить ссылку"), Toast.LENGTH_SHORT).show()
                                                        }
                                                }
                                            }) { Text("Сбросить") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (details.isCreator) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (channel) "Удалить канал" else "Удалить группу", color = scheme.error) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val newPerms = perms
                                if (permsChanged && newPerms != null) {
                                    runCatching { viewModel.savePerms(newPerms) }.onFailure {
                                        Toast.makeText(context, tr("Не удалось сохранить права"), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                val warnings = viewModel.saveEdit(
                                    title = title.trim().takeIf { it != details.title },
                                    description = description.trim().takeIf { it != details.description },
                                    username = if (usernameChanged) effectiveUsername else null,
                                    photoPath = photoPath,
                                    removePhoto = removePhoto,
                                )
                                if (warnings.isNotEmpty()) {
                                    Toast.makeText(context, warnings.joinToString("\n"), Toast.LENGTH_LONG).show()
                                }
                                photoPath?.let { File(it).delete() }
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message ?: tr("Не удалось сохранить"), Toast.LENGTH_LONG).show()
                                busy = false
                            }
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottom + 12.dp)
                        .height(56.dp),
                ) {
                    if (busy) {
                        LoadingIndicator(Modifier.size(24.dp), color = scheme.onPrimary)
                    } else {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}