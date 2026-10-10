package app.yougram.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShadowBan: () -> Unit,
    onToggleTypingWatch: () -> Unit,
) {
    val mine = message.reactions.filter { it.chosen }.map { it.emoji }.toSet()
    val lowTier = rememberDeviceTier() == DeviceTier.Low
    val motion = MaterialTheme.motionScheme
    val fadeSpec: FiniteAnimationSpec<Float> = if (lowTier) tween(120) else motion.defaultEffectsSpec()
    val scaleSpec: FiniteAnimationSpec<Float> = if (lowTier) tween(150) else motion.defaultSpatialSpec()
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }

    val actions = buildList {
        add(MenuAction(Icons.AutoMirrored.Filled.Reply, "Ответить") { onReply() })
        if (canSave) add(MenuAction(Icons.Filled.Download, "Сохранить в галерею") { onSave() })
        if (canSave && onExport != null) add(MenuAction(Icons.Filled.FolderOpen, "Сохранить в файлы") { onExport() })
        add(MenuAction(Icons.AutoMirrored.Filled.Send, "Переслать") { onForward() })
        add(MenuAction(Icons.Filled.PushPin, "Закрепить") { onPin() })
        if (canEdit) add(MenuAction(Icons.Filled.Edit, "Изменить") { onEdit() })
        if (canWatchTyping) {
            add(
                MenuAction(
                    Icons.Filled.Visibility,
                    if (watchingTyping) "Не следить за набором" else "Следить за набором",
                ) { onToggleTypingWatch() },
            )
        }
        if (canShadowBan) add(MenuAction(Icons.Filled.VisibilityOff, "Теневой бан автора") { onShadowBan() })
        add(MenuAction(Icons.Filled.Delete, "Удалить", danger = true) { onDelete() })
    }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = 0.85f),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Реакции: выбранная — круг на акцентном контейнере, остальные — прозрачные.
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        Modifier
                            .widthIn(max = 320.dp)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        reactions.forEach { emoji ->
                            val chosen = emoji in mine
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
                // Время отправки — отдельная компактная «таблетка».
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
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
                // Действия: сгруппированный список со смыкающимися скруглениями.
                Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
