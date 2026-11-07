package app.yougram.feature.settings.extras

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr

private val GroupOuter = 28.dp
private val GroupInner = 8.dp

/**
 * Форма сегмента связанной группы: крайние углы крупные, внутренние малые.
 * Углы плавно перетекают, когда сегмент становится крайним/средним (фильтр категорий, установка, удаление).
 */
@Composable
internal fun animatedSegmentShape(index: Int, count: Int): RoundedCornerShape {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val top by animateDpAsState(if (index <= 0) GroupOuter else GroupInner, spec, label = "segTop")
    val bottom by animateDpAsState(if (index >= count - 1) GroupOuter else GroupInner, spec, label = "segBottom")
    val t = top.coerceAtLeast(0.dp)
    val b = bottom.coerceAtLeast(0.dp)
    return RoundedCornerShape(topStart = t, topEnd = t, bottomEnd = b, bottomStart = b)
}

/** Ряд кнопок категорий: «Все» + по одной на категорию; выбранная заливается и получает галочку. */
@Composable
internal fun PluginCategoryChips(
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "__all") { CategoryChip(tr("Все"), selected == null) { onSelect(null) } }
        items(categories, key = { it }) { c ->
            CategoryChip(c, selected == c, Modifier) { onSelect(if (selected == c) null else c) }
        }
    }
}

@Composable
internal fun CategoryChip(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        fx, label = "chipBg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        fx, label = "chipFg",
    )
    Surface(onClick = onClick, modifier = modifier, shape = CircleShape, color = bg, contentColor = fg) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}
