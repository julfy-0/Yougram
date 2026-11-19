package app.yougram.navbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

object YougramNavBarDefaults {
    val Height = 60.dp
    val Margin = 8.dp
    internal val Slot = 46.dp
    internal val Gap = 2.dp
    internal val PadH = 10.dp
    internal val PadV = 7.dp
    internal val SearchShape = RoundedCornerShape(13.dp)
    internal const val SearchScale = 0.7f
    /** Отступ текста от правого края выбранной вкладки (слева его задаёт слот иконки). */
    internal val LabelPad = 14.dp

    /** Высота навбара вместе с отступом снизу (без системной панели навигации). */
    val TotalHeight: Dp get() = Height + Margin

    @Composable
    fun labelStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)

    @Composable
    fun tint(): Color = lerp(MaterialTheme.colorScheme.surface, Color.White, 0.10f)

    @Composable
    fun navigationBarInset(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
}

/**
 * Стеклянный навбар из YouGram: «таблетка» с вкладками, выбранная раскрывается пружиной с подписью,
 * справа опциональная кнопка поиска.
 *
 * @param backdrop источник размытия; null — навбар без размытия (только подкраска).
 * @param progress 1 — панель на месте, 0 — уехала вниз за край экрана.
 */
@Composable
fun YougramNavBar(
    items: List<NavBarItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: BackdropState? = null,
    glass: NavBarGlassSettings = NavBarGlassSettings(),
    /** Не null — справа от панели вкладок рисуется отдельная кнопка поиска. */
    onSearch: (() -> Unit)? = null,
    tint: Color = YougramNavBarDefaults.tint(),
    labelStyle: TextStyle = YougramNavBarDefaults.labelStyle(),
    labelStyleFor: @Composable (NavBarItem) -> TextStyle = { labelStyle },
    bottomInset: Dp = YougramNavBarDefaults.navigationBarInset(),
    haptics: Boolean = true,
    progress: () -> Float = { 1f },
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier
            .offset {
                val distance = (YougramNavBarDefaults.Height + YougramNavBarDefaults.Margin + bottomInset).roundToPx()
                IntOffset(0, (distance * (1f - progress())).roundToInt())
            }
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomInset + YougramNavBarDefaults.Margin),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(YougramNavBarDefaults.Height)
                .navGlass(backdrop, glass, CircleShape, tint)
                .clip(CircleShape),
        ) {
            Box(Modifier.align(Alignment.Center)) {
                NavTabs(items, selectedId, labelStyleFor) { id ->
                    if (id != selectedId && haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(id)
                }
            }
        }
        if (onSearch != null) {
            Box(
                Modifier
                    .size(YougramNavBarDefaults.Height * YougramNavBarDefaults.SearchScale)
                    .navGlass(backdrop, glass, YougramNavBarDefaults.SearchShape, tint)
                    .clip(YougramNavBarDefaults.SearchShape)
                    .clickable(onClick = onSearch, role = Role.Button),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun NavTabs(
    items: List<NavBarItem>,
    selectedId: String,
    styleFor: @Composable (NavBarItem) -> TextStyle,
    onSelect: (String) -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    Row(
        Modifier.padding(horizontal = YougramNavBarDefaults.PadH, vertical = YougramNavBarDefaults.PadV),
        horizontalArrangement = Arrangement.spacedBy(YougramNavBarDefaults.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val style = styleFor(item)
            val px = remember(item.label, style) { measurer.measure(item.label, style, maxLines = 1).size.width }
            val extra = with(density) { px.toDp() } + YougramNavBarDefaults.LabelPad
            NavTab(item, item.id == selectedId, extra, style) { onSelect(item.id) }
        }
    }
}

@Composable
private fun NavTab(item: NavBarItem, selected: Boolean, extra: Dp, style: TextStyle, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val frac by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 650f), label = "navFrac")
    val textAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(200, easing = FastOutSlowInEasing), label = "navTextAlpha")
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f), tween(160), label = "navContainer",
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, tween(160), label = "navContent",
    )
    val slot = YougramNavBarDefaults.Slot
    Box(
        Modifier
            .width(slot + extra * frac.coerceIn(0f, 1.12f))
            .height(slot)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).wrapContentWidth(Alignment.Start, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
                Icon(
                    item.icon,
                    contentDescription = if (selected) null else item.label,
                    tint = content,
                    modifier = Modifier.size(24.dp),
                )
                if (item.badge > 0) {
                    Surface(
                        shape = CircleShape,
                        color = scheme.primary,
                        contentColor = scheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (item.badge > 99) "99+" else item.badge.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (selected || frac > 0.001f) {
                // Оптический центр по высоте шапки букв: линия строки включает запас под выносные элементы,
                // из-за чего обычный centerVertically поднимает текст. Смещаем так, чтобы середина
                // заглавной буквы (~0.35 кегля над базовой линией) лежала ровно по центру ряда.
                var shiftY by remember { mutableFloatStateOf(0f) }
                Text(
                    item.label,
                    modifier = Modifier.graphicsLayer {
                        alpha = textAlpha
                        translationY = shiftY
                    },
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    style = style,
                    onTextLayout = { r ->
                        val fs = r.layoutInput.style.fontSize.value.takeUnless { it.isNaN() } ?: 14f
                        val fsPx = fs * r.layoutInput.density.fontScale * r.layoutInput.density.density
                        shiftY = r.size.height / 2f - (r.firstBaseline - 0.35f * fsPx)
                    },
                )
            }
        }
    }
}