package app.yougram.core.ui.component

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
    """(?<![\p{L}\p{N}_@./-])(?:https?://|www\.|tg://|(?:t|telegram)\.me/)[^\s<>"']+""",
    RegexOption.IGNORE_CASE,
)
// @username: 5–32 символа, начинается с буквы. Не трогаем адреса почты (user@host) и куски ссылок.
private val MentionPattern = Regex("""(?<![\p{L}\p{N}_@./])@([A-Za-z][A-Za-z0-9_]{3,31})(?![A-Za-z0-9_])""")
private val SchemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
private const val TrailingPunctuation = ".,;:!?)]}»\u2026"

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
        Toast.makeText(context, "Нет приложения для открытия ссылки", Toast.LENGTH_SHORT).show()
    }
}

private class LinkToken(val start: Int, val end: Int, val value: String, val mention: Boolean, val emojiId: Long = 0L)

/** Текст сообщения, в котором ссылки и @юзернеймы подсвечены и кликабельны. */
@Composable
fun rememberLinkified(
    text: String,
    linkColor: Color,
    onOpen: (String) -> Unit,
    onMention: (String) -> Unit = {},
    emojis: List<CustomEmojiSpan> = emptyList(),
): AnnotatedString {
    val currentOpen by rememberUpdatedState(onOpen)
    val currentMention by rememberUpdatedState(onMention)
    return remember(text, linkColor, emojis) {
        val tokens = ArrayList<LinkToken>()
        for (match in UrlPattern.findAll(text)) {
            var url = match.value
            while (url.isNotEmpty() && url.last() in TrailingPunctuation) url = url.dropLast(1)
            if (url.length < 4) continue
            tokens += LinkToken(match.range.first, match.range.first + url.length, url, mention = false)
        }
        val urlCount = tokens.size
        for (match in MentionPattern.findAll(text)) {
            val start = match.range.first
            // @ внутри ссылки (например, t.me/@name) — это часть ссылки, не отдельный юзернейм.
            if ((0 until urlCount).any { start >= tokens[it].start && start < tokens[it].end }) continue
            tokens += LinkToken(start, match.range.last + 1, match.groupValues[1], mention = true)
        }
        val textTokens = tokens.size
        for (e in emojis) {
            val end = e.offset + e.length
            if (e.length <= 0 || e.offset < 0 || end > text.length) continue
            if ((0 until textTokens).any { e.offset < tokens[it].end && end > tokens[it].start }) continue
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
                    ) { append(t.value) }
                }
                cursor = t.end
            }
            append(text.substring(cursor))
        }
    }
}
