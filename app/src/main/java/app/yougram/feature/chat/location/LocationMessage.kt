package app.yougram.feature.chat.location

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.ui.component.openExternally
import kotlinx.coroutines.delay

private fun openMap(context: Context, item: LocationItem) {
    val lat = item.latitude
    val lon = item.longitude
    val label = item.title.takeIf { it.isNotBlank() }?.let { "(" + Uri.encode(it) + ")" }.orEmpty()
    val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon$label")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        openExternally(context, "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=16/$lat/$lon")
    }
}

private fun remainingText(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600} ч ${(seconds % 3600) / 60} мин"
    seconds >= 60 -> "${seconds / 60} мин"
    else -> "меньше минуты"
}

/** Геопозиция, место или трансляция геопозиции внутри пузыря сообщения. */
@Composable
fun LocationMessage(
    item: LocationItem,
    mine: Boolean,
    onStopLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis() / 1000, item.liveUntil, item.livePeriod) {
        if (!item.isLive) return@produceState
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis() / 1000
        }
    }
    val active = item.isLive && item.liveUntil > now
    val muted = LocalContentColor.current.copy(alpha = 0.7f)

    Column(modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MapPreview(
            latitude = item.latitude,
            longitude = item.longitude,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .clickable { openMap(context, item) },
        )
        if (item.title.isNotBlank()) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(horizontal = 6.dp))
        }
        if (item.address.isNotBlank()) {
            Text(item.address, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, modifier = Modifier.padding(horizontal = 6.dp))
        }
        if (item.isLive) {
            Text(
                if (active) "Трансляция геопозиции · осталось ${remainingText(item.liveUntil - now)}"
                else "Трансляция геопозиции завершена",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            if (active && mine) {
                TextButton(onClick = onStopLive) { Text("Остановить трансляцию") }
            }
        }
    }
}
