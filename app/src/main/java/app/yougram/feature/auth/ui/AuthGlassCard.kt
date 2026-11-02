package app.yougram.feature.auth.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.yougram.core.settings.BlurType
import app.yougram.core.settings.GlassSettings
import app.yougram.core.ui.glass.BackdropState
import app.yougram.core.ui.glass.glass
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.yougram.feature.splash.ui.HalloweenEvent

/**
 * Полупрозрачная «стеклянная» карточка: фон за ней уже размыт, сверху лёгкий блик.
 * Пока идёт хэллоуинский ивент, у неё тонкая оранжевая рамка.
 */
@Composable
fun AuthGlassCard(
    backdrop: BackdropState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    // Радиус 48dp = внешний радиус кнопок (28dp) + внутренний отступ (20dp), поэтому углы подложки и кнопок идут «параллельно».
    val shape = RoundedCornerShape(48.dp)
    val halloween = HalloweenEvent.isActive()
    // Настоящее размытие фона за карточкой (тот же механизм, что у панелей чата) + подкраска цветом темы.
    val glass = remember(dark) {
        GlassSettings(blurRadius = 32f, opacity = if (dark) 0.55f else 0.62f, blurType = BlurType.Gaussian)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(backdrop, glass, shape, scheme.surfaceContainerHigh)
            .then(
                if (halloween) Modifier.border(1.dp, Color(0xFFFF8A1F).copy(alpha = 0.45f), shape) else Modifier,
            )
            .padding(20.dp),
        content = content,
    )
}