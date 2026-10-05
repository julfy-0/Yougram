package app.yougram.ui.settings

import app.yougram.ui.glass.plateColor
import app.yougram.data.PlateArea
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

class SettingGroupScope {
    internal val items = mutableListOf<@Composable () -> Unit>()

    /** Один сегмент группы. */
    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

private val OuterCorner = 24.dp
private val InnerCorner = 6.dp

fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Группа сегментов: у каждого свой фон, между ними узкий зазор, крайние углы крупнее внутренних. */
@Composable
fun SettingGroup(modifier: Modifier = Modifier, build: SettingGroupScope.() -> Unit) {
    val items = SettingGroupScope().apply(build).items
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, content ->
            Surface(
                Modifier.fillMaxWidth(),
                shape = segmentShape(index, items.size),
                color = plateColor(PlateArea.Settings),
            ) { content() }
        }
    }
}

/** Заголовок раздела. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Строка настроек: название, пояснение, необязательные иконка, значение справа, элемент справа и блок под текстом. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (below != null) {
                Spacer(Modifier.height(14.dp))
                below()
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** Переключатель с галочкой на бегунке. */
@Composable
fun CheckSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            Icon(
                if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

/**
 * Ползунок в виде двух «таблеток» с точками и вертикальной чертой-бегунком.
 * Слева от бегунка — залитая часть, справа — тёмная.
 */
@Composable
fun DotSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    dots: Int = 9,
) {
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span == 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val activeColor = onSurface
    val inactiveColor = onSurface.copy(alpha = 0.14f)
    val dotOnActive = MaterialTheme.colorScheme.surfaceContainerHigh
    val dotOnInactive = onSurface.copy(alpha = 0.55f)

    var widthPx by remember { mutableFloatStateOf(1f) }
    val currentOnChange by rememberUpdatedState(onValueChange)
    fun update(x: Float) {
        val f = (x / widthPx).coerceIn(0f, 1f)
        currentOnChange(valueRange.start + f * span)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .onSizeChanged { widthPx = max(1, it.width).toFloat() }
            .pointerInput(Unit) { detectTapGestures { update(it.x) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragStart = { update(it.x) }) { change, _ ->
                    change.consume()
                    update(change.position.x)
                }
            },
    ) {
        val trackH = 20.dp.toPx()
        val trackTop = (size.height - trackH) / 2f
        val gap = 6.dp.toPx()
        val thumbW = 4.dp.toPx()
        val thumbX = fraction * size.width
        val radius = CornerRadius(trackH / 2f)

        val leftEnd = max(0f, thumbX - gap)
        if (leftEnd > 0f) {
            drawRoundRect(
                activeColor, Offset(0f, trackTop), Size(leftEnd, trackH),
                CornerRadius(min(trackH / 2f, leftEnd / 2f)),
            )
        }
        val rightStart = min(size.width, thumbX + gap)
        if (rightStart < size.width) {
            drawRoundRect(
                inactiveColor, Offset(rightStart, trackTop), Size(size.width - rightStart, trackH),
                CornerRadius(min(trackH / 2f, (size.width - rightStart) / 2f)),
            )
        }

        val dotR = 1.5.dp.toPx()
        for (i in 1..dots) {
            val x = size.width * i / (dots + 1)
            when {
                x < leftEnd - dotR * 2 -> drawCircle(dotOnActive, dotR, Offset(x, size.height / 2f))
                x > rightStart + dotR * 2 -> drawCircle(dotOnInactive, dotR, Offset(x, size.height / 2f))
            }
        }

        drawRoundRect(
            activeColor,
            Offset(thumbX - thumbW / 2f, 0f),
            Size(thumbW, size.height),
            CornerRadius(thumbW / 2f),
        )
    }
}