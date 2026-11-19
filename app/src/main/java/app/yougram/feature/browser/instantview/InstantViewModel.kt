package app.yougram.feature.browser.instantview

import android.net.Uri

enum class IvStyle { Bold, Italic, Underline, Strike, Fixed, Marked, Link }

data class IvSpan(val start: Int, val end: Int, val style: IvStyle, val url: String? = null)

data class IvText(val text: String, val spans: List<IvSpan> = emptyList()) {
    val isBlank: Boolean get() = text.isBlank()

    companion object {
        val Empty = IvText("")
    }
}

data class IvListItem(val label: String, val blocks: List<IvBlock>)

data class IvRelated(val title: String, val description: String, val url: String)

sealed interface IvBlock {
    data class Title(val text: IvText) : IvBlock
    data class Subtitle(val text: IvText) : IvBlock
    data class Header(val text: IvText, val level: Int) : IvBlock
    data class Kicker(val text: IvText) : IvBlock
    data class AuthorDate(val line: String) : IvBlock
    data class Paragraph(val text: IvText) : IvBlock
    data class Preformatted(val text: IvText) : IvBlock
    data class Footer(val text: IvText) : IvBlock
    data object Divider : IvBlock
    data class Quote(val text: IvText, val credit: IvText, val pull: Boolean) : IvBlock
    data class BlockQuote(val blocks: List<IvBlock>, val credit: IvText) : IvBlock
    data class ListBlock(val items: List<IvListItem>) : IvBlock
    data class Photo(
        val fileId: Int,
        val width: Int,
        val height: Int,
        val caption: IvText,
        val credit: IvText,
    ) : IvBlock
    data class Details(val header: IvText, val blocks: List<IvBlock>, val open: Boolean) : IvBlock
    data class Related(val header: IvText, val items: List<IvRelated>) : IvBlock
    data class Table(val caption: IvText, val rows: List<List<IvText>>) : IvBlock
    /** Видео, встраивания, карты и т. п.: заглушка со ссылкой «Открыть в браузере». */
    data class Unsupported(val label: String) : IvBlock
}

data class InstantViewPage(val url: String, val title: String, val blocks: List<IvBlock>)

/** Сайты, где Instant View запрашиваем из сети; для остальных берём только уже закэшированный в TDLib. */
fun isInstantViewHost(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
    return host == "telegra.ph" || host == "graph.org" || host == "te.legra.ph"
}
