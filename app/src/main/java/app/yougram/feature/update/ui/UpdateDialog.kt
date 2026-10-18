package app.yougram.feature.update.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yougram.BuildConfig
import app.yougram.feature.update.data.AppUpdater
import app.yougram.feature.update.data.UpdateState

/**
 * Диалог «Обновление vX» при входе в приложение: список изменений, «Обновить» / «Позже».
 * «Обновить» запускает скачивание и открывает страницу обновления с прогрессом.
 */
@Composable
fun UpdateAvailableDialog(
    updater: AppUpdater,
    onOpenUpdatePage: () -> Unit,
) {
    val state by updater.state.collectAsState()
    val postponed by updater.postponedVersion.collectAsState()

    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Ready -> s.info
        else -> return
    }
    if (postponed == info.versionName) return

    val lines = remember(info) {
        info.notes.lines()
            .map { it.trim().trimStart('-', '*', '•', '·').trim() }
            .filter { it.isNotEmpty() }
    }

    AlertDialog(
        onDismissRequest = { updater.postpone() },
        title = { Text("Обновление ${info.versionName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Сейчас установлена v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Что изменилось:", style = MaterialTheme.typography.titleSmall)
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (lines.isEmpty()) {
                        Text("Список изменений не указан.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        lines.forEach { line ->
                            Row {
                                Text("•", color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (state is UpdateState.Available) updater.download()
                onOpenUpdatePage()
            }) { Text(if (state is UpdateState.Ready) "Установить" else "Обновить") }
        },
        dismissButton = { TextButton(onClick = { updater.postpone() }) { Text("Позже") } },
    )
}
