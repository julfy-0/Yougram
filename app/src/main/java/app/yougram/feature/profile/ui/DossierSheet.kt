package app.yougram.feature.profile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import app.yougram.core.ui.component.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.feature.chat.data.UserDossier
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow

/** Нижняя панель «Досье»: сводка по контакту из данных, которые Telegram уже отдал клиенту. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DossierSheet(viewModel: ProfileViewModel, onDismiss: () -> Unit) {
    val dossier by viewModel.dossier.collectAsState()
    val loading by viewModel.dossierLoading.collectAsState()
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Досье", modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
        val d = dossier
        if (d == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                if (loading) LoadingIndicator(Modifier.size(32.dp))
                else Text("Нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val rows = remember(d) { rowsOf(d) }
            Column(Modifier.navigationBarsPadding()) {
                LazyColumn(
                    Modifier.heightIn(max = 520.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    item {
                        SettingGroup {
                            rows.forEach { (label, value) ->
                                item { SettingRow(title = value, subtitle = label, onClick = { copy(context, value) }) }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { copy(context, rows.joinToString("\n") { (l, v) -> "$l: $v" }, "Досье скопировано") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                ) { Text("Скопировать всё") }
            }
        }
    }
}

private fun rowsOf(d: UserDossier): List<Pair<String, String>> = buildList {
    add("Имя" to d.name.ifEmpty { "—" })
    add("ID" to d.id.toString())
    if (d.usernames.isNotEmpty()) add("Имя пользователя" to d.usernames.joinToString(", ") { "@$it" })
    add("Телефон" to (d.phone ?: "скрыт"))
    if (d.bio.isNotEmpty()) add("О себе" to d.bio)
    add("В контактах" to if (d.isContact) "да" else "нет")
    add("Взаимный контакт" to if (d.isMutualContact) "да" else "нет")
    add("Telegram Premium" to if (d.isPremium) "да" else "нет")
    add("Клиент Yougram" to if (d.isYougram) "да" else "не определён")
    if (d.isYougram) add("Версия клиента" to (d.clientVersion?.let { "v$it" } ?: "старая (до 0.9.3)"))
    if (d.isBot) add("Тип" to "бот")
    add("Общие группы" to if (d.commonGroupsCount == 0) "нет" else "${d.commonGroupsCount}" +
            if (d.commonGroups.isNotEmpty()) ": " + d.commonGroups.joinToString(", ") else "")
    add("Удалённых сообщений в архиве" to d.deletedMessages.toString())
}

private fun copy(context: Context, text: String, toast: String = "Скопировано") {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("dossier", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}