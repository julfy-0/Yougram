package app.yougram.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import app.yougram.core.ui.component.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.DataPrefs

/** Диалог прокси: используется и на экране входа, и в «Данные и память». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProxyDialog(
    initial: DataPrefs,
    onConfirm: (type: String, server: String, port: String, user: String, pass: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(initial.proxyType) }
    var server by remember { mutableStateOf(initial.proxyServer) }
    var port by remember { mutableStateOf(initial.proxyPort) }
    var user by remember { mutableStateOf(initial.proxyUser) }
    var pass by remember { mutableStateOf(initial.proxyPass) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Прокси") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            TextButton(onClick = {
                val validPort = if (port.toIntOrNull() in 1..65535) port else ""
                onConfirm(type, server.trim(), validPort, if (type == "mtproto") "" else user.trim(), pass)
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (initial.proxyServer.isNotBlank()) {
                    TextButton(onClick = { onConfirm(type, "", "", "", "") }) { Text("Отключить") }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}