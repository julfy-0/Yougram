package app.yougram.feature.account.data

import app.yougram.core.telegram.TelegramClient
import app.yougram.core.telegram.getOrThrow
import dev.g000sha256.tdl.dto.AccountTtl
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.Birthdate
import dev.g000sha256.tdl.dto.BlockListMain
import dev.g000sha256.tdl.dto.EmailAddressAuthenticationCode
import dev.g000sha256.tdl.dto.MessageAutoDeleteTime
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.OptionValueBoolean
import dev.g000sha256.tdl.dto.PhoneNumberCodeTypeChange
import dev.g000sha256.tdl.dto.UserPrivacySetting
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowCalls
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowChatInvites
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages
import dev.g000sha256.tdl.dto.UserPrivacySettingAllowUnpaidMessages
import dev.g000sha256.tdl.dto.UserPrivacySettingAutosaveGifts
import dev.g000sha256.tdl.dto.UserPrivacySettingRule
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowChatMembers
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowContacts
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleAllowUsers
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictAll
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictChatMembers
import dev.g000sha256.tdl.dto.UserPrivacySettingRuleRestrictUsers
import dev.g000sha256.tdl.dto.UserPrivacySettingRules
import dev.g000sha256.tdl.dto.UserPrivacySettingShowBio
import dev.g000sha256.tdl.dto.UserPrivacySettingShowBirthdate
import dev.g000sha256.tdl.dto.UserPrivacySettingShowLinkInForwardedMessages
import dev.g000sha256.tdl.dto.UserPrivacySettingShowPhoneNumber
import dev.g000sha256.tdl.dto.UserPrivacySettingShowProfileAudio
import dev.g000sha256.tdl.dto.UserPrivacySettingShowProfilePhoto
import dev.g000sha256.tdl.dto.UserPrivacySettingShowStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

const val BIO_MAX_LENGTH = 70

/** [year] == 0 — год скрыт. */
data class BirthdateInfo(val day: Int, val month: Int, val year: Int)

data class PersonalChatOption(val id: Long, val title: String)

data class AccountInfo(
    val firstName: String,
    val lastName: String,
    val username: String?,
    val phone: String,
    val birthdate: BirthdateInfo?,
    val bio: String,
    val personalChatId: Long,
    val personalChatTitle: String?,
)

data class SessionItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val lastActive: Int,
    val current: Boolean,
)

/** Порядок значений — порядок строк на экране «Конфиденциальность». [premium] — менять может только Premium. */
enum class PrivacyKey(val title: String, val premium: Boolean = false) {
    PhoneNumber("Номер телефона"),
    LastSeen("Время захода"),
    ProfilePhoto("Фотографии профиля"),
    Forwards("Пересылка сообщений"),
    Calls("Звонки"),
    VoiceMessages("Голосовые сообщения", premium = true),
    Messages("Сообщения", premium = true),
    Birthdate("День рождения"),
    Gifts("Подарки"),
    Bio("О себе"),
    SavedMusic("Сохранённая музыка"),
    Invites("Приглашения"),
}

enum class PrivacyLevel(val label: String) {
    Everybody("Все"),
    Contacts("Мои контакты"),
    Nobody("Никто"),
}

data class StorageInfo(val filesBytes: Long, val databaseBytes: Long)

/** Общее правило и число исключений: [allowCount] — «всегда разрешать», [restrictCount] — «всегда запрещать». */
data class PrivacyDetail(val level: PrivacyLevel, val allowCount: Int, val restrictCount: Int)

/** Параметры безопасности; null — не удалось загрузить. */
data class SecurityInfo(
    val hasPassword: Boolean? = null,
    /** Маска почты для входа, например «j***2@gmail.com»; пусто — не задана. */
    val loginEmail: String? = null,
    val autoDeleteSeconds: Int? = null,
    val passkeys: Int? = null,
    val blocked: Int? = null,
    val ttlDays: Int? = null,
    val topChats: Boolean? = null,
)

/** Заблокированный отправитель: ровно одно из [userId] / [chatId] не ноль. */
data class BlockedItem(val userId: Long, val chatId: Long, val title: String)

data class WebsiteItem(val id: Long, val domain: String, val subtitle: String)

/** Аккаунт, сеансы, конфиденциальность и память: всё, что нужно подэкранам настроек. */
class AccountRepository(
    private val telegram: TelegramClient,
    private val accountManager: AccountManager? = null,
) {
    private val client get() = telegram.client

    suspend fun loadAccount(): AccountInfo {
        val me = client.getMe().getOrThrow()
        val full = runCatching { client.getUserFullInfo(userId = me.id).getOrThrow() }.getOrNull()
        val personalId = full?.personalChatId ?: 0L
        val personalTitle = if (personalId != 0L) {
            runCatching { client.getChat(chatId = personalId).getOrThrow().title }.getOrNull()
        } else null
        val phoneStr = if (me.phoneNumber.isEmpty()) "" else "+${me.phoneNumber}"
        val nameStr = "${me.firstName} ${me.lastName}".trim().ifEmpty { "Аккаунт" }
        val avatarId = me.profilePhoto?.small?.id

        accountManager?.updateAccountDetails(
            id = telegram.accountDirName,
            userId = me.id,
            name = nameStr,
            phone = phoneStr,
            username = me.usernames?.activeUsernames?.firstOrNull(),
            avatarFileId = avatarId,
        )

        return AccountInfo(
            firstName = me.firstName,
            lastName = me.lastName,
            username = me.usernames?.activeUsernames?.firstOrNull(),
            phone = phoneStr,
            birthdate = full?.birthdate?.let { BirthdateInfo(day = it.day, month = it.month, year = it.year) },
            bio = full?.bio?.text.orEmpty(),
            personalChatId = personalId,
            personalChatTitle = personalTitle,
        )
    }

    suspend fun setBio(bio: String) {
        client.setBio(bio = bio).getOrThrow()
    }

    suspend fun setBirthdate(value: BirthdateInfo?) {
        client.setBirthdate(
            birthdate = value?.let { Birthdate(day = it.day, month = it.month, year = it.year) },
        ).getOrThrow()
    }

    /** Шаг 1 смены номера: Telegram отправляет код на новый номер. */
    suspend fun requestPhoneChange(phone: String) {
        client.sendPhoneNumberCode(
            phoneNumber = phone.trim(),
            settings = null,
            type = PhoneNumberCodeTypeChange(),
        ).getOrThrow()
    }

    /** Шаг 2 смены номера: подтверждение кодом. */
    suspend fun confirmPhoneChange(code: String) {
        client.checkPhoneNumberCode(code = code.trim()).getOrThrow()
    }

    suspend fun loadPersonalChats(): List<PersonalChatOption> =
        client.getSuitablePersonalChats().getOrThrow().chatIds.map { id ->
            PersonalChatOption(id, runCatching { client.getChat(chatId = id).getOrThrow().title }.getOrDefault("Канал"))
        }

    /** [chatId] == 0 — убрать личный канал. */
    suspend fun setPersonalChat(chatId: Long) {
        client.setPersonalChat(chatId = chatId).getOrThrow()
    }

    /** Выход: TDLib удаляет базу и закрывается; ждём закрытия, дальше UI перезапускает приложение. */
    suspend fun logOut() {
        client.logOut().getOrThrow()
        withTimeoutOrNull(10_000) { telegram.authState.first { it is AuthorizationStateClosed } }
    }

    suspend fun setName(firstName: String, lastName: String) {
        client.setName(firstName = firstName, lastName = lastName).getOrThrow()
    }

    suspend fun setUsername(username: String) {
        client.setUsername(username = username).getOrThrow()
    }

    suspend fun loadSessions(): List<SessionItem> =
        client.getActiveSessions().getOrThrow().sessions.orEmpty().filterNotNull().map { s ->
            SessionItem(
                id = s.id,
                title = listOf(s.applicationName, s.applicationVersion).filter { it.isNotEmpty() }.joinToString(" "),
                subtitle = listOf(s.deviceModel, "${s.platform} ${s.systemVersion}".trim())
                    .filter { it.isNotEmpty() }
                    .joinToString(", "),
                lastActive = s.lastActiveDate,
                current = s.isCurrent,
            )
        }

    suspend fun terminateSession(sessionId: Long) {
        client.terminateSession(sessionId = sessionId).getOrThrow()
    }

    suspend fun terminateOtherSessions() {
        client.terminateAllOtherSessions().getOrThrow()
    }

    private fun PrivacyKey.setting(): UserPrivacySetting = when (this) {
        PrivacyKey.PhoneNumber -> UserPrivacySettingShowPhoneNumber()
        PrivacyKey.LastSeen -> UserPrivacySettingShowStatus()
        PrivacyKey.ProfilePhoto -> UserPrivacySettingShowProfilePhoto()
        PrivacyKey.Calls -> UserPrivacySettingAllowCalls()
        PrivacyKey.Invites -> UserPrivacySettingAllowChatInvites()
        PrivacyKey.Birthdate -> UserPrivacySettingShowBirthdate()
        PrivacyKey.Bio -> UserPrivacySettingShowBio()
        PrivacyKey.Forwards -> UserPrivacySettingShowLinkInForwardedMessages()
        PrivacyKey.VoiceMessages -> UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages()
        PrivacyKey.Messages -> UserPrivacySettingAllowUnpaidMessages()
        PrivacyKey.Gifts -> UserPrivacySettingAutosaveGifts()
        PrivacyKey.SavedMusic -> UserPrivacySettingShowProfileAudio()
    }

    private suspend fun rulesOf(key: PrivacyKey): List<UserPrivacySettingRule> =
        client.getUserPrivacySettingRules(setting = key.setting()).getOrThrow().rules.orEmpty().filterNotNull()

    suspend fun loadPrivacy(key: PrivacyKey): PrivacyDetail {
        val rules = rulesOf(key)
        val level = when {
            rules.any { it is UserPrivacySettingRuleAllowAll } -> PrivacyLevel.Everybody
            rules.any { it is UserPrivacySettingRuleAllowContacts } -> PrivacyLevel.Contacts
            else -> PrivacyLevel.Nobody
        }
        val allow = rules.fold(0) { acc, r ->
            acc + when (r) {
                is UserPrivacySettingRuleAllowUsers -> r.userIds.size
                is UserPrivacySettingRuleAllowChatMembers -> r.chatIds.size
                else -> 0
            }
        }
        val restrict = rules.fold(0) { acc, r ->
            acc + when (r) {
                is UserPrivacySettingRuleRestrictUsers -> r.userIds.size
                is UserPrivacySettingRuleRestrictChatMembers -> r.chatIds.size
                else -> 0
            }
        }
        return PrivacyDetail(level, allow, restrict)
    }

    /** Меняет базовое правило, сохраняя исключения (списки конкретных пользователей и чатов). */
    suspend fun setPrivacy(key: PrivacyKey, level: PrivacyLevel) {
        val kept = rulesOf(key).filter {
            it !is UserPrivacySettingRuleAllowAll &&
                    it !is UserPrivacySettingRuleAllowContacts &&
                    it !is UserPrivacySettingRuleRestrictAll
        }
        val base: List<UserPrivacySettingRule> = when (level) {
            PrivacyLevel.Everybody -> listOf(UserPrivacySettingRuleAllowAll())
            PrivacyLevel.Contacts -> listOf(UserPrivacySettingRuleAllowContacts(), UserPrivacySettingRuleRestrictAll())
            PrivacyLevel.Nobody -> listOf(UserPrivacySettingRuleRestrictAll())
        }
        client.setUserPrivacySettingRules(
            setting = key.setting(),
            rules = UserPrivacySettingRules(rules = (kept + base).toTypedArray()),
        ).getOrThrow()
    }

    suspend fun loadSecurity(): SecurityInfo {
        val password = runCatching { client.getPasswordState().getOrThrow() }.getOrNull()
        return SecurityInfo(
            hasPassword = password?.hasPassword,
            loginEmail = password?.loginEmailAddressPattern,
            autoDeleteSeconds = runCatching { client.getDefaultMessageAutoDeleteTime().getOrThrow().time }.getOrNull(),
            passkeys = runCatching { client.getLoginPasskeys().getOrThrow().passkeys.size }.getOrNull(),
            blocked = runCatching {
                client.getBlockedMessageSenders(blockList = BlockListMain(), offset = 0, limit = 1).getOrThrow().totalCount
            }.getOrNull(),
            ttlDays = runCatching { client.getAccountTtl().getOrThrow().days }.getOrNull(),
            topChats = runCatching {
                // Опция «disable_top_chats» = подсказки выключены; если опции нет, подсказки включены.
                (client.getOption(name = "disable_top_chats").getOrThrow() as? OptionValueBoolean)?.value?.not() ?: true
            }.getOrNull(),
        )
    }

    /** Пустой [newPassword] отключает облачный пароль. */
    suspend fun setCloudPassword(oldPassword: String, newPassword: String, hint: String) {
        client.setPassword(
            oldPassword = oldPassword,
            newPassword = newPassword,
            newHint = hint,
            setRecoveryEmailAddress = false,
            newRecoveryEmailAddress = "",
        ).getOrThrow()
    }

    suspend fun setAutoDelete(seconds: Int) {
        client.setDefaultMessageAutoDeleteTime(messageAutoDeleteTime = MessageAutoDeleteTime(time = seconds)).getOrThrow()
    }

    suspend fun setAccountTtl(days: Int) {
        client.setAccountTtl(ttl = AccountTtl(days = days)).getOrThrow()
    }

    /** Шаг 1 смены почты для входа: код уходит на новую почту. */
    suspend fun requestLoginEmail(email: String) {
        client.setLoginEmailAddress(newLoginEmailAddress = email.trim()).getOrThrow()
    }

    suspend fun confirmLoginEmail(code: String) {
        client.checkLoginEmailAddressCode(code = EmailAddressAuthenticationCode(code = code.trim())).getOrThrow()
    }

    suspend fun setTopChats(enabled: Boolean) {
        client.setOption(name = "disable_top_chats", value = OptionValueBoolean(value = !enabled)).getOrThrow()
    }

    suspend fun clearPaymentData() {
        client.deleteSavedOrderInfo().getOrThrow()
        client.deleteSavedCredentials().getOrThrow()
    }

    suspend fun clearImportedContacts() {
        client.clearImportedContacts().getOrThrow()
    }

    suspend fun loadBlocked(): List<BlockedItem> =
        client.getBlockedMessageSenders(blockList = BlockListMain(), offset = 0, limit = 100).getOrThrow()
            .senders.map { sender ->
                when (sender) {
                    is MessageSenderUser -> BlockedItem(
                        userId = sender.userId,
                        chatId = 0L,
                        title = runCatching {
                            client.getUser(userId = sender.userId).getOrThrow().let { "${it.firstName} ${it.lastName}".trim() }
                        }.getOrDefault("Пользователь"),
                    )
                    is MessageSenderChat -> BlockedItem(
                        userId = 0L,
                        chatId = sender.chatId,
                        title = runCatching { client.getChat(chatId = sender.chatId).getOrThrow().title }.getOrDefault("Чат"),
                    )
                    else -> BlockedItem(0L, 0L, "")
                }
            }.filter { it.userId != 0L || it.chatId != 0L }

    suspend fun unblock(item: BlockedItem) {
        val sender: MessageSender =
            if (item.userId != 0L) MessageSenderUser(userId = item.userId) else MessageSenderChat(chatId = item.chatId)
        client.setMessageSenderBlockList(senderId = sender, blockList = null).getOrThrow()
    }

    suspend fun loadWebsites(): List<WebsiteItem> =
        client.getConnectedWebsites().getOrThrow().websites.map { w ->
            val device = listOf(w.browser, w.platform).filter { it.isNotEmpty() }.joinToString(", ")
            WebsiteItem(w.id, w.domainName, if (w.location.isNotBlank()) "$device • ${w.location}" else device)
        }

    suspend fun disconnectWebsite(id: Long) {
        client.disconnectWebsite(websiteId = id).getOrThrow()
    }

    suspend fun disconnectAllWebsites() {
        client.disconnectAllWebsites().getOrThrow()
    }

    suspend fun loadStorage(): StorageInfo {
        val s = client.getStorageStatisticsFast().getOrThrow()
        return StorageInfo(filesBytes = s.filesSize, databaseBytes = s.databaseSize)
    }

    /** Удаляет кэшированные файлы (медиа можно скачать заново). Пустые списки — без ограничений по типам и чатам. */
    suspend fun clearCache() {
        client.optimizeStorage(
            size = 0L,
            ttl = 0,
            count = 0,
            immunityDelay = 0,
            fileTypes = emptyArray(),
            chatIds = longArrayOf(),
            excludeChatIds = longArrayOf(),
            returnDeletedFileStatistics = false,
            chatLimit = 0,
        ).getOrThrow()
    }
}