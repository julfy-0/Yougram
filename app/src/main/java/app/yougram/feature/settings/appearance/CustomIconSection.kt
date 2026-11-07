@file:OptIn(ExperimentalLayoutApi::class)

package app.yougram.feature.settings.appearance

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.util.CustomAppIcon
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Palette = listOf(
    0xFF3A3F47, 0xFF000000, 0xFFFFFFFF, 0xFF1E88E5, 0xFF00ACC1, 0xFF43A047,
    0xFFFDD835, 0xFFFB8C00, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1,
).map { it.toInt() }

/** Своя иконка: фото или цвета (фон, фигура, значок). Добавляется на рабочий стол ярлыком. */
@Composable
fun CustomIconSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var style by remember { mutableStateOf(CustomAppIcon.load(context)) }
    var version by remember { mutableIntStateOf(0) }

    fun update(next: CustomAppIcon.Style) {
        style = next
        CustomAppIcon.save(context, next)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { CustomAppIcon.importPhoto(context, uri) }
            if (ok) {
                version++
                update(style.copy(usePhoto = true))
            } else {
                Toast.makeText(context, tr("Не удалось загрузить фото"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val preview = remember(style, version) { CustomAppIcon.render(context, style, 216).asImageBitmap() }

    SectionLabel("Своя иконка")
    SettingGroup {
        item {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Лаунчер показывает центральные 72 из 108dp холста — предпросмотр увеличен так же.
                Image(
                    preview,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (style.usePhoto) "Другое фото" else "Выбрать фото") }
                    if (style.usePhoto) {
                        TextButton(onClick = { update(style.copy(usePhoto = false)) }) { Text("Убрать фото") }
                    }
                }
                if (!style.usePhoto) {
                    ColorRow("Фон", style.background) { update(style.copy(background = it)) }
                    ColorRow("Фигура", style.shape) { update(style.copy(shape = it)) }
                    ColorRow("Значок", style.glyph) { update(style.copy(glyph = it)) }
                }
                Button(onClick = {
                    val ok = runCatching { CustomAppIcon.requestPin(context, style) }.getOrDefault(false)
                    Toast.makeText(
                        context,
                        if (ok) tr("Подтвердите добавление на рабочий стол") else tr("Лаунчер не поддерживает добавление ярлыков"),
                        Toast.LENGTH_LONG,
                    ).show()
                }) { Text("Добавить на рабочий стол") }
                Text(
                    "Android не позволяет подменить саму иконку приложения произвольной картинкой, поэтому она добавляется отдельным ярлыком. Исходную иконку можно скрыть средствами лаунчера.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ColorRow(label: String, selected: Int, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Palette.forEach { argb ->
                val on = argb == selected
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(if (on) 3.dp else 1.dp, if (on) scheme.primary else scheme.outlineVariant, CircleShape)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onPick(argb) }),
                )
            }
        }
    }
}
