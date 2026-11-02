package app.yougram.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** «Цветочная» форма Expressive для аватарок с непросмотренными историями. */
@Composable
fun rememberCookieAvatarShape(): Shape = MaterialShapes.Cookie9Sided.toShape()

/** Круглая аватарка: картинка из файла TDLib, а пока её нет — первая буква названия. */
@Composable
fun Avatar(
    title: String,
    path: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Unspecified,
    enablePressScale: Boolean = true,
) {
    var isPressed by remember { mutableStateOf(value = false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enablePressScale) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "avatarScale",
    )

    val pressModifier = if (enablePressScale) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                try {
                    waitForUpOrCancellation()
                } finally {
                    isPressed = false
                }
            }
        }
    } else Modifier

    val image = rememberFileBitmap(path = path, maxSide = 192)

    val scaledModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(pressModifier)

    if ((borderWidth > 0.dp) && (borderColor != Color.Unspecified)) {
        Box(
            modifier = scaledModifier
                .size(size)
                .clip(shape)
                .background(borderColor)
                .padding(borderWidth),
            contentAlignment = Alignment.Center,
        ) {
            val innerSize = size - (borderWidth * 2)
            val contentModifier = Modifier
                .size(if (innerSize > 0.dp) innerSize else size)
                .clip(shape)

            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = contentModifier,
                )
            } else {
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = contentModifier,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    } else {
        val contentModifier = scaledModifier
            .size(size)
            .clip(shape)

        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = contentModifier,
            )
        } else {
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = contentModifier,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}