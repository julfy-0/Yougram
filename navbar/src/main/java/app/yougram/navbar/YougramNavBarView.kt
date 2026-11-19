package app.yougram.navbar

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext

/**
 * Навбар для приложений на обычных View/XML (без Compose в остальном UI).
 *
 * <app.yougram.navbar.YougramNavBarView android:id="@+id/navbar" ... />
 *
 * navbar.items = listOf(NavBarItem("home", "Главная", Icons.Filled.Home))
 * navbar.selectedId = "home"
 * navbar.onSelect = { id -> ... }
 */
class YougramNavBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var items: List<NavBarItem> by mutableStateOf(emptyList())
    var selectedId: String by mutableStateOf("")
    var onSelect: (String) -> Unit by mutableStateOf<(String) -> Unit>({ selectedId = it })
    var onSearch: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
    var glass: NavBarGlassSettings by mutableStateOf(NavBarGlassSettings())

    @Composable
    override fun Content() {
        YougramNavBarTheme {
            YougramNavBar(
                items = items,
                selectedId = selectedId,
                onSelect = {
                    selectedId = it
                    onSelect(it)
                },
                glass = glass,
                onSearch = onSearch,
                modifier = Modifier,
            )
        }
    }
}

/** Тема по умолчанию для встраивания: Material You на Android 12+, иначе стандартная светлая/тёмная. */
@Composable
fun YougramNavBarTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
