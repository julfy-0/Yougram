package app.yougram.feature.settings.privacy

import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yougram.feature.account.data.SessionItem
import app.yougram.feature.settings.SettingsDetailsViewModel
import app.yougram.feature.settings.component.ConfirmDialog
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import java.text.DateFormat
import java.util.Date

@Composable
fun DevicesScreen(viewModel: SettingsDetailsViewModel, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadSessions() }
    var toTerminate by remember { mutableStateOf<SessionItem?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    val sessions = state.sessions
    val current = sessions?.firstOrNull { it.current }
    val others = sessions.orEmpty().filter { !it.current }

    SettingsPageColumn(contentPadding) {
        if (sessions == null) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        } else {
            LinkDeviceCard(onScan = { scanning = true })
            if (current != null) {
                SectionLabel("Это устройство")
                SettingGroup { item { SessionRow(current, onClick = null) } }
            }
            if (others.isNotEmpty()) {
                SectionLabel("Другие сеансы")
                SettingGroup {
                    others.forEach { session -> item { SessionRow(session) { toTerminate = session } } }
                }
                SettingGroup {
                    item {
                        SettingRow(
                            "Завершить все другие сеансы",
                            icon = Icons.Filled.Delete,
                            onClick = { confirmAll = true },
                        )
                    }
                }
                SettingsFootnote("Нажмите на сеанс, чтобы завершить его.")
            }
        }
    }

    if (scanning) {
        QrScannerDialog(
            onResult = { link ->
                scanning = false
                viewModel.linkDevice(link)
            },
            onDismiss = { scanning = false },
        )
    }

    toTerminate?.let { session ->
        ConfirmDialog(
            title = "Завершить сеанс?",
            text = session.title.ifEmpty { session.subtitle },
            confirmLabel = "Завершить",
            onConfirm = {
                viewModel.terminateSession(session.id)
                toTerminate = null
            },
            onDismiss = { toTerminate = null },
        )
    }
    if (confirmAll) {
        ConfirmDialog(
            title = "Завершить все другие сеансы?",
            text = "На всех остальных устройствах нужно будет войти заново.",
            confirmLabel = "Завершить",
            onConfirm = {
                viewModel.terminateOtherSessions()
                confirmAll = false
            },
            onDismiss = { confirmAll = false },
        )
    }
}

@Composable
private fun SessionRow(session: SessionItem, onClick: (() -> Unit)?) {
    val last = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(session.lastActive * 1000L))
    SettingRow(
        title = session.title.ifEmpty { "Сеанс" },
        subtitle = listOf(session.subtitle, last).filter { it.isNotEmpty() }.joinToString("\n"),
        icon = Icons.Filled.Laptop,
        onClick = onClick,
    )
}