package app.yougram.feature.settings.appearance

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.settings.ThemeMode
import app.yougram.core.ui.theme.Accents
import app.yougram.feature.settings.component.CheckSwitch
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Тема (нажатие переключает режим по кругу), системные цвета и выбор акцента. */
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
                val (label, next) = when (theme.mode) {
                    ThemeMode.System -> "Как в системе" to ThemeMode.Light
                    ThemeMode.Light -> "Светлая" to ThemeMode.Dark
                    ThemeMode.Dark -> "Тёмная" to ThemeMode.System
                }
                SettingRow(
                    title = "Тема",
                    subtitle = label,
                    onClick = { settings.setThemeMode(next) },
                )
            }
            item {
                SettingRow(
                    title = "Цвета системы",
                    subtitle = "Акцент берётся из обоев и настроек устройства",
                    onClick = { settings.setDynamicColor(!theme.dynamic) },
                    trailing = { CheckSwitch(theme.dynamic, settings::setDynamicColor) },
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
                        below = {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(Accents.size) { index ->
                                    Box(
                                        Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Accents[index].color)
                                            .clickable { settings.setAccent(index) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (index == theme.accent) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = Accents[index].name,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
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
