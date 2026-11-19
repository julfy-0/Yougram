package app.yougram.feature.chat.data

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.settings.ThemeMode
import app.yougram.core.telegram.TelegramClient
import app.yougram.core.ui.component.chatWallpaperFile
import app.yougram.core.ui.wallpaper.CustomPresetId
import app.yougram.core.ui.wallpaper.GradientType
import app.yougram.core.ui.wallpaper.WallpaperConfig
import app.yougram.core.ui.wallpaper.WallpaperKind
import app.yougram.core.ui.wallpaper.WallpaperStore
import app.yougram.core.ui.wallpaper.resolvePreset
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Background
import dev.g000sha256.tdl.dto.BackgroundFill
import dev.g000sha256.tdl.dto.BackgroundFillFreeformGradient
import dev.g000sha256.tdl.dto.BackgroundFillGradient
import dev.g000sha256.tdl.dto.BackgroundFillSolid
import dev.g000sha256.tdl.dto.BackgroundTypeFill
import dev.g000sha256.tdl.dto.BackgroundTypePattern
import dev.g000sha256.tdl.dto.BackgroundTypeWallpaper
import dev.g000sha256.tdl.dto.InputBackgroundLocal
import dev.g000sha256.tdl.dto.InputFileLocal
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Синхронизирует с Telegram только обои чатов (фон по умолчанию для светлой и тёмной темы).
 * Затемнение, размытие, форма узора и его яркость остаются локальными.
 *
 * Telegram -> приложение: фото скачивается; заливка становится «своим» градиентом; у узорных обоев берётся заливка,
 * а сам рисунок узора — из локального набора (картинки узоров Telegram не воспроизводятся).
 * Приложение -> Telegram: фото уходит как обои, пресеты/градиенты/узоры — как заливка (узор в Telegram не передаётся),
 * сброс обоев удаляет их в Telegram.
 */
class WallpaperSync(
    private val telegram: TelegramClient,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    private val context: Context,
) {
    private val client get() = telegram.client
    private val store = WallpaperStore.get(context)
    private val prefs = context.getSharedPreferences("yougram_wallpaper_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val latest = HashMap<Boolean, Background?>()
    private val seen = HashSet<Boolean>()

    /** То, что сейчас в хранилище пришло из Telegram (или ушло в него): по этому значению не отправляем обратно своё же. */
    @Volatile private var appliedWire: Wire? = null
    @Volatile private var shownId = 0L

    /** Что синхронизируется: тип, цвета заливки, угол, метка фото. */
    private data class Wire(val mode: Int, val colors: List<Int>, val angle: Float, val stamp: Long)

    private fun wireOf(c: WallpaperConfig): Wire = when (c.kind) {
        WallpaperKind.None -> Wire(0, emptyList(), 0f, 0L)
        WallpaperKind.Photo -> Wire(1, emptyList(), 0f, c.photoStamp)
        WallpaperKind.Preset, WallpaperKind.Pattern -> Wire(
            2,
            c.resolvePreset().colors.map { it.toArgb() },
            if (c.presetId == CustomPresetId && c.gradientType == GradientType.Linear) c.gradientAngle else 135f,
            0L,
        )
    }

    private fun syncedId(dark: Boolean) = prefs.getLong(if (dark) "id_dark" else "id_light", 0L)

    private fun setSyncedId(dark: Boolean, id: Long) {
        shownId = id
        prefs.edit().putLong(if (dark) "id_dark" else "id_light", id).apply()
    }

    fun start() {
        shownId = syncedId(isDark())
        // Подписка должна начаться до авторизации: начальное значение TDLib присылает сразу после входа.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client.defaultBackgroundUpdates.collect { onRemote(it.forDarkTheme, it.background) }
        }
        scope.launch {
            store.config.map { wireOf(it) }.distinctUntilChanged().drop(1).collect { onLocal(it) }
        }
        // Старые обои-фото (экран «Настройки чатов») переносим в новое хранилище, дальше они синхронизируются как обычно.
        scope.launch {
            settings.chatPrefs.map { it.wallpaper }.distinctUntilChanged().drop(1).collect { stamp ->
                if (stamp != 0L) runCatching { store.setPhoto(Uri.fromFile(chatWallpaperFile(context))) }
            }
        }
        scope.launch {
            settings.theme.map { isDark() }.distinctUntilChanged().drop(1).collect { dark ->
                mutex.withLock {
                    val bg = latest[dark]
                    if (bg != null && bg.id != shownId) applyRemote(dark, bg)
                }
            }
        }
    }

    private fun isDark(): Boolean = when (settings.theme.value.mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private suspend fun onRemote(dark: Boolean, bg: Background?) = mutex.withLock {
        val first = seen.add(dark)
        latest[dark] = bg
        if (dark != isDark()) return@withLock
        val hasLocal = store.config.value.kind != WallpaperKind.None
        if (bg == null) {
            // В Telegram обоев нет: при первом ответе отправляем туда локальные, позже — значит их убрали на другом устройстве.
            if (hasLocal) {
                if (first) upload(dark, wireOf(store.config.value)) else clearLocal()
            }
            return@withLock
        }
        if (bg.id == syncedId(dark)) {
            shownId = bg.id
            return@withLock
        }
        applyRemote(dark, bg)
    }

    private suspend fun onLocal(wire: Wire) = mutex.withLock {
        if (wire == appliedWire) return@withLock
        val dark = isDark()
        if (wire.mode == 0) {
            if (client.deleteDefaultBackground(forDarkTheme = dark) is TdlResult.Success) {
                setSyncedId(dark, 0L)
                latest[dark] = null
            }
        } else {
            upload(dark, wire)
        }
    }

    private suspend fun upload(dark: Boolean, wire: Wire) {
        val type: dev.g000sha256.tdl.dto.BackgroundType
        var input: InputBackgroundLocal? = null
        if (wire.mode == 1) {
            val source = store.photoFile
            if (!source.exists()) return
            // Копия нужна, потому что файл обоев перезаписывается при следующей смене картинки.
            val copy = File(context.cacheDir, "bg_upload_${System.currentTimeMillis()}.jpg")
            withContext(Dispatchers.IO) { source.copyTo(copy, overwrite = true) }
            input = InputBackgroundLocal(InputFileLocal(copy.absolutePath))
            type = BackgroundTypeWallpaper(isBlurred = false, isMoving = false)
        } else {
            type = BackgroundTypeFill(fillOf(wire) ?: return)
        }
        val r = client.setDefaultBackground(background = input, type = type, forDarkTheme = dark)
        if (r is TdlResult.Success) {
            appliedWire = wire
            setSyncedId(dark, r.result.id)
            latest[dark] = r.result
        }
    }

    private fun fillOf(w: Wire): BackgroundFill? {
        fun rgb(c: Int) = c and 0xFFFFFF
        return when {
            w.colors.isEmpty() -> null
            w.colors.size == 1 -> BackgroundFillSolid(rgb(w.colors[0]))
            w.colors.size == 2 -> {
                // Угол Telegram: 0° — сверху вниз, по часовой, кратно 45; у нас 0° — слева направо.
                val tg = (((w.angle / 45f).roundToInt() * 45 - 90) % 360 + 360) % 360
                BackgroundFillGradient(rgb(w.colors[0]), rgb(w.colors[1]), tg)
            }
            else -> BackgroundFillFreeformGradient(w.colors.take(4).map { rgb(it) }.toIntArray())
        }
    }

    private suspend fun applyRemote(dark: Boolean, bg: Background) {
        val ok = when (val type = bg.type) {
            is BackgroundTypeWallpaper -> {
                val file = bg.document?.document
                val r = file?.let { client.downloadFile(fileId = it.id, priority = 32, offset = 0L, limit = 0L, synchronous = true) }
                val path = (r as? TdlResult.Success)?.result?.local?.path?.takeIf { it.isNotBlank() }
                path != null && runCatching { store.setPhoto(Uri.fromFile(File(path))) }.isSuccess
            }
            is BackgroundTypeFill -> applyFill(type.fill, pattern = false, intensity = 0)
            is BackgroundTypePattern -> applyFill(type.fill, pattern = true, intensity = type.intensity)
            else -> false
        }
        if (!ok) return
        appliedWire = wireOf(store.config.value)
        setSyncedId(dark, bg.id)
    }

    private fun applyFill(fill: BackgroundFill, pattern: Boolean, intensity: Int): Boolean {
        val colors: List<Int>
        var angle = 135f
        when (fill) {
            is BackgroundFillSolid -> colors = listOf(fill.color)
            is BackgroundFillGradient -> {
                colors = listOf(fill.topColor, fill.bottomColor)
                angle = (fill.rotationAngle + 90) % 360f
            }
            is BackgroundFillFreeformGradient -> colors = fill.colors.toList()
            else -> return false
        }
        if (colors.isEmpty()) return false
        store.update {
            it.copy(
                kind = if (pattern) WallpaperKind.Pattern else WallpaperKind.Preset,
                presetId = CustomPresetId,
                customColors = colors.map { c -> c or (0xFF shl 24) },
                gradientType = GradientType.Linear,
                gradientAngle = angle,
                patternIntensity = if (pattern) (abs(intensity) / 100f * 0.5f).coerceIn(0.03f, 0.5f) else it.patternIntensity,
            )
        }
        return true
    }

    private fun clearLocal() {
        store.update { it.copy(kind = WallpaperKind.None) }
        appliedWire = wireOf(store.config.value)
        setSyncedId(isDark(), 0L)
    }
}