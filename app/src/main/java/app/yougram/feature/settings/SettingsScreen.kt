package app.yougram.feature.settings

import app.yougram.core.i18n.tr
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yougram.feature.main.ui.MainTab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yougram.core.settings.GlassSettings
import app.yougram.core.settings.PlateRepository
import app.yougram.core.settings.SettingsRepository
import app.yougram.feature.settings.appearance.AppIconSection
import app.yougram.feature.settings.appearance.BlurTypeRow
import app.yougram.feature.settings.appearance.GlassPreview
import app.yougram.feature.settings.appearance.PlateSection
import app.yougram.feature.settings.appearance.ThemeSection
import app.yougram.feature.settings.component.DotSlider
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    plates: PlateRepository,
    contentPadding: PaddingValues,
    /** 0 — тема и цвета, 1 — панели и навигация; -1 — всё. */
    part: Int = -1,
) {
    val glass by settings.glass.collectAsState()

    SettingsPageColumn(contentPadding) {
        if (part == -1 || part == 0) {
        SectionLabel("Тема и цвета")
        ThemeSection(settings)

        AppIconSection()

        // На бюджетных устройствах размытие и тяжёлые анимации отключены автоматически;
        // здесь их можно включить вручную.
        val context = androidx.compose.ui.platform.LocalContext.current
        if (app.yougram.core.ui.DeviceProfile.hardwareTier(context) == app.yougram.core.ui.DeviceTier.Low) {
            val enhanced by settings.enhancedUi.collectAsState()
            SettingGroup {
                item {
                    app.yougram.feature.settings.component.SwitchRow(
                        "Улучшенный интерфейс",
                        enhanced,
                        settings::setEnhancedUi,
                        subtitle = "Размытие и плавные анимации. Может лагать на вашем устройстве",
                    )
                }
            }
            SettingsFootnote("Ваше устройство определено как бюджетное, поэтому эффекты по умолчанию выключены.")
        }

        }
        if (part == -1 || part == 1) {
        SectionLabel("Панели")
        GlassPreview(glass.blurRadius, glass.opacity)
        SettingGroup {
            item {
                SettingRow(
                    title = "Размытие",
                    subtitle = glass.blurRadius.roundToInt().toString(),
                    belowFullWidth = true,
                    below = {
                        DotSlider(glass.blurRadius, settings::setBlur, 0f..GlassSettings.MAX_BLUR)
                    },
                )
            }
            item { BlurTypeRow(glass.blurType, settings::setBlurType) }
            item {
                SettingRow(
                    title = "Плотность панелей",
                    subtitle = "${(glass.opacity * 100).roundToInt()} %",
                    belowFullWidth = true,
                    below = { DotSlider(glass.opacity, settings::setOpacity, 0f..1f) },
                )
            }
            item {
                SettingRow(
                    title = "Сбросить по умолчанию",
                    subtitle = "Размытие, тип размытия и плотность панелей",
                    onClick = settings::resetGlass,
                )
            }
        }
        SettingsFootnote("Предпросмотр показывает размытие по Гауссу, выбранный тип применяется к самим панелям.")

        NavOrderSection(settings)

        PlateSection(plates)
        }
    }
}
/** Порядок вкладок нижней панели навигации: стрелки вверх/вниз у каждой вкладки. */
@Composable
private fun NavOrderSection(settings: SettingsRepository) {
    val raw by settings.navOrder.collectAsState()
    val order = MainTab.parseOrder(raw)
    fun move(i: Int, dir: Int) {
        val j = i + dir
        if (j !in order.indices) return
        val next = order.toMutableList().apply { val t = this[i]; this[i] = this[j]; this[j] = t }
        settings.setNavOrder(MainTab.serializeOrder(next))
    }
    SectionLabel("Порядок вкладок навбара")
    SettingGroup {
        order.forEachIndexed { i, tab ->
            item {
                SettingRow(
                    title = tab.title,
                    icon = tab.icon,
                    trailing = {
                        Row {
                            IconButton(onClick = { move(i, -1) }, enabled = i > 0, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.KeyboardArrowUp, tr("{0}: выше", tab.title))
                            }
                            IconButton(onClick = { move(i, 1) }, enabled = i < order.lastIndex, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.KeyboardArrowDown, tr("{0}: ниже", tab.title))
                            }
                        }
                    },
                )
            }
        }
        item {
            SettingRow(
                title = "Сбросить порядок",
                subtitle = "Контакты, Звонки, Чаты, Настройки",
                onClick = { settings.setNavOrder(null) },
            )
        }
    }
    SettingsFootnote("Вкладки нижней панели отображаются слева направо в выбранном порядке.")
}
