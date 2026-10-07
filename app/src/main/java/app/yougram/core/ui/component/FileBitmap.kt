package app.yougram.core.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

private fun decode(path: String, maxSide: Int): Bitmap? {
    val key = "$maxSide:$path"
    cache.get(key)?.let { return it }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    if (bitmap != null) cache.put(key, bitmap)
    return bitmap
}

/** Декодирует картинку из файла с уменьшением до maxSide и кэширует результат. */
@Composable
fun rememberFileBitmap(path: String?, maxSide: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxSide) {
        value = if (path == null) null else withContext(Dispatchers.IO) { decode(path, maxSide)?.asImageBitmap() }
    }
    return bitmap
}