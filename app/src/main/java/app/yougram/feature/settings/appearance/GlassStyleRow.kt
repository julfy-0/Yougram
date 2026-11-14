package app.yougram.feature.settings.appearance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.Composable
import app.yougram.core.settings.GlassStyle
import app.yougram.feature.settings.component.ConnectedChoiceGroup
import app.yougram.feature.settings.component.SettingRow

/** Выбор стиля стеклянных панелей: классический или «жидкое стекло» как в iOS. */
@Composable
fun GlassStyleRow(selected: GlassStyle, onSelect: (GlassStyle) -> Unit) {
    SettingRow(
        title = "Стиль стекла",
        subtitle = selected.hint,
        icon = Icons.Filled.AutoAwesome,
        belowFullWidth = true,
        below = {
            ConnectedChoiceGroup(
                options = GlassStyle.entries,
                selected = selected,
                label = { it.title },
                onSelect = onSelect,
            )
        },
    )
}
