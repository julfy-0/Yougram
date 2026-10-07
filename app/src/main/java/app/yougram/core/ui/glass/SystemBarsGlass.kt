package app.yougram.core.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import app.yougram.core.settings.GlassSettings

/** Настройки стекла, доступные любому экрану (задаются в MainActivity). */
val LocalGlass = compositionLocalOf { GlassSettings() }

/**
 * Обёртка для экранов без собственных стеклянных панелей: содержимое заезжает под статус-бар
 * и навигацию, а под ними рисуется размытая копия этого содержимого.
 */
@Composable
fun SystemBarsGlass(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberBackdropState()
    val glass = LocalGlass.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .background(background),
            content = content,
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topInset)
                .glass(backdrop, glass, RectangleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset)
                .glass(backdrop, glass, RectangleShape),
        )
    }
}