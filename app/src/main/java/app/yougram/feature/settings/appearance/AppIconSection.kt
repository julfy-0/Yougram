package app.yougram.feature.settings.appearance

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import app.yougram.R
import app.yougram.core.util.AppIcon
import app.yougram.core.util.AppIconManager
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow

/** Выбор иконки приложения на рабочем столе. */
@Composable
fun AppIconSection() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppIconManager.current(context)) }

    SectionLabel("Иконка приложения")
    SettingGroup {
        AppIcon.entries.forEach { icon ->
            item {
                SettingRow(
                    title = icon.title,
                    subtitle = icon.subtitle,
                    icon = Icons.Filled.Apps,
                    onClick = {
                        if (selected != icon) {
                            runCatching { AppIconManager.set(context, icon) }
                                .onSuccess {
                                    selected = icon
                                    Toast.makeText(
                                        context,
                                        "Иконка изменена, лаунчер может обновить её через несколько секунд",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                .onFailure {
                                    Toast.makeText(context, "Не удалось сменить иконку", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    trailing = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconPreview(icon)
                            if (selected == icon) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Выбрано",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun IconPreview(icon: AppIcon) {
    val context = LocalContext.current
    val bitmap = remember(icon) {
        val res = when (icon) {
            AppIcon.Classic -> R.mipmap.ic_launcher
            AppIcon.Neon -> R.mipmap.ic_launcher_neon
            AppIcon.Blue -> R.mipmap.ic_launcher_blue
            AppIcon.Green -> R.mipmap.ic_launcher_green
        }
        ContextCompat.getDrawable(context, res)?.toBitmap(160, 160)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = icon.title, modifier = Modifier.size(48.dp))
    }
}
