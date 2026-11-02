package app.yougram.feature.settings.extras

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.ui.component.TextButton
import app.yougram.plugin.ScanReport
import java.io.File

/** Пакет, который проверка сочла подозрительным и ждёт решения пользователя. */
class PendingPlugin(val file: File, val report: ScanReport)

@Composable
fun PluginScanDialog(pending: PendingPlugin, onInstall: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Плагин выглядит подозрительно") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Проверка нашла признаки, требующие внимания:")
                Spacer(Modifier.height(8.dp))
                Text(pending.report.details(), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("SHA-256: ${pending.report.sha256.take(16)}…", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Не устанавливать") } },
        dismissButton = { TextButton(onClick = onInstall) { Text("Всё равно установить") } },
    )
}
