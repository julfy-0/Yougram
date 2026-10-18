package app.yougram.plugin

/** Методы host->call: метод -> нужное разрешение (null — разрешение не требуется). */
object PluginMethods {
    val REQUIRED: Map<String, String?> = mapOf(
        "get_app_info" to null,
        "storage_get" to null,
        "storage_set" to null,
        "storage_delete" to null,
        "storage_keys" to null,
        "storage_clear" to null,
        "settings_get" to null,
        "settings_all" to null,
        "settings_set" to null,
        "timer_start" to null,
        "timer_stop" to null,
        "get_me" to PluginPermission.CHAT_READ,
        "get_chat" to PluginPermission.CHAT_READ,
        "get_user" to PluginPermission.CHAT_READ,
        "get_message" to PluginPermission.CHAT_READ,
        "get_chats" to PluginPermission.CHAT_READ,
        "get_chat_history" to PluginPermission.CHAT_READ,
        "search_messages" to PluginPermission.CHAT_READ,
        "mark_read" to PluginPermission.CHAT_READ,
        "send_message" to PluginPermission.CHAT_SEND,
        "send_photo" to PluginPermission.CHAT_SEND,
        "send_document" to PluginPermission.CHAT_SEND,
        "forward_message" to PluginPermission.CHAT_SEND,
        "edit_message" to PluginPermission.CHAT_MODIFY,
        "delete_message" to PluginPermission.CHAT_MODIFY,
        "react" to PluginPermission.CHAT_MODIFY,
        "pin_message" to PluginPermission.CHAT_MODIFY,
        "http_request" to PluginPermission.NETWORK,
        "http_request_async" to PluginPermission.NETWORK,
        "http_download" to PluginPermission.NETWORK,
        "register_command" to PluginPermission.HOOKS,
        "unregister_command" to PluginPermission.HOOKS,
        "register_action" to PluginPermission.UI,
        "unregister_action" to PluginPermission.UI,
        "toast" to PluginPermission.UI,
        "copy_to_clipboard" to PluginPermission.UI,
        "send_to_plugin" to PluginPermission.IPC,
    )

    /** Методы, меняющие что-то в Telegram: на них действует лимит частоты, чтобы плагин не устроил флуд. */
    val THROTTLED: Set<String> = setOf(
        "send_message", "send_photo", "send_document", "forward_message",
        "edit_message", "delete_message", "react", "pin_message",
    )
}
