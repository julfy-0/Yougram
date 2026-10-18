package app.yougram.feature.settings.hub

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import app.yougram.feature.settings.appearance.HalloweenPromo
import app.yougram.feature.settings.SettingsPage
import app.yougram.feature.settings.SettingsScreen
import app.yougram.feature.settings.component.LocalEmbeddedPage
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.general.ChatSettingsScreen
import app.yougram.feature.settings.general.FoldersScreen

private const val PromoKey = "halloween_2026"

private val AppearanceTabs = listOf("Тема", "Панели", "Сообщения", "Список чатов", "Прочее", "Обои", "Баннер и значок", "Папки")

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
    val context = LocalContext.current
    val promoPrefs = remember { context.getSharedPreferences("yougram_promo", Context.MODE_PRIVATE) }
    var showPromo by rememberSaveable { mutableStateOf(!promoPrefs.getBoolean(PromoKey, false)) }
    if (showPromo) {
        HalloweenPromo(
            onDismiss = { showPromo = false },
            onDone = {
                promoPrefs.edit().putBoolean(PromoKey, true).apply()
                showPromo = false
                tab = 6
            },
        )
    }
    SettingsPageColumn(contentPadding) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppearanceTabs.forEachIndexed { i, title ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(title) })
            }
        }
        CompositionLocalProvider(LocalEmbeddedPage provides true) {
            when (tab) {
                0 -> {
                    SettingsScreen(settings, plates, PaddingValues(), part = 0)
                    ChatSettingsScreen(settings, PaddingValues(), onNavigate, part = 0) // масштаб интерфейса
                }
                1 -> SettingsScreen(settings, plates, PaddingValues(), part = 1)
                2 -> ChatSettingsScreen(settings, PaddingValues(), onNavigate, part = 2)
                3 -> ChatSettingsScreen(settings, PaddingValues(), onNavigate, part = 3)
                4 -> ChatSettingsScreen(settings, PaddingValues(), onNavigate, part = 4)
                5 -> WallpaperContent()
                6 -> BannerScreen(settings, PaddingValues())
                else -> FoldersScreen(details, PaddingValues())
            }
        }
    }
}