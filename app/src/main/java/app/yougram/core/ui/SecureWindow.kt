package app.yougram.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.flow.MutableStateFlow

/** Счётчик открытых секретных чатов: пока он > 0, окна приложения защищены от скриншотов и записи экрана. */
object SecureScreen {
    val count = MutableStateFlow(0)
    fun enter() = count.update { it + 1 }
    fun leave() = count.update { (it - 1).coerceAtLeast(0) }
}

private inline fun MutableStateFlow<Int>.update(block: (Int) -> Int) {
    while (true) {
        val old = value
        if (compareAndSet(old, block(old))) return
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Ставит FLAG_SECURE на окно, в котором находится (окно activity или окно Dialog), пока открыт секретный чат.
 * FLAG_SECURE запрещает скриншоты и запись экрана и скрывает содержимое в «Недавних».
 * Вызывать надо и внутри диалогов (просмотр фото/видео): у них своё окно.
 */
@Composable
fun SecureWindowEffect() {
    val secure = SecureScreen.count.collectAsState().value > 0
    val view = LocalView.current
    DisposableEffect(secure, view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: view.context.findActivity()?.window
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
