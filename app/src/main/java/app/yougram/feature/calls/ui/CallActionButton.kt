package app.yougram.feature.calls.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.Haptics

/**
 * Кнопка экрана звонка с подписью: при нажатии пружинно сжимается, при включении круг плавно
 * превращается в скруглённый квадрат, цвет и иконка меняются анимированно.
 * [container]/[content] задают постоянные цвета (завершить, принять); иначе цвет зависит от [active].
 */
@Composable
fun CallActionButton(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 64.dp,
    container: Color? = null,
    content: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = app.yougram.core.ui.rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "callButtonScale",
    )
    val corner by animateIntAsState(
        targetValue = if (active) 34 else 50,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "callButtonCorner",
    )
    val bg by animateColorAsState(
        container ?: if (active) scheme.primary else scheme.surfaceContainerHighest,
        tween(220),
        label = "callButtonBg",
    )
    val fg by animateColorAsState(
        content ?: if (active) scheme.onPrimary else scheme.onSurface,
        tween(220),
        label = "callButtonFg",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = {
                haptics(Haptics.Kind.Click)
                onClick()
            },
            shape = RoundedCornerShape(percent = corner),
            color = bg,
            contentColor = fg,
            interactionSource = interaction,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = icon,
                    transitionSpec = {
                        (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f, animationSpec = tween(160)))
                            .togetherWith(fadeOut(tween(100)))
                    },
                    label = "callButtonIcon",
                ) { shown ->
                    Icon(shown, contentDescription = label, modifier = Modifier.size(size * 0.43f))
                }
            }
        }
        if (label != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Сегмент «связанной группы кнопок» M3 Expressive (вход­ящий звонок): крайние сегменты скруглены полностью,
 * внутренние углы маленькие; при нажатии сегмент пружинно «округляется». Иконка над подписью.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CallSegmentButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    leftEdge: Boolean = false,
    rightEdge: Boolean = false,
    height: Dp = 88.dp,
) {
    val haptics = app.yougram.core.ui.rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val full = height / 2
    val inner = 12.dp
    val spec = spring<Dp>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    val start by animateDpAsState(if (pressed || leftEdge) full else inner, spec, label = "segmentStart")
    val end by animateDpAsState(if (pressed || rightEdge) full else inner, spec, label = "segmentEnd")
    Surface(
        onClick = {
            haptics(Haptics.Kind.Click)
            onClick()
        },
        shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier.height(height),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
        }
    }
}

/** Широкая кнопка-«таблетка» M3 Expressive с иконкой и подписью; при нажатии форма пружинно сжимается. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CallWideButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
) {
    val haptics = app.yougram.core.ui.rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        if (pressed) 22.dp else height / 2,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "wideCorner",
    )
    Surface(
        onClick = {
            haptics(Haptics.Kind.Click)
            onClick()
        },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = modifier.height(height),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
        }
    }
}
