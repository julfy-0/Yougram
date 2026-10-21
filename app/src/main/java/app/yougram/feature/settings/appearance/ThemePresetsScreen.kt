package app.yougram.feature.settings.appearance

import app.yougram.core.i18n.tr
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.BuiltInPresets
import app.yougram.core.settings.PlateRepository
import app.yougram.core.settings.SavedTheme
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.settings.ThemeLibrary
import app.yougram.core.settings.ThemeSnapshot
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.component.EditDialog

/** Темы: готовые пресеты, сохранение текущего оформления, обмен через буфер обмена. */
@Composable
fun ThemePresetsScreen(settings: SettingsRepository, plates: PlateRepository, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val library = remember { ThemeLibrary(context) }
    var saved by remember { mutableStateOf(library.load()) }
    var saveDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedTheme?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    SettingsPageColumn(contentPadding) {
        SectionLabel("Моя тема")
        SettingGroup {
            item {
                SettingRow(
                    title = "Сохранить текущую тему",
                    subtitle = "Акцент, режим, шрифт, стекло, подложки, порядок вкладок, оформление сообщений",
                    icon = Icons.Filled.Save,
                    onClick = { saveDialog = true },
                )
            }
            item {
                SettingRow(
                    title = "Вставить тему из буфера обмена",
                    subtitle = "Импорт темы, скопированной из Yougram",
                    icon = Icons.Filled.FileDownload,
                    onClick = {
                        val text = readClipboard(context)
                        val json = text?.let(ThemeSnapshot::parse)
                        if (json == null) toast(tr("В буфере нет темы Yougram"))
                        else {
                            saved = library.add("Импортированная тема", json)
                            toast(tr("Тема добавлена"))
                        }
                    },
                )
            }
        }

        if (saved.isNotEmpty()) {
            SectionLabel("Сохранённые темы")
            SettingGroup {
                saved.forEach { theme ->
                    item {
                        SettingRow(
                            title = theme.name,
                            subtitle = "Нажмите, чтобы применить",
                            icon = Icons.Filled.Palette,
                            onClick = {
                                ThemeSnapshot.apply(theme.data, settings, plates)
                                toast(tr("Тема «{0}» применена", theme.name))
                            },
                            trailing = {
                                Row {
                                    IconButton(onClick = { renaming = theme }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.Edit, tr("Переименовать"))
                                    }
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(context, theme.data.toString())
                                            toast(tr("Тема скопирована"))
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) { Icon(Icons.Filled.ContentCopy, tr("Копировать")) }
                                    IconButton(onClick = { saved = library.delete(theme.id) }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.Delete, tr("Удалить"))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }

        SectionLabel("Готовые пресеты")
        SettingGroup {
            BuiltInPresets.all.forEach { preset ->
                item {
                    SettingRow(
                        title = preset.name,
                        subtitle = preset.description,
                        icon = Icons.Filled.Palette,
                        onClick = {
                            ThemeSnapshot.apply(preset.data, settings, plates)
                            toast(tr("Пресет «{0}» применён", preset.name))
                        },
                    )
                }
            }
        }
    }

    if (saveDialog) {
        EditDialog(
            title = "Сохранить тему",
            labels = listOf("Название"),
            initial = listOf(tr("Моя тема {0}", saved.size + 1)),
            onConfirm = { values ->
                saved = library.add(values.first(), ThemeSnapshot.capture(settings, plates))
                saveDialog = false
                toast(tr("Тема сохранена"))
            },
            onDismiss = { saveDialog = false },
        )
    }
    renaming?.let { theme ->
        EditDialog(
            title = "Переименовать тему",
            labels = listOf("Название"),
            initial = listOf(theme.name),
            onConfirm = { values ->
                saved = library.rename(theme.id, values.first())
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Yougram theme", text))
}

private fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}
