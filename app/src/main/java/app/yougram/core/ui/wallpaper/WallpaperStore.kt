package app.yougram.core.ui.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

enum class WallpaperKind { None, Preset, Pattern, Photo }

enum class WallpaperPattern(val title: String) {
    Doodle("Микс"),
    Dots("Круги"),
    Grid("Квадраты"),
    Diagonal("Капсулы"),
    Waves("Цветы"),
    Triangles("Треугольники"),
    Stars("Искры"),
    Hearts("Сердечки"),
    Rings("Печенье"),
    Crosses("Клевер"),
    Hex("Соты"),
    Mosaic("Мозаика"),
    Bricks("Кирпичи"),
    Plaid("Клетка"),
    Stripes("Полосы"),
    Scales("Чешуя"),
    Bubbles("Пузыри"),
    Confetti("Конфетти"),
    Petals("Лепестки"),
    Gems("Кристаллы"),
    Zigzag("Зигзаги"),
    Checker("Шахматы"),
    Bursts("Взрывы"),
}

enum class GradientType(val title: String) { Linear("Линейный"), Radial("Радиальный"), Sweep("Конусный") }

const val CustomPresetId = "custom"

data class WallpaperConfig(
    val kind: WallpaperKind = WallpaperKind.None,
    /** Базовые цвета: для пресета — сам фон, для узора — подложка. */
    val presetId: String = "midnight",
    val pattern: WallpaperPattern = WallpaperPattern.Doodle,
    val patternIntensity: Float = 0.14f,
    /** Метка версии файла фото (меняется при каждой замене, сбрасывает кэш). */
    val photoStamp: Long = 0L,
    val photoDim: Float = 0.25f,
    /** Размытие фото в dp, 0..24. */
    val photoBlur: Float = 0f,
    /** Свои цвета (ARGB) для presetId == "custom": 2–5 точек градиента, одна — сплошной цвет. */
    val customColors: List<Int> = listOf(0xFF1D4E89.toInt(), 0xFF2A9D8F.toInt()),
    val gradientType: GradientType = GradientType.Linear,
    /** Угол линейного градиента / поворот конусного, градусы. */
    val gradientAngle: Float = 135f,
)

/** Хранилище обоев (SharedPreferences + файл фото во внутренней папке). Один экземпляр на приложение. */
class WallpaperStore private constructor(private val appContext: Context) {
    private val prefs = appContext.getSharedPreferences("yougram_wallpaper", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<WallpaperConfig> = _config

    val photoFile: File get() = File(appContext.filesDir, "wallpaper_photo.jpg")

    private fun load(): WallpaperConfig {
        val d = WallpaperConfig()
        return WallpaperConfig(
            kind = runCatching { WallpaperKind.valueOf(prefs.getString("kind", null) ?: "") }.getOrDefault(d.kind),
            presetId = prefs.getString("preset", d.presetId) ?: d.presetId,
            pattern = runCatching { WallpaperPattern.valueOf(prefs.getString("pattern", null) ?: "") }.getOrDefault(d.pattern),
            patternIntensity = prefs.getFloat("intensity", d.patternIntensity),
            photoStamp = prefs.getLong("stamp", 0L),
            photoDim = prefs.getFloat("dim", d.photoDim),
            photoBlur = prefs.getFloat("blur", d.photoBlur),
            customColors = prefs.getString("customColors", null)
                ?.split(",")?.mapNotNull { it.toLongOrNull(16)?.toInt() }?.takeIf { it.isNotEmpty() } ?: d.customColors,
            gradientType = runCatching { GradientType.valueOf(prefs.getString("gradType", null) ?: "") }.getOrDefault(d.gradientType),
            gradientAngle = prefs.getFloat("gradAngle", d.gradientAngle),
        )
    }

    fun update(transform: (WallpaperConfig) -> WallpaperConfig) {
        val next = transform(_config.value)
        _config.value = next
        prefs.edit()
            .putString("kind", next.kind.name)
            .putString("preset", next.presetId)
            .putString("pattern", next.pattern.name)
            .putFloat("intensity", next.patternIntensity)
            .putLong("stamp", next.photoStamp)
            .putFloat("dim", next.photoDim)
            .putFloat("blur", next.photoBlur)
            .putString("customColors", next.customColors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString(16) })
            .putString("gradType", next.gradientType.name)
            .putFloat("gradAngle", next.gradientAngle)
            .apply()
    }

    /** Копирует выбранное фото в приложение (уменьшив до ~1600 px, с учётом EXIF-поворота) и включает его как обои. */
    suspend fun setPhoto(uri: Uri) = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MaxPhotoSide) {
                val k = MaxPhotoSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
            }
        }
        photoFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        update { it.copy(kind = WallpaperKind.Photo, photoStamp = System.currentTimeMillis()) }
    }

    fun removePhoto() {
        runCatching { photoFile.delete() }
        update { it.copy(kind = if (it.kind == WallpaperKind.Photo) WallpaperKind.None else it.kind, photoStamp = 0L) }
    }

    companion object {
        private const val MaxPhotoSide = 1600
        @Volatile private var instance: WallpaperStore? = null
        fun get(context: Context): WallpaperStore =
            instance ?: synchronized(this) {
                instance ?: WallpaperStore(context.applicationContext).also { instance = it }
            }
    }
}