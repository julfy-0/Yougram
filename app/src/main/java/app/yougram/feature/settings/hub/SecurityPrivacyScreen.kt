package app.yougram.feature.settings.hub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.yougram.core.settings.SettingsRepository
import app.yougram.feature.security.data.AppLock
import app.yougram.feature.settings.SettingsDetailsViewModel
import app.yougram.feature.settings.SettingsPage
import app.yougram.feature.settings.component.LocalEmbeddedPage
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.privacy.PrivacyScreen
import app.yougram.feature.settings.privacy.SecurityScreen

/** Одна страница «Безопасность»: приватность аккаунта и защита приложения подряд. */
@Composable
fun SecurityPrivacyScreen(
    details: SettingsDetailsViewModel,
    appLock: AppLock,
    settings: SettingsRepository,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
) {
    SettingsPageColumn(contentPadding) {
        CompositionLocalProvider(LocalEmbeddedPage provides true) {
            PrivacyScreen(details, appLock, settings, PaddingValues(), onNavigate)
            SecurityScreen(appLock, PaddingValues())
        }
    }
}
