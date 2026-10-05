package app.yougram

import app.yougram.ui.glass.LocalPlates
import app.yougram.ui.LocalYougramBanners
import app.yougram.ui.LocalOwnCustomBanner
import app.yougram.ui.OwnCustomBanner
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.yougram.data.AuthStep
import app.yougram.data.ConnectionService
import app.yougram.data.NotificationCenter
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
import app.yougram.ui.LocalCreatorUsers
import app.yougram.ui.LocalGoldUsers
import app.yougram.ui.LocalYougramUsers

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as YougramApp).container }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Тап по уведомлению несёт id чата: передаём его навигации. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null || !intent.hasExtra(NotificationCenter.EXTRA_CHAT_ID)) return
        val chatId = intent.getLongExtra(NotificationCenter.EXTRA_CHAT_ID, 0L)
        intent.removeExtra(NotificationCenter.EXTRA_CHAT_ID)
        if (chatId == 0L) return
        container.notificationCenter.forgetChat(chatId)
        container.pendingOpenChat.value = chatId
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Флаг защиты ставим до первого кадра, чтобы превью в «Недавних» не успело показать содержимое.
        applySecureFlag(container.appLock.settings.value.let { it.type != LockType.None && it.secureScreen })

        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val theme by container.settings.theme.collectAsState()
            val glass by container.settings.glass.collectAsState()
            val plates by container.plates.plates.collectAsState()
            val lock by container.appLock.settings.collectAsState()
            val dark = isDarkTheme(theme)
            val secure = lock.type != LockType.None && lock.secureScreen
            val yougramUsers by container.chatRepository.yougramUsers.collectAsState()
            val goldUsers by container.chatRepository.goldUsers.collectAsState()
            val creatorUsers by container.chatRepository.creatorUsers.collectAsState()
            val yougramBanners by container.chatRepository.banners.collectAsState()
            val ownUserId by container.chatRepository.ownUserId.collectAsState()
            val customBannerVersion by container.settings.customBanner.collectAsState()
            val inCall by container.callManager.call.collectAsState()
            val authStep by container.authRepository.step.collectAsState(initial = AuthStep.Loading)
            val notifPrefs by container.settings.notifications.collectAsState()

            // После входа: запрашиваем разрешение на уведомления и поднимаем фоновое соединение.
            LaunchedEffect(authStep == AuthStep.Ready, notifPrefs.backgroundConnection) {
                if (authStep != AuthStep.Ready) return@LaunchedEffect
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (notifPrefs.backgroundConnection) {
                    ConnectionService.start(this@MainActivity)
                    askIgnoreBatteryOptimizations()
                }
            }

            LaunchedEffect(secure) { applySecureFlag(secure) }

            // Входящий звонок должен быть виден и на заблокированном экране.
            LaunchedEffect(inCall != null) {
                setShowWhenLocked(inCall != null)
                setTurnScreenOn(inCall != null)
            }

            // Иконки статус-бара и навигации должны быть контрастны выбранной теме,
            // даже если она отличается от системной.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            YougramTheme(settings = theme) {
                CompositionLocalProvider(
                    LocalGlass provides glass,
                    LocalPlates provides plates,
                    LocalYougramUsers provides yougramUsers,
                    LocalGoldUsers provides goldUsers,
                    LocalCreatorUsers provides creatorUsers,
                    LocalYougramBanners provides yougramBanners,
                    LocalOwnCustomBanner provides
                            if (ownUserId != 0L && customBannerVersion > 0L) OwnCustomBanner(ownUserId, customBannerVersion) else null,
                    LocalBadgeChecker provides container.chatRepository::checkBadge,
                ) {
                    LockGate(container.appLock) {
                        YougramNavHost(container)
                    }
                }
            }
        }
    }

    /** Без исключения из оптимизации батареи система усыпляет сеть, и сообщения приходят с большой задержкой. Спрашиваем один раз. */
    private fun askIgnoreBatteryOptimizations() {
        val pm = getSystemService(android.os.PowerManager::class.java) ?: return
        val misc = getSharedPreferences("yougram_misc", MODE_PRIVATE)
        if (pm.isIgnoringBatteryOptimizations(packageName) || misc.getBoolean("battery_prompted", false)) return
        misc.edit().putBoolean("battery_prompted", true).apply()
        runCatching {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    override fun onStart() {
        super.onStart()
        container.notificationCenter.appVisible = true
        container.appLock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        container.notificationCenter.appVisible = false
        // Поворот экрана тоже вызывает onStop, но это не уход из приложения.
        if (!isChangingConfigurations) container.appLock.onBackground()
    }

    private fun applySecureFlag(secure: Boolean) {
        // Разрешаем скриншоты и запись экрана во всех режимах.
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}