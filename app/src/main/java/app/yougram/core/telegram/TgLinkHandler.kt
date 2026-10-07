package app.yougram.core.telegram

import dev.g000sha256.tdl.TdlClient

sealed interface TgLinkResult {
    data class OpenChat(val chatId: Long) : TgLinkResult
    data class OpenUrl(val url: String) : TgLinkResult
    data object Failed : TgLinkResult
}

/** Передай сюда сам TdlClient (поле внутри TelegramClient, см. PATCH.md). */
class TgLinkHandler(private val tdl: TdlClient) {
    private val tme = Regex("""^(?:https?://)?(?:t|telegram)\.me/.+""", RegexOption.IGNORE_CASE)

    fun isTgLink(url: String) = tme.matches(url) || url.startsWith("tg://")

    suspend fun resolve(url: String): TgLinkResult =
        if (isTgLink(url)) tdl.resolveInternalLink(url) else TgLinkResult.OpenUrl(url)
}
