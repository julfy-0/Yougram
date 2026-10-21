package app.yougram.core.ui.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** «Цветочная» форма Expressive для аватарок с непросмотренными историями. */
@Composable
fun rememberCookieAvatarShape(): Shape = MaterialShapes.Cookie9Sided.toShape()

/** Круглая аватарка: картинка из файла TDLib, а пока её нет — первая буква названия. */
@Composable
fun Avatar(title: String, path: String?, size: Dp = 48.dp, shape: Shape = CircleShape) {
    val image = rememberFileBitmap(path = path, maxSide = 192)

    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(size)) {
            Box(contentAlignment = Alignment.Center) {
                Text(title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}