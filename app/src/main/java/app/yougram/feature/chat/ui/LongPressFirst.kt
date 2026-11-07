package app.yougram.feature.chat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Долгий тап, который срабатывает даже поверх дочерних элементов с собственными тапами
 * (ClickableText, цитата ответа, медиа, кнопка комментариев).
 *
 * Слушает событие в проходе Initial (раньше детей), ничего не потребляет, пока тап короткий,
 * а после срабатывания съедает остаток жеста — чтобы ребёнок не открыл ссылку/медиа при отпускании.
 */
@Composable
fun Modifier.longPressFirst(key: Any?, onLongPress: () -> Unit): Modifier {
    val current = rememberUpdatedState(onLongPress)
    val cfg = LocalViewConfiguration.current
    return this.pointerInput(key, cfg) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = cfg.touchSlop
            val ended = withTimeoutOrNull(cfg.longPressTimeoutMillis) {
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    val stop = ch == null || !ch.pressed || ch.isConsumed ||
                        (ch.position - down.position).getDistance() > slop
                } while (!stop)
                true
            }
            if (ended == null) {
                current.value()
                do {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    ev.changes.forEach { it.consume() }
                } while (ev.changes.any { it.pressed })
            }
        }
    }
}
