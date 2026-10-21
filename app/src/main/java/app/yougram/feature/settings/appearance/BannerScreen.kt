package app.yougram.feature.settings.appearance

import app.yougram.core.i18n.tr
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.yougram.core.ui.component.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import app.yougram.core.ui.component.OutlinedButton
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.ui.component.BannerPalettes
import app.yougram.core.ui.component.BannerPatternNames
import app.yougram.core.ui.component.BannerShapeNames
import app.yougram.core.ui.component.CustomBannerImage
import app.yougram.core.ui.component.ProfileBanner
import app.yougram.core.ui.component.deleteCustomBanner
import app.yougram.core.ui.component.saveCustomBanner
import app.yougram.feature.badge.data.YougramBanner
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SwitchRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import app.yougram.feature.settings.component.SettingsPageColumn
import kotlinx.coroutines.launch

private data class BannerPreset(val name: String, val banner: YougramBanner)

private val Presets = listOf(
    BannerPreset("Закат", YougramBanner(palette = 1, pattern = 4, shape = 0)),
    BannerPreset("Неон", YougramBanner(palette = 4, pattern = 1, shape = 3)),
    BannerPreset("Изумруд", YougramBanner(palette = 2, pattern = 3, shape = 0)),
    BannerPreset("Космос", YougramBanner(palette = 14, pattern = 1, shape = 3)),
    BannerPreset("Огонь", YougramBanner(palette = 11, pattern = 2, shape = 1)),
    BannerPreset("Золото", YougramBanner(palette = 3, pattern = 5, shape = 0)),
    BannerPreset("Киберпанк", YougramBanner(palette = 8, pattern = 1, shape = 2)),
    BannerPreset("Минимализм", YougramBanner(palette = 9, pattern = 0, shape = 0)),
)

/** Редактор баннера профиля. Баннер увидят только пользователи Yougram. */
@Composable
fun BannerScreen(settings: SettingsRepository, contentPadding: PaddingValues) {
    val saved by settings.banner.collectAsState()
    val badge by settings.badge.collectAsState()
    val customVersion by settings.customBanner.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(saved ?: YougramBanner()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (saveCustomBanner(context, uri)) {
                settings.setCustomBanner(System.currentTimeMillis())
            } else {
                Toast.makeText(context, tr("Не удалось загрузить картинку"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val badgeStyle by settings.badgeStyle.collectAsState()

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item {
                SwitchRow(
                    "Значок Yougram",
                    badge,
                    { v -> settings.setBadge(v) },
                    subtitle = "Ставит метку в «О себе»: другие пользователи Yougram увидят значок у вашего имени",
                    icon = Icons.Filled.VerifiedUser,
                )
            }
        }
        SectionLabel("Значок рядом с именем")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { style ->
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .then(
                            if (badgeStyle == style) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                            else Modifier,
                        )
                        .clickable { settings.setBadgeStyle(style) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    app.yougram.feature.badge.ui.BadgeStylePreview(style, Modifier.size(40.dp))
                    Text(
                        app.yougram.feature.badge.ui.BadgeStyleNames[style],
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Text(
            "Другие пользователи Yougram увидят этот значок рядом с вашим именем. Для остальных приложений он невидим.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )

        if (customVersion > 0L) {
            CustomBannerImage(customVersion, Modifier.fillMaxWidth().height(140.dp))
        } else {
            ProfileBanner(draft, Modifier.fillMaxWidth().height(140.dp))
        }

        SectionLabel("Своя картинка")
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (customVersion > 0L) "Заменить картинку" else "Выбрать картинку") }
        if (customVersion > 0L) {
            OutlinedButton(
                onClick = {
                    deleteCustomBanner(context)
                    settings.setCustomBanner(0L)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Вернуться к шаблонам") }
        }
        Text(
            tr("Картинка хранится только на этом устройстве и заменяет шаблон в вашем профиле. ") +
                    tr("Другим пользователям Yougram по-прежнему виден выбранный ниже шаблон: в «О себе» помещаются только 12 бит."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )

        SectionLabel("Быстрые шаблоны")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Presets.forEach { preset ->
                FilterChip(
                    selected = draft == preset.banner,
                    onClick = { draft = preset.banner },
                    label = { Text(preset.name) },
                )
            }
        }

        SectionLabel("Цвет")
        BannerPalettes.withIndex().chunked(8).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (index, colors) ->
                    val selected = draft.palette == index
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(colors.first, colors.second)))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                            .clickable { draft = draft.copy(palette = index) },
                    )
                }
            }
        }

        SectionLabel("Узор")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BannerPatternNames.forEachIndexed { index, name ->
                FilterChip(
                    selected = draft.pattern == index,
                    onClick = { draft = draft.copy(pattern = index) },
                    label = { Text(name) },
                )
            }
        }

        SectionLabel("Градиент")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BannerShapeNames.forEachIndexed { index, name ->
                FilterChip(
                    selected = draft.shape == index,
                    onClick = { draft = draft.copy(shape = index) },
                    label = { Text(name) },
                )
            }
        }

        Button(
            onClick = {
                settings.setBanner(draft)
                Toast.makeText(context, tr("Баннер сохранён"), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text("Применить") }
        if (saved != null) {
            OutlinedButton(
                onClick = { settings.setBanner(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Убрать баннер") }
        }

        Text(
            buildString {
                append(tr("Баннер записывается невидимыми символами в «О себе» (занимает 10 из 70 символов) "))
                append(tr("и виден только в Yougram: для остальных приложений профиль выглядит как обычно. "))
                append(tr("Если скрыть «О себе» в настройках приватности, баннер тоже станет невидим."))
                if (!badge) append(tr("\n\nСейчас значок Yougram выключен, поэтому баннер не публикуется. Включите значок в разделе «Режим призрака и шпион»."))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}