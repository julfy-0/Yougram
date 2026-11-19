package app.yougram.navbar

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/** Одна вкладка навбара. [badge] > 0 рисует плашку с числом. */
@Immutable
class NavBarItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
)

/** Настройки стекла навбара. */
@Immutable
data class NavBarGlassSettings(
    /** Радиус размытия, dp. 0 — сплошная панель. */
    val blurRadius: Float = 24f,
    /** Плотность подкраски: 0 — прозрачная, 1 — сплошная. */
    val opacity: Float = 0.45f,
    /** true — Kawase (Android 13+), false — гауссово размытие. */
    val kawase: Boolean = false,
)
