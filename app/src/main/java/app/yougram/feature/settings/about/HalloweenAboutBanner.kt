package app.yougram.feature.settings.about

import app.yougram.core.i18n.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yougram.R
import app.yougram.feature.splash.ui.drawCobweb

private val HalloweenOrange = Color(0xFFFF8A1F)
private val HalloweenBgTop = Color(0xFF1B0A2E)
private val HalloweenBgBottom = Color(0xFF09050F)

/** Хэллоуинский баннер «О приложении»: показывается вместо обычного, пока включено оформление под ивенты. */
@Composable
internal fun HalloweenAboutBanner(modifier: Modifier = Modifier) {
    // Своя картинка: app/src/main/res/drawable/about_banner_halloween.png (1000×400, 2.5:1).
    // Нет файла — рисуем баннер кодом ниже.
    val context = LocalContext.current
    val customRes = remember {
        context.resources.getIdentifier("about_banner_halloween", "drawable", context.packageName)
    }
    if (customRes != 0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HalloweenBgBottom),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = customRes),
                contentDescription = tr("Хэллоуинский баннер Yougram"),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(HalloweenBgTop, HalloweenBgBottom))),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Оранжевое свечение за тыквой.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HalloweenOrange.copy(alpha = 0.34f), Color.Transparent),
                    center = Offset(size.height * 0.75f, size.height / 2f),
                    radius = size.height * 0.95f,
                ),
                radius = size.height * 0.95f,
                center = Offset(size.height * 0.75f, size.height / 2f),
            )
            // Паутина в верхних углах.
            val web = 190.dp.toPx()
            translate(0f, 0f) { drawCobweb(web, Color.White.copy(alpha = 0.30f)) }
            translate(size.width, 0f) { drawCobweb(web, Color.White.copy(alpha = 0.30f)) }
        }
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_badge_halloween),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(104.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Yougram",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = HalloweenOrange,
                )
                Text(
                    "🎃 Happy Halloween!",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}
