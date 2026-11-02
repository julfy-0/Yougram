package app.yougram.feature.auth.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import app.yougram.core.util.AppIcon
import app.yougram.core.util.AppIconManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.R
import app.yougram.core.ui.rememberReduceMotion
import app.yougram.feature.splash.ui.HalloweenEvent

/**
 * Знак приложения с мягкой пульсацией. Пока идёт хэллоуинский ивент — бейдж-тыква с оранжевым свечением,
 * потом обычный скруглённый треугольник на градиентной плитке.
 */
@Composable
fun AuthLogo(modifier: Modifier = Modifier, logoSize: Dp = 92.dp) {
    val scheme = MaterialTheme.colorScheme
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "authLogoPulse")
    val animated by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "authLogoScale",
    )
    val pulse = if (reduceMotion) 1f else animated

    // Та же иконка, что выбрана на рабочем столе (adaptive XML из mipmap-anydpi-v26).
    val context = LocalContext.current
    val iconBitmap = remember {
        val res = when (AppIconManager.current(context)) {
            AppIcon.Classic -> R.mipmap.ic_launcher
            AppIcon.Neon -> R.mipmap.ic_launcher_neon
            AppIcon.Blue -> R.mipmap.ic_launcher_blue
            AppIcon.Green -> R.mipmap.ic_launcher_green
        }
        val d = ContextCompat.getDrawable(context, res)
        if (d is AdaptiveIconDrawable) {
            // Слои рисуем сами, без системной маски: иначе по углам остаются прозрачные «выкусы» (видна тень под плиткой).
            val size = 432
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.background?.apply { setBounds(0, 0, size, size); draw(canvas) }
            d.foreground?.apply { setBounds(0, 0, size, size); draw(canvas) }
            bmp.asImageBitmap()
        } else {
            d?.toBitmap(432, 432)?.asImageBitmap()
        }
    }

    if (HalloweenEvent.isActive()) {
        Box(modifier.size(logoSize), contentAlignment = Alignment.Center) {
            // Свечение шире самого значка, поэтому requiredSize (иначе его обрежет размер родителя).
            Box(
                Modifier
                    .requiredSize(logoSize * 1.9f)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFFF8A1F).copy(alpha = 0.40f), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Image(
                painter = painterResource(R.drawable.ic_badge_halloween),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.requiredSize(logoSize * 1.15f).scale(pulse),
            )
        }
        return
    }

    val tile = RoundedCornerShape(logoSize * 0.3f)

    Box(
        modifier = modifier
            .size(logoSize)
            .scale(pulse)
            .shadow(10.dp, tile, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(tile),
        contentAlignment = Alignment.Center,
    ) {
        if (iconBitmap != null) {
            // Адаптивная иконка рисуется на холсте 108dp, видна центральная часть 72dp — увеличиваем в 1.5 раза и обрезаем плиткой.
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.requiredSize(logoSize * 1.5f).scale(1.01f),
            )
        }
    }
}