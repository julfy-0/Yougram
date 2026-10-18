package app.yougram.plugin

object PluginPermission {
    const val CHAT_READ = "chat.read"
    const val CHAT_SEND = "chat.send"
    const val CHAT_MODIFY = "chat.modify"
    const val NETWORK = "network"
    const val HOOKS = "hooks"
    const val UI = "ui"
    const val IPC = "ipc"
    val ALL = listOf(CHAT_READ, CHAT_SEND, CHAT_MODIFY, NETWORK, HOOKS, UI, IPC)
}
