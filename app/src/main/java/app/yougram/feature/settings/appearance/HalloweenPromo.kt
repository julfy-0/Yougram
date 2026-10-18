package app.yougram.feature.settings.appearance

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yougram.feature.badge.ui.BadgeStyleIcon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PromoOrange = Color(0xFFFF8A1F)

/**
 * Полноэкранный экран «Хеллоуинское обновление»: закрывается только жестом «потянуть слева направо»
 * внизу ([onDone]); системная кнопка «назад» просто скрывает его ([onDismiss]).
 */
@Composable
fun HalloweenPromo(onDismiss: () -> Unit, onDone: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0B2E), Color(0xFF2B0F3F), Color(0xFF120A06)))),
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                val pulse by rememberInfiniteTransition(label = "pumpkin").animateFloat(
                    initialValue = 0.94f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "pumpkinScale",
                )
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(300.dp)
                            .background(
                                Brush.radialGradient(listOf(PromoOrange.copy(alpha = 0.35f), Color.Transparent)),
                                CircleShape,
                            ),
                    )
                    BadgeStyleIcon(3, Modifier.size(170.dp).scale(pulse))
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    "Хеллоуинское обновление",
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Новый хеллоуинский значок. Выберите его в разделе «Баннер и значок»: его увидят все пользователи Yougram.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.weight(1f))
                SlideToContinue(onComplete = onDone, modifier = Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

/** Ползунок «потяни вправо»: доведён до конца (85%) — срабатывает, иначе возвращается в начало. */
@Composable
private fun SlideToContinue(onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = app.yougram.core.ui.rememberHaptics()
    val thumb = 56.dp
    val inset = 4.dp
    var x by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(thumb + inset * 2)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        val maxPx = with(density) { (maxWidth - thumb - inset * 2).toPx() }.coerceAtLeast(1f)
        Text(
            "Потяните вправо",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = (1f - x / maxPx).coerceIn(0f, 1f) },
        )
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .padding(inset)
                .size(thumb)
                .clip(CircleShape)
                .background(PromoOrange)
                .draggable(
                    state = rememberDraggableState { delta -> if (!done) x = (x + delta).coerceIn(0f, maxPx) },
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (done) return@draggable
                        if (x >= maxPx * 0.85f) {
                            done = true
                            haptics(app.yougram.core.ui.Haptics.Kind.Heavy)
                            scope.launch {
                                animate(x, maxPx) { v, _ -> x = v }
                                onComplete()
                            }
                        } else {
                            scope.launch { animate(x, 0f, animationSpec = spring()) { v, _ -> x = v } }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF2B1200))
        }
    }
}