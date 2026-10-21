package app.yougram.feature.settings.general

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.AppLanguage
import app.yougram.core.i18n.Localization
import app.yougram.core.i18n.tr
import app.yougram.core.ui.component.TextButton
import app.yougram.feature.settings.component.ConfirmDialog
import app.yougram.feature.settings.component.EditDialog
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn

@Composable
fun LanguageScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val current = Localization.current
    val languages = Localization.languages
    var editing by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && Localization.importFrom(uri) == null) {
            Toast.makeText(context, tr("Не удалось прочитать файл локализации"), Toast.LENGTH_SHORT).show()
        }
    }

    editing?.let { code ->
        BackHandler { editing = null }
        TranslationEditor(code, contentPadding, onClose = { editing = null })
        return
    }

    SettingsPageColumn(contentPadding) {
        SectionLabel("Язык интерфейса")
        SettingGroup {
            languages.forEach { lang ->
                item {
                    SettingRow(
                        title = lang.name,
                        subtitle = if (lang.custom) {
                            tr("Своя локализация") + " · " + tr("основа") + ": " + Localization.nameOf(lang.base ?: Localization.SOURCE)
                        } else null,
                        icon = if (lang.code == current) Icons.Filled.Check else Icons.Filled.Language,
                        onClick = { Localization.select(lang.code) },
                        trailing = if (lang.custom) {
                            {
                                IconButton(onClick = { editing = lang.code }) {
                                    Icon(Icons.Filled.Edit, contentDescription = tr("Изменить"))
                                }
                            }
                        } else null,
                    )
                }
            }
        }

        SectionLabel("Своя локализация")
        SettingGroup {
            item {
                SettingRow(
                    "Создать локализацию",
                    subtitle = "На основе любого языка, со своим названием",
                    icon = Icons.Filled.Add,
                    onClick = { creating = true },
                )
            }
            item {
                SettingRow(
                    "Добавить из файла",
                    subtitle = "Файл JSON с переводом",
                    icon = Icons.Filled.Download,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            }
        }
        SettingsFootnote("Непереведённые строки показываются на русском. Свою локализацию можно изменить кнопкой с карандашом.")
    }

    if (creating) {
        CreateLanguageDialog(
            languages = languages,
            onCreate = { name, base ->
                creating = false
                editing = Localization.create(name, base)
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun CreateLanguageDialog(
    languages: List<AppLanguage>,
    onCreate: (name: String, base: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var base by remember { mutableStateOf(Localization.current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Новая локализация")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Название")) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                Text(
                    tr("На основе языка"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                languages.forEach { lang ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = base == lang.code, role = Role.RadioButton) { base = lang.code },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = base == lang.code, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(lang.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, base) }, enabled = name.isNotBlank()) { Text(tr("Создать")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } },
    )
}

@Composable
private fun TranslationEditor(code: String, contentPadding: PaddingValues, onClose: () -> Unit) {
    val lang = Localization.languages.firstOrNull { it.code == code }
    if (lang == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    var overrides by remember(code) { mutableStateOf(Localization.overrides(code)) }
    val baseTable = remember(code) { Localization.baseTable(code) }
    val allKeys = remember(code) { Localization.knownKeys() }
    var query by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf(40) }
    var editKey by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) Localization.exportTo(code, uri)
    }

    fun shownValue(key: String) = overrides[key] ?: baseTable[key] ?: key

    val filtered = remember(query, overrides, allKeys) {
        val keys = (allKeys + overrides.keys).distinct()
        if (query.isBlank()) keys
        else keys.filter { it.contains(query, ignoreCase = true) || shownValue(it).contains(query, ignoreCase = true) }
    }

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item {
                SettingRow(
                    "Название",
                    value = lang.name,
                    icon = Icons.Filled.Edit,
                    onClick = { renaming = true },
                )
            }
            item {
                SettingRow(
                    "Поделиться файлом",
                    subtitle = "Сохранить перевод в файл JSON",
                    icon = Icons.Filled.Share,
                    onClick = { exportLauncher.launch("${lang.name}.json") },
                )
            }
            item {
                SettingRow(
                    "Удалить локализацию",
                    icon = Icons.Filled.Delete,
                    onClick = { deleting = true },
                )
            }
        }

        SectionLabel("Строки")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; limit = 40 },
            label = { Text(tr("Поиск")) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingGroup {
            filtered.take(limit).forEach { key ->
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { editKey = key }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            shownValue(key),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (key in overrides) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (filtered.size > limit) {
                item { SettingRow("Показать ещё", icon = Icons.Filled.Add, onClick = { limit += 40 }) }
            }
        }
        SettingsFootnote("Здесь только строки, которые приложение уже показывало. Открывайте разные экраны, и список пополнится. Изменённые строки выделены цветом.")
    }

    editKey?.let { key ->
        EditDialog(
            title = "Перевод",
            labels = listOf(key),
            initial = listOf(shownValue(key)),
            onConfirm = { values ->
                val v = values.first().trim()
                overrides = if (v.isEmpty() || v == key) overrides - key else overrides + (key to v)
                Localization.saveStrings(code, overrides)
                editKey = null
            },
            onDismiss = { editKey = null },
        )
    }
    if (renaming) {
        EditDialog(
            title = "Название",
            labels = listOf("Название"),
            initial = listOf(lang.name),
            onConfirm = { Localization.rename(code, it.first()); renaming = false },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Удалить локализацию?",
            text = "Перевод будет удалён без возможности восстановления.",
            confirmLabel = "Удалить",
            onConfirm = { deleting = false; Localization.delete(code); onClose() },
            onDismiss = { deleting = false },
        )
    }
}
