package app.yougram.feature.settings.hub

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.PlateRepository
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.ui.wallpaper.WallpaperContent
import app.yougram.feature.settings.SettingsDetailsViewModel
import app.yougram.feature.settings.appearance.BannerScreen
import app.yougram.feature.settings.appearance.FontsSection
import app.yougram.feature.settings.appearance.ThemePresetsScreen
import app.yougram.feature.settings.SettingsPage
import app.yougram.feature.settings.SettingsScreen
import app.yougram.feature.settings.component.LocalEmbeddedPage
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.general.ChatSettingsScreen
import app.yougram.feature.settings.general.FoldersScreen

private val AppearanceTabs = listOf("Тема", "Шрифты", "Панели", "Сообщения", "Список чатов", "Прочее", "Обои", "Баннер и значок", "Папки", "Темы")

/** Страница «Внешний вид и чаты»: настройки разложены по вкладкам-чипам вместо одной длинной ленты. */
@Composable
fun AppearanceChatsScreen(
    settings: SettingsRepository,
    plates: PlateRepository,
    details: SettingsDetailsViewModel,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SettingsPageColumn(contentPadding) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppearanceTabs.forEachIndexed { i, title ->
                FilterChip(
                    selected = tab == i,
                    onClick = { tab = i },
                    label = {
                        Text(
                            title,
                            fontFamily = app.yougram.core.ui.theme.roundedFamilyFor(app.yougram.core.i18n.tr(title)),
                            fontWeight = if (tab == i) androidx.compose.ui.text.font.FontWeight.ExtraBold
                            else androidx.compose.ui.text.font.FontWeight.Medium,
                        )
                    },
                )
            }
        }
        CompositionLocalProvider(LocalEmbeddedPage provides true) {
            when (tab) {
                0 -> {
                    SettingsScreen(settings, plates, PaddingValues(), part = 0)
                    ChatSettingsScreen(settings, PaddingValues(), onNavigate, part = 0) // масштаб интерфейса
                }
                1 -> FontsSection(settings)
                2 -> {
                    SettingsScreen(settings, plates, PaddingValues(), part = 1)
                    SectionLabel("Главная страница настроек")
                    SettingGroup {
                        item {
                            SettingRow(
                                title = "Порядок настроек",
                                subtitle = "Разделы и пункты на главной странице настроек",
                                icon = Icons.Filled.Tune,
                                onClick = { onNavigate(SettingsPage.HomeLayoutEditor) },
                            )
                        }
                    }
                }
                3 -> ChatSettingsScreen(settings, PaddingValues(), onNavigate, part = 2)
                4 -> ChatSettingsScreen(settings, PaddingValues(), onNavigate, part = 3)
                5 -> ChatSettingsScreen(settings, PaddingValues(), onNavigate, part = 4)
                6 -> WallpaperContent()
                7 -> BannerScreen(settings, PaddingValues())
                8 -> FoldersScreen(details, PaddingValues())
                else -> ThemePresetsScreen(settings, plates, PaddingValues())
            }
        }
    }
}