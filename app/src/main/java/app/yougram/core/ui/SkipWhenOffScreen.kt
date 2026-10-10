package app.yougram.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo

/** Не рисует содержимое, пока оно целиком за границами экрана (например, уехавший экран при анимации перехода). */
fun Modifier.skipWhenOffscreen(): Modifier = composed {
    var visible by remember { mutableStateOf(true) }
    val window = LocalWindowInfo.current
    this
        .onGloballyPositioned { coordinates ->
            val b = coordinates.boundsInWindow()
            val size = window.containerSize
            val onScreen = b.right > 0f && b.left < size.width && b.bottom > 0f && b.top < size.height
            if (onScreen != visible) visible = onScreen
        }
        .drawWithContent { if (visible) drawContent() }
}