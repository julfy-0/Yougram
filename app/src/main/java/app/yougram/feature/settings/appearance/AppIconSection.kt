@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.settings.appearance

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import app.yougram.R
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
import app.yougram.core.util.AppIcon
import app.yougram.core.util.AppIconManager
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup

/** Выбор иконки приложения на рабочем столе: сетка плиток 2×N, выбранная плитка с «живой» формой. */
@Composable
fun AppIconSection() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppIconManager.current(context)) }

    fun choose(icon: AppIcon) {
        if (selected == icon) return
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

    SectionLabel("Иконка приложения")
    SettingGroup {
        item {
            FlowRow(
                modifier = Modifier.padding(12.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                AppIcon.entries.forEach { icon ->
                    AppIconTile(
                        icon = icon,
                        selected = selected == icon,
                        onClick = { choose(icon) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconTile(icon: AppIcon, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest.copy(alpha = 0.6f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "iconTile",
    )
    val low = rememberDeviceTier() == DeviceTier.Low
    val previewShape: Shape =
        if (selected && !low) MaterialShapes.Cookie9Sided.toShape() else RoundedCornerShape(20.dp)

    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${icon.title}. ${icon.subtitle}" }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            IconPreview(icon, previewShape)
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Text(
            icon.title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun IconPreview(icon: AppIcon, shape: Shape) {
    val context = LocalContext.current
    val bitmap = remember(icon) {
        val res = when (icon) {
            AppIcon.Classic -> R.mipmap.ic_launcher
            AppIcon.Neon -> R.mipmap.ic_launcher_neon
            AppIcon.Blue -> R.mipmap.ic_launcher_blue
            AppIcon.Green -> R.mipmap.ic_launcher_green
        }
        ContextCompat.getDrawable(context, res)?.toBitmap(192, 192)?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(72.dp).clip(shape))
    }
}