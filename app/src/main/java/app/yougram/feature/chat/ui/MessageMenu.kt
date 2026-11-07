package app.yougram.feature.chat.ui

import app.yougram.core.i18n.tr
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.Animatable
import android.view.WindowManager
import android.view.ViewGroup
import android.os.Build
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
import app.yougram.feature.chat.data.MessageItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Набор по умолчанию, пока не пришёл список реакций, доступных в чате. */
val DefaultReactions = listOf("❤️", "👍", "👎", "🔥", "🥰", "👏", "😁")
private val DateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")

private val GroupOuter = 24.dp
private val GroupInner = 6.dp

private fun stamp(date: Int): String {
    val at = Instant.ofEpochSecond(date.toLong()).atZone(ZoneId.systemDefault())
    val today = LocalDate.now(at.zone)
    val day = when (at.toLocalDate()) {
        today -> "сегодня"
        today.minusDays(1) -> "вчера"
        else -> at.format(DateFmt)
    }
    return "$day, ${at.format(TimeFmt)}"
}

private class MenuAction(
    val icon: ImageVector,
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Меню сообщения по долгому тапу в стиле Material 3 Expressive: реакции в «таблетке» сверху,
 * действия — сгруппированный список (крупные внешние и малые внутренние скругления).
 */
@Composable
fun MessageMenu(
    message: MessageItem,
    isRead: Boolean,
    canEdit: Boolean,
    canSave: Boolean,
    canShadowBan: Boolean,
    canWatchTyping: Boolean,
    watchingTyping: Boolean,
    reactions: List<String>,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onSave: () -> Unit,
    /** Сохранить через системный проводник; null — пункта нет. */
    onExport: (() -> Unit)? = null,
    onForward: () -> Unit,
    isPinned: Boolean = false,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShadowBan: () -> Unit,
    onToggleTypingWatch: () -> Unit,
    /** Копировать текст; null — пункта нет (у сообщения нет текста). */
    onCopy: (() -> Unit)? = null,
    /** Копировать ссылку на сообщение; null — пункта нет. */
    onCopyLink: (() -> Unit)? = null,
    /** Пожаловаться; null — пункта нет. */
    onReport: (() -> Unit)? = null,
    /** Чат сообщения: передаётся пунктам плагинов в событии action. */
    chatId: Long = 0L,
    /** Снимок и позиция сообщения: если задан, сообщение остаётся чётким, а всё остальное размывается. */
    anchor: MessageAnchor? = null,
) {
    val mine = message.reactions.filter { it.chosen }.map { it.emoji }.toSet()
    val lowTier = rememberDeviceTier() == DeviceTier.Low

    val pluginApi = app.yougram.plugin.LocalPluginApi.current
    val pluginActions = pluginApi?.actions?.collectAsState()?.value.orEmpty().filter { it.where == "message_menu" }
    val hidden = app.yougram.plugin.rememberPluginHiddenSet()
    val actions = buildList {
        if (app.yougram.plugin.PluginUi.MENU_REPLY !in hidden) add(MenuAction(Icons.AutoMirrored.Filled.Reply, tr("Ответить")) { onReply() })
        if (onCopy != null) add(MenuAction(Icons.Filled.ContentCopy, tr("Копировать")) { onCopy() })
        if (onCopyLink != null) add(MenuAction(Icons.Filled.Link, tr("Копировать ссылку")) { onCopyLink() })
        if (canSave && app.yougram.plugin.PluginUi.MENU_SAVE !in hidden) add(MenuAction(Icons.Filled.Download, tr("Сохранить в галерею")) { onSave() })
        if (canSave && onExport != null && app.yougram.plugin.PluginUi.MENU_EXPORT !in hidden) add(MenuAction(Icons.Filled.FolderOpen, tr("Сохранить в файлы")) { onExport() })
        if (app.yougram.plugin.PluginUi.MENU_FORWARD !in hidden) add(MenuAction(Icons.AutoMirrored.Filled.Send, tr("Переслать")) { onForward() })
        if (app.yougram.plugin.PluginUi.MENU_PIN !in hidden) add(MenuAction(Icons.Filled.PushPin, if (isPinned) "Открепить" else "Закрепить") { onPin() })
        if (canEdit && app.yougram.plugin.PluginUi.MENU_EDIT !in hidden) add(MenuAction(Icons.Filled.Edit, tr("Изменить")) { onEdit() })
        if (canWatchTyping && app.yougram.plugin.PluginUi.MENU_TYPING_WATCH !in hidden) {
            add(
                MenuAction(
                    Icons.Filled.Visibility,
                    if (watchingTyping) "Не следить за набором" else "Следить за набором",
                ) { onToggleTypingWatch() },
            )
        }
        if (canShadowBan && app.yougram.plugin.PluginUi.MENU_SHADOW_BAN !in hidden) add(MenuAction(Icons.Filled.VisibilityOff, tr("Теневой бан автора")) { onShadowBan() })
        // Пункты плагинов (register_action, where = message_menu).
        pluginActions.forEach { a ->
            add(MenuAction(app.yougram.plugin.PluginUi.icon(a.icon), a.title) { pluginApi?.invokeAction(a, chatId, message.id) })
        }
        if (onReport != null && !message.isOutgoing) add(MenuAction(Icons.Filled.Report, tr("Пожаловаться")) { onReport() })
        if (app.yougram.plugin.PluginUi.MENU_DELETE !in hidden) add(MenuAction(Icons.Filled.Delete, tr("Удалить"), danger = true) { onDelete() })
    }

    // Окно на весь экран: всё, что за ним (чат, панели, шапка), размывается; сообщение рисуется поверх чётко.
    val outgoing = message.isOutgoing
    val bmp = anchor?.bitmap
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // В секретных чатах у диалога своё окно: без FLAG_SECURE копия сообщения попала бы в скриншоты.
        app.yougram.core.ui.SecureWindowEffect()
        // Размытие делает сам экран чата (ChatScreen), окно диалога только затемняет.
        DialogWindowBlur(radius = 0.dp, dim = if (lowTier) 0.45f else 0.30f)
        val density = LocalDensity.current
        val prog = remember { Animatable(0f) }
        LaunchedEffect(Unit) { prog.animateTo(1f, tween(if (lowTier) 180 else 340, easing = FastOutSlowInEasing)) }
        val popIn = Modifier.graphicsLayer {
            alpha = prog.value
            val k = 0.9f + 0.1f * prog.value
            scaleX = k
            scaleY = k
            transformOrigin = TransformOrigin(if (outgoing) 1f else 0f, 0.5f)
        }
        var origin by remember { mutableStateOf(Offset.Zero) }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionOnScreen() }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (anchor != null && bmp != null) {
                val screenW = constraints.maxWidth
                val screenH = constraints.maxHeight
                val edge = with(density) { 8.dp.roundToPx() }
                val msgW = anchor.size.width
                val msgH = anchor.size.height
                val left = (anchor.position.x - origin.x).roundToInt()
                val top = (anchor.position.y - origin.y).roundToInt()
                val gap = with(density) { 8.dp.roundToPx() }
                val pillEst = with(density) { 56.dp.roundToPx() }
                val timeEst = with(density) { 32.dp.roundToPx() }
                val actionsEst = with(density) { (actions.size * 54 - 2).dp.roundToPx() }
                var pillPx by remember { mutableStateOf(pillEst) }
                var actionsPx by remember { mutableStateOf(actionsEst) }
                val groupH = pillEst + actionsEst + msgH + timeEst + gap * 3
                val safeTop = maxOf(WindowInsets.systemBars.getTop(density), with(density) { 24.dp.roundToPx() }) + edge
                val safeBottom = WindowInsets.systemBars.getBottom(density) + edge
                // Меню сверху, сообщение под ним; вся группа по центру экрана, если помещается.
                val startTop = ((screenH - groupH) / 2).coerceIn(safeTop, maxOf(safeTop, screenH - safeBottom - groupH))
                val startPad = if (outgoing) edge else left.coerceAtLeast(edge)
                val endPad = if (outgoing) (screenW - (left + msgW)).coerceAtLeast(edge) else edge
                // Где сообщение окажется в итоге; в начале оно стоит на своём старом месте.
                val finalTop = startTop + pillPx + gap + actionsPx + gap
                val fromShift = (top - finalTop).toFloat()
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            top = with(density) { startTop.toDp() },
                            bottom = with(density) { safeBottom.toDp() },
                            start = with(density) { startPad.toDp() },
                            end = with(density) { endPad.toDp() },
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
                ) {
                    ReactionsPill(reactions, mine, onReact, onDismiss, popIn.onSizeChanged { pillPx = it.height })
                    ActionsList(actions, onDismiss, popIn.onSizeChanged { actionsPx = it.height })
                    // Сообщение «уезжает» с прежнего места под меню.
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier
                            .size(with(density) { msgW.toDp() }, with(density) { msgH.toDp() })
                            .graphicsLayer { translationY = fromShift * (1f - prog.value) },
                    )
                    TimePill(message, isRead, popIn)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ReactionsPill(reactions, mine, onReact, onDismiss, popIn)
                    TimePill(message, isRead, popIn)
                    ActionsList(actions, onDismiss, popIn)
                }
            }
        }
    }
}

/** Позиция (в координатах экрана), размер и снимок сообщения, снятые в момент долгого тапа. */
class MessageAnchor(val position: Offset, val size: IntSize, val bitmap: ImageBitmap?)

@Composable
private fun ReactionsPill(
    reactions: List<String>,
    chosenSet: Set<String>,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Реакции: выбранная — круг на акцентном контейнере, остальные — прозрачные.
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier
                .widthIn(max = 320.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            reactions.forEach { emoji ->
                val chosen = emoji in chosenSet
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(if (chosen) CircleShape else RoundedCornerShape(14.dp))
                        .background(
                            if (chosen) MaterialTheme.colorScheme.primaryContainer
                            else androidx.compose.ui.graphics.Color.Transparent,
                        )
                        .clickable { onReact(emoji); onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 26.sp) }
            }
        }
    }
}

@Composable
private fun TimePill(message: MessageItem, isRead: Boolean, modifier: Modifier = Modifier) {
    // Время отправки — отдельная компактная «таблетка».
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (message.isOutgoing) {
                Icon(
                    if (isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                    contentDescription = if (isRead) "Прочитано" else "Отправлено",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                stamp(message.date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionsList(actions: List<MenuAction>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Действия: сгруппированный список со смыкающимися скруглениями.
    Column(modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        actions.forEachIndexed { index, action ->
            val shape = RoundedCornerShape(
                topStart = if (index == 0) GroupOuter else GroupInner,
                topEnd = if (index == 0) GroupOuter else GroupInner,
                bottomStart = if (index == actions.lastIndex) GroupOuter else GroupInner,
                bottomEnd = if (index == actions.lastIndex) GroupOuter else GroupInner,
            )
            MenuItem(action, shape) { action.onClick(); onDismiss() }
        }
    }
}

/**
 * Размытие и затемнение всего, что находится за окном диалога (системный blur-behind, Android 12+).
 * Появляется плавно; если устройство не поддерживает размытие окон, остаётся только затемнение.
 */
@Composable
internal fun DialogWindowBlur(radius: Dp, dim: Float) {
    val view = LocalView.current
    val density = LocalDensity.current
    LaunchedEffect(view, radius, dim) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val blurPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radius > 0.dp) with(density) { radius.roundToPx() } else 0
        if (blurPx > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        Animatable(0f).animateTo(1f, tween(220)) {
            val attrs = window.attributes
            attrs.dimAmount = dim * value
            if (blurPx > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) attrs.blurBehindRadius = (blurPx * value).toInt()
            window.attributes = attrs
        }
    }
}

@Composable
private fun MenuItem(action: MenuAction, shape: RoundedCornerShape, onClick: () -> Unit) {
    val container = if (action.danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (action.danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val iconTint = if (action.danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
    Surface(onClick = onClick, shape = shape, color = container, contentColor = content, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(action.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(action.label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}