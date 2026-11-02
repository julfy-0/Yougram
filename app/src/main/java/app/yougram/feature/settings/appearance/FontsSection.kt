package app.yougram.feature.settings.appearance

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.tr
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.ui.theme.FontRole
import app.yougram.core.ui.theme.FontStore
import app.yougram.core.ui.theme.RoleFont
import app.yougram.feature.settings.component.ConnectedChoiceGroup
import app.yougram.feature.settings.component.DotSlider
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SwitchRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun weightLabel(shift: Int?): String = when (shift) {
    null -> tr("Как везде")
    -200 -> tr("Тонкий")
    0 -> tr("Обычный")
    100 -> tr("Плотный")
    else -> tr("Жирный")
}

private fun roundLabel(value: Int?): String = when (value) {
    null -> tr("Как везде")
    0 -> tr("Нет")
    50 -> tr("Средне")
    else -> tr("Максимум")
}

private fun italicLabel(value: Boolean?): String = when (value) {
    null -> tr("Как везде")
    false -> tr("Прямой")
    true -> tr("Курсив")
}

/** Вкладка «Шрифты»: общий шрифт приложения и отдельные настройки для заголовков, навбара, сообщений и т.д. */
@Composable
fun FontsSection(settings: SettingsRepository) {
    val theme by settings.theme.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var globalDialog by remember { mutableStateOf(false) }
    var roleDialog by remember { mutableStateOf<FontRole?>(null) }

    if (globalDialog) {
        GoogleFontPickerDialog(
            selected = theme.googleFont,
            onSelect = { name ->
                theme.fontPath?.let { runCatching { File(it).delete() } }
                settings.setGoogleFont(name)
                globalDialog = false
            },
            onDismiss = { globalDialog = false },
        )
    }
    roleDialog?.let { role ->
        GoogleFontPickerDialog(
            selected = theme.roleFonts[role]?.googleFont,
            onSelect = { name ->
                val cur = theme.roleFonts[role] ?: RoleFont()
                settings.setRoleFont(role, cur.copy(googleFont = name))
                roleDialog = null
            },
            onDismiss = { roleDialog = null },
        )
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { importFont(context, uri) }
            if (result == null) {
                Toast.makeText(context, tr("Не удалось открыть шрифт: нужен файл .ttf или .otf"), Toast.LENGTH_LONG).show()
            } else {
                val old = theme.fontPath
                settings.setCustomFont(result.first, result.second)
                if (old != null && old != result.first) runCatching { File(old).delete() }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Шрифт приложения")
        SettingGroup {
            item {
                SettingRow(
                    title = "Шрифт из Google Fonts",
                    subtitle = theme.googleFont ?: "Не выбран",
                    value = "Выбрать",
                    onClick = { globalDialog = true },
                )
            }
            item {
                SettingRow(
                    title = "Шрифт из файла",
                    subtitle = theme.fontName ?: "Файл .ttf или .otf",
                    value = "Выбрать",
                    onClick = { fontPicker.launch(arrayOf("*/*")) },
                )
            }
            if (theme.fontPath != null || theme.googleFont != null) {
                item {
                    SettingRow(
                        title = "Вернуть системный шрифт",
                        onClick = {
                            theme.fontPath?.let { runCatching { File(it).delete() } }
                            settings.setCustomFont(null, null)
                            settings.setGoogleFont(null)
                        },
                    )
                }
            }
            item {
                val revision = FontStore.revision
                val size = remember(revision) { FontStore.sizeBytes(context) }
                SettingRow(
                    title = "Скачанные шрифты",
                    subtitle = if (size == 0L) "Пока ничего не скачано" else "%.1f МБ в памяти приложения".format(size / 1048576.0),
                    value = "Очистить",
                    onClick = {
                        val keep = listOfNotNull(theme.googleFont) + theme.roleFonts.values.mapNotNull { it.googleFont }
                        FontStore.clearExcept(context, keep)
                    },
                )
            }
            item {
                SettingRow(
                    title = "Жирность шрифта",
                    belowFullWidth = true,
                    below = {
                        ConnectedChoiceGroup(
                            options = listOf(-200, 0, 100, 200),
                            selected = theme.fontWeightShift,
                            label = { weightLabel(it) },
                            onSelect = settings::setFontWeightShift,
                        )
                    },
                )
            }
            item {
                SettingRow(
                    title = "Скругление шрифта",
                    subtitle = if (theme.fontRoundness == 0) "Выключено" else "${theme.fontRoundness}%",
                    belowFullWidth = true,
                    below = {
                        DotSlider(
                            value = theme.fontRoundness.toFloat(),
                            onValueChange = { settings.setFontRoundness(it.toInt()) },
                            valueRange = 0f..100f,
                        )
                    },
                )
            }
            item {
                SwitchRow(
                    title = "Курсив",
                    checked = theme.fontItalic,
                    onChange = settings::setFontItalic,
                    subtitle = "Весь текст в приложении курсивом",
                )
            }
        }

        SettingsFootnote("Шрифты из Google Fonts скачиваются в память приложения только когда вы их выбираете или видите в списке; «Очистить» удаляет те, что сейчас не используются. Скругление шрифта работает у Google Sans Flex (он встроен в приложение) и у своих вариативных шрифтов с осью ROND; у остальных ничего не меняется. Ниже можно задать свой шрифт, жирность и курсив для отдельных элементов. «Как везде» — берётся общая настройка.")

        FontRole.entries.forEach { role ->
            val rf = theme.roleFonts[role] ?: RoleFont()
            SectionLabel(role.title)
            SettingGroup {
                item {
                    SettingRow(
                        title = "Шрифт",
                        subtitle = rf.googleFont ?: "Как везде",
                        value = "Выбрать",
                        onClick = { roleDialog = role },
                    )
                }
                item {
                    SettingRow(
                        title = "Жирность",
                        belowFullWidth = true,
                        below = {
                            ConnectedChoiceGroup(
                                options = listOf<Int?>(null, -200, 0, 100, 200),
                                selected = rf.weightShift,
                                label = { weightLabel(it) },
                                onSelect = { settings.setRoleFont(role, rf.copy(weightShift = it)) },
                            )
                        },
                    )
                }
                item {
                    SettingRow(
                        title = "Скругление",
                        belowFullWidth = true,
                        below = {
                            ConnectedChoiceGroup(
                                options = listOf<Int?>(null, 0, 50, 100),
                                selected = rf.roundness,
                                label = { roundLabel(it) },
                                onSelect = { settings.setRoleFont(role, rf.copy(roundness = it)) },
                            )
                        },
                    )
                }
                item {
                    SettingRow(
                        title = "Начертание",
                        belowFullWidth = true,
                        below = {
                            ConnectedChoiceGroup(
                                options = listOf<Boolean?>(null, false, true),
                                selected = rf.italic,
                                label = { italicLabel(it) },
                                onSelect = { settings.setRoleFont(role, rf.copy(italic = it)) },
                            )
                        },
                    )
                }
                if (!rf.isDefault) {
                    item {
                        SettingRow(
                            title = "Сбросить",
                            subtitle = role.hint,
                            onClick = { settings.setRoleFont(role, RoleFont()) },
                        )
                    }
                }
            }
        }
    }
}

private fun importFont(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
        ?: "Свой шрифт"
    val dir = File(context.filesDir, "fonts").apply { mkdirs() }
    val target = File(dir, "custom-${System.currentTimeMillis()}.ttf")
    context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
    try {
        android.graphics.fonts.Font.Builder(target).build()
    } catch (e: Exception) {
        target.delete()
        throw e
    }
    target.absolutePath to name
}.getOrNull()
