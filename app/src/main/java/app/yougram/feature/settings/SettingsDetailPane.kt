package app.yougram.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.di.AppContainer
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.core.ui.shape.LocalShapeBag
import app.yougram.core.ui.shape.ShapeBag
import app.yougram.feature.settings.about.UpdateGlassBar

/**
 * Страница настроек в правой панели планшета: заголовок со стрелкой «назад» (если есть куда вернуться)
 * и само содержимое. Колонка по центру не шире 640dp, чтобы настройки не растягивались на всю панель.
 */
@Composable
fun SettingsDetailPane(
    page: SettingsPage,
    canGoBack: Boolean,
    container: AppContainer,
    onBack: () -> Unit,
    onNavigate: (SettingsPage) -> Unit,
    onOpenChat: (Long) -> Unit,
) {
    val backdrop = rememberBackdropState()
    val glass by container.settings.glass.collectAsState()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canGoBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                } else {
                    Box(Modifier.padding(start = 12.dp))
                }
                Text(
                    page.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth().backdropSource(backdrop)) {
                val shapeBag = remember(page) { ShapeBag() }
                CompositionLocalProvider(LocalShapeBag provides shapeBag) {
                    SettingsPageContent(
                        page = page,
                        container = container,
                        contentPadding = PaddingValues(bottom = navBottom + 8.dp),
                        onNavigate = onNavigate,
                        onOpenChat = onOpenChat,
                    )
                }
            }
        }
        if (page == SettingsPage.Update) {
            UpdateGlassBar(
                updater = container.updater,
                backdrop = backdrop,
                glass = glass,
                bottomInset = navBottom,
                modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp),
            )
        }
    }
}
