package app.yougram.feature.chat.location

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

private val tileCache = LruCache<String, Bitmap>(48)

private fun tileKey(z: Int, x: Int, y: Int) = "$z/$x/$y"

private suspend fun loadTile(z: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
    val n = 1 shl z
    if (y < 0 || y >= n) return@withContext null
    val wx = ((x % n) + n) % n
    val key = tileKey(z, wx, y)
    tileCache.get(key)?.let { return@withContext it }
    try {
        val conn = URL("https://tile.openstreetmap.org/$z/$wx/$y.png").openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Yougram/1.0 (Android)")
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val bmp = try {
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
        bmp?.also { tileCache.put(key, it) }
    } catch (_: Exception) {
        null
    }
}

/**
 * Превью карты на плитках OpenStreetMap (без SDK карт): 2×2 плитки вокруг точки, метка по центру.
 * Размеры задаёт [modifier] (обычно fillMaxWidth + aspectRatio).
 */
@Composable
fun MapPreview(latitude: Double, longitude: Double, modifier: Modifier = Modifier, zoom: Int = 15) {
    val n = (1 shl zoom).toDouble()
    val fx = (longitude + 180.0) / 360.0 * n
    val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
    val fy = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
    val x0 = floor(fx - 0.5).toInt()
    val y0 = floor(fy - 0.5).toInt()
    val maxIndex = (1 shl zoom)

    val tiles by produceState<List<Bitmap?>>(
        initialValue = List(4) { i ->
            val wx = (((x0 + i % 2) % maxIndex) + maxIndex) % maxIndex
            tileCache.get(tileKey(zoom, wx, y0 + i / 2))
        },
        zoom, x0, y0,
    ) {
        value = coroutineScope {
            (0 until 4).map { i -> async { loadTile(zoom, x0 + i % 2, y0 + i / 2) } }.awaitAll()
        }
    }

    Box(modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Canvas(Modifier.matchParentSize()) {
            val scale = size.width / 240f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val tilePx = ceil(256f * scale).toInt()
            for (i in 0 until 4) {
                val bmp = tiles[i] ?: continue
                val left = cx + ((x0 + i % 2 - fx) * 256.0 * scale).toFloat()
                val top = cy + ((y0 + i / 2 - fy) * 256.0 * scale).toFloat()
                drawImage(
                    bmp.asImageBitmap(),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(tilePx, tilePx),
                )
            }
            drawCircle(Color.White, radius = 11.dp.toPx(), center = Offset(cx, cy))
            drawCircle(Color(0xFFE53935), radius = 8.dp.toPx(), center = Offset(cx, cy))
        }
        Text(
            "© OpenStreetMap",
            fontSize = 9.sp,
            color = Color.Black.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = 0.7f))
                .padding(horizontal = 3.dp),
        )
    }
}
