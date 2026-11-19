package app.yougram.feature.chat.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import app.yougram.feature.chat.data.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Подписка на обновления положения; возвращает функцию отписки.
 * Сразу отдаёт последнее известное положение, затем свежие.
 */
@SuppressLint("MissingPermission")
internal fun startLocationUpdates(
    context: Context,
    minTimeMs: Long,
    minDistanceM: Float,
    onFix: (Location) -> Unit,
): () -> Unit {
    if (!hasLocationPermission(context)) return {}
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return {}
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    try {
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }?.let(onFix)
        for (p in providers) {
            if (runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) {
                lm.requestLocationUpdates(p, minTimeMs, minDistanceM, listener, Looper.getMainLooper())
            }
        }
    } catch (_: SecurityException) {
        return {}
    }
    return { runCatching { lm.removeUpdates(listener) } }
}

/**
 * Обновляет координаты своих активных трансляций геопозиции (их регистрирует [track] при разборе сообщений).
 * Работает, пока приложение открыто; сама отключается, когда активных трансляций не осталось.
 */
object LiveLocationSharer {
    private class Tracked(val livePeriod: Int, val liveUntil: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracked = ConcurrentHashMap<Pair<Long, Long>, Tracked>()
    private var stopUpdates: (() -> Unit)? = null
    private var appContext: Context? = null
    private var repo: ChatRepository? = null

    /** Запоминает свою активную трансляцию; обновления запускаются, если уже известны контекст и репозиторий. */
    fun track(chatId: Long, messageId: Long, livePeriod: Int, liveUntil: Long) {
        tracked[chatId to messageId] = Tracked(livePeriod, liveUntil)
        val c = appContext
        val r = repo
        if (c != null && r != null) ensureRunning(c, r)
    }

    fun untrack(chatId: Long, messageId: Long) {
        tracked.remove(chatId to messageId)
    }

    @Synchronized
    fun ensureRunning(context: Context, repository: ChatRepository) {
        appContext = context.applicationContext
        repo = repository
        if (stopUpdates != null || tracked.isEmpty() || !hasLocationPermission(context)) return
        stopUpdates = startLocationUpdates(context.applicationContext, 10_000L, 10f) { loc ->
            scope.launch {
                val now = System.currentTimeMillis() / 1000
                tracked.entries.removeAll { it.value.liveUntil <= now }
                if (tracked.isEmpty()) {
                    stop()
                    return@launch
                }
                for ((key, t) in tracked) {
                    runCatching {
                        repository.updateLiveLocation(
                            key.first, key.second, loc.latitude, loc.longitude, loc.accuracy.toDouble(), t.livePeriod,
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        stopUpdates?.invoke()
        stopUpdates = null
    }
}
