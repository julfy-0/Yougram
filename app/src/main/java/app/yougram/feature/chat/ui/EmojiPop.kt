package app.yougram.feature.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yougram.core.i18n.Text
import app.yougram.core.ui.component.CustomEmojiSpan
import kotlinx.coroutines.delay

/** «Выезд» эмодзи: из уменьшенного и сдвинутого вниз состояния пружинкой на место, с задержкой [index] * 70 мс. */
@Composable
internal fun PopIn(index: Int, enabled: Boolean, content: @Composable () -> Unit) {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) {
            delay(index * 70L)
            progress.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val v = progress.value
            scaleX = v
            scaleY = v
            alpha = v.coerceIn(0f, 1f)
            translationY = (1f - v) * 12.dp.toPx()
        },
        contentAlignment = Alignment.Center,
    ) { content() }
}

class PopEmoji(val text: AnnotatedString, val inline: Map<String, InlineTextContent>)

private fun isEmojiBase(cp: Int): Boolean =
    cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF || cp == 0x2B50 || cp == 0x2B55 || cp == 0x2B1B || cp == 0x2B1C ||
            cp == 0x231A || cp == 0x231B || cp in 0x23E9..0x23F3 || cp in 0x23F8..0x23FA

/** Диапазоны обычных (unicode) эмодзи в тексте: с модификаторами, ZWJ-последовательностями и флагами. */
internal fun emojiRanges(t: String): List<IntRange> {
    val out = ArrayList<IntRange>()
    var i = 0
    while (i < t.length) {
        val cp = t.codePointAt(i)
        if (!isEmojiBase(cp)) { i += Character.charCount(cp); continue }
        val start = i
        i += Character.charCount(cp)
        if (cp in 0x1F1E6..0x1F1FF && i < t.length && t.codePointAt(i) in 0x1F1E6..0x1F1FF) {
            i += Character.charCount(t.codePointAt(i))
        }
        while (i < t.length) {
            val c = t.codePointAt(i)
            if (c == 0xFE0F || c in 0x1F3FB..0x1F3FF) {
                i += Character.charCount(c)
            } else if (c == 0x200D && i + 1 < t.length && isEmojiBase(t.codePointAt(i + 1))) {
                i += 1
                i += Character.charCount(t.codePointAt(i))
            } else {
                break
            }
        }
        out += start until i
    }
    return out
}

/**
 * Заменяет обычные эмодзи в тексте на inline-содержимое с анимацией «выезда».
 * Ссылки, выделение поиска и текст остаются как были; кастомные эмодзи пропускаются (они анимируются отдельно).
 */
@Composable
fun rememberPopEmoji(
    text: AnnotatedString,
    custom: List<CustomEmojiSpan>,
    sizeSp: Float,
    animate: Boolean,
): PopEmoji = remember(text, custom, sizeSp, animate) {
    val ranges = emojiRanges(text.text)
        .filter { r -> custom.none { c -> r.first < c.offset + c.length && c.offset <= r.last } }
        .take(40)
    if (ranges.isEmpty()) return@remember PopEmoji(text, emptyMap())
    val inline = HashMap<String, InlineTextContent>()
    val built = buildAnnotatedString {
        var pos = 0
        ranges.forEachIndexed { index, r ->
            if (r.first > pos) append(text.subSequence(pos, r.first))
            val emoji = text.text.substring(r.first, r.last + 1)
            val key = "ue:$index"
            inline[key] = InlineTextContent(
                Placeholder(sizeSp.sp, sizeSp.sp, PlaceholderVerticalAlign.TextCenter),
            ) { PopIn(index, animate) { Text(emoji, fontSize = (sizeSp * 0.85f).sp) } }
            appendInlineContent(key, emoji)
            pos = r.last + 1
        }
        if (pos < text.length) append(text.subSequence(pos, text.length))
    }
    PopEmoji(built, inline)
}