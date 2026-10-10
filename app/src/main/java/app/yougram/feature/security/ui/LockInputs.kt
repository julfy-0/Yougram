package app.yougram.feature.security.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import app.yougram.core.ui.component.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
import kotlin.math.roundToInt

private const val MaxPinLength = 16
private val KeySize = 76.dp
private val KeyGap = 16.dp

/** Цифровая клавиатура: клавиши «перетекают» из круга в скруглённый квадрат при нажатии, точки появляются пружиной, при ошибке встряхивание. */
@Composable
fun PinEntry(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    minLength: Int = 4,
    extraKey: (@Composable () -> Unit)? = null,
) {
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isError) {
        if (isError) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    (-24f) at 50
                    24f at 100
                    (-18f) at 150
                    18f at 200
                    (-12f) at 250
                    12f at 300
                    (-6f) at 350
                    0f at 400
                },
            )
        }
    }

    val dotColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KeyGap),
    ) {
        Row(Modifier.height(24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until MaxPinLength) {
                AnimatedVisibility(
                    visible = i < value.length,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    PinDot(index = i, color = dotColor)
                }
            }
        }
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                row.forEach { digit ->
                    PinKey(enabled = enabled, onClick = {
                        if (value.length < MaxPinLength) onValueChange(value + digit)
                    }) { Text(digit.toString(), style = MaterialTheme.typography.headlineSmallEmphasized) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            Box(Modifier.size(KeySize), contentAlignment = Alignment.Center) { extraKey?.invoke() }
            PinKey(enabled = enabled, onClick = {
                if (value.length < MaxPinLength) onValueChange(value + "0")
            }) { Text("0", style = MaterialTheme.typography.headlineSmallEmphasized) }
            PinKey(enabled = enabled && value.isNotEmpty(), tonal = true, onClick = { onValueChange(value.dropLast(1)) }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Стереть")
            }
        }
        Button(
            onClick = onSubmit,
            enabled = enabled && value.length >= minLength,
            shape = CircleShape,
            modifier = Modifier.width(KeySize * 3 + KeyGap * 2).height(56.dp),
        ) { Text("Подтвердить", style = MaterialTheme.typography.titleMedium) }
    }
}

/** Фигуры для введённых символов: каждая следующая цифра появляется новой формой, как на Pixel. */
private val PinDotPolygons by lazy {
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Flower,
        MaterialShapes.Sunny,
        MaterialShapes.SoftBurst,
        MaterialShapes.Pentagon,
        MaterialShapes.Gem,
        MaterialShapes.Puffy,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Clover8Leaf,
    )
}

/** Точка ввода: своя фигура по номеру символа, появляется пружиной с поворотом. */
@Composable
private fun PinDot(index: Int, color: Color) {
    val shape = PinDotPolygons[index % PinDotPolygons.size].toShape()
    val rotation = remember { Animatable(-120f) }
    LaunchedEffect(Unit) {
        rotation.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    Box(
        Modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = rotation.value }
            .clip(shape)
            .background(color),
    )
}

@Composable
private fun PinKey(enabled: Boolean, onClick: () -> Unit, tonal: Boolean = false, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val low = rememberDeviceTier() == DeviceTier.Low
    // Форма перетекает: круг → скруглённый квадрат при нажатии (на слабых устройствах без морфинга).
    val corner by animateDpAsState(
        targetValue = if (isPressed && !low) KeySize / 4 else KeySize / 2,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "keyCorner",
    )
    val scheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner.coerceAtLeast(0.dp)),
        color = if (tonal) scheme.secondaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (tonal) scheme.onSecondaryContainer else scheme.onSurface,
        interactionSource = interactionSource,
        modifier = Modifier.size(KeySize),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Поле 3×3 для графического ключа с анимацией дрожания при ошибке.
 */
@Composable
fun PatternPad(
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    onStart: () -> Unit = {},
) {
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(isError) {
        if (isError) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 400
                    0f at 0
                    (-24f) at 50
                    24f at 100
                    (-18f) at 150
                    18f at 200
                    (-12f) at 250
                    12f at 300
                    (-6f) at 350
                    0f at 400
                },
            )
        }
    }

    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnStart by rememberUpdatedState(onStart)

    fun centerOf(index: Int, width: Float): Offset {
        val cell = width / 3f
        return Offset((index % 3 + 0.5f) * cell, (index / 3 + 0.5f) * cell)
    }

    fun hit(position: Offset) {
        val radius = widthPx / 3f * 0.4f
        for (i in 0..8) {
            if (i !in selected && (centerOf(i, widthPx) - position).getDistance() <= radius) {
                selected.add(i)
                break
            }
        }
    }

    Canvas(
        modifier
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selected.clear()
                    currentOnStart()
                    finger = down.position
                    hit(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        finger = change.position
                        hit(change.position)
                    }
                    finger = null
                    val result = selected.joinToString(",")
                    selected.clear()
                    if (result.isNotEmpty()) currentOnComplete(result)
                }
            },
    ) {
        val w = size.width
        val stroke = 6.dp.toPx()
        for (k in 0 until selected.size - 1) {
            drawLine(accent, centerOf(selected[k], w), centerOf(selected[k + 1], w), stroke, StrokeCap.Round)
        }
        val tip = finger
        if (tip != null && selected.isNotEmpty()) {
            drawLine(accent, centerOf(selected.last(), w), tip, stroke, StrokeCap.Round)
        }
        for (i in 0..8) {
            val c = centerOf(i, w)
            if (i in selected) {
                drawCircle(accent.copy(alpha = 0.25f), 26.dp.toPx(), c)
                drawCircle(accent, 10.dp.toPx(), c)
            } else {
                drawCircle(idle, 8.dp.toPx(), c)
            }
        }
    }
}