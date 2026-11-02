package app.yougram.feature.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.height
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import app.yougram.core.settings.GlassSettings
import app.yougram.core.ui.glass.BackdropState
import app.yougram.core.ui.glass.glass
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.ui.component.Button
import app.yougram.core.ui.component.TextButton
import app.yougram.core.ui.theme.LocalChatBackground
import app.yougram.core.ui.wallpaper.WallpaperBackground
import app.yougram.core.ui.wallpaper.WallpaperKind
import app.yougram.core.ui.wallpaper.WallpaperStore
import app.yougram.feature.assistant.data.AiAction
import app.yougram.feature.assistant.data.AiAssistant
import app.yougram.feature.assistant.data.AiMessage
import app.yougram.feature.assistant.data.AiModelState
import app.yougram.feature.assistant.data.AiModel
import app.yougram.feature.assistant.data.AiModelManager
import app.yougram.feature.settings.extras.PendingPlugin
import app.yougram.feature.settings.extras.PluginScanDialog
import app.yougram.plugin.SuspiciousPluginException
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import app.yougram.feature.settings.SettingsPage

/**
 * Страница «ИИ-ассистент»: оформлена как обычный чат (обои, пузыри с цветами и скруглением из настроек чатов,
 * поле ввода с круглой кнопкой). Нижняя панель вкладок на этой странице скрыта (см. MainScreen).
 */
@Composable
fun AssistantScreen(
    assistant: AiAssistant,
    settings: SettingsRepository,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
    plugins: app.yougram.plugin.NativePluginManager,
) {
    val model by assistant.models.state.collectAsState()
    val context = LocalContext.current
    val importScope = androidx.compose.runtime.rememberCoroutineScope()
    var confirmImport by remember { mutableStateOf(false) }
    var pendingScan by remember { mutableStateOf<PendingPlugin?>(null) }
    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importScope.launch {
            val temp = java.io.File(context.cacheDir, "plugin-import-${System.nanoTime()}.ygplugin")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                        ?: error("Не удалось прочитать файл")
                    plugins.installFromZip(temp)
                }
            }
            val err = result.exceptionOrNull()
            if (err is SuspiciousPluginException) {
                pendingScan = PendingPlugin(temp, err.report)
                return@launch
            }
            withContext(Dispatchers.IO) { temp.delete() }
            assistant.report(
                result.fold(
                    onSuccess = { "Плагин «${it.manifest.name}» установлен (v${it.manifest.version}). Проверка на вредоносный код пройдена." },
                    onFailure = { "Не удалось установить плагин: ${it.message}" },
                ),
            )
        }
    }
    pendingScan?.let { p ->
        PluginScanDialog(
            p,
            onInstall = {
                pendingScan = null
                importScope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { plugins.installFromZip(p.file, allowSuspicious = true) }.also { p.file.delete() }
                    }
                    assistant.report(
                        r.fold(
                            onSuccess = { "Плагин «${it.manifest.name}» установлен (v${it.manifest.version}) по вашему подтверждению." },
                            onFailure = { "Не удалось установить плагин: ${it.message}" },
                        ),
                    )
                }
            },
            onCancel = { pendingScan = null; p.file.delete() },
        )
    }
    if (confirmImport) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Установить плагин?") },
            text = { Text("Плагин — это машинный код. Он получит полный доступ к данным Yougram и вашему аккаунту Telegram. Ставьте только из источников, которым доверяете.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }) { Text("Выбрать файл") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Отмена") } },
        )
    }
    val chatPrefs by settings.chatPrefs.collectAsState()

    // Фон как на страницах настроек (а не обои чата).
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        val top = contentPadding.calculateTopPadding()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding(),
        ) {
            val selected by assistant.models.selected.collectAsState()
            when {
                !assistant.supported -> Notice("Ассистент работает только на 64-битных устройствах.", top)
                else -> when (val s = model) {
                    is AiModelState.Ready -> Chat(assistant, top, chatPrefs.textSize, chatPrefs.bubbleRadius, chatPrefs.ownBubbleColor, chatPrefs.otherBubbleColor, chatPrefs.bubbleOpacity, onNavigate) { confirmImport = true }
                    is AiModelState.Downloading -> Column(Modifier.padding(top = top + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Загрузка ${selected.title}… ${(s.progress * 100).toInt()}% (${s.downloaded / 1_000_000} из ${s.total / 1_000_000} МБ)")
                        app.yougram.core.ui.component.LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth(), thick = true)
                        TextButton(onClick = assistant::cancelDownload) { Text("Отмена") }
                    }
                    else -> Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(top = top + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ModelPicker(selected, assistant.models, assistant::select)
                        if (!selected.configured) {
                            Text("Ссылка на эту модель не задана в local.properties. Выберите другую или добавьте ссылку.", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Модель работает на устройстве, без интернета. Её нужно один раз скачать (около ${selected.approxSizeMb} МБ) — лучше по Wi-Fi.")
                            if (s is AiModelState.Error) Text(s.message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = assistant::download) { Text(if (s is AiModelState.Error) "Повторить" else "Скачать модель") }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Выбор нейросети в стиле Material 3 Expressive: «соединённая» группа карточек.
 * Крайние карточки — крупные скругления снаружи и малые между собой; выбранная морфится в «таблетку»
 * и перекрашивается в primaryContainer (пружинная анимация из motionScheme).
 */
@Composable
private fun ModelPicker(selected: AiModel, manager: AiModelManager, onSelect: (AiModel) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val all = AiModel.entries
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "Нейросеть",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        all.forEachIndexed { i, m ->
            val isSelected = m == selected
            val big = 28.dp
            val small = 6.dp
            val topTarget = if (isSelected || i == 0) big else small
            val bottomTarget = if (isSelected || i == all.lastIndex) big else small
            val top by animateDpAsState(topTarget, motion.fastSpatialSpec(), label = "modelTop")
            val bottom by animateDpAsState(bottomTarget, motion.fastSpatialSpec(), label = "modelBottom")
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                motion.defaultEffectsSpec(), label = "modelContainer",
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                motion.defaultEffectsSpec(), label = "modelContent",
            )
            val downloaded = manager.isDownloaded(m)
            Surface(
                onClick = { onSelect(m) },
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                color = container,
                contentColor = content,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "${m.approxSizeMb} МБ" + if (downloaded) " · скачана" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = content.copy(alpha = 0.72f),
                        )
                        Text(m.note, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.width(12.dp))
                    if (isSelected) {
                        Icon(Icons.Filled.Check, tr("Выбрано"), modifier = Modifier.size(24.dp))
                    } else if (downloaded) {
                        Icon(Icons.Filled.DownloadDone, tr("Скачана"), modifier = Modifier.size(22.dp), tint = content.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, top: androidx.compose.ui.unit.Dp) =
    Text(text, modifier = Modifier.padding(top = top + 16.dp, start = 16.dp, end = 16.dp, bottom = 16.dp))

@Composable
private fun ColumnScope.Chat(
    assistant: AiAssistant,
    topPadding: androidx.compose.ui.unit.Dp,
    textSize: Int,
    bubbleRadius: Int,
    ownColor: Long,
    otherColor: Long,
    opacityPercent: Int,
    onNavigate: (SettingsPage) -> Unit,
    onImportPlugin: () -> Unit,
) {
    val messages by assistant.messages.collectAsState()
    val busy by assistant.busy.collectAsState()
    val error by assistant.error.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPadding + 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (messages.isEmpty()) item {
            Text(
                "Спросите, где что настроить. Например: «как отключить уведомления?» или «включи светлую тему».",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        itemsIndexed(messages) { _, m ->
            Bubble(
                m, textSize = textSize, radius = bubbleRadius,
                ownColor = ownColor, otherColor = otherColor, opacity = opacityPercent / 100f,
                onNavigate = onNavigate, onApply = assistant::apply, onImportPlugin = onImportPlugin,
            )
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

    // Нижняя панель как в Gemini: овальное поле + круглая кнопка отправки.
    val active = busy || input.isNotBlank()
    val sendContainer by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "sendContainer",
    )
    val sendContent by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "sendContent",
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (input.isEmpty()) {
                    Text("Сообщение", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = sendContainer,
            contentColor = sendContent,
            onClick = { if (busy) assistant.stop() else if (input.isNotBlank()) { assistant.send(input); input = "" } },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (busy) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send, tr("Отправить"))
            }
        }
    }
}

@Composable
private fun Bubble(
    m: AiMessage,
    textSize: Int,
    radius: Int,
    ownColor: Long,
    otherColor: Long,
    opacity: Float,
    onNavigate: (SettingsPage) -> Unit,
    onApply: (AiAction.Set) -> Unit,
    onImportPlugin: () -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val r = radius.coerceIn(12, 28).dp
    val alpha = opacity.coerceIn(0f, 1f)

    if (m.fromUser) {
        // Твои сообщения: бирюзовая «таблетка» справа, без времени и галочек.
        val bg = ownColor.takeIf { it != 0L }?.let { Color(it) } ?: if (dark) Color(0xFF004F5B) else Color(0xFFC8EEF4)
        val fg = if (bg.luminance() > 0.5f) Color.Black else Color.White
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                shape = RoundedCornerShape(r + 4.dp),
                color = bg.copy(alpha = alpha),
                contentColor = fg,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                Text(m.text, fontSize = textSize.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }
    } else {
        // Ответ ИИ: карточка на всю ширину, текст с markdown (жирный, списки, код).
        val bg = otherColor.takeIf { it != 0L }?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainer
        val fg = when {
            otherColor != 0L -> if (bg.luminance() > 0.5f) Color.Black else Color.White
            else -> MaterialTheme.colorScheme.onSurface
        }
        val rendered = remember(m.text, textSize, fg) { renderMarkdown(m.text, fg.copy(alpha = 0.14f), textSize.toFloat()) }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(r + 12.dp),
            color = bg.copy(alpha = alpha),
            contentColor = fg,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(
                    if (m.text.isEmpty()) AnnotatedString("…") else rendered,
                    fontSize = textSize.sp,
                    lineHeight = (textSize * 1.45f).sp,
                )
                m.actions.forEach { a ->
                    TextButton(
                        onClick = {
                            when (a) {
                                is AiAction.Open -> onNavigate(a.page)
                                is AiAction.Set -> onApply(a)
                                AiAction.ImportPlugin -> onImportPlugin()
                            }
                        },
                    ) { Text(a.label) }
                }
            }
        }
    }
}

/**
 * Шапка как в Gemini: «назад», искра, название, значок «на устройстве» и меню «⋮».
 * Рисуется из MainScreen вместо общей верхней панели, пока открыта страница ассистента.
 */
@Composable
fun AssistantTopBar(
    assistant: AiAssistant,
    backdrop: BackdropState,
    glass: GlassSettings,
    topInset: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages by assistant.messages.collectAsState()
    val model by assistant.models.state.collectAsState()
    val selectedModel by assistant.models.selected.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .height(topInset + 64.dp)
            .glass(backdrop, glass, RectangleShape),
    ) {
        Row(
            Modifier.padding(top = topInset).height(64.dp).fillMaxWidth().padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад")) }
            Icon(
                Icons.Filled.AutoAwesome, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp).size(30.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "ИИ-ассистент",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (model is AiModelState.Ready) {
                Icon(
                    Icons.Outlined.VerifiedUser, tr("Работает на устройстве"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp).size(24.dp),
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, tr("Меню")) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    AiModel.entries.forEach { m ->
                        DropdownMenuItem(
                            text = { Text((if (m == selectedModel) "✓ " else "      ") + m.title) },
                            onClick = { menuOpen = false; assistant.select(m) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Очистить чат") },
                        enabled = messages.isNotEmpty(),
                        onClick = { menuOpen = false; assistant.clear() },
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить модель") },
                        onClick = { menuOpen = false; assistant.deleteModel() },
                    )
                }
            }
        }
    }
}
