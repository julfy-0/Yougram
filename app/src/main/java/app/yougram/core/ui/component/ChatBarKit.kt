package app.yougram.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph

/**
 * Общие части нижней панели ввода для всех экранов-чатов (основной чат, комментарии, ассистент):
 * тёмное поле-«таблетка», кнопка-«печенье» справа. Сама панель — на всю ширину, скруглена сверху
 * (BottomBarShape + barTint() из core.ui.glass).
 */

/** Цвет поля ввода и круглых кнопок слева: чёрный, 50% непрозрачности. */
val ChatBarFieldColor: Color = Color.Black.copy(alpha = 0.5f)

/** Скругление поля ввода. */
val ChatBarFieldCorner = 28.dp

/** Цвет текста и подсказки в поле ввода. */
val ChatBarTextColor: Color = Color.White
val ChatBarHintColor: Color = Color.White.copy(alpha = 0.6f)

/** Цвета Material TextField для поля ввода панели. */
@Composable
fun chatBarTextFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = ChatBarFieldColor,
    unfocusedContainerColor = ChatBarFieldColor,
    focusedTextColor = ChatBarTextColor,
    unfocusedTextColor = ChatBarTextColor,
    focusedPlaceholderColor = ChatBarHintColor,
    unfocusedPlaceholderColor = ChatBarHintColor,
    focusedLeadingIconColor = Color.White.copy(alpha = 0.7f),
    unfocusedLeadingIconColor = Color.White.copy(alpha = 0.7f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

/**
 * Кнопка справа: «печенье» (9 волн), когда отправлять нечего, и круг, когда [active].
 * Между формами плавно перетекает (Morph на пружине), цвет — primary.
 */
@Composable
fun CookieSendButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shapeOf = { a: Boolean -> if (a) MaterialShapes.Circle else MaterialShapes.Cookie9Sided }
    var shapeFrom by remember { mutableStateOf(shapeOf(active)) }
    var shapeTo by remember { mutableStateOf(shapeOf(active)) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(active) {
        val target = shapeOf(active)
        if (target !== shapeTo) {
            shapeFrom = shapeTo
            shapeTo = target
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
        }
    }
    val morph = remember(shapeFrom, shapeTo) { Morph(shapeFrom.normalized(), shapeTo.normalized()) }
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(size),
        enabled = enabled,
        shape = CookieMorphShape(morph, progress.value),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = scheme.primary,
            contentColor = scheme.onPrimary,
            disabledContainerColor = scheme.primary,
            disabledContentColor = scheme.onPrimary.copy(alpha = 0.6f),
        ),
        content = content,
    )
}

/** Shape из [Morph] (полигоны нормализованы в единичный квадрат): контур на [progress] масштабируется под размер. */
private class CookieMorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        var first = true
        for (c in morph.asCubics(progress)) {
            if (first) {
                path.moveTo(c.anchor0X * size.width, c.anchor0Y * size.height)
                first = false
            }
            path.cubicTo(
                c.control0X * size.width, c.control0Y * size.height,
                c.control1X * size.width, c.control1Y * size.height,
                c.anchor1X * size.width, c.anchor1Y * size.height,
            )
        }
        path.close()
        return Outline.Generic(path)
    }
}
