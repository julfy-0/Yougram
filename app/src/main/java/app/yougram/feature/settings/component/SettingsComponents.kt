@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.settings.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.glass.plateColor
import app.yougram.core.ui.rememberDeviceTier
import kotlin.math.max
import kotlin.math.min

class SettingGroupScope {
    internal val items = mutableListOf<@Composable () -> Unit>()

    /** Один сегмент группы. */
    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

private val OuterCorner = 28.dp
private val InnerCorner = 8.dp

fun segmentShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Номер сегмента в группе: от него зависит цвет плитки иконки (primary → tertiary → secondary). */
private val LocalSegmentIndex = compositionLocalOf { 0 }

/** Группа сегментов: у каждого свой фон, между ними узкий зазор, крайние углы крупнее внутренних. */
@Composable
fun SettingGroup(modifier: Modifier = Modifier, build: SettingGroupScope.() -> Unit) {
    val items = SettingGroupScope().apply(build).items
    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, content ->
            Surface(
                Modifier.fillMaxWidth(),
                shape = segmentShape(index, items.size),
                color = plateColor(PlateArea.Settings),
            ) {
                CompositionLocalProvider(LocalSegmentIndex provides index, content = content)
            }
        }
    }
}

/** Заголовок раздела. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Плитка с иконкой: форма «печенье» (на слабых устройствах — скруглённый квадрат), цвет зависит от места в группе. */
@Composable
private fun SettingIconTile(icon: ImageVector) {
    val scheme = MaterialTheme.colorScheme
    val tone = LocalSegmentIndex.current % 3
    val container = when (tone) {
        0 -> scheme.primaryContainer
        1 -> scheme.tertiaryContainer
        else -> scheme.secondaryContainer
    }
    val content = when (tone) {
        0 -> scheme.onPrimaryContainer
        1 -> scheme.onTertiaryContainer
        else -> scheme.onSecondaryContainer
    }
    val low = rememberDeviceTier() == DeviceTier.Low
    val shape: Shape = if (low) RoundedCornerShape(16.dp) else MaterialShapes.Cookie6Sided.toShape()
    Box(
        Modifier.size(44.dp).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
    }
}

/**
 * Строка настроек: название, пояснение, необязательные иконка, значение справа, элемент справа и блок под текстом.
 * [belowFullWidth] = true — блок [below] занимает всю ширину строки (ползунки, группы кнопок), а не колонку текста.
 */
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
    belowFullWidth: Boolean = false,
) {
    val shownIcon = icon ?: autoIcon(title)
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingIconTile(shownIcon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (below != null && !belowFullWidth) {
                    Spacer(Modifier.height(14.dp))
                    below()
                }
            }
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Text(value, style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (below != null && belowFullWidth) {
            Spacer(Modifier.height(14.dp))
            below()
        }
    }
}

/** Переключатель с галочкой на бегунке. [onCheckedChange] = null — переключает строка целиком (см. SwitchRow). */
@Composable
fun CheckSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
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
 * Выбор одного варианта в «связанной» группе кнопок (connected button group): соседние кнопки почти смыкаются,
 * выбранная меняет форму. При крупном системном шрифте варианты переносятся по два в ряд.
 */
@Composable
fun <T> ConnectedChoiceGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = if (LocalDensity.current.fontScale > 1.3f) options.chunked(2) else listOf(options)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                row.forEachIndexed { index, option ->
                    // TODO: если connected*ButtonShapes недоступны в вашей версии, заменить на ToggleButtonDefaults.shapes()
                    val shapes = when {
                        row.size == 1 -> ToggleButtonDefaults.shapes()
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == row.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    ToggleButton(
                        checked = option == selected,
                        onCheckedChange = { checked -> if (checked) onSelect(option) },
                        modifier = Modifier.weight(1f),
                        shapes = shapes,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Ползунок Expressive: толстый трек из двух «таблеток», между ними зазор и вертикальный бегунок-ручка.
 * Слева от ручки залитая часть (primary), справа — secondaryContainer; при касании ручка сужается.
 * Поведение и сигнатура прежние.
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
    val scheme = MaterialTheme.colorScheme
    val activeColor = scheme.primary
    val inactiveColor = scheme.secondaryContainer
    val dotOnActive = scheme.onPrimary
    val dotOnInactive = scheme.onSecondaryContainer.copy(alpha = 0.6f)

    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val handleDp by animateFloatAsState(
        targetValue = if (dragging) 2f else 4f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "sliderHandle",
    )
    val currentOnChange by rememberUpdatedState(onValueChange)
    fun update(x: Float) {
        val f = (x / widthPx).coerceIn(0f, 1f)
        currentOnChange(valueRange.start + f * span)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
                setProgress { target -> currentOnChange(target.coerceIn(valueRange)); true }
            }
            .onSizeChanged { widthPx = max(1, it.width).toFloat() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        dragging = true
                        tryAwaitRelease()
                        dragging = false
                    },
                    onTap = { update(it.x) },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; update(it.x) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    update(change.position.x)
                }
            },
    ) {
        val trackH = 24.dp.toPx()
        val trackTop = (size.height - trackH) / 2f
        val gap = 6.dp.toPx()
        val thumbW = handleDp.dp.toPx()
        val thumbX = fraction * size.width
        val outer = trackH / 2f
        val inner = 4.dp.toPx()

        fun track(color: Color, left: Float, right: Float, leftR: Float, rightR: Float) {
            val w = right - left
            if (w <= 0f) return
            val l = min(leftR, w / 2f)
            val r = min(rightR, w / 2f)
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left, trackTop, right, trackTop + trackH,
                        topLeftCornerRadius = CornerRadius(l), bottomLeftCornerRadius = CornerRadius(l),
                        topRightCornerRadius = CornerRadius(r), bottomRightCornerRadius = CornerRadius(r),
                    ),
                )
            }
            drawPath(path, color)
        }

        val leftEnd = max(0f, thumbX - gap)
        val rightStart = min(size.width, thumbX + gap)
        track(activeColor, 0f, leftEnd, outer, inner)
        track(inactiveColor, rightStart, size.width, inner, outer)

        val dotR = 2.dp.toPx()
        for (i in 1..dots) {
            val x = size.width * i / (dots + 1)
            when {
                x < leftEnd - dotR * 2 -> drawCircle(dotOnActive, dotR, Offset(x, size.height / 2f))
                x > rightStart + dotR * 2 -> drawCircle(dotOnInactive, dotR, Offset(x, size.height / 2f))
            }
        }

        drawRoundRect(
            activeColor,
            Offset(thumbX - thumbW / 2f, trackTop - 4.dp.toPx()),
            Size(thumbW, trackH + 8.dp.toPx()),
            CornerRadius(thumbW / 2f),
        )
    }
}

/**
 * Иконка по названию строки: так все страницы настроек выглядят одинаково (плитка с иконкой слева),
 * даже если иконку явно не передали. Порядок важен: срабатывает первое совпадение.
 */
private val AutoIcons: List<Pair<List<String>, ImageVector>> = listOf(
    listOf("сброс", "сбросить") to Icons.Filled.RestartAlt,
    listOf("удалить", "очист") to Icons.Filled.DeleteOutline,
    listOf("перезапуск") to Icons.Filled.Refresh,
    listOf("повтор") to Icons.Filled.Repeat,
    listOf("вибро") to Icons.Filled.Vibration,
    listOf("рингтон", "звук", "аудио") to Icons.Filled.VolumeUp,
    listOf("всплыва", "уведомлен", "счётчик", "счетчик") to Icons.Filled.Notifications,
    listOf("личные") to Icons.Filled.Person,
    listOf("групп") to Icons.Filled.Group,
    listOf("канал") to Icons.Filled.Campaign,
    listOf("истори", "правок") to Icons.Filled.History,
    listOf("реакци") to Icons.Filled.Favorite,
    listOf("закреп") to Icons.Filled.PushPin,
    listOf("контакт") to Icons.Filled.Contacts,
    listOf("синхрон") to Icons.Filled.Sync,
    listOf("карты", "карта") to Icons.Filled.Map,
    listOf("ссылок", "сайт") to Icons.Filled.Link,
    listOf("фильтр") to Icons.Filled.FilterList,
    listOf("чёрн", "в чс", "блок", "теневой") to Icons.Filled.Block,
    listOf("призрак") to Icons.Filled.VisibilityOff,
    listOf("шпион", "дату чтения", "последний онлайн") to Icons.Filled.Visibility,
    listOf("бот") to Icons.Filled.SmartToy,
    listOf("папк") to Icons.Filled.Folder,
    listOf("фото", "изображ") to Icons.Filled.Image,
    listOf("видео", "стрим", "mkv") to Icons.Filled.Videocam,
    listOf("файл", "вложен", "документ") to Icons.Filled.Description,
    listOf("wi-fi", "wifi") to Icons.Filled.Wifi,
    listOf("мобильн", "роуминг") to Icons.Filled.SignalCellularAlt,
    listOf("плат", "доставк") to Icons.Filled.CreditCard,
    listOf("черновик") to Icons.Filled.Edit,
    listOf("поиск", "подсказк") to Icons.Filled.Search,
    listOf("плагин") to Icons.Filled.Extension,
    listOf("соединен", "фон") to Icons.Filled.Cloud,
    listOf("текст", "размер") to Icons.Filled.TextFields,
    listOf("тема", "цвет", "оформлен") to Icons.Filled.Palette,
    listOf("блюр", "размыт", "прозрачн", "подлож", "панел") to Icons.Filled.BlurOn,
    listOf("пин", "код", "ключ", "парол") to Icons.Filled.Lock,
    listOf("отпечат", "биометр") to Icons.Filled.Fingerprint,
    listOf("язык") to Icons.Filled.Language,
    listOf("верси", "обновлен", "update") to Icons.Filled.SystemUpdate,
    listOf("устройств", "сеанс") to Icons.Filled.Laptop,
    listOf("помощь", "faq", "вопрос") to Icons.Filled.HelpOutline,
    listOf("подарк") to Icons.Filled.CardGiftcard,
    listOf("звёзд", "premium", "премиум") to Icons.Filled.Star,
    listOf("бизнес") to Icons.Filled.Storefront,
    listOf("информац", "приложен") to Icons.Filled.Info,
)

internal fun autoIcon(title: String): ImageVector {
    val t = title.lowercase()
    return AutoIcons.firstOrNull { (keys, _) -> keys.any { it in t } }?.second ?: Icons.Filled.Tune
}