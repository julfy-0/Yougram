package app.yougram.feature.badge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.yougram.R

/** Названия видов значка: индекс совпадает со значением, которое пишется в «О себе». */
val BadgeStyleNames = listOf("Классика", "Сакура", "Неон", "Хеллоуин")

/**
 * Имена PNG в res/drawable для видов 1..3. Если файла нет, значок рисуется кодом:
 * 1 и 2 — форма Material Expressive с градиентом, 3 — тыква.
 */
private val BadgeStyleResNames = listOf("", "ic_badge_sakura", "ic_badge_neon", "ic_badge_halloween")

@Composable
fun BadgeStyleIcon(style: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resId = remember(style) {
        val name = BadgeStyleResNames.getOrNull(style).orEmpty()
        if (name.isEmpty()) 0 else context.resources.getIdentifier(name, "drawable", context.packageName)
    }
    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        return
    }
    if (style >= 3) {
        PumpkinBadge(modifier)
        return
    }
    val (shape, colors) = when (style) {
        1 -> MaterialShapes.Cookie12Sided.toShape() to listOf(Color(0xFFFF8FB8), Color(0xFFB65CFF))
        else -> MaterialShapes.Sunny.toShape() to listOf(Color(0xFF3DE6FF), Color(0xFF3D6BFF))
    }
    Box(
        modifier.clip(shape).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.fillMaxSize().scale(0.5f),
        )
    }
}

/** Хеллоуинский значок: тыква с вырезанным лицом. */
@Composable
private fun PumpkinBadge(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val orange = Brush.verticalGradient(listOf(Color(0xFFFFA63A), Color(0xFFE2570A)))
        val rib = Color(0xFFB8420A)
        val dark = Color(0xFF2B1200)
        val glow = Color(0xFFFFE27A)

        // Черенок.
        val stem = Path().apply {
            moveTo(w * 0.45f, h * 0.24f)
            lineTo(w * 0.41f, h * 0.05f)
            lineTo(w * 0.59f, h * 0.10f)
            lineTo(w * 0.56f, h * 0.24f)
            close()
        }
        drawPath(stem, Color(0xFF4E8F2F))

        // Тело: две боковые дольки и центральная.
        drawOval(orange, Offset(w * 0.03f, h * 0.22f), Size(w * 0.50f, h * 0.74f))
        drawOval(orange, Offset(w * 0.47f, h * 0.22f), Size(w * 0.50f, h * 0.74f))
        drawOval(orange, Offset(w * 0.22f, h * 0.17f), Size(w * 0.56f, h * 0.80f))
        val ribStroke = Stroke(width = w * 0.025f)
        drawOval(rib, Offset(w * 0.22f, h * 0.17f), Size(w * 0.56f, h * 0.80f), style = ribStroke)

        // Глаза-треугольники.
        val leftEye = Path().apply {
            moveTo(w * 0.28f, h * 0.60f)
            lineTo(w * 0.44f, h * 0.60f)
            lineTo(w * 0.36f, h * 0.44f)
            close()
        }
        val rightEye = Path().apply {
            moveTo(w * 0.56f, h * 0.60f)
            lineTo(w * 0.72f, h * 0.60f)
            lineTo(w * 0.64f, h * 0.44f)
            close()
        }
        drawPath(leftEye, dark)
        drawPath(rightEye, dark)
        drawPath(leftEye, glow.copy(alpha = 0.35f), style = Stroke(width = w * 0.012f))
        drawPath(rightEye, glow.copy(alpha = 0.35f), style = Stroke(width = w * 0.012f))

        // Зубастая улыбка.
        val mouth = Path().apply {
            moveTo(w * 0.26f, h * 0.70f)
            lineTo(w * 0.36f, h * 0.84f)
            lineTo(w * 0.42f, h * 0.75f)
            lineTo(w * 0.50f, h * 0.87f)
            lineTo(w * 0.58f, h * 0.75f)
            lineTo(w * 0.64f, h * 0.84f)
            lineTo(w * 0.74f, h * 0.70f)
            lineTo(w * 0.64f, h * 0.72f)
            lineTo(w * 0.50f, h * 0.76f)
            lineTo(w * 0.36f, h * 0.72f)
            close()
        }
        drawPath(mouth, dark)
    }
}

/** Превью вида значка для экрана выбора: 0 — классическая PNG-картинка. */
@Composable
fun BadgeStylePreview(style: Int, modifier: Modifier = Modifier) {
    if (style == 0) {
        Image(
            painter = painterResource(id = R.drawable.ic_badge_normal),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        BadgeStyleIcon(style, modifier)
    }
}