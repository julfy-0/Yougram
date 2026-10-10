package app.yougram.feature.settings.appearance

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.runtime.Composable
import app.yougram.core.settings.BlurType
import app.yougram.feature.settings.component.ConnectedChoiceGroup
import app.yougram.feature.settings.component.SettingRow

/** Выбор алгоритма размытия фона под панелями: Гаусс / Box / Kawase / Боке в связанной группе кнопок. */
@Composable
fun BlurTypeRow(selected: BlurType, onSelect: (BlurType) -> Unit) {
    val shaders = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val note = if (!shaders && selected != BlurType.Gaussian) " (на Android 12 работает как Гаусс)" else ""
    SettingRow(
        title = "Тип размытия",
        subtitle = selected.hint + note,
        icon = Icons.Filled.BlurOn,
        belowFullWidth = true,
        below = {
            ConnectedChoiceGroup(
                options = BlurType.entries,
                selected = selected,
                label = { it.title },
                onSelect = onSelect,
            )
        },
    )
}