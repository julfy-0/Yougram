package app.yougram.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.automirrored.filled.Send
import app.yougram.core.ui.component.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.yougram.feature.settings.component.CheckSwitch

/** Точки встраивания и типы элементов, которые плагин может запросить через register_ui (API 2.1). */
object PluginUi {
    const val CHAT_TOP_BAR = "chat_top_bar"
    const val CHAT_INPUT = "chat_input"
    const val CHAT_LIST_HEADER = "chat_list_header"
    const val SETTINGS_HOME = "settings_home"
    const val PROFILE = "profile_screen"

    val SLOTS = setOf(CHAT_TOP_BAR, CHAT_INPUT, CHAT_LIST_HEADER, SETTINGS_HOME, PROFILE)

    /** Встроенные элементы, которые плагин может скрыть через hide_ui (API 2.2). */
    const val CHAT_SEARCH = "chat.search"
    const val CHAT_CALL_AUDIO = "chat.call_audio"
    const val CHAT_CALL_VIDEO = "chat.call_video"
    const val MENU_REPLY = "message_menu.reply"
    const val MENU_SAVE = "message_menu.save"
    const val MENU_EXPORT = "message_menu.export"
    const val MENU_FORWARD = "message_menu.forward"
    const val MENU_PIN = "message_menu.pin"
    const val MENU_EDIT = "message_menu.edit"
    const val MENU_TYPING_WATCH = "message_menu.typing_watch"
    const val MENU_SHADOW_BAN = "message_menu.shadow_ban"
    const val MENU_DELETE = "message_menu.delete"
    const val PROFILE_ACTIONS = "profile.actions"
    const val PROFILE_INFO = "profile.info"
    const val PROFILE_TABS = "profile.tabs"
    const val SETTINGS_UPDATE = "settings.update"
    const val SETTINGS_ENTRY_PREFIX = "settings.entry."

    /** Вкладки главного экрана настроек (id из homeEntries), которые можно скрыть. «Плагины» скрыть нельзя. */
    val SETTINGS_ENTRIES = listOf(
        "appearance", "client", "account", "security", "devices",
        "create", "archive", "notifications", "system", "telegram",
    )

    val TARGETS: Set<String> = setOf(
        CHAT_SEARCH, CHAT_CALL_AUDIO, CHAT_CALL_VIDEO,
        MENU_REPLY, MENU_SAVE, MENU_EXPORT, MENU_FORWARD, MENU_PIN, MENU_EDIT,
        MENU_TYPING_WATCH, MENU_SHADOW_BAN, MENU_DELETE,
        PROFILE_ACTIONS, PROFILE_INFO, PROFILE_TABS,
        SETTINGS_UPDATE,
    ) + SETTINGS_ENTRIES.map { SETTINGS_ENTRY_PREFIX + it }

    /** То, что скрыть нельзя: иначе пользователь не сможет отключить плагин. */
    val PROTECTED: Set<String> = setOf(SETTINGS_ENTRY_PREFIX + "plugins")
    val KINDS = setOf("button", "icon_button", "switch", "text", "badge")

    private val ICONS: Map<String, ImageVector> = mapOf(
        "extension" to Icons.Filled.Extension,
        "bolt" to Icons.Filled.Bolt,
        "build" to Icons.Filled.Build,
        "code" to Icons.Filled.Code,
        "copy" to Icons.Filled.ContentCopy,
        "delete" to Icons.Filled.Delete,
        "download" to Icons.Filled.Download,
        "edit" to Icons.Filled.Edit,
        "favorite" to Icons.Filled.Favorite,
        "info" to Icons.Filled.Info,
        "lock" to Icons.Filled.Lock,
        "notifications" to Icons.Filled.Notifications,
        "search" to Icons.Filled.Search,
        "settings" to Icons.Filled.Settings,
        "share" to Icons.Filled.Share,
        "star" to Icons.Filled.Star,
        "translate" to Icons.Filled.Translate,
        "send" to Icons.AutoMirrored.Filled.Send,
    )

    /** Имя значка из register_ui -> иконка; неизвестное имя даёт «пазл» (значок плагина). */
    fun icon(name: String): ImageVector = ICONS[name.lowercase()] ?: Icons.Filled.Extension
}

/** API плагинов для интерфейса; null — плагины недоступны (превью, тесты). Выставляется в корне приложения. */
val LocalPluginApi: ProvidableCompositionLocal<PluginApi?> = staticCompositionLocalOf { null }

/** true, если какой-то плагин скрыл встроенный элемент [target] (см. [PluginUi.TARGETS]). */
@Composable
fun rememberPluginHidden(target: String): Boolean {
    val api = LocalPluginApi.current ?: return false
    val hides by api.hides.collectAsState()
    return remember(hides, target) { hides.any { it.target == target } }
}

/** Набор скрытых целей одним значением: удобно, когда в экране много элементов. */
@Composable
fun rememberPluginHiddenSet(): Set<String> {
    val api = LocalPluginApi.current ?: return emptySet()
    val hides by api.hides.collectAsState()
    return remember(hides) { hides.mapTo(HashSet()) { it.target } }
}

/** Рисует [content], если плагины не скрыли [target]. */
@Composable
fun PluginHideable(target: String, content: @Composable () -> Unit) {
    if (!rememberPluginHidden(target)) content()
}

enum class PluginSlotLayout { Row, Column }

/**
 * Рисует элементы плагинов для точки [slot]. Если плагинов с элементами здесь нет, не занимает места.
 * [chatId] / [messageId] попадают в событие «ui_event», чтобы плагин знал контекст нажатия.
 */
@Composable
fun PluginSlot(
    slot: String,
    modifier: Modifier = Modifier,
    layout: PluginSlotLayout = PluginSlotLayout.Row,
    chatId: Long = 0L,
    messageId: Long = 0L,
) {
    val api = LocalPluginApi.current ?: return
    val all by api.uiItems.collectAsState()
    val items = remember(all, slot) { all.filter { it.slot == slot && it.visible }.sortedBy { it.order } }
    if (items.isEmpty()) return
    when (layout) {
        PluginSlotLayout.Row -> Row(
            modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { items.forEach { PluginUiView(it, api, chatId, messageId, compact = true) } }
        PluginSlotLayout.Column -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { PluginUiView(it, api, chatId, messageId, compact = false) }
        }
    }
}

@Composable
private fun PluginUiView(item: PluginUiItem, api: PluginApi, chatId: Long, messageId: Long, compact: Boolean) {
    when (item.kind) {
        "icon_button" -> IconButton(
            onClick = { api.invokeUi(item, null, chatId, messageId) },
            enabled = item.enabled,
            modifier = Modifier.size(36.dp),
        ) { Icon(PluginUi.icon(item.icon), contentDescription = item.title, modifier = Modifier.size(22.dp)) }

        "button" -> FilledTonalButton(
            onClick = { api.invokeUi(item, null, chatId, messageId) },
            enabled = item.enabled,
            modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
        ) {
            if (item.icon.isNotEmpty()) {
                Icon(PluginUi.icon(item.icon), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        "switch" -> {
            var checked by remember(item.checked) { mutableStateOf(item.checked) }
            val toggle = { value: Boolean ->
                checked = value
                api.invokeUi(item, value, chatId, messageId)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = (if (compact) Modifier else Modifier.fillMaxWidth()).clickable(enabled = item.enabled) { toggle(!checked) },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(if (compact) Modifier else Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.subtitle.isNotEmpty()) {
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    CheckSwitch(checked, if (item.enabled) toggle else null)
                }
            }
        }

        "badge" -> Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.icon.isNotEmpty()) {
                    Icon(PluginUi.icon(item.icon), contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }

        else -> Column {
            Text(item.title, style = MaterialTheme.typography.bodyMedium)
            if (item.subtitle.isNotEmpty()) {
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
