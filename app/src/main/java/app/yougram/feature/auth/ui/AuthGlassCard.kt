package app.yougram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.yougram.feature.splash.ui.HalloweenEvent

/**
 * Полупрозрачная «стеклянная» карточка: фон за ней уже размыт, сверху лёгкий блик.
 * Пока идёт хэллоуинский ивент, у неё тонкая оранжевая рамка.
 */
@Composable
fun AuthGlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val top = scheme.surface.copy(alpha = if (dark) 0.64f else 0.74f)
    val bottom = scheme.surface.copy(alpha = if (dark) 0.46f else 0.58f)
    val shape = MaterialTheme.shapes.extraLargeIncreased
    val halloween = HalloweenEvent.isActive()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .then(
                if (halloween) Modifier.border(1.dp, Color(0xFFFF8A1F).copy(alpha = 0.45f), shape) else Modifier,
            )
            .padding(24.dp),
        content = content,
    )
}