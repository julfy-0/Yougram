package app.yougram.feature.settings.appearance

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.settings.ThemeMode
import app.yougram.core.ui.theme.Accents
import app.yougram.feature.settings.component.ConnectedChoiceGroup
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SwitchRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun ThemeMode.title(): String = when (this) {
    ThemeMode.System -> "Система"
    ThemeMode.Light -> "Светлая"
    ThemeMode.Dark -> "Тёмная"
}

/** Тема (группа кнопок), системные цвета и выбор акцента крупными формами. */
@Composable
fun ThemeSection(settings: SettingsRepository) {
    val theme by settings.theme.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { importFont(context, uri) }
            if (result == null) {
                Toast.makeText(context, "Не удалось открыть шрифт: нужен файл .ttf или .otf", Toast.LENGTH_LONG).show()
            } else {
                val old = theme.fontPath
                settings.setCustomFont(result.first, result.second)
                if (old != null && old != result.first) runCatching { File(old).delete() }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingGroup {
            item {
                SettingRow(
                    title = "Тема",
                    subtitle = when (theme.mode) {
                        ThemeMode.System -> "Как в системе"
                        ThemeMode.Light -> "Светлая"
                        ThemeMode.Dark -> "Тёмная"
                    },
                    icon = Icons.Filled.Palette,
                    belowFullWidth = true,
                    below = {
                        ConnectedChoiceGroup(
                            options = ThemeMode.entries,
                            selected = theme.mode,
                            label = { it.title() },
                            onSelect = settings::setThemeMode,
                        )
                    },
                )
            }
            item {
                SwitchRow(
                    title = "Цвета системы",
                    checked = theme.dynamic,
                    onChange = settings::setDynamicColor,
                    subtitle = "Акцент берётся из обоев и настроек устройства",
                )
            }
        }

        SettingGroup {
            item {
                SettingRow(
                    title = "Шрифт",
                    subtitle = theme.fontName ?: "Системный",
                    value = "Выбрать",
                    onClick = { fontPicker.launch(arrayOf("*/*")) },
                )
            }
            if (theme.fontPath != null) {
                item {
                    SettingRow(
                        title = "Вернуть системный шрифт",
                        onClick = {
                            theme.fontPath?.let { runCatching { File(it).delete() } }
                            settings.setCustomFont(null, null)
                        },
                    )
                }
            }
        }

        if (!theme.dynamic) {
            SettingGroup {
                item {
                    SettingRow(
                        title = "Акцентный цвет",
                        belowFullWidth = true,
                        below = {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(Accents.size, key = { it }) { index ->
                                    AccentSwatch(
                                        color = Accents[index].color,
                                        name = Accents[index].name,
                                        selected = index == theme.accent,
                                        onClick = { settings.setAccent(index) },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Образец акцента: выбранный плавно становится кругом, остальные — скруглённые квадраты. */
@Composable
private fun AccentSwatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    val corner: Dp by animateDpAsState(
        targetValue = if (selected) 32.dp else 20.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "accentCorner",
    )
    // Цвет значка — по яркости самого образца (сам образец берётся из списка акцентов, а не из темы).
    val checkColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(corner.coerceAtLeast(0.dp)))
            .background(color)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = checkColor, modifier = Modifier.size(28.dp))
        }
    }
}

/** Копирует выбранный файл шрифта в хранилище приложения и проверяет, что это настоящий шрифт. Возвращает путь и имя. */
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