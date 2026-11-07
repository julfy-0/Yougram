package app.yougram.core.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.yougram.R
import java.io.File

/**
 * Своя иконка: фото или настроенные цвета (фон, фигура, значок).
 * Системную иконку приложения Android менять на произвольную не позволяет (только готовые activity-alias),
 * поэтому своя иконка добавляется на рабочий стол закреплённым ярлыком, который открывает приложение.
 */
object CustomAppIcon {
    data class Style(
        val background: Int = 0xFF3A3F47.toInt(),
        val shape: Int = 0xFF5C6370.toInt(),
        val glyph: Int = 0xFFFFFFFF.toInt(),
        val usePhoto: Boolean = false,
    )

    private const val PREFS = "custom_app_icon"

    private fun photoFile(context: Context) = File(context.filesDir, "custom_app_icon.jpg")

    fun load(context: Context): Style {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = Style()
        return Style(
            background = p.getInt("bg", d.background),
            shape = p.getInt("shape", d.shape),
            glyph = p.getInt("glyph", d.glyph),
            usePhoto = p.getBoolean("photo", false) && photoFile(context).exists(),
        )
    }

    fun save(context: Context, style: Style) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("bg", style.background)
            .putInt("shape", style.shape)
            .putInt("glyph", style.glyph)
            .putBoolean("photo", style.usePhoto)
            .apply()
    }

    /** Сохраняет фото из [uri] как квадрат 512×512 (по центру). Выполнять не на главном потоке. */
    fun importPhoto(context: Context, uri: Uri): Boolean = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize((minOf(info.size.width, info.size.height) / 1024).coerceAtLeast(1))
        }
        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
        photoFile(context).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    }.getOrDefault(false)

    /**
     * Рисует содержимое адаптивной иконки (холст 108×108dp, лаунчер показывает центральные 72dp).
     * Фото вписывается ровно в видимую область, чтобы лаунчер ничего не обрезал.
     */
    fun render(context: Context, style: Style, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val photo = if (style.usePhoto) photoFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } else null
        if (photo != null) {
            canvas.drawColor(style.background)
            val inset = size * 18 / 108
            canvas.drawBitmap(photo, null, Rect(inset, inset, size - inset, size - inset), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawColor(style.background)
            drawLayer(context, canvas, R.drawable.ic_launcher_star, style.shape, size)
            drawLayer(context, canvas, R.drawable.ic_launcher_plane, style.glyph, size)
        }
        return bitmap
    }

    private fun drawLayer(context: Context, canvas: Canvas, res: Int, color: Int, size: Int) {
        val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        d.setTint(color)
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
    }

    /** Просит лаунчер добавить ярлык со своей иконкой на рабочий стол. false — лаунчер не поддерживает закрепление. */
    fun requestPin(context: Context, style: Style): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(context, app.yougram.MainActivity::class.java).setAction(Intent.ACTION_MAIN)
        val info = ShortcutInfoCompat.Builder(context, "custom_icon_${System.currentTimeMillis()}")
            .setShortLabel(context.getString(R.string.app_name))
            .setIcon(IconCompat.createWithAdaptiveBitmap(render(context, style, 432)))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
}
