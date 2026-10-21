package app.yougram.feature.splash.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime

/**
 * Хэллоуинский ивент: идёт с 1 октября до 23:59:59 31 октября 2026.
 * Оформление под ивенты можно выключить в настройках (по умолчанию включено);
 * тогда [isActive] всегда false и приложение выглядит как обычный Yougram.
 */
object HalloweenEvent {
    val Start: LocalDateTime = LocalDateTime.of(2026, 10, 1, 0, 0, 0)
    val End: LocalDateTime = LocalDateTime.of(2026, 10, 31, 23, 59, 59)

    private const val Prefs = "yougram_promo"
    private const val KeyEnabled = "events_enabled"

    /** Состояние Compose: при переключении всё оформление обновляется сразу. */
    var themingEnabled by mutableStateOf(true)
        private set

    /** Вызывается один раз при старте приложения. */
    fun init(context: Context) {
        themingEnabled = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .getBoolean(KeyEnabled, true)
    }

    fun setThemingEnabled(context: Context, enabled: Boolean) {
        themingEnabled = enabled
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            .edit().putBoolean(KeyEnabled, enabled).apply()
    }

    /** Идёт ли ивент по датам (без учёта настройки). */
    fun isInPeriod(now: LocalDateTime = LocalDateTime.now()): Boolean =
        !now.isBefore(Start) && !now.isAfter(End)

    /** Нужно ли сейчас рисовать ивентовое оформление: ивент идёт и оно не выключено в настройках. */
    fun isActive(now: LocalDateTime = LocalDateTime.now()): Boolean =
        themingEnabled && isInPeriod(now)
}
