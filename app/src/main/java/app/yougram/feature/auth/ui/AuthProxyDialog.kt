package app.yougram.feature.auth.ui

import app.yougram.core.i18n.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.ProxyEntry
import app.yougram.core.ui.component.Button
import app.yougram.core.ui.component.TextButton

/**
 * Список сохранённых прокси: выбор активного (радио-кнопка), правка, удаление, добавление нового.
 * Используется и на экране входа, и в «Данные и память».
 */
@Composable
internal fun ProxyManagerDialog(
    proxies: List<ProxyEntry>,
    activeId: String?,
    onSelect: (String?) -> Unit,
    onSave: (ProxyEntry) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<ProxyEntry?>(null) }
    var adding by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Прокси") },
        text = {
            Column(
                Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ProxyRow(
                    title = "Без прокси",
                    subtitle = null,
                    selected = activeId == null,
                    onSelect = { onSelect(null) },
                )
                proxies.forEach { p ->
                    ProxyRow(
                        title = p.title,
                        subtitle = p.typeLabel,
                        selected = p.id == activeId,
                        onSelect = { onSelect(p.id) },
                        onEdit = { editing = p },
                        onDelete = { onDelete(p.id) },
                    )
                }
                if (proxies.isEmpty()) {
                    Text(
                        "Сохранённых прокси пока нет. Добавьте первый — их можно переключать одним нажатием.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { adding = true }) { Text("Добавить прокси") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )

    if (adding) {
        ProxyEditDialog(
            initial = null,
            onConfirm = { entry ->
                onSave(entry)
                // Только что добавленный прокси сразу включаем.
                onSelect(entry.id)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
    editing?.let { current ->
        ProxyEditDialog(
            initial = current,
            onConfirm = { entry ->
                onSave(entry)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ProxyRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = tr("Изменить прокси"), modifier = Modifier.size(20.dp))
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = tr("Удалить прокси"),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Форма одного прокси: [initial] == null — новый. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProxyEditDialog(
    initial: ProxyEntry?,
    onConfirm: (ProxyEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(initial?.type ?: "socks5") }
    var server by remember { mutableStateOf(initial?.server.orEmpty()) }
    var port by remember { mutableStateOf(initial?.port.orEmpty()) }
    var user by remember { mutableStateOf(initial?.user.orEmpty()) }
    var pass by remember { mutableStateOf(initial?.pass.orEmpty()) }
    val valid = server.isNotBlank() && port.toIntOrNull() in 1..65535

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый прокси" else "Изменить прокси") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("socks5" to "SOCKS5", "http" to "HTTP", "mtproto" to "MTProto").forEach { (key, label) ->
                        FilterChip(selected = type == key, onClick = { type = key }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Сервер") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("Порт") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (type == "mtproto") {
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Секрет") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                } else {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Логин") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        ProxyEntry(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            type = type,
                            server = server.trim(),
                            port = port,
                            user = if (type == "mtproto") "" else user.trim(),
                            pass = pass,
                        ),
                    )
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}