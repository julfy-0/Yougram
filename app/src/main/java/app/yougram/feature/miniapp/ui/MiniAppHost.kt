package app.yougram.feature.miniapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.yougram.feature.chat.data.ChatRepository

/**
 * Слой мини-приложений: затемнение и выезжающая снизу панель Material 3. Кладётся в корень навигации
 * поверх всех экранов; [request] == null — приложение закрыто.
 */
@Composable
fun MiniAppHost(
    request: MiniAppRequest?,
    repository: ChatRepository,
    openLink: (String) -> Unit,
    onClose: () -> Unit,
) {
    var last by remember { mutableStateOf<MiniAppRequest?>(null) }
    if (request != null) last = request
    val shown = request != null
    val scrim by animateFloatAsState(
        targetValue = if (shown) 0.32f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "miniAppScrim",
    )
    Box(Modifier.fillMaxSize()) {
        if (scrim > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrim))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            )
        }
        AnimatedVisibility(
            visible = shown,
            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(),
            exit = slideOutVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeOut(),
        ) {
            last?.let { r ->
                androidx.compose.runtime.key(r) {
                    MiniAppScreen(request = r, repository = repository, openLink = openLink, onClose = onClose)
                }
            }
        }
    }
}
