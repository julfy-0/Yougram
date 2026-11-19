package app.yougram.feature.browser.instantview

import dev.g000sha256.tdl.dto.*
import java.text.DateFormat
import java.util.Date

// Все обращения к DTO TDLib для Instant View собраны в этом файле: при расхождении имён править только его.

private fun <T> Array<out T?>?.items(): List<T> = this?.filterNotNull().orEmpty()
private fun <T> Iterable<T?>?.items(): List<T> = this?.filterNotNull().orEmpty()

private class RichBuilder {
    val sb = StringBuilder()
    val spans = mutableListOf<IvSpan>()
}

private inline fun RichBuilder.span(style: IvStyle, url: String?, body: () -> Unit) {
    val start = sb.length
    body()
    if (sb.length > start) spans += IvSpan(start, sb.length, style, url)
}

private fun RichBuilder.append(t: RichText?) {
    when (t) {
        null -> Unit
        is RichTextPlain -> sb.append(t.text)
        is RichTexts -> t.texts.items().forEach { append(it) }
        is RichTextBold -> span(IvStyle.Bold, null) { append(t.text) }
        is RichTextItalic -> span(IvStyle.Italic, null) { append(t.text) }
        is RichTextUnderline -> span(IvStyle.Underline, null) { append(t.text) }
        is RichTextStrikethrough -> span(IvStyle.Strike, null) { append(t.text) }
        is RichTextFixed -> span(IvStyle.Fixed, null) { append(t.text) }
        is RichTextMarked -> span(IvStyle.Marked, null) { append(t.text) }
        is RichTextUrl -> span(IvStyle.Link, t.url) { append(t.text) }
        is RichTextEmailAddress -> span(IvStyle.Link, "mailto:" + t.emailAddress) { append(t.text) }
        is RichTextPhoneNumber -> span(IvStyle.Link, "tel:" + t.phoneNumber) { append(t.text) }
        is RichTextSubscript -> append(t.text)
        is RichTextSuperscript -> append(t.text)
        is RichTextAnchorLink -> append(t.text)
        is RichTextReference -> append(t.text)
        else -> Unit
    }
}

private fun rich(t: RichText?): IvText {
    val b = RichBuilder()
    b.append(t)
    return IvText(b.sb.toString(), b.spans)
}

private fun mapBlocks(blocks: Array<out PageBlock?>?): List<IvBlock> = blocks.items().flatMap { mapBlock(it) }

private fun mapBlocks(blocks: Iterable<PageBlock?>?): List<IvBlock> = blocks.items().flatMap { mapBlock(it) }

private fun photoBlock(photo: Photo?, caption: PageBlockCaption?): List<IvBlock> {
    val best = photo?.sizes.items().maxByOrNull { it.width * it.height } ?: return emptyList()
    val file = best.photo ?: return emptyList()
    return listOf(
        IvBlock.Photo(
            fileId = file.id,
            width = best.width,
            height = best.height,
            caption = rich(caption?.text),
            credit = rich(caption?.credit),
        ),
    )
}

private fun one(block: IvBlock, text: IvText): List<IvBlock> = if (text.isBlank) emptyList() else listOf(block)

private fun mapBlock(b: PageBlock?): List<IvBlock> = when (b) {
    is PageBlockTitle -> rich(b.title).let { one(IvBlock.Title(it), it) }
    is PageBlockSubtitle -> rich(b.subtitle).let { one(IvBlock.Subtitle(it), it) }
    is PageBlockKicker -> rich(b.kicker).let { one(IvBlock.Kicker(it), it) }
    is PageBlockHeader -> rich(b.header).let { one(IvBlock.Header(it, 1), it) }
    is PageBlockSubheader -> rich(b.subheader).let { one(IvBlock.Header(it, 2), it) }
    is PageBlockParagraph -> rich(b.text).let { one(IvBlock.Paragraph(it), it) }
    is PageBlockPreformatted -> rich(b.text).let { one(IvBlock.Preformatted(it), it) }
    is PageBlockFooter -> rich(b.footer).let { one(IvBlock.Footer(it), it) }
    is PageBlockDivider -> listOf(IvBlock.Divider)
    is PageBlockAuthorDate -> {
        val author = rich(b.author).text.trim()
        val date = if (b.publishDate > 0) {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(b.publishDate * 1000L))
        } else ""
        val line = listOf(author, date).filter { it.isNotEmpty() }.joinToString(" · ")
        if (line.isEmpty()) emptyList() else listOf(IvBlock.AuthorDate(line))
    }
    is PageBlockBlockQuote -> mapBlocks(b.blocks).let { inner ->
        if (inner.isEmpty()) emptyList() else listOf(IvBlock.BlockQuote(inner, rich(b.credit)))
    }
    is PageBlockPullQuote -> rich(b.text).let { one(IvBlock.Quote(it, rich(b.credit), pull = true), it) }
    is PageBlockList -> {
        val items = b.items.items().map { IvListItem(it.label.orEmpty(), mapBlocks(it.blocks)) }
        if (items.isEmpty()) emptyList() else listOf(IvBlock.ListBlock(items))
    }
    is PageBlockPhoto -> photoBlock(b.photo, b.caption)
    is PageBlockCover -> mapBlock(b.cover)
    is PageBlockCollage -> mapBlocks(b.blocks)
    is PageBlockSlideshow -> mapBlocks(b.blocks)
    is PageBlockDetails -> listOf(IvBlock.Details(rich(b.header), mapBlocks(b.blocks), b.isOpen))
    is PageBlockRelatedArticles -> {
        val items = b.articles.items().map {
            IvRelated(it.title.orEmpty(), it.description.orEmpty(), it.url.orEmpty())
        }.filter { it.url.isNotEmpty() }
        if (items.isEmpty()) emptyList() else listOf(IvBlock.Related(rich(b.header), items))
    }
    is PageBlockTable -> {
        val rows = b.cells.items().map { row -> row.items().map { rich(it.text) } }
        if (rows.isEmpty()) emptyList() else listOf(IvBlock.Table(rich(b.caption), rows))
    }
    is PageBlockVideo -> listOf(IvBlock.Unsupported("Видео"))
    is PageBlockAnimation -> listOf(IvBlock.Unsupported("Анимация"))
    is PageBlockAudio -> listOf(IvBlock.Unsupported("Аудио"))
    is PageBlockEmbedded, is PageBlockEmbeddedPost -> listOf(IvBlock.Unsupported("Встроенное содержимое"))
    is PageBlockMap -> listOf(IvBlock.Unsupported("Карта"))
    else -> emptyList()
}

internal fun mapInstantView(url: String, iv: WebPageInstantView): InstantViewPage {
    val blocks = mapBlocks(iv.blocks)
    val title = (blocks.firstOrNull { it is IvBlock.Title } as? IvBlock.Title)?.text?.text.orEmpty()
    return InstantViewPage(url = url, title = title, blocks = blocks)
}
