package app.yougram.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import app.yougram.MainActivity
import dev.g000sha256.tdl.dto.MessagePinMessage
import dev.g000sha256.tdl.dto.NotificationGroupTypeCalls
import dev.g000sha256.tdl.dto.NotificationGroupTypeSecretChat
import dev.g000sha256.tdl.dto.NotificationTypeNewMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.g000sha256.tdl.dto.Notification as TdNotification

/**
 * Уведомления о сообщениях на основе механизма TDLib (updateActiveNotifications / updateNotificationGroup).
 * TDLib сам решает, о чём уведомлять (учитывает «без звука» чата и прочитанность), мы лишь показываем.
 * Одна группа TDLib = один чат = одно уведомление в стиле MessagingStyle + общая сводка.
 */
class NotificationCenter(
    private val context: Context,
    private val telegram: TelegramClient,
    private val scope: CoroutineScope,
    private val chats: ChatRepository,
    private val settings: SettingsRepository,
) {
    private val client get() = telegram.client
    private val nm: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val mutex = Mutex()
    private val groups = HashMap<Int, Group>()
    private var ownSeq = 0

    /** true, пока приложение на экране: тогда уведомления не показываем. */
    @Volatile
    var appVisible: Boolean = false

    private class Entry(
        /** id уведомления TDLib; у собственных ответов отрицательный. */
        val id: Int,
        val messageId: Long,
        val sender: String,
        val text: String,
        val dateMs: Long,
        val mine: Boolean = false,
    )

    private class Group(val id: Int, val chatId: Long, val title: String, val kind: ChatKind) {
        val entries = ArrayList<Entry>()
        var channel: String = CH_SILENT
    }

    private class Snapshot(val id: Int, val chatId: Long, val skip: Boolean, val added: List<TdNotification>)

    /** Подписки нужно оформить до первого запроса к TDLib, поэтому вызывается из AppContainer.start(). */
    fun start() {
        createChannels()
        scope.launch {
            client.activeNotificationsUpdates.collect { update ->
                val list = update.groups.orEmpty().filterNotNull().map {
                    Snapshot(
                        id = it.id,
                        chatId = it.chatId,
                        skip = it.type is NotificationGroupTypeCalls || it.type is NotificationGroupTypeSecretChat,
                        added = it.notifications.orEmpty().filterNotNull(),
                    )
                }
                mutex.withLock { onActive(list) }
            }
        }
        scope.launch {
            client.notificationGroupUpdates.collect { update ->
                val type = update.type
                if (type is NotificationGroupTypeCalls || type is NotificationGroupTypeSecretChat) return@collect
                val added = update.addedNotifications.orEmpty().filterNotNull()
                val removed = update.removedNotificationIds.toSet()
                mutex.withLock { apply(update.notificationGroupId, update.chatId, added, removed, alert = true) }
            }
        }
    }

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    private fun createChannels() {
        fun channel(id: String, name: String, importance: Int) =
            nm.createNotificationChannel(NotificationChannel(id, name, importance))
        channel(CH_PRIVATE, "Личные сообщения", NotificationManager.IMPORTANCE_HIGH)
        channel(CH_GROUPS, "Группы", NotificationManager.IMPORTANCE_HIGH)
        channel(CH_CHANNELS, "Каналы", NotificationManager.IMPORTANCE_HIGH)
        channel(CH_SILENT, "Без звука", NotificationManager.IMPORTANCE_LOW)
    }

    private fun channelFor(kind: ChatKind) = when (kind) {
        ChatKind.PRIVATE -> CH_PRIVATE
        ChatKind.GROUP -> CH_GROUPS
        ChatKind.CHANNEL -> CH_CHANNELS
    }

    private fun kindEnabled(kind: ChatKind, p: NotificationPrefs) = when (kind) {
        ChatKind.PRIVATE -> p.privateChats
        ChatKind.GROUP -> p.groups
        ChatKind.CHANNEL -> p.channels
    }

    private fun notifyId(groupId: Int) = BASE_ID + groupId

    private suspend fun onActive(list: List<Snapshot>) {
        list.filter { !it.skip }.forEach { apply(it.id, it.chatId, it.added, emptySet(), alert = false) }
        val keep = list.filter { !it.skip }.map { notifyId(it.id) }.toSet()
        // Уведомления прошлого запуска, которых TDLib больше не считает активными.
        nm.activeNotifications.filter { it.id >= BASE_ID && it.id !in keep }.forEach { nm.cancel(it.id) }
        updateSummary()
    }

    private suspend fun apply(groupId: Int, chatId: Long, added: List<TdNotification>, removed: Set<Int>, alert: Boolean) {
        val prefs = settings.notifications.value
        val g = groups[groupId] ?: Group(
            id = groupId,
            chatId = chatId,
            title = runCatching { chats.getChatTitle(chatId) }.getOrDefault("Yougram"),
            kind = chats.chatKind(chatId),
        ).also { groups[groupId] = it }
        if (!kindEnabled(g.kind, prefs)) {
            dropGroup(groupId)
            return
        }
        if (removed.isNotEmpty()) g.entries.removeAll { it.id in removed }
        var fresh = false
        var loud = false
        for (n in added) {
            val type = n.type as? NotificationTypeNewMessage ?: continue
            val m = type.message
            if (m.content is MessagePinMessage && !prefs.pinnedMessages) continue
            if (g.entries.any { it.id == n.id }) continue
            val (name, text) = chats.describeForNotification(m)
            val shown = if (prefs.preview && type.showPreview) text else "Новое сообщение"
            g.entries += Entry(n.id, m.id, name, shown, n.date * 1000L)
            fresh = true
            if (!n.isSilent) loud = true
        }
        if (g.entries.isEmpty()) {
            dropGroup(groupId)
            return
        }
        g.entries.sortBy { it.dateMs }
        // Звук только для новых несилентных сообщений (TDLib уже учёл «без звука» чата) и если звук включён в настройках.
        val alertNow = alert && fresh && loud && prefs.sound
        if (fresh) g.channel = if (alertNow) channelFor(g.kind) else CH_SILENT
        render(g, alertNow)
        updateSummary()
    }

    private fun dropGroup(groupId: Int) {
        groups.remove(groupId)
        nm.cancel(notifyId(groupId))
        updateSummary()
    }

    private fun render(g: Group, alertNow: Boolean) {
        if (appVisible || !nm.areNotificationsEnabled() || g.entries.isEmpty()) return
        val me = Person.Builder().setName("Вы").build()
        val style = NotificationCompat.MessagingStyle(me)
        if (g.kind != ChatKind.PRIVATE) {
            style.setConversationTitle(g.title)
            style.setGroupConversation(true)
        }
        val tail = g.entries.takeLast(MAX_LINES)
        tail.forEach { e ->
            val person = if (e.mine) null else Person.Builder().setName(e.sender.ifEmpty { g.title }).build()
            style.addMessage(e.text, e.dateMs, person)
        }
        val lastIncoming = g.entries.lastOrNull { !it.mine }
        val lastMessageId = lastIncoming?.messageId ?: 0L
        val maxNotificationId = g.entries.maxOf { it.id }
        val base = g.id * 4

        val open = PendingIntent.getActivity(
            context, base,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_CHAT_ID, g.chatId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val actionIntent = { action: String ->
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_CHAT_ID, g.chatId)
                .putExtra(EXTRA_GROUP_ID, g.id)
                .putExtra(EXTRA_MESSAGE_ID, lastMessageId)
                .putExtra(EXTRA_MAX_NOTIFICATION, maxNotificationId)
        }
        // Для RemoteInput PendingIntent обязан быть изменяемым.
        val replyPi = PendingIntent.getBroadcast(
            context, base + 1, actionIntent(ACTION_REPLY),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val readPi = PendingIntent.getBroadcast(
            context, base + 2, actionIntent(ACTION_READ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismissPi = PendingIntent.getBroadcast(
            context, base + 3, actionIntent(ACTION_DISMISS),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val replyAction = NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Ответить", replyPi)
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Ответить").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val readAction = NotificationCompat.Action.Builder(android.R.drawable.checkbox_on_background, "Прочитано", readPi)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val notification = NotificationCompat.Builder(context, g.channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(g.title)
            .setContentText(tail.last().text)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(!alertNow)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(tail.last().dateMs)
            .setContentIntent(open)
            .setDeleteIntent(dismissPi)
            .addAction(replyAction)
            .addAction(readAction)
            .build()
        nm.notify(notifyId(g.id), notification)
    }

    private fun updateSummary() {
        if (appVisible || groups.isEmpty() || !nm.areNotificationsEnabled()) {
            nm.cancel(SUMMARY_ID)
            return
        }
        val total = groups.values.sumOf { g -> g.entries.count { !it.mine } }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val summary = NotificationCompat.Builder(context, CH_SILENT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Yougram")
            .setContentText("Новых сообщений: $total, чатов: ${groups.size}")
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(SUMMARY_ID, summary)
    }

    /** Чат открыт из уведомления: забываем накопленные строки, чтобы следующее сообщение пришло одно. */
    fun forgetChat(chatId: Long) {
        scope.launch {
            mutex.withLock {
                groups.values.filter { it.chatId == chatId }.map { it.id }.forEach { groups.remove(it) }
                updateSummary()
            }
        }
    }

    /** Ответ из уведомления (RemoteInput). */
    suspend fun reply(chatId: Long, groupId: Int, replyToId: Long, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        try {
            chats.sendText(chatId, clean, replyToId.takeIf { it != 0L })
        } catch (e: Exception) {
            toast("Не удалось отправить: ${e.message.orEmpty()}")
            return
        }
        // Как и в чате: в режиме призрака читаем только если включено «читать при действии».
        val ghost = settings.ghost.value
        if (replyToId != 0L) chats.markRead(chatId, listOf(replyToId), force = ghost.enabled && ghost.readOnAction)
        mutex.withLock {
            val g = groups[groupId] ?: return@withLock
            g.entries += Entry(--ownSeq, 0L, "", clean, System.currentTimeMillis(), mine = true)
            render(g, alertNow = false) // обновляем уведомление, чтобы не висел индикатор отправки
        }
    }

    /** Кнопка «Прочитано»: явное действие пользователя, поэтому читаем даже в режиме призрака. */
    suspend fun markRead(chatId: Long, groupId: Int, messageId: Long) {
        if (messageId != 0L) chats.markRead(chatId, listOf(messageId), force = true)
        mutex.withLock { dropGroup(groupId) }
    }

    /** Уведомление смахнули: просим TDLib больше его не показывать. */
    suspend fun dismissed(groupId: Int, maxNotificationId: Int) {
        mutex.withLock { groups.remove(groupId); updateSummary() }
        client.removeNotificationGroup(notificationGroupId = groupId, maxNotificationId = maxNotificationId)
    }

    private fun toast(text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val EXTRA_CHAT_ID = "app.yougram.extra.CHAT_ID"
        const val EXTRA_GROUP_ID = "app.yougram.extra.GROUP_ID"
        const val EXTRA_MESSAGE_ID = "app.yougram.extra.MESSAGE_ID"
        const val EXTRA_MAX_NOTIFICATION = "app.yougram.extra.MAX_NOTIFICATION"
        const val KEY_REPLY = "app.yougram.key.REPLY"
        const val ACTION_REPLY = "app.yougram.notification.REPLY"
        const val ACTION_READ = "app.yougram.notification.READ"
        const val ACTION_DISMISS = "app.yougram.notification.DISMISS"

        private const val CH_PRIVATE = "msg_private"
        private const val CH_GROUPS = "msg_groups"
        private const val CH_CHANNELS = "msg_channels"
        private const val CH_SILENT = "msg_silent"
        private const val GROUP_KEY = "app.yougram.messages"
        private const val SUMMARY_ID = 4200
        private const val BASE_ID = 100_000
        private const val MAX_LINES = 10
    }
}
