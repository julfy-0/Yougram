package app.yougram.core.telegram

import dev.g000sha256.tdl.TdlClient

sealed interface TgLinkResult {
    data class OpenChat(val chatId: Long, val messageId: Long = 0L) : TgLinkResult
    data class OpenUrl(val url: String) : TgLinkResult
    /** Приглашение в чат, где мы ещё не состоим: перед вступлением спрашиваем пользователя. */
    data class JoinInvite(val url: String, val title: String) : TgLinkResult
    /** t.me/бот/приложение или главное приложение бота: [shortName] == null — главное. */
    data class MiniApp(
        val chatId: Long,
        val botUserId: Long,
        val botName: String,
        val shortName: String?,
        val startParameter: String,
    ) : TgLinkResult
    data object Failed : TgLinkResult
}

/** Передай сюда сам TdlClient (поле client внутри TelegramClient). */
class TgLinkHandler(private val tdl: TdlClient) {
    // t.me, telegram.me и telegram.dog: обычные ссылки на чаты, сообщения и приглашения.
    private val tme = Regex("""^(?:https?://)?(?:t\.me|telegram\.me|telegram\.dog)/.+""", RegexOption.IGNORE_CASE)

    fun isTgLink(url: String) = tme.matches(url) || url.startsWith("tg:", ignoreCase = true)

    suspend fun resolve(url: String): TgLinkResult =
        if (isTgLink(url)) tdl.resolveInternalLink(url) else TgLinkResult.OpenUrl(url)
}
