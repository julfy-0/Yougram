package app.yougram.feature.settings.privacy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Иллюстрация ноутбука с окном Telegram Desktop. */
@Composable
fun LaptopIllustration(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(width = 150.dp, height = 112.dp)) {
        val w = size.width
        val h = size.height
        val frame = Color(0xFF3A3A3F)
        val screenH = h * 0.72f

        // Крышка и экран
        drawRoundRect(frame, Offset(w * 0.1f, 0f), Size(w * 0.8f, screenH), CornerRadius(10.dp.toPx()))
        val sx = w * 0.1f + 6.dp.toPx()
        val sy = 6.dp.toPx()
        val sw = w * 0.8f - 12.dp.toPx()
        val sh = screenH - 12.dp.toPx()
        drawRoundRect(Color(0xFFF4F6F8), Offset(sx, sy), Size(sw, sh), CornerRadius(4.dp.toPx()))

        // Список чатов слева
        val listW = sw * 0.34f
        drawRect(Color.White, Offset(sx, sy), Size(listW, sh))
        val dots = listOf(Color(0xFF8E6CD8), accent, Color(0xFFEF8A3C), Color(0xFF4CAF50), Color(0xFFE5534B))
        val rowH = sh / 6f
        dots.forEachIndexed { i, c ->
            val cy = sy + rowH * (i + 0.8f)
            if (i == 1) drawRect(accent.copy(alpha = 0.18f), Offset(sx, cy - rowH * 0.5f), Size(listW, rowH))
            drawCircle(c, 3.5.dp.toPx(), Offset(sx + 7.dp.toPx(), cy))
            drawRoundRect(Color(0xFFCFD4D9), Offset(sx + 14.dp.toPx(), cy - 2.dp.toPx()), Size(listW - 20.dp.toPx(), 3.dp.toPx()), CornerRadius(2.dp.toPx()))
        }

        // Переписка справа
        val cx = sx + listW + 6.dp.toPx()
        val cw = sw - listW - 12.dp.toPx()
        drawRoundRect(Color(0xFFFFFFFF), Offset(cx, sy + sh * 0.18f), Size(cw * 0.6f, sh * 0.16f), CornerRadius(4.dp.toPx()))
        drawRoundRect(Color(0xFFDCF0C8), Offset(cx + cw * 0.35f, sy + sh * 0.42f), Size(cw * 0.65f, sh * 0.2f), CornerRadius(4.dp.toPx()))
        drawRoundRect(Color(0xFFFFFFFF), Offset(cx, sy + sh * 0.70f), Size(cw * 0.5f, sh * 0.16f), CornerRadius(4.dp.toPx()))

        // Основание с клавиатурой
        val baseY = screenH
        drawRoundRect(Color(0xFF2C2C30), Offset(0f, baseY), Size(w, h - baseY), CornerRadius(6.dp.toPx()))
        drawRoundRect(Color(0xFFBDBDC2), Offset(w * 0.04f, baseY + 3.dp.toPx()), Size(w * 0.92f, h - baseY - 6.dp.toPx()), CornerRadius(4.dp.toPx()))
        drawRoundRect(Color(0xFFD6D6DA), Offset(w * 0.38f, baseY + (h - baseY) * 0.5f), Size(w * 0.24f, (h - baseY) * 0.38f), CornerRadius(3.dp.toPx()))
    }
}
