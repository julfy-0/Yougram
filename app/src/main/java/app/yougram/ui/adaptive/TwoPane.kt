package app.yougram.ui.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Что показано в правой панели планшета / разложенного фолда. Последний элемент стека — верхний. */
sealed interface DetailRoute {
    val chatId: Long

    data class Chat(override val chatId: Long, val messageId: Long = 0L) : DetailRoute
    data class Profile(override val chatId: Long) : DetailRoute
}

/** Стек хранится строкой, чтобы переживать пересоздание Activity через rememberSaveable. */
fun List<DetailRoute>.encodeDetail(): String = joinToString(";") {
    when (it) {
        is DetailRoute.Chat -> "c:${it.chatId}:${it.messageId}"
        is DetailRoute.Profile -> "p:${it.chatId}"
    }
}

fun decodeDetail(raw: String): List<DetailRoute> =
    raw.split(';').mapNotNull { part ->
        val p = part.split(':')
        when {
            p.size == 3 && p[0] == "c" -> DetailRoute.Chat(p[1].toLongOrNull() ?: return@mapNotNull null, p[2].toLongOrNull() ?: 0L)
            p.size == 2 && p[0] == "p" -> DetailRoute.Profile(p[1].toLongOrNull() ?: return@mapNotNull null)
            else -> null
        }
    }

private const val TwoPaneMinWidthDp = 660
private const val TwoPaneMinHeightDp = 480

/**
 * Две панели включаются, когда окно достаточно широкое и не слишком низкое: планшеты,
 * разложенные фолды, split-screen на больших экранах. Телефон в альбомной ориентации остаётся одноколоночным.
 * Читает размер окна, поэтому складывание/раскладывание и изменение размера окна обрабатываются само.
 */
@Composable
fun rememberIsTwoPane(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp >= TwoPaneMinWidthDp && c.screenHeightDp >= TwoPaneMinHeightDp
}

/** Ширина левой панели (списка): на узких окнах фиксированная, на широких — треть окна. */
@Composable
fun rememberListPaneWidth(): Dp {
    val w = LocalConfiguration.current.screenWidthDp
    return if (w < 840) 352.dp else (w * 0.34f).dp.coerceIn(352.dp, 420.dp)
}

@Composable
fun TwoPaneShell(
    detail: DetailRoute?,
    listPane: @Composable () -> Unit,
    detailPane: @Composable (DetailRoute) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .width(rememberListPaneWidth())
                .fillMaxHeight(),
        ) { listPane() }

        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AnimatedContent(
                targetState = detail,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                label = "detailPane",
            ) { route ->
                if (route == null) DetailPlaceholder() else detailPane(route)
            }
        }
    }
}

@Composable
private fun DetailPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.ChatBubble,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
            Text(
                "Выберите чат, чтобы начать переписку",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
