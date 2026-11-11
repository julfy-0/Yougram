package app.yougram.feature.settings.appearance

import app.yougram.core.i18n.tr
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
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
import app.yougram.feature.settings.component.DotSlider
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SwitchRow
import app.yougram.feature.splash.ui.HalloweenEvent
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
    var customDialog by remember { mutableStateOf(false) }
    if (customDialog) {
        CustomAccentDialog(
            initial = if (theme.customAccent != 0) Color(theme.customAccent) else Accents[theme.accent.coerceIn(Accents.indices)].color,
            onDismiss = { customDialog = false },
            onConfirm = { settings.setCustomAccent(it.toArgb()); customDialog = false },
        )
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
            item {
                SwitchRow(
                    title = "Оформление под ивенты",
                    checked = HalloweenEvent.themingEnabled,
                    onChange = { HalloweenEvent.setThemingEnabled(context, it) },
                    subtitle = "Праздничные заставки, частицы и значки. Выключено — обычный MaterialGram",
                )
            }
        }

        SettingGroup {
            item {
                SettingRow(
                    title = "Скругление углов",
                    subtitle = "${(theme.cornerScale * 100).toInt()}%",
                    belowFullWidth = true,
                    below = {
                        DotSlider(
                            value = theme.cornerScale,
                            onValueChange = settings::setCornerScale,
                            valueRange = 0f..1.5f,
                        )
                    },
                )
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
                                        selected = theme.customAccent == 0 && index == theme.accent,
                                        onClick = { settings.setAccent(index) },
                                    )
                                }
                                // Свой цвет: открывает палитру.
                                item(key = "custom") {
                                    val custom = theme.customAccent
                                    AccentSwatch(
                                        color = if (custom != 0) Color(custom) else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        name = "Свой цвет",
                                        selected = custom != 0,
                                        onClick = { customDialog = true },
                                        icon = if (custom == 0) Icons.Filled.Add else null,
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
private fun AccentSwatch(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
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
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
    }
}
