package app.yougram.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yougram.data.AppContainer

/** Выбирает, какой экран настроек показать для текущей страницы. */
@Composable
fun SettingsPageContent(
    page: SettingsPage,
    container: AppContainer,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
    onOpenChat: (Long) -> Unit,
) {
    val context = LocalContext.current
    val details: SettingsDetailsViewModel = viewModel(
        factory = SettingsDetailsViewModel.factory(container.accountRepository, container.chatRepository),
    )
    val state by details.state.collectAsState()
    var showGift by remember { mutableStateOf(false) }
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            details.dismissError()
        }
    }

    when (page) {
        SettingsPage.Home -> SettingsHomeScreen(
            viewModel = viewModel<SettingsHomeViewModel>(factory = SettingsHomeViewModel.factory(container.chatRepository)),
            accountManager = container.accountManager,
            contentPadding = contentPadding,
            onNavigate = onNavigate,
            onOpenChat = onOpenChat,
            onGift = { showGift = true },
        )
        SettingsPage.Appearance -> SettingsScreen(container.settings, contentPadding)
        SettingsPage.Account -> AccountScreen(details, contentPadding)
        SettingsPage.ChatSettings -> ChatSettingsScreen(container.settings, contentPadding, onNavigate)
        SettingsPage.Privacy -> PrivacyScreen(details, container.appLock, container.settings, contentPadding, onNavigate)
        SettingsPage.Blocked -> BlockedScreen(details, contentPadding)
        SettingsPage.Websites -> WebsitesScreen(details, contentPadding)
        SettingsPage.Security -> SecurityScreen(container.appLock, contentPadding)
        SettingsPage.Notifications -> NotificationsScreen(container.settings, contentPadding)
        SettingsPage.DataStorage -> DataStorageScreen(details, container.settings, contentPadding)
        SettingsPage.Folders -> FoldersScreen(details, contentPadding)
        SettingsPage.Devices -> DevicesScreen(details, contentPadding)
        SettingsPage.PowerSaving -> PowerSavingScreen(container.settings, contentPadding)
        SettingsPage.Language -> LanguageScreen(contentPadding)
        SettingsPage.About -> AboutScreen(contentPadding, container.updater)
        SettingsPage.Extras -> ExtrasScreen(container.settings, contentPadding, onNavigate)
        SettingsPage.Ghost -> GhostModeScreen(container.settings, contentPadding)
        SettingsPage.Spy -> SpyModeScreen(container.settings, container.spy, contentPadding)
        SettingsPage.Banner -> BannerScreen(container.settings, contentPadding)
        SettingsPage.MessageFilters -> MessageFiltersScreen(container.settings, contentPadding, onNavigate)
        SettingsPage.SharedFilters -> SharedFiltersScreen(container.settings, contentPadding)
        SettingsPage.ShadowBan -> ShadowBanScreen(container.settings, contentPadding)
        SettingsPage.Premium -> {
            val premium by produceState<Boolean?>(null) { value = container.chatRepository.isPremium() }
            PremiumScreen(premium, contentPadding)
        }
        SettingsPage.Stars -> StarsScreen(contentPadding, onGift = { showGift = true })
        SettingsPage.Business -> BusinessScreen(contentPadding)
    }

    if (showGift) {
        GiftSheet(container.chatRepository, onOpenChat = onOpenChat, onDismiss = { showGift = false })
    }
}