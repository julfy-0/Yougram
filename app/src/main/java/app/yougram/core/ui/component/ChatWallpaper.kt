package app.yougram.core.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.yougram.core.ui.wallpaper.WallpaperBackground
import app.yougram.core.ui.wallpaper.WallpaperKind
import app.yougram.core.ui.wallpaper.WallpaperStore
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WallpaperMaxSide = 1600

fun chatWallpaperFile(context: Context): File = File(context.filesDir, "chat_wallpaper.jpg")

/** Копирует выбранную картинку во внутреннее хранилище с уменьшением; false — не удалось. */
suspend fun saveChatWallpaper(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > WallpaperMaxSide) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext false
        chatWallpaperFile(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Обои чата. Если на экране «Обои» выбраны пресет, узор или фото (WallpaperStore) — рисуем их;
 * иначе старые обои-фото [version] (0 — обоев нет).
 */
@Composable
fun ChatWallpaper(version: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { WallpaperStore.get(context) }
    val config by store.config.collectAsState()
    if (config.kind != WallpaperKind.None) {
        WallpaperBackground(config, modifier)
    } else if (version != 0L) {
        val bitmap by produceState<ImageBitmap?>(null, version) {
            value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(chatWallpaperFile(context).absolutePath)?.asImageBitmap()
            }
        }
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop) }
    }
}