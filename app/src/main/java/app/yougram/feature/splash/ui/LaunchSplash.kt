package app.yougram.feature.splash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import app.yougram.R
import app.yougram.core.ui.component.LinearWavyProgressIndicator

/** Длительность заставки при запуске приложения. */
private const val SplashMillis = 2000

/**
 * Заставка при запуске: иконка приложения и волнистая полоска-прогресс под ней, которая заполняется ровно за 2 секунды,
 * затем плавно исчезает и вызывается [onFinished].
 */
@Composable
fun LaunchSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val icon = remember {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(512, 512)?.asImageBitmap()
    }
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(SplashMillis, easing = LinearEasing))
        alpha.animateTo(0f, tween(350))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(alpha.value)
            .background(MaterialTheme.colorScheme.background)
            // Пока идёт заставка, нажатия не проходят к экрану под ней.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = "Yougram",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(144.dp).clip(CircleShape),
                )
            }
            // Волнистая полоска прогресса под иконкой.
            LinearWavyProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.width(180.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Yougram",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
