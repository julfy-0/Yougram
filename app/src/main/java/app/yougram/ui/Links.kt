package app.yougram.ui

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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/** Открывает ссылку так, как выбрано в настройках: во встроенном браузере или во внешнем приложении. */
val LocalOpenLink = staticCompositionLocalOf<(String) -> Unit> { {} }

private val UrlPattern = Regex("""(?:https?://|www\.)[^\s<>"']+""", RegexOption.IGNORE_CASE)
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

/** Текст сообщения, в котором ссылки подсвечены и кликабельны. */
@Composable
fun rememberLinkified(text: String, linkColor: Color, onOpen: (String) -> Unit): AnnotatedString {
    val currentOpen by rememberUpdatedState(onOpen)
    return remember(text, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            for (match in UrlPattern.findAll(text)) {
                var url = match.value
                while (url.isNotEmpty() && url.last() in TrailingPunctuation) url = url.dropLast(1)
                if (url.length < 4) continue
                append(text.substring(cursor, match.range.first))
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "url",
                        styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { currentOpen(url) },
                    ),
                ) { append(url) }
                cursor = match.range.first + url.length
            }
            append(text.substring(cursor))
        }
    }
}
