package app.yougram.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** Подсвечивает фоном все вхождения [query] (без учёта регистра); ссылки и стили исходного текста сохраняются. */
fun AnnotatedString.highlightMatches(query: String, background: Color): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return this
    val source = text
    return buildAnnotatedString {
        append(this@highlightMatches)
        var from = 0
        while (true) {
            val i = source.indexOf(q, from, ignoreCase = true)
            if (i < 0) break
            addStyle(SpanStyle(background = background), i, i + q.length)
            from = i + q.length
        }
    }
}