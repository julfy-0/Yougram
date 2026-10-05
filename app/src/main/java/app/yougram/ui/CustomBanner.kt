package app.yougram.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

private const val BannerMaxSide = 1600

/** Своя картинка вместо шаблона баннера: хранится только на этом устройстве. */
data class OwnCustomBanner(val userId: Long, val version: Long)

val LocalOwnCustomBanner = compositionLocalOf<OwnCustomBanner?> { null }

fun customBannerFile(context: Context): File = File(context.filesDir, "profile_banner.jpg")

/** Копирует выбранную картинку во внутреннее хранилище с уменьшением; false — не удалось. */
suspend fun saveCustomBanner(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > BannerMaxSide) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@withContext false
        customBannerFile(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        true
    } catch (e: Exception) {
        false
    }
}

fun deleteCustomBanner(context: Context) {
    runCatching { customBannerFile(context).delete() }
}

/** Картинка-баннер; [version] меняется при замене файла и перезагружает её. */
@Composable
fun CustomBannerImage(version: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, version) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(customBannerFile(context).absolutePath)?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}
