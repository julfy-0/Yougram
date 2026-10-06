package app.yougram.feature

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable

const val TELEGRAM_SETTINGS_ROUTE = "settings/telegram"

fun NavGraphBuilder.telegramSettingsRoute(
    navController: NavHostController,
    onOpenPremium: () -> Unit,
    onOpenStars: () -> Unit,
    onOpenBusiness: () -> Unit,
    onSendGift: () -> Unit,
    onAskQuestion: () -> Unit,
) {
    composable(TELEGRAM_SETTINGS_ROUTE) {
        TelegramSettingsScreen(
            onBack = { navController.popBackStack() },
            onOpenPremium = onOpenPremium,
            onOpenStars = onOpenStars,
            onOpenBusiness = onOpenBusiness,
            onSendGift = onSendGift,
            onAskQuestion = onAskQuestion,
        )
    }
}
