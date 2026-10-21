package app.yougram.core.ui.component

import app.yougram.core.i18n.tr
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/** Открывает ссылку так, как выбрано в настройках: во встроенном браузере или во внешнем приложении. */
val LocalOpenLink = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Открывает профиль по @username (без «@»): ищет пользователя или публичный чат в Telegram. */
val LocalOpenUsername = staticCompositionLocalOf<(String) -> Unit> { {} }

private val UrlPattern = Regex(
    """(?<![\p{L}\p{N}_@./-])(?:https?://|www\.|tg://|tg:(?=[a-z_]+\?)|(?:t|telegram)\.me/)[^\s<>"']+""",
    RegexOption.IGNORE_CASE,
)
// Адрес без схемы и без www: example.com, site.ru/path, shop.com.ua?x=1. Домены только с известной зоной,
// чтобы не превращать в ссылки имена файлов и опечатки без пробела («конец.Начало»).
private val BareDomainPattern = Regex(
    """(?<![\p{L}\p{N}_@./:%-])(?:[\p{L}\p{N}](?:[\p{L}\p{N}-]{0,61}[\p{L}\p{N}])?\.)+""" +
        """(?:com|org|net|edu|gov|info|biz|io|me|app|dev|co|ru|ua|by|kz|su|рф|укр|xyz|online|site|tech|store|shop|club|top|pro|ai|tv|fm|gg|ly|cc|uk|de|fr|eu|cn|jp|link|live|news|blog|cloud|space|website|page|wiki|dog)""" +
        """(?![\p{L}\p{N}-])(?::\d{1,5})?(?:[/?#][^\s<>"']*)?""",
    RegexOption.IGNORE_CASE,
)
private val EmailPattern = Regex(
    """(?<![\p{L}\p{N}_.@+%-])[A-Za-z0-9._%+-]+@(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}(?![\p{L}\p{N}@-])""",
)
// @username: 5–32 символа, начинается с буквы. Не трогаем адреса почты (user@host) и куски ссылок.
private val MentionPattern = Regex("""(?<![\p{L}\p{N}_@./])@([A-Za-z][A-Za-z0-9_]{3,31})(?![A-Za-z0-9_])""")
private val SchemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
private const val TrailingPunctuation = ".,;:!?)]}»\u2026"

/** Убирает знаки препинания в конце ссылки; закрывающую скобку — только если она без пары (иначе ломаются ссылки вида …/Foo_(bar)). */
private fun trimUrlEnd(raw: String): String {
    var url = raw
    while (url.isNotEmpty() && url.last() in TrailingPunctuation) {
        if (url.last() == ')' && url.count { it == '(' } >= url.count { it == ')' }) break
        url = url.dropLast(1)
    }
    return url
}

/** Ссылка, спрятанная за текстом сообщения (TextUrl из Telegram): диапазон символов и адрес. */
data class LinkSpan(val offset: Int, val length: Int, val url: String)

fun normalizeUrl(raw: String): String {
    val t = raw.trim()
    return if (SchemePattern.containsMatchIn(t)) t else "https://$t"
}

fun isWebUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

/** Ссылки Telegram лучше отдавать системе: их откроет само приложение Telegram. */
fun isTelegramUrl(url: String): Boolean {
    if (url.startsWith("tg:", ignoreCase = true)) return true
    val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
    return host == "t.me" || host.endsWith(".t.me") || host == "telegram.me" || host == "telegram.dog"
}

/** HTTP без шифрования Android 9+ всё равно блокирует, поэтому поднимаем до HTTPS. */
fun httpsOnly(url: String): String =
    if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

fun openExternally(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, tr("Нет приложения для открытия ссылки"), Toast.LENGTH_SHORT).show()
    }
}

private class LinkToken(val start: Int, val end: Int, val value: String, val mention: Boolean, val emojiId: Long = 0L)

private fun overlaps(tokens: List<LinkToken>, from: Int, to: Int, start: Int, end: Int): Boolean =
    (from until to).any { start < tokens[it].end && end > tokens[it].start }

/** Текст сообщения, в котором ссылки и @юзернеймы подсвечены и кликабельны. */
@Composable
fun rememberLinkified(
    text: String,
    linkColor: Color,
    onOpen: (String) -> Unit,
    onMention: (String) -> Unit = {},
    emojis: List<CustomEmojiSpan> = emptyList(),
    links: List<LinkSpan> = emptyList(),
): AnnotatedString {
    val currentOpen by rememberUpdatedState(onOpen)
    val currentMention by rememberUpdatedState(onMention)
    return remember(text, linkColor, emojis, links) {
        val tokens = ArrayList<LinkToken>()
        // Ссылки, заданные самим Telegram («слово» со спрятанным адресом), важнее всего найденного по тексту.
        for (l in links) {
            val end = l.offset + l.length
            if (l.length <= 0 || l.offset < 0 || end > text.length || l.url.isBlank()) continue
            if (overlaps(tokens, 0, tokens.size, l.offset, end)) continue
            tokens += LinkToken(l.offset, end, l.url, mention = false)
        }
        val explicitCount = tokens.size
        // Адреса в тексте: с схемой/www, затем почта, затем домены без схемы.
        for (match in UrlPattern.findAll(text)) {
            val url = trimUrlEnd(match.value)
            if (url.length < 4) continue
            val start = match.range.first
            if (overlaps(tokens, 0, tokens.size, start, start + url.length)) continue
            tokens += LinkToken(start, start + url.length, url, mention = false)
        }
        for (match in EmailPattern.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (overlaps(tokens, 0, tokens.size, start, end)) continue
            tokens += LinkToken(start, end, "mailto:" + match.value, mention = false)
        }
        for (match in BareDomainPattern.findAll(text)) {
            val url = trimUrlEnd(match.value)
            if (url.length < 4) continue
            val start = match.range.first
            if (overlaps(tokens, 0, tokens.size, start, start + url.length)) continue
            tokens += LinkToken(start, start + url.length, url, mention = false)
        }
        val urlCount = tokens.size
        for (match in MentionPattern.findAll(text)) {
            val start = match.range.first
            // @ внутри ссылки (например, t.me/@name) — это часть ссылки, не отдельный юзернейм.
            if (overlaps(tokens, 0, urlCount, start, start + 1)) continue
            tokens += LinkToken(start, match.range.last + 1, match.groupValues[1], mention = true)
        }
        val textTokens = tokens.size
        for (e in emojis) {
            val end = e.offset + e.length
            if (e.length <= 0 || e.offset < 0 || end > text.length) continue
            if (overlaps(tokens, 0, textTokens, e.offset, end)) continue
            tokens += LinkToken(e.offset, end, "", mention = false, emojiId = e.id)
        }
        tokens.sortBy { it.start }

        buildAnnotatedString {
            var cursor = 0
            for (t in tokens) {
                if (t.start < cursor) continue
                append(text.substring(cursor, t.start))
                if (t.emojiId != 0L) {
                    appendInlineContent(customEmojiKey(t.emojiId), text.substring(t.start, t.end))
                } else if (t.mention) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "mention",
                            styles = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)),
                            linkInteractionListener = { currentMention(t.value) },
                        ),
                    ) { append(text.substring(t.start, t.end)) }
                } else {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "url",
                            styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                            linkInteractionListener = { currentOpen(t.value) },
                        ),
                    ) { append(text.substring(t.start, t.end)) }
                }
                cursor = t.end
            }
            append(text.substring(cursor))
        }
    }
}
