package app.yougram.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

private fun hsvColor(h: Float, s: Float, v: Float) =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

private fun hexOf(c: Color) = "%06X".format(c.toArgb() and 0xFFFFFF)

/** Палитра для своего акцентного цвета: оттенок / насыщенность / яркость и ввод HEX. */
@Composable
fun CustomAccentDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var h by remember { mutableFloatStateOf(start[0]) }
    var s by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2]) }
    val color = hsvColor(h, s, v)
    var hex by remember { mutableStateOf(hexOf(color)) }

    fun sync() { hex = hexOf(hsvColor(h, s, v)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Свой цвет") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(24.dp)).background(color),
                )
                GradientSlider(
                    label = "Оттенок",
                    value = h,
                    range = 0f..360f,
                    brush = Brush.horizontalGradient((0..6).map { hsvColor(it * 60f, 1f, 1f) }),
                    onChange = { h = it; sync() },
                )
                GradientSlider(
                    label = "Насыщенность",
                    value = s,
                    range = 0f..1f,
                    brush = Brush.horizontalGradient(listOf(hsvColor(h, 0f, v), hsvColor(h, 1f, v))),
                    onChange = { s = it; sync() },
                )
                GradientSlider(
                    label = "Яркость",
                    value = v,
                    range = 0f..1f,
                    brush = Brush.horizontalGradient(listOf(Color.Black, hsvColor(h, s, 1f))),
                    onChange = { v = it; sync() },
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { t ->
                        val clean = t.removePrefix("#").uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        hex = clean
                        if (clean.length == 6) {
                            val argb = (0xFF shl 24) or clean.toInt(16)
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(argb, hsv)
                            h = hsv[0]; s = hsv[1]; v = hsv[2]
                        }
                    },
                    prefix = { Text("#") },
                    label = { Text("HEX") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(color) }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Ползунок поверх цветного градиента вместо обычной дорожки. */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    brush: Brush,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(16.dp)).background(brush),
        ) {
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
