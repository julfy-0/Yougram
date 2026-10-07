package app.yougram.feature.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.yougram.MainActivity
import app.yougram.YougramApp
import app.yougram.feature.auth.data.AuthStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Служба переднего плана с постоянным уведомлением: не даёт системе убить процесс,
 * чтобы TDLib оставался на связи и присылал уведомления о сообщениях.
 */
class ConnectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val container get() = (application as YougramApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Фоновое соединение", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground нужно вызвать сразу, иначе система убьёт процесс.
        if (!promote()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            job = scope.launch {
                combine(container.authRepository.step, container.settings.notifications) { step, prefs ->
                    // Выходим, если фоновое соединение выключено или пользователь вышел из аккаунта.
                    prefs.backgroundConnection && step != AuthStep.EnterPhone
                }.distinctUntilChanged().collect { keep ->
                    if (!keep) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return if (container.settings.notifications.value.restartOnClose) START_STICKY else START_NOT_STICKY
    }

    private fun promote(): Boolean = try {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Yougram")
            .setContentText("Соединение с Telegram")
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            // Некоторые оболочки (HyperOS, ColorOS, Realme UI, OxygenOS) отклоняют явный тип.
            // Повторяем без типа: берётся тип из манифеста. Если и так не вышло, остаётся только выйти.
            Log.w(TAG, "startForeground(specialUse) failed, retry without type", e)
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: Exception) {
        Log.w(TAG, "startForeground failed", e)
        false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ConnectionService"
        private const val NOTIFICATION_ID = 4102
        private const val CHANNEL = "connection"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java))
            } catch (e: Exception) {
                // Из фона запуск может быть запрещён системой — тогда служба стартует при следующем открытии приложения.
                Log.w(TAG, "cannot start connection service", e)
            }
        }
    }
}