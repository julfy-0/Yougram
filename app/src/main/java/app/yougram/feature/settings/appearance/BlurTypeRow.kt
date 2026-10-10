package app.yougram.feature.settings.appearance

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.BlurType
import app.yougram.feature.settings.component.SettingRow

/** Выбор алгоритма размытия фона под панелями. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlurTypeRow(selected: BlurType, onSelect: (BlurType) -> Unit) {
    val shaders = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val note = if (!shaders && selected != BlurType.Gaussian) " (на Android 12 работает как Гаусс)" else ""
    SettingRow(
        title = "Тип размытия",
        subtitle = selected.hint + note,
        icon = Icons.Filled.BlurOn,
        below = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BlurType.entries.forEach { type ->
                    FilterChip(
                        selected = type == selected,
                        onClick = { onSelect(type) },
                        label = { Text(type.title) },
                    )
                }
            }
        },
    )
}
