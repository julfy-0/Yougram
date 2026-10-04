package app.yougram.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.yougram.data.SettingsRepository
import app.yougram.data.ThemeMode
import app.yougram.ui.theme.Accents

/** Тема (нажатие переключает режим по кругу), системные цвета и выбор акцента. */
@Composable
fun ThemeSection(settings: SettingsRepository) {
    val theme by settings.theme.collectAsState()

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