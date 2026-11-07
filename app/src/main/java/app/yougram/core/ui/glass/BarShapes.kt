package app.yougram.core.ui.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.GlassSettings

/** Единая форма верхних панелей на всех экранах: скруглены снизу. */
val TopBarShape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)

/** Единая форма нижних панелей (поле ввода): скруглены сверху. */
val BottomBarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** Цвет панелей на всю ширину: чуть светлее поверхности (как у прямоугольных панелей по умолчанию). */
@Composable
fun barTint(): Color = lerp(MaterialTheme.colorScheme.surface, Color.White, 0.10f)

/** Стеклянная верхняя панель со скруглёнными нижними углами. */
@Composable
fun Modifier.glassTopBar(state: BackdropState, settings: GlassSettings, tint: Color = barTint()): Modifier =
    glass(state, settings, TopBarShape, tint)