package app.yougram.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow

private class SearchEntry(
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val page: SettingsPage,
    /** Дополнительные слова для поиска (через пробел). */
    val keywords: String = "",
)

/** Все страницы настроек, по которым ищет кнопка поиска, когда открыта вкладка «Настройки». */
private val searchEntries = listOf(
    SearchEntry("Настройки Yougram", "Тема, цвета, панели", Icons.Filled.Tune, SettingsPage.Appearance,
        "внешний вид тема цвет акцент размытие блюр плотность панели стекло плашки"),
    SearchEntry("Призрак, шпион, фильтры", "Скрытность и локальный архив", Icons.Filled.VisibilityOff, SettingsPage.Extras,
        "режим призрака шпион удалённые сообщения архив"),
    SearchEntry(SettingsPage.Ghost.title, null, Icons.Filled.VisibilityOff, SettingsPage.Ghost, "призрак скрытность"),
    SearchEntry(SettingsPage.Spy.title, null, Icons.Filled.VisibilityOff, SettingsPage.Spy, "шпион архив удалённые"),
    SearchEntry(SettingsPage.MessageFilters.title, null, Icons.Filled.VisibilityOff, SettingsPage.MessageFilters, "фильтр сообщений"),
    SearchEntry(SettingsPage.SharedFilters.title, null, Icons.Filled.VisibilityOff, SettingsPage.SharedFilters, "фильтр общие"),
    SearchEntry(SettingsPage.ShadowBan.title, null, Icons.Filled.VisibilityOff, SettingsPage.ShadowBan, "бан теневой"),
    SearchEntry(SettingsPage.Banner.title, "Баннер и метка в профиле", Icons.Filled.CameraAlt, SettingsPage.Banner, "баннер метка бейдж профиль"),
    SearchEntry("Плагины", "Плагины на C++", Icons.Filled.Extension, SettingsPage.Plugins, "плагин расширение ygplugin"),
    SearchEntry("Энергосбережение", "Экономия заряда", Icons.Filled.BatterySaver, SettingsPage.PowerSaving, "батарея заряд"),
    SearchEntry("Аккаунт", "Номер телефона, имя пользователя", Icons.Filled.Person, SettingsPage.Account, "телефон имя username профиль аватар"),
    SearchEntry("Конфиденциальность", "Кто видит ваши данные", Icons.Filled.VpnKey, SettingsPage.Privacy, "приватность видимость номер"),
    SearchEntry(SettingsPage.Blocked.title, null, Icons.Filled.Block, SettingsPage.Blocked, "заблокированные блок"),
    SearchEntry(SettingsPage.Websites.title, null, Icons.Filled.Language, SettingsPage.Websites, "сайты вход авторизация"),
    SearchEntry("Безопасность", "Пин-код, графический ключ, отпечаток", Icons.Filled.Lock, SettingsPage.Security, "пароль пин код биометрия отпечаток двухфакторная"),
    SearchEntry("Устройства", "Активные сеансы", Icons.Filled.Laptop, SettingsPage.Devices, "сеансы сессии"),
    SearchEntry("Настройки чатов", "Размер текста, анимации", Icons.Filled.ChatBubble, SettingsPage.ChatSettings, "размер текста анимации пузыри обои поиск сверху"),
    SearchEntry("Папки с чатами", "Сортировка чатов по папкам", Icons.Filled.Folder, SettingsPage.Folders, "папки"),
    SearchEntry("Архив", "Архивные чаты", Icons.Filled.Archive, SettingsPage.Archive, "архив архивные чаты"),
    SearchEntry("Уведомления", "Звуки, сигналы, бейджи", Icons.Filled.Notifications, SettingsPage.Notifications, "звук сигнал бейдж вибрация"),
    SearchEntry("Данные и память", "Кэш, автозагрузка медиа", Icons.Filled.PieChart, SettingsPage.DataStorage, "кэш память автозагрузка медиа трафик"),
    SearchEntry("Язык", null, Icons.Filled.Language, SettingsPage.Language, "язык локализация"),
    SearchEntry(SettingsPage.Premium.title, null, Icons.Filled.Star, SettingsPage.Premium, "премиум premium"),
    SearchEntry(SettingsPage.Stars.title, null, Icons.Filled.Star, SettingsPage.Stars, "звёзды stars"),
    SearchEntry(SettingsPage.Business.title, null, Icons.Filled.Storefront, SettingsPage.Business, "бизнес"),
    SearchEntry("Telegram", "Premium, Звёзды, Бизнес, подарки, помощь", Icons.Filled.Star, SettingsPage.TelegramHub, "помощь поддержка подарок"),
    SearchEntry("О приложении", "Версия, баннер, информация", Icons.Filled.Info, SettingsPage.About, "версия обновление update ота github"),
)

/** Результаты поиска по настройкам: показываются поверх вкладки, пока в панели введён запрос. */
@Composable
fun SettingsSearchResults(
    query: String,
    contentPadding: PaddingValues,
    onOpen: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val q = query.trim().lowercase()
    val found = remember(q) {
        searchEntries.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle?.lowercase()?.contains(q) == true ||
                it.keywords.contains(q)
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 12.dp),
    ) {
        if (found.isEmpty()) {
            Text(
                "Ничего не найдено",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SettingGroup {
                found.forEach { e ->
                    item { SettingRow(e.title, subtitle = e.subtitle, icon = e.icon, onClick = { onOpen(e.page) }) }
                }
            }
        }
    }
}
