package app.yougram.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import app.yougram.MainActivity
import app.yougram.YougramApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Служба на время звонка: держит процесс живым в фоне, показывает уведомление звонка
 * (с кнопками «Принять/Отклонить/Завершить»), играет рингтон входящего и гасит экран у уха.
 */
class CallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var ringtone: Ringtone? = null
    private var ringing = false
    private var proximity: PowerManager.WakeLock? = null

    private val manager get() = (application as YougramApp).container.callManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Текущий звонок", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACCEPT -> manager.accept()
            ACTION_DECLINE, ACTION_HANGUP -> manager.hangUp()
        }
        // startForeground нужно вызвать сразу, иначе система убьёт процесс.
        if (!promote(manager.call.value)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            job = scope.launch {
                combine(manager.call, manager.route) { call, route -> call to route }
                    .collect { (call, route) -> render(call, route) }
            }
        }
        return START_NOT_STICKY
    }

    private fun render(call: ActiveCall?, route: AudioRoute) {
        if (call == null) {
            stopRinger()
            setProximity(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        promote(call)
        val incoming = !call.isOutgoing && call.phase == CallPhase.RINGING
        if (incoming) startRinger() else stopRinger()
        setProximity(call.phase == CallPhase.ACTIVE && !call.isVideo && route == AudioRoute.EARPIECE)
    }

    private fun promote(call: ActiveCall?): Boolean {
        val notification = buildNotification(call)
        val phone = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        val withMic = phone or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        val attempts = if (hasMic()) listOf(withMic, phone) else listOf(phone)
        for (type in attempts) {
            try {
                startForeground(NOTIFICATION_ID, notification, type)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "startForeground($type) failed", e)
            }
        }
        return false
    }

    private fun buildNotification(call: ActiveCall?): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val incoming = call != null && !call.isOutgoing && call.phase == CallPhase.RINGING
        val builder = Notification.Builder(this, if (incoming) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (call == null) return builder.setContentTitle("Звонок").build()

        val person = Person.Builder().setName(call.title.ifEmpty { "Звонок" }).setImportant(true).build()
        if (incoming) {
            // Без доступа к микрофону принять из шторки нельзя: открываем приложение, там запросим разрешение.
            val answer = if (hasMic()) servicePi(ACTION_ACCEPT) else open
            builder
                .setStyle(Notification.CallStyle.forIncomingCall(person, servicePi(ACTION_DECLINE), answer))
                .setFullScreenIntent(open, true)
        } else {
            builder.setStyle(Notification.CallStyle.forOngoingCall(person, servicePi(ACTION_HANGUP)))
            val started = call.startedAtMillis
            if (call.phase == CallPhase.ACTIVE && started != null) {
                builder.setWhen(started).setShowWhen(true).setUsesChronometer(true)
            } else {
                builder.setContentText(call.message ?: "Соединение…")
            }
        }
        return builder.build()
    }

    private fun servicePi(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, CallService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startRinger() {
        if (ringing) return
        ringing = true
        val audio = getSystemService(AudioManager::class.java)
        if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                isLooping = true
                play()
            }
        }
        if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
                ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 1000), 0))
        }
    }

    private fun stopRinger() {
        if (!ringing) return
        ringing = false
        ringtone?.stop()
        ringtone = null
        getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
    }

    /** Экран гаснет, когда телефон у уха (только обычный разговор через разговорный динамик). */
    private fun setProximity(enabled: Boolean) {
        val pm = getSystemService(PowerManager::class.java)
        if (enabled) {
            if (proximity == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "yougram:call").apply {
                    setReferenceCounted(false)
                }
            }
            proximity?.takeIf { !it.isHeld }?.acquire()
        } else {
            proximity?.takeIf { it.isHeld }?.release()
        }
    }

    override fun onDestroy() {
        stopRinger()
        setProximity(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallService"
        private const val NOTIFICATION_ID = 4101
        private const val CHANNEL_INCOMING = "calls_incoming"
        private const val CHANNEL_ONGOING = "calls_ongoing"
        private const val ACTION_ACCEPT = "app.yougram.call.ACCEPT"
        private const val ACTION_DECLINE = "app.yougram.call.DECLINE"
        private const val ACTION_HANGUP = "app.yougram.call.HANGUP"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
            } catch (e: Exception) {
                // Из фона запуск службы может быть запрещён системой: звонок тогда работает без неё.
                Log.w(TAG, "cannot start call service", e)
            }
        }
    }
}
