package app.yougram.feature.auth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import app.yougram.core.i18n.Text
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Вход по QR как в Telegram Web: QR на белой плашке + инструкция. Ссылку обновляет сам TDLib. */
@Composable
fun AuthQrStep(link: String) {
    val scheme = MaterialTheme.colorScheme
    val matrix = remember(link) {
        runCatching {
            QRCodeWriter().encode(
                link, BarcodeFormat.QR_CODE, 1, 1,
                mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        }.getOrNull()
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Вход по QR-коду", style = MaterialTheme.typography.titleLargeEmphasized)
        Spacer(Modifier.height(16.dp))

        // Белая плашка с чёрными модулями: так QR читается в любой теме.
        Column(
            Modifier
                .widthIn(max = 240.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (matrix == null) {
                LoadingIndicator(contained = true)
            } else {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val n = matrix.width
                    val cell = size.width / n
                    for (y in 0 until n) for (x in 0 until n) {
                        if (matrix.get(x, y)) {
                            drawRoundRect(
                                color = Color.Black,
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell + 0.6f, cell + 0.6f),
                                cornerRadius = CornerRadius(cell * 0.25f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        val steps = listOf(
            "1. Откройте Telegram на телефоне",
            "2. Настройки → Устройства → Подключить устройство",
            "3. Наведите камеру на этот QR-код",
        )
        steps.forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
    }
}
