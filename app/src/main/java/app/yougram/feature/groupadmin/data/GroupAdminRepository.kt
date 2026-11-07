package app.yougram.feature.groupadmin.data

import app.yougram.core.telegram.TelegramClient
import app.yougram.core.telegram.getOrThrow
import dev.g000sha256.tdl.dto.ChatEvent
import dev.g000sha256.tdl.dto.ChatEventDescriptionChanged
import dev.g000sha256.tdl.dto.ChatEventInviteLinkEdited
import dev.g000sha256.tdl.dto.ChatEventInviteLinkDeleted
import dev.g000sha256.tdl.dto.ChatEventInviteLinkRevoked
import dev.g000sha256.tdl.dto.ChatEventMemberInvited
import dev.g000sha256.tdl.dto.ChatEventMemberJoined
import dev.g000sha256.tdl.dto.ChatEventMemberJoinedByInviteLink
import dev.g000sha256.tdl.dto.ChatEventMemberJoinedByRequest
import dev.g000sha256.tdl.dto.ChatEventMemberLeft
import dev.g000sha256.tdl.dto.ChatEventMemberPromoted
import dev.g000sha256.tdl.dto.ChatEventMemberRestricted
import dev.g000sha256.tdl.dto.ChatEventMessageDeleted
import dev.g000sha256.tdl.dto.ChatEventMessageEdited
import dev.g000sha256.tdl.dto.ChatEventMessagePinned
import dev.g000sha256.tdl.dto.ChatEventMessageUnpinned
import dev.g000sha256.tdl.dto.ChatEventSlowModeDelayChanged
import dev.g000sha256.tdl.dto.ChatEventTitleChanged
import dev.g000sha256.tdl.dto.ChatEventUsernameChanged
import dev.g000sha256.tdl.dto.ChatStatisticsChannel
import dev.g000sha256.tdl.dto.ChatStatisticsSupergroup
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser

/** Что произошло в журнале действий — укрупнённые категории для значков и фильтра. */
enum class AdminEventKind { MEMBERS, MESSAGES, SETTINGS, LINKS, OTHER }

data class AdminLogEntry(
    val id: Long,
    val date: Int,
    val actor: String,
    val text: String,
    val kind: AdminEventKind,
)

data class JoinRequestItem(
    val userId: Long,
    val name: String,
    val username: String?,
    val bio: String,
    val date: Int,
)

data class InviteLinkItem(
    val link: String,
    val name: String,
    val date: Int,
    val expirationDate: Int,
    /** 0 — без ограничения. */
    val memberLimit: Int,
    val memberCount: Int,
    val pendingRequests: Int,
    val createsJoinRequest: Boolean,
    val isPrimary: Boolean,
    val isRevoked: Boolean,
)

data class StatItem(val title: String, val value: Double, val previous: Double, val growthPercent: Double)

data class BoostInfo(
    val level: Int,
    val boosts: Int,
    val currentLevelBoosts: Int,
    /** 0 — достигнут максимальный уровень. */
    val nextLevelBoosts: Int,
    val url: String,
)

/** Статистика и бусты; каждая часть может быть недоступна (нужны права админа, мало подписчиков). */
data class ChatStatsResult(val items: List<StatItem>, val boost: BoostInfo?)

/** Срок действия новой ссылки. */
enum class LinkLifetime(val label: String, val seconds: Int) {
    FOREVER("Бессрочно", 0),
    HOUR("1 час", 3600),
    DAY("1 день", 86_400),
    WEEK("1 неделя", 604_800),
}

/**
 * Управление группой или каналом: журнал действий админов, заявки на вступление, пригласительные ссылки
 * и медленный режим. Порт ChannelAdminLogActivity, MemberRequestsActivity, ManageLinksActivity и
 * части ChatEditActivity из Telegram для Android.
 */
class GroupAdminRepository(private val telegram: TelegramClient) {
    private val client get() = telegram.client

    /** Допустимые задержки медленного режима, сек (как в Telegram). */
    val slowModeSteps: List<Int> = listOf(0, 10, 30, 60, 300, 900, 3600)

    private val nameCache = java.util.concurrent.ConcurrentHashMap<Long, String>()

    /** Имя пользователя; неудачный запрос в кэш не попадает, чтобы заглушка не прилипла навсегда. */
    private suspend fun userName(userId: Long): String {
        nameCache[userId]?.let { return it }
        val user = try {
            client.getUser(userId = userId).getOrThrow()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            return "Пользователь"
        }
        val name = "${user.firstName} ${user.lastName}".trim().ifEmpty { "Пользователь" }
        nameCache[userId] = name
        return name
    }

    private suspend fun senderName(sender: Any?): String = when (sender) {
        is MessageSenderUser -> userName(sender.userId)
        is MessageSenderChat -> runCatching { client.getChat(chatId = sender.chatId).getOrThrow().title }
            .getOrDefault("Чат")
        else -> "Кто-то"
    }

    // ── Журнал действий ──────────────────────────────────────────────────────────────────────────

    /** Страница журнала: события новее [fromEventId] не включаются, 0 — с самых свежих. */
    suspend fun adminLog(chatId: Long, fromEventId: Long = 0L, query: String = ""): List<AdminLogEntry> {
        val events = client.getChatEventLog(
            chatId = chatId,
            query = query,
            fromEventId = fromEventId,
            limit = 50,
            filters = null,
            userIds = longArrayOf(),
        ).getOrThrow().events.toList()
        return events.map { toEntry(it) }
    }

    private suspend fun toEntry(event: ChatEvent): AdminLogEntry {
        val actor = senderName(event.memberId)
        val (text, kind) = when (val a = event.action) {
            is ChatEventMessageDeleted -> "удалил(а) сообщение" to AdminEventKind.MESSAGES
            is ChatEventMessageEdited -> "изменил(а) сообщение" to AdminEventKind.MESSAGES
            is ChatEventMessagePinned -> "закрепил(а) сообщение" to AdminEventKind.MESSAGES
            is ChatEventMessageUnpinned -> "открепил(а) сообщение" to AdminEventKind.MESSAGES
            is ChatEventMemberJoined -> "вступил(а) в чат" to AdminEventKind.MEMBERS
            is ChatEventMemberJoinedByInviteLink -> "вступил(а) по ссылке" to AdminEventKind.MEMBERS
            is ChatEventMemberJoinedByRequest -> "принят(а) по заявке" to AdminEventKind.MEMBERS
            is ChatEventMemberLeft -> "покинул(а) чат" to AdminEventKind.MEMBERS
            is ChatEventMemberInvited -> "пригласил(а) ${userName(a.userId)}" to AdminEventKind.MEMBERS
            is ChatEventMemberPromoted -> "изменил(а) права ${userName(a.userId)}" to AdminEventKind.MEMBERS
            is ChatEventMemberRestricted -> "изменил(а) ограничения для ${senderName(a.memberId)}" to AdminEventKind.MEMBERS
            is ChatEventTitleChanged -> "переименовал(а) чат: «${a.newTitle}»" to AdminEventKind.SETTINGS
            is ChatEventDescriptionChanged -> "изменил(а) описание" to AdminEventKind.SETTINGS
            is ChatEventUsernameChanged -> "изменил(а) адрес чата" to AdminEventKind.SETTINGS
            is ChatEventSlowModeDelayChanged ->
                (if (a.newSlowModeDelay == 0) "выключил(а) медленный режим"
                else "включил(а) медленный режим: ${formatDelay(a.newSlowModeDelay)}") to AdminEventKind.SETTINGS
            is ChatEventInviteLinkEdited -> "изменил(а) пригласительную ссылку" to AdminEventKind.LINKS
            is ChatEventInviteLinkRevoked -> "отозвал(а) пригласительную ссылку" to AdminEventKind.LINKS
            is ChatEventInviteLinkDeleted -> "удалил(а) пригласительную ссылку" to AdminEventKind.LINKS
            else -> "выполнил(а) действие (${a.javaClass.simpleName.removePrefix("ChatEvent")})" to AdminEventKind.OTHER
        }
        return AdminLogEntry(event.id, event.date, actor, text, kind)
    }

    // ── Заявки на вступление ─────────────────────────────────────────────────────────────────────

    suspend fun joinRequests(chatId: Long): List<JoinRequestItem> {
        val requests = client.getChatJoinRequests(
            chatId = chatId,
            inviteLink = "",
            query = "",
            limit = 100,
        ).getOrThrow().requests.toList()
        return requests.map { r ->
            val user = runCatching { client.getUser(userId = r.userId).getOrThrow() }.getOrNull()
            JoinRequestItem(
                userId = r.userId,
                name = user?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Пользователь",
                username = user?.usernames?.activeUsernames?.firstOrNull(),
                bio = r.bio,
                date = r.date,
            )
        }
    }

    suspend fun processJoinRequest(chatId: Long, userId: Long, approve: Boolean) {
        client.processChatJoinRequest(chatId = chatId, userId = userId, approve = approve).getOrThrow()
    }

    // ── Пригласительные ссылки ───────────────────────────────────────────────────────────────────

    suspend fun inviteLinks(chatId: Long, revoked: Boolean): List<InviteLinkItem> {
        val me = client.getMe().getOrThrow().id
        return client.getChatInviteLinks(
            chatId = chatId,
            creatorUserId = me,
            isRevoked = revoked,
            offsetDate = 0,
            offsetInviteLink = "",
            limit = 100,
        ).getOrThrow().inviteLinks.toList().map { l ->
            InviteLinkItem(
                link = l.inviteLink,
                name = l.name,
                date = l.date,
                expirationDate = l.expirationDate,
                memberLimit = l.memberLimit,
                memberCount = l.memberCount,
                pendingRequests = l.pendingJoinRequestCount,
                createsJoinRequest = l.createsJoinRequest,
                isPrimary = l.isPrimary,
                isRevoked = l.isRevoked,
            )
        }
    }

    suspend fun createInviteLink(
        chatId: Long,
        name: String,
        lifetime: LinkLifetime,
        memberLimit: Int,
        createsJoinRequest: Boolean,
    ) {
        val expiration = if (lifetime.seconds == 0) 0 else (System.currentTimeMillis() / 1000).toInt() + lifetime.seconds
        client.createChatInviteLink(
            chatId = chatId,
            name = name,
            expirationDate = expiration,
            memberLimit = if (createsJoinRequest) 0 else memberLimit,
            createsJoinRequest = createsJoinRequest,
        ).getOrThrow()
    }

    suspend fun revokeInviteLink(chatId: Long, link: String) {
        client.revokeChatInviteLink(chatId = chatId, inviteLink = link).getOrThrow()
    }

    suspend fun deleteRevokedInviteLink(chatId: Long, link: String) {
        client.deleteRevokedChatInviteLink(chatId = chatId, inviteLink = link).getOrThrow()
    }

    // ── Медленный режим ──────────────────────────────────────────────────────────────────────────

    /** Текущая задержка медленного режима; для не-супергрупп — null (режим недоступен). */
    suspend fun slowModeDelay(chatId: Long): Int? {
        val type = client.getChat(chatId = chatId).getOrThrow().type as? ChatTypeSupergroup ?: return null
        if (type.isChannel) return null
        return client.getSupergroupFullInfo(supergroupId = type.supergroupId).getOrThrow().slowModeDelay
    }

    suspend fun setSlowModeDelay(chatId: Long, seconds: Int) {
        client.setChatSlowModeDelay(chatId = chatId, slowModeDelay = seconds).getOrThrow()
    }

    // ── Статистика и бусты ───────────────────────────────────────────────────────────────────────

    /** Числовая статистика и статус бустов. Графики (асинхронные токены TDLib) пока не переносились. */
    suspend fun stats(chatId: Long): ChatStatsResult {
        var firstError: Exception? = null
        val items = try {
            when (val st = client.getChatStatistics(chatId = chatId, isDark = false).getOrThrow()) {
                is ChatStatisticsChannel -> listOf(
                    StatItem("Подписчики", st.memberCount.value, st.memberCount.previousValue, st.memberCount.growthRatePercentage),
                    StatItem("Просмотры сообщения (в среднем)", st.meanMessageViewCount.value, st.meanMessageViewCount.previousValue, st.meanMessageViewCount.growthRatePercentage),
                    StatItem("Репосты сообщения (в среднем)", st.meanMessageShareCount.value, st.meanMessageShareCount.previousValue, st.meanMessageShareCount.growthRatePercentage),
                    StatItem("Включённые уведомления, %", st.enabledNotificationsPercentage, 0.0, 0.0),
                )
                is ChatStatisticsSupergroup -> listOf(
                    StatItem("Участники", st.memberCount.value, st.memberCount.previousValue, st.memberCount.growthRatePercentage),
                    StatItem("Сообщения", st.messageCount.value, st.messageCount.previousValue, st.messageCount.growthRatePercentage),
                    StatItem("Читатели", st.viewerCount.value, st.viewerCount.previousValue, st.viewerCount.growthRatePercentage),
                    StatItem("Авторы", st.senderCount.value, st.senderCount.previousValue, st.senderCount.growthRatePercentage),
                )
                else -> emptyList()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            firstError = e
            emptyList()
        }
        val boost = try {
            val b = client.getChatBoostStatus(chatId = chatId).getOrThrow()
            BoostInfo(b.level, b.boostCount, b.currentLevelBoostCount, b.nextLevelBoostCount, b.boostUrl)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (items.isEmpty()) throw firstError ?: e
            null
        }
        if (items.isEmpty() && boost == null) throw firstError ?: IllegalStateException("Статистика недоступна")
        return ChatStatsResult(items, boost)
    }

    // ── Автоудаление сообщений ───────────────────────────────────────────────────────────────────

    /** Допустимые сроки автоудаления, сек (0 — выкл.; как в Telegram: сутки, неделя, месяц). */
    val autoDeleteSteps: List<Int> = listOf(0, 86_400, 604_800, 2_678_400)

    suspend fun autoDeleteTime(chatId: Long): Int = client.getChat(chatId = chatId).getOrThrow().messageAutoDeleteTime

    suspend fun setAutoDeleteTime(chatId: Long, seconds: Int) {
        client.setChatMessageAutoDeleteTime(chatId = chatId, messageAutoDeleteTime = seconds).getOrThrow()
    }

    companion object {
        fun formatAutoDelete(seconds: Int): String = when {
            seconds <= 0 -> "Выкл."
            seconds < 604_800 -> "1 день"
            seconds < 2_678_400 -> "1 нед."
            else -> "1 мес."
        }

        fun formatDelay(seconds: Int): String = when {
            seconds <= 0 -> "Выкл."
            seconds < 60 -> "$seconds с"
            seconds < 3600 -> "${seconds / 60} мин"
            else -> "${seconds / 3600} ч"
        }
    }
}
