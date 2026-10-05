package app.yougram.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import app.yougram.data.PlateArea
import app.yougram.data.PlateTransparency

val LocalPlates = compositionLocalOf { PlateTransparency() }

/** Цвет подложки под строками экрана [area] с учётом выбранной прозрачности. */
@Composable
fun plateColor(area: PlateArea): Color =
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f - LocalPlates.current.of(area))
