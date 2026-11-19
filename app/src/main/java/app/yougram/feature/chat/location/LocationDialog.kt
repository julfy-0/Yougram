package app.yougram.feature.chat.location

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import kotlin.math.roundToInt

private val LivePeriods = listOf(15 * 60 to "15 мин", 60 * 60 to "1 час", 8 * 60 * 60 to "8 часов")

/** Выбор: отправить текущее местоположение или транслировать его 15 минут, час или 8 часов. */
@Composable
fun LocationDialog(
    onDismiss: () -> Unit,
    onSend: (latitude: Double, longitude: Double, accuracy: Double, livePeriod: Int) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasLocationPermission(context)
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    var fix by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(granted) {
        val stop = if (granted) startLocationUpdates(context, 2_000L, 0f) { fix = it } else ({})
        onDispose { stop() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Геопозиция") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val f = fix
                when {
                    !granted -> {
                        Text("Разрешите доступ к геолокации, чтобы отправить своё местоположение.")
                        TextButton(onClick = {
                            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }) { Text("Разрешить") }
                    }
                    f == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Определяем местоположение…")
                    }
                    else -> {
                        MapPreview(
                            f.latitude, f.longitude,
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                        )
                        Text(
                            "Точность: ±${f.accuracy.roundToInt()} м",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { onSend(f.latitude, f.longitude, f.accuracy.toDouble(), 0) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Отправить моё местоположение") }
                        Text("Транслировать геопозицию", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((seconds, label) in LivePeriods) {
                                OutlinedButton(
                                    onClick = { onSend(f.latitude, f.longitude, f.accuracy.toDouble(), seconds) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) { Text(label, maxLines = 1) }
                            }
                        }
                        Text(
                            "Трансляция обновляется, пока приложение открыто.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
