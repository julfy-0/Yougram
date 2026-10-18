package app.yougram.feature.settings.extras

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.TextButton
import app.yougram.plugin.InstalledPlugin
import app.yougram.plugin.NativePluginManager

/** Диалог настроек плагина: поля строятся по схеме из manifest.json, «Сохранить» отправляет плагину settings_changed. */
@Composable
fun PluginSettingsDialog(plugin: InstalledPlugin, manager: NativePluginManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manifest = plugin.manifest
    val drafts = remember(manifest.id) {
        mutableStateMapOf<String, String>().apply {
            manifest.settings.forEach { put(it.key, manager.settings.raw(manifest, it.key)) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(manifest.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                manifest.settings.forEach { setting ->
                    PluginSettingField(setting, drafts[setting.key].orEmpty()) { drafts[setting.key] = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { manager.applySettings(manifest.id, drafts.toMap()) }
                    .onSuccess { onDismiss() }
                    .onFailure { Toast.makeText(context, it.message ?: "Не удалось сохранить", Toast.LENGTH_LONG).show() }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
