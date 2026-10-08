package app.yougram

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import app.yougram.core.ui.component.LocalOwnCustomBanner
import app.yougram.core.ui.component.OwnCustomBanner
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.LocalPlates
import app.yougram.core.ui.theme.YougramTheme
import app.yougram.core.ui.theme.isDarkTheme
import app.yougram.feature.auth.data.AuthStep
import app.yougram.feature.badge.ui.LocalBadgeChecker
import app.yougram.feature.badge.ui.LocalCreatorUsers
import app.yougram.feature.badge.ui.LocalGoldUsers
import app.yougram.feature.badge.ui.LocalYougramBanners
import app.yougram.feature.badge.ui.LocalYougramUsers
import app.yougram.feature.notifications.ConnectionService
import app.yougram.feature.notifications.NotificationCenter
import app.yougram.feature.security.data.LockType
import app.yougram.feature.security.ui.LockGate
import app.yougram.navigation.YougramNavHost

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as YougramApp).container }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Тап по уведомлению несёт id чата: передаём его навигации. */
    private fun handleIntent(intent: Intent?) {
        // Ссылки t.me / tg:// из других приложений.
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { container.pendingOpenUrl.value = it }
            intent.data = null
            return
        }
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
        // Скриншоты разрешены везде, кроме секретных чатов (их флаг держит SecureWindowEffect).
        if (app.yougram.core.ui.SecureScreen.count.value > 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}