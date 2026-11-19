package app.yougram.navbar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Готовая обёртка: контент заезжает под стеклянный навбар, под системной панелью навигации тоже стекло.
 * [content] получает отступы, которые нужно передать спискам (contentPadding).
 */
@Composable
fun YougramNavBarScaffold(
    items: List<NavBarItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    glass: NavBarGlassSettings = NavBarGlassSettings(),
    onSearch: (() -> Unit)? = null,
    hidden: Boolean = false,
    background: Color = MaterialTheme.colorScheme.background,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val backdrop = rememberBackdropState()
    val bottomInset = YougramNavBarDefaults.navigationBarInset()
    val navProgress by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "navbar-hide",
    )
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .background(background),
        ) {
            content(PaddingValues(bottom = bottomInset + YougramNavBarDefaults.TotalHeight + 16.dp))
        }
        YougramNavBar(
            items = items,
            selectedId = selectedId,
            onSelect = onSelect,
            backdrop = backdrop,
            glass = glass,
            onSearch = onSearch,
            bottomInset = bottomInset,
            progress = { navProgress },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset)
                .navGlass(backdrop, glass, RectangleShape, YougramNavBarDefaults.tint()),
        )
    }
}
