package app.yougram.feature.assistant.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/** Мини-markdown для ответов ИИ: **жирный**, `код`, списки (* / - / •), заголовки (#). Недозакрытые ** при стриминге остаются как есть. */
fun renderMarkdown(text: String, codeBg: Color, fontSize: Float): AnnotatedString = buildAnnotatedString {
    for (raw in text.split('\n')) {
        val trimmed = raw.trimStart()
        val level = (raw.length - trimmed.length) / 2
        val bullet = listOf("* ", "- ", "• ").firstOrNull { trimmed.startsWith(it) }
        when {
            raw.isBlank() -> withStyle(SpanStyle(fontSize = (fontSize * 0.4f).sp)) { append(" ") }
            bullet != null -> {
                val first = (level * 16).sp
                val rest = (level * 16 + fontSize).sp
                withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = first, restLine = rest))) {
                    append("•  ")
                    inline(trimmed.removePrefix(bullet), codeBg)
                }
            }
            trimmed.startsWith("#") -> withStyle(ParagraphStyle()) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (fontSize * 1.1f).sp)) {
                    inline(trimmed.trimStart('#').trim(), codeBg)
                }
            }
            else -> withStyle(ParagraphStyle()) { inline(raw, codeBg) }
        }
    }
}

private fun AnnotatedString.Builder.inline(s: String, codeBg: Color) {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(s.substring(i + 2, end), codeBg) }
                    i = end + 2
                } else { append(s[i]); i++ }
            }
            s[i] == '`' -> {
                val end = s.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = app.yougram.core.ui.theme.AppSansFamily, background = codeBg)) { append(s.substring(i + 1, end)) }
                    i = end + 1
                } else { append('`'); i++ }
            }
            else -> { append(s[i]); i++ }
        }
    }
}
