package app.yougram

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yougram.data.LockType
import app.yougram.ui.YougramNavHost
import app.yougram.ui.security.LockGate
import app.yougram.ui.theme.YougramTheme
import app.yougram.ui.theme.isDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import app.yougram.ui.glass.LocalGlass
import app.yougram.ui.LocalBadgeChecker
import app.yougram.ui.LocalYougramUsers

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as YougramApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Флаг защиты ставим до первого кадра, чтобы превью в «Недавних» не успело показать содержимое.
        applySecureFlag(container.appLock.settings.value.let { it.type != LockType.None && it.secureScreen })

        setContent {
            val theme by container.settings.theme.collectAsState()
            val glass by container.settings.glass.collectAsState()
            val lock by container.appLock.settings.collectAsState()
            val dark = isDarkTheme(theme)
            val secure = lock.type != LockType.None && lock.secureScreen
            val yougramUsers by container.chatRepository.yougramUsers.collectAsState()

            LaunchedEffect(secure) { applySecureFlag(secure) }

            // Иконки статус-бара и навигации должны быть контрастны выбранной теме,
            // даже если она отличается от системной.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            YougramTheme(settings = theme) {
                CompositionLocalProvider(
                    LocalGlass provides glass,
                    LocalYougramUsers provides yougramUsers,
                    LocalBadgeChecker provides container.chatRepository::checkBadge,
                ) {
                    LockGate(container.appLock) {
                        YougramNavHost(container)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Поворот экрана тоже вызывает onStop, но это не уход из приложения.
        if (!isChangingConfigurations) container.appLock.onBackground()
    }

    private fun applySecureFlag(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}