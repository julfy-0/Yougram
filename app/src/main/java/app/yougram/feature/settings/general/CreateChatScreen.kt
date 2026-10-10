@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.settings.general

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.Avatar
import app.yougram.core.ui.component.Button
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.chat.data.ContactItem
import app.yougram.feature.chat.data.UsernameCheck
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val UsernameRegex = Regex("^[A-Za-z][A-Za-z0-9_]{4,31}$")
private const val UsernameHint = "От 5 до 32 символов: латиница, цифры и «_», первая — буква"

/** Поле Material 3 без собственного фона и линии: фон даёт сегмент группы. */
@Composable
private fun flatFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
)

/** Создание группы (название, фото, описание, участники) или канала (название, фото, описание, тип и адрес). */
@Composable
fun CreateChatScreen(
    channel: Boolean,
    repository: ChatRepository,
    contentPadding: PaddingValues,
    onCreated: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    var isPublic by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var usernameCheck by remember { mutableStateOf<UsernameCheck?>(null) }

    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }

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
            } else {
                Toast.makeText(context, "Не удалось прочитать изображение", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(channel) {
        if (!channel) {
            contacts = runCatching { repository.loadContacts().sortedBy { it.name.lowercase() } }
                .getOrDefault(emptyList())
        }
    }

    val usernameValid = UsernameRegex.matches(username)
    LaunchedEffect(username, isPublic) {
        usernameCheck = null
        if (channel && isPublic && usernameValid) {
            delay(500)
            usernameCheck = runCatching { repository.checkNewChatUsername(username) }.getOrNull()
        }
    }

    val canCreate = title.isNotBlank() && !busy &&
        (!channel || !isPublic || usernameCheck == UsernameCheck.Ok)

    val scheme = MaterialTheme.colorScheme
    val cookie = MaterialShapes.Cookie9Sided.toShape()

    SettingsPageColumn(contentPadding) {
        // Фото: крупная «печенька» Material 3 Expressive; бейдж справа внизу убирает фото.
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(cookie)
                        .background(scheme.primaryContainer)
                        .clickable {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (photoPath != null) {
                        Avatar(
                            title = title.ifBlank { if (channel) "Канал" else "Группа" },
                            path = photoPath,
                            size = 120.dp,
                            shape = cookie,
                        )
                    } else {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Выбрать фото",
                            modifier = Modifier.size(40.dp),
                            tint = scheme.onPrimaryContainer,
                        )
                    }
                }
                if (photoPath != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(scheme.secondaryContainer)
                            .clickable {
                                photoPath?.let { File(it).delete() }
                                photoPath = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Убрать фото",
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
                    label = { Text(if (channel) "Название канала" else "Название группы") },
                    colors = flatFieldColors(),
                )
            }
            item {
                TextField(
                    value = description,
                    onValueChange = { if (it.length <= 255) description = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    label = { Text("Описание (необязательно)") },
                    supportingText = { Text("${description.length}/255") },
                    colors = flatFieldColors(),
                )
            }
        }

        if (channel) {
            SectionLabel("Тип канала")
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
                    username.isEmpty() || !usernameValid -> UsernameHint
                    usernameCheck == null -> "Проверяем адрес…"
                    usernameCheck == UsernameCheck.Ok -> "Адрес свободен"
                    usernameCheck == UsernameCheck.Occupied -> "Адрес уже занят"
                    usernameCheck == UsernameCheck.Invalid -> "Недопустимый адрес"
                    usernameCheck == UsernameCheck.TooMany -> "Слишком много публичных чатов, сделайте канал приватным"
                    else -> "Этот адрес сейчас недоступен"
                }
                val bad = username.isNotEmpty() &&
                    (!usernameValid || (usernameCheck != null && usernameCheck != UsernameCheck.Ok))
                SettingGroup {
                    item {
                        TextField(
                            value = username,
                            onValueChange = { v ->
                                username = v.filter { (it.isLetterOrDigit() && it.code < 128) || it == '_' }.take(32)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            prefix = { Text("t.me/") },
                            label = { Text("Адрес канала") },
                            isError = bad,
                            supportingText = { Text(status) },
                            colors = flatFieldColors(),
                        )
                    }
                }
            } else {
                SettingsFootnote("В приватный канал можно вступить только по пригласительной ссылке.")
            }
        } else {
            SectionLabel("Участники" + if (selected.isNotEmpty()) " · ${selected.size}" else "")
            SettingGroup {
                item {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Поиск по контактам") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        colors = flatFieldColors(),
                    )
                }
            }
            val shown = remember(contacts, query) {
                contacts.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            }
            if (shown.isNotEmpty()) {
                SettingGroup {
                    shown.take(60).forEach { c ->
                        item {
                            val checked = c.id in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = checked,
                                        role = Role.Checkbox,
                                        onValueChange = { selected = if (it) selected + c.id else selected - c.id },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FileAvatar(title = c.name, fileId = c.avatarFileId, fileState = repository::fileState, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    c.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Checkbox(checked = checked, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
            when {
                shown.size > 60 -> SettingsFootnote("Показаны первые 60 контактов, уточните поиск")
                contacts.isEmpty() -> SettingsFootnote("Контактов пока нет, участников можно добавить позже.")
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        val res = if (channel) {
                            repository.createChannel(title, description, if (isPublic) username else null, photoPath)
                        } else {
                            repository.createGroup(title, description, selected.toList(), photoPath)
                        }
                        photoPath?.let { File(it).delete() }
                        if (res.warnings.isNotEmpty()) {
                            Toast.makeText(context, res.warnings.joinToString("\n"), Toast.LENGTH_LONG).show()
                        }
                        onCreated(res.chatId)
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Не удалось создать", Toast.LENGTH_LONG).show()
                        busy = false
                    }
                }
            },
            enabled = canCreate,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (busy) {
                LoadingIndicator(Modifier.size(24.dp), color = scheme.onPrimary)
            } else {
                Text(if (channel) "Создать канал" else "Создать группу")
            }
        }
    }
}
