package app.yougram.core.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Тактильный отклик разной силы. Использует системные константы [HapticFeedbackConstants], поэтому на
 * устройствах с хорошим вибромотором (Pixel и др.) отклик «настоящий», а не простой жужж. Выключается в
 * настройках чатов ([enabled]) и уважает системную настройку «Вибрация при касании».
 */
object Haptics {
    /** Глобальный выключатель: синхронизируется с настройками чатов. */
    @Volatile
    var enabled: Boolean = true

    /** Интенсивность отклика, %: 100 — системный отклик, меньше — вибромотор с заданной амплитудой. */
    @Volatile
    var intensity: Int = 100

    enum class Kind {
        /** Самый слабый «тик»: переключение вкладки, жест «назад». */
        Tick,

        /** Лёгкий щелчок: выбор, голос в опросе, отправка. */
        Click,

        /** Подтверждение действия: сообщение отправлено, готово. */
        Confirm,

        /** Сильный отклик: долгое нажатие, удаление. */
        Heavy,

        /** Отказ/ошибка: двойной толчок. */
        Reject,
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun performScaled(view: View, kind: Kind): Boolean {
        val vib = vibrator(view.context) ?: return false
        if (!vib.hasVibrator() || !vib.hasAmplitudeControl()) return false
        val k = intensity.coerceIn(5, 100) / 100f
        val (ms, base) = when (kind) {
            Kind.Tick -> 8L to 90
            Kind.Click -> 12L to 140
            Kind.Confirm -> 18L to 180
            Kind.Heavy -> 28L to 255
            Kind.Reject -> 0L to 0
        }
        val effect = if (kind == Kind.Reject) {
            val amp = (220 * k).toInt().coerceIn(1, 255)
            VibrationEffect.createWaveform(longArrayOf(0, 20, 40, 20), intArrayOf(0, amp, 0, amp), -1)
        } else {
            VibrationEffect.createOneShot(ms, (base * k).toInt().coerceIn(1, 255))
        }
        vib.vibrate(effect)
        return true
    }

    @Volatile
    private var lastTime = 0L

    @Volatile
    private var lastOrdinal = -1

    fun perform(view: View, kind: Kind) {
        if (!enabled) return
        // Явный отклик и отклик по клику могут сработать одновременно: гасим дубль, сильный отклик пропускаем.
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTime < 60 && kind.ordinal <= lastOrdinal) return
        lastTime = now
        lastOrdinal = kind.ordinal
        if (intensity < 100 && performScaled(view, kind)) return
        val constant = when (kind) {
            Kind.Tick -> HapticFeedbackConstants.CLOCK_TICK
            Kind.Click -> HapticFeedbackConstants.CONTEXT_CLICK
            Kind.Confirm -> HapticFeedbackConstants.CONFIRM
            Kind.Heavy -> HapticFeedbackConstants.LONG_PRESS
            Kind.Reject -> HapticFeedbackConstants.REJECT
        }
        view.performHapticFeedback(constant)
    }
}

/** Функция отклика, привязанная к текущему окну: `val haptics = rememberHaptics(); haptics(Haptics.Kind.Tick)`. */
@Composable
fun rememberHaptics(): (Haptics.Kind) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind -> Haptics.perform(view, kind) } }
}
