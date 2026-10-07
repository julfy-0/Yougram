package app.yougram.feature.settings.general

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yougram.core.settings.ChatPrefs
import app.yougram.core.settings.DistanceUnit
import app.yougram.core.settings.MicrophoneSource
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.settings.SwipeAction
import app.yougram.core.settings.ThemeMode
import app.yougram.core.ui.component.ChatWallpaper
import app.yougram.core.ui.component.chatWallpaperFile
import app.yougram.core.ui.component.saveChatWallpaper
import app.yougram.core.ui.theme.Accents
import app.yougram.feature.settings.SettingsPage
import app.yougram.feature.settings.component.ChoiceDialog
import app.yougram.feature.settings.component.DotSlider
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.component.SwitchRow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class ChatDialog { NameColor, Microphone, Distance }

/** Палитра цветов имени (как в Telegram). */
private val NameColors = listOf(
    "Красный" to Color(0xFFCC5049),
    "Оранжевый" to Color(0xFFD67722),
    "Фиолетовый" to Color(0xFF955CDB),
    "Зелёный" to Color(0xFF40A920),
    "Бирюзовый" to Color(0xFF309EBA),
    "Синий" to Color(0xFF368AD1),
    "Розовый" to Color(0xFFC7508B),
)

private val ThemeEmojis = listOf("🏠", "🐥", "⛄", "💎", "🌋", "🔮")

@Composable
fun ChatSettingsScreen(
    settings: SettingsRepository,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
) {
    val prefs by settings.chatPrefs.collectAsState()
    val theme by settings.theme.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<ChatDialog?>(null) }
    fun update(transform: (ChatPrefs) -> ChatPrefs) = settings.updateChatPrefs(transform)

    val dark = theme.mode == ThemeMode.Dark || (theme.mode == ThemeMode.System && isSystemInDarkTheme())

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                if (saveChatWallpaper(context, uri)) {
                    update { it.copy(wallpaper = System.currentTimeMillis()) }
                } else {
                    Toast.makeText(context, "Не удалось загрузить картинку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SettingsPageColumn(contentPadding) {
        SectionLabel("Размер текста сообщений")
        SettingGroup {
            item { SliderRow(prefs.textSize, 12..30) { v -> update { it.copy(textSize = v) } } }
            item {
                Column {
                    ChatPreview(prefs)
                    SettingRow(
                        "Изменить обои",
                        icon = Icons.Filled.Wallpaper,
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                    if (prefs.wallpaper != 0L) {
                        SettingRow(
                            "Убрать обои",
                            icon = Icons.Filled.Delete,
                            onClick = {
                                chatWallpaperFile(context).delete()
                                update { it.copy(wallpaper = 0L) }
                            },
                        )
                    }
                    val nameColor = NameColors[prefs.nameColor.coerceIn(NameColors.indices)].second
                    SettingRow(
                        "Изменить цвет имени",
                        icon = Icons.Filled.Palette,
                        onClick = { dialog = ChatDialog.NameColor },
                        trailing = {
                            Surface(shape = CircleShape, color = nameColor.copy(alpha = 0.22f)) {
                                Text(
                                    "Julfy",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = nameColor,
                                )
                            }
                        },
                    )
                }
            }
        }

        SectionLabel("Цветовая тема")
        SettingGroup {
            item {
                Column {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(Accents) { index, accent ->
                            ThemeCard(
                                color = accent.color,
                                emoji = ThemeEmojis.getOrElse(index) { "🎨" },
                                selected = !theme.dynamic && theme.accent == index,
                                onClick = {
                                    settings.setDynamicColor(false)
                                    settings.setAccent(index)
                                },
                            )
                        }
                    }
                    SettingRow(
                        if (dark) "Переключить на дневную тему" else "Переключить на ночную тему",
                        icon = if (dark) Icons.Filled.WbSunny else Icons.Filled.DarkMode,
                        onClick = { settings.setThemeMode(if (dark) ThemeMode.Light else ThemeMode.Dark) },
                    )
                    SettingRow(
                        "Настройки темы",
                        icon = Icons.Filled.FormatPaint,
                        onClick = { onNavigate(SettingsPage.Appearance) },
                    )
                }
            }
        }

        SectionLabel("Углы блоков с сообщениями")
        SettingGroup {
            item { SliderRow(prefs.bubbleRadius, 0..28) { v -> update { it.copy(bubbleRadius = v) } } }
        }

        SectionLabel("Прозрачность блоков с сообщениями")
        SettingGroup {
            item { SliderRow(prefs.bubbleOpacity, 20..100) { v -> update { it.copy(bubbleOpacity = v) } } }
            item {
                SwitchRow(
                    "Размывать фон под сообщениями",
                    prefs.bubbleBlur,
                    { v -> update { it.copy(bubbleBlur = v) } },
                    subtitle = "Обои чата просвечивают сквозь сообщения размытыми. Заметно при прозрачности ниже 100",
                )
            }
        }

        SectionLabel("Список чатов")
        SettingGroup {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ListStyleOption("Двустрочный", 2, prefs.listLines == 2, Modifier.weight(1f)) {
                        update { it.copy(listLines = 2) }
                    }
                    ListStyleOption("Трёхстрочный", 3, prefs.listLines == 3, Modifier.weight(1f)) {
                        update { it.copy(listLines = 3) }
                    }
                }
            }
        }

        SectionLabel("Кнопка поиска")
        SettingGroup {
            item {
                SwitchRow(
                    "Поиск в верхней панели",
                    prefs.searchOnTop,
                    { v -> update { it.copy(searchOnTop = v) } },
                )
            }
        }
        SettingsFootnote("Включено — кнопка поиска в верхней панели. Выключено — рядом с нижней панелью вкладок.")

        SectionLabel("Смахивание влево в списке чатов")
        SettingGroup {
            item { SwipeActionPicker(prefs.swipeAction) { a -> update { it.copy(swipeAction = a) } } }
        }
        SettingsFootnote("Выбор действия, которое будет выполняться при смахивании влево в списке чатов.")

        SettingGroup {
            item {
                SwitchRow(
                    "Смена темы ночью",
                    prefs.nightAuto,
                    { v -> update { it.copy(nightAuto = v) } },
                    subtitle = if (prefs.nightAuto) "Включена" else "Выключена",
                    icon = Icons.Filled.NightsStay,
                )
            }
            item {
                SwitchRow(
                    "Встроенный браузер",
                    prefs.inAppBrowser,
                    { v -> update { it.copy(inAppBrowser = v) } },
                    subtitle = "Открывать ссылки в приложении",
                    icon = Icons.Filled.Public,
                )
            }
            item {
                SettingRow(
                    "Анимации",
                    subtitle = "Настройки для экономии заряда",
                    icon = Icons.Filled.PlayCircleOutline,
                    onClick = { onNavigate(SettingsPage.PowerSaving) },
                )
            }
            item {
                SettingRow(
                    "Стикеры и эмодзи",
                    subtitle = "Настройки стикеров, эмодзи и реакций",
                    icon = Icons.Filled.EmojiEmotions,
                    onClick = { Toast.makeText(context, "Скоро", Toast.LENGTH_SHORT).show() },
                )
            }
        }

        SectionLabel("Медиафайлы и звук")
        SettingGroup {
            item {
                SwitchRow(
                    "Листать медиафайлы по нажатию",
                    prefs.mediaTapFlip,
                    { v -> update { it.copy(mediaTapFlip = v) } },
                    subtitle = "Листать нажатием у края экрана в режиме просмотра медиафайлов",
                )
            }
            item {
                SwitchRow(
                    "Поднести и слушать",
                    prefs.raiseToListen,
                    { v -> update { it.copy(raiseToListen = v) } },
                    subtitle = "Переключаться на верхний динамик, когда Вы подносите телефон к уху",
                )
            }
            item {
                SwitchRow(
                    "Поднести и говорить",
                    prefs.raiseToSpeak,
                    { v -> update { it.copy(raiseToSpeak = v) } },
                    subtitle = "Записывать голосовое сообщение, когда Вы подносите телефон к уху",
                )
            }
            item {
                SwitchRow(
                    "Пауза музыки при записи",
                    prefs.pauseMusicOnRecord,
                    { v -> update { it.copy(pauseMusicOnRecord = v) } },
                    subtitle = "Останавливать музыку на время записи видеосообщения",
                )
            }
            item {
                SwitchRow(
                    "Пауза музыки при запуске медиа",
                    prefs.pauseMusicOnMedia,
                    { v -> update { it.copy(pauseMusicOnMedia = v) } },
                )
            }
            item {
                SettingRow(
                    "Микрофон",
                    value = prefs.microphone.label,
                    onClick = { dialog = ChatDialog.Microphone },
                )
            }
        }

        SectionLabel("Прочие настройки")
        SettingGroup {
            item {
                SwitchRow(
                    "Direct Share",
                    prefs.directShare,
                    { v -> update { it.copy(directShare = v) } },
                    subtitle = "Показывать недавние чаты в меню",
                )
            }
            item {
                SwitchRow(
                    "Показывать материалы 18+",
                    prefs.showSensitive,
                    { v -> update { it.copy(showSensitive = v) } },
                    subtitle = "Не скрывать медиа, предназначенные только для взрослых",
                )
            }
            item {
                SwitchRow(
                    "Отправка по Enter",
                    prefs.enterToSend,
                    { v -> update { it.copy(enterToSend = v) } },
                )
            }
            item {
                SettingRow(
                    "Мера расстояния",
                    value = prefs.distanceUnit.label,
                    onClick = { dialog = ChatDialog.Distance },
                )
            }
        }
        SettingsFootnote("Применяются: размер текста, углы, прозрачность и размытие сообщений, обои, отправка по Enter и строки в списке чатов. Остальное пока только сохраняется.")
    }

    when (dialog) {
        ChatDialog.NameColor -> NameColorDialog(
            selected = prefs.nameColor,
            onSelect = { i ->
                update { it.copy(nameColor = i) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        ChatDialog.Microphone -> ChoiceDialog(
            title = "Микрофон",
            options = MicrophoneSource.entries,
            selected = prefs.microphone,
            label = { it.label },
            onSelect = { v ->
                update { it.copy(microphone = v) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        ChatDialog.Distance -> ChoiceDialog(
            title = "Мера расстояния",
            options = DistanceUnit.entries,
            selected = prefs.distanceUnit,
            label = { it.label },
            onSelect = { v ->
                update { it.copy(distanceUnit = v) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** Ползунок с числом справа. */
@Composable
private fun SliderRow(value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotSlider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            value.toString(),
            modifier = Modifier.widthIn(min = 24.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
        )
    }
}

/** Образец чата: размер текста, углы пузырей, обои и цвет имени меняются на глазах. */
@Composable
private fun ChatPreview(prefs: ChatPrefs) {
    val nameColor = NameColors[prefs.nameColor.coerceIn(NameColors.indices)].second
    val shape = RoundedCornerShape(prefs.bubbleRadius.dp)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = prefs.textSize.sp,
        lineHeight = (prefs.textSize * 1.4f).sp,
    )
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        if (prefs.wallpaper != 0L) ChatWallpaper(prefs.wallpaper, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = prefs.bubbleOpacity / 100f),
                modifier = Modifier.align(Alignment.Start).widthIn(max = 280.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(nameColor))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Julfy | Tech", style = MaterialTheme.typography.labelLarge, color = nameColor, fontWeight = FontWeight.Medium)
                            Text("Доброе утро! 👋", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Знаешь, который час?", style = textStyle)
                    Text(
                        "14:26",
                        modifier = Modifier.align(Alignment.End),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = prefs.bubbleOpacity / 100f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.End),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("В Токио утро 😎", style = textStyle)
                    Spacer(Modifier.width(8.dp))
                    Text("14:41", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Карточка цветовой темы: пузырь акцентного цвета, серые заглушки и эмодзи. */
@Composable
private fun ThemeCard(color: Color, emoji: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .size(width = 84.dp, height = 116.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.align(Alignment.End).size(width = 48.dp, height = 16.dp).clip(CircleShape).background(color))
        Box(Modifier.align(Alignment.Start).size(width = 40.dp, height = 16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)))
        Spacer(Modifier.weight(1f))
        Text(emoji, fontSize = 22.sp)
    }
}

/** Вариант списка чатов: макет из двух строк и переключатель. */
@Composable
private fun ListStyleOption(label: String, lines: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = onSurface.copy(alpha = 0.06f),
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(2) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.25f)))
                            Spacer(Modifier.width(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(lines) { i ->
                                    Box(
                                        Modifier
                                            .size(width = (44 - i * 8).dp, height = 4.dp)
                                            .clip(CircleShape)
                                            .background(onSurface.copy(alpha = if (i == 0) 0.4f else 0.22f)),
                                    )
                                }
                            }
                        }
                    }
                }
                RadioButton(selected = selected, onClick = null)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun SwipeAction.icon() = when (this) {
    SwipeAction.Delete -> Icons.Filled.Delete
    SwipeAction.ChangeFolder -> Icons.Filled.Folder
    SwipeAction.Pin -> Icons.Filled.PushPin
}

/** Слева макет строки чата с кнопкой действия, справа список вариантов. */
@Composable
private fun SwipeActionPicker(selected: SwipeAction, onSelect: (SwipeAction) -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight().background(onSurface.copy(alpha = 0.06f)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.25f)))
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.35f)))
                    Box(Modifier.size(width = 28.dp, height = 4.dp).clip(CircleShape).background(onSurface.copy(alpha = 0.2f)))
                }
            }
            Box(
                Modifier.width(56.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(selected.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SwipeAction.entries.forEach { action ->
                val isSelected = action == selected
                Text(
                    action.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSelect(action) }
                        .padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun NameColorDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Цвет имени") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NameColors.chunked(4).forEachIndexed { row, chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        chunk.forEachIndexed { col, (name, color) ->
                            val index = row * 4 + col
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(color).clickable { onSelect(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (index == selected) {
                                    Icon(Icons.Filled.Check, contentDescription = name, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}