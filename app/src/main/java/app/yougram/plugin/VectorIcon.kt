package app.yougram.plugin

import android.util.Xml
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser

/** Одна фигура иконки: контур, нужна ли заливка и/или обводка. Цвет задаёт тот, кто рисует. */
class VectorShape(
    val path: Path,
    val fill: Boolean,
    val stroke: Boolean,
    val strokeWidth: Float,
    val cap: StrokeCap,
    val join: StrokeJoin,
)

class VectorIcon(val minX: Float, val minY: Float, val width: Float, val height: Float, val shapes: List<VectorShape>)

/**
 * Разбор векторной иконки плагина: SVG (path, circle, ellipse, rect, line, polygon, polyline) или
 * Android VectorDrawable xml (path, group). Градиенты, transform, текст и картинки не поддерживаются.
 * Всё рисуется одним цветом, поэтому иконку можно красить в акцентный цвет темы.
 */
object VectorIconParser {
    const val MAX_BYTES = 64 * 1024
    private const val MAX_SHAPES = 300

    /** Файл начинается с «<» (после BOM и пробелов): это xml/svg, а не png/jpg/webp. */
    fun looksLikeVector(bytes: ByteArray): Boolean {
        var i = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) i = 3
        while (i < bytes.size && bytes[i].toInt().toChar().isWhitespace()) i++
        return i < bytes.size && bytes[i] == '<'.code.toByte()
    }

    fun parse(bytes: ByteArray): VectorIcon? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        return runCatching { doParse(bytes) }.getOrNull()
    }

    private class Style(
        val fill: String? = null,
        val stroke: String? = null,
        val strokeWidth: Float? = null,
        val cap: String? = null,
        val join: String? = null,
        val evenOdd: Boolean = false,
    )

    private fun attrs(p: XmlPullParser): Map<String, String> {
        val m = HashMap<String, String>()
        for (i in 0 until p.attributeCount) m[p.getAttributeName(i).substringAfter(':')] = p.getAttributeValue(i)
        return m
    }

    private fun num(a: Map<String, String>, key: String, def: Float = 0f): Float =
        a[key]?.trim()?.removeSuffix("px")?.toFloatOrNull() ?: def

    private fun isNone(v: String): Boolean {
        val s = v.trim()
        return s.equals("none", true) || s.equals("transparent", true) || (s.startsWith("#") && s.length == 9 && s.substring(1, 3) == "00")
    }

    private fun readStyle(a: Map<String, String>, parent: Style, android: Boolean): Style {
        var fill = parent.fill
        var stroke = parent.stroke
        var sw = parent.strokeWidth
        var cap = parent.cap
        var join = parent.join
        var eo = parent.evenOdd
        if (android) {
            a["fillColor"]?.let { fill = it.trim() }
            a["strokeColor"]?.let { stroke = it.trim() }
            a["strokeWidth"]?.trim()?.toFloatOrNull()?.let { sw = it }
            a["strokeLineCap"]?.let { cap = it.trim() }
            a["strokeLineJoin"]?.let { join = it.trim() }
            a["fillType"]?.let { eo = it.trim().equals("evenOdd", true) }
        } else {
            fun put(k: String, v: String) {
                when (k) {
                    "fill" -> fill = v
                    "stroke" -> stroke = v
                    "stroke-width" -> v.removeSuffix("px").toFloatOrNull()?.let { sw = it }
                    "stroke-linecap" -> cap = v
                    "stroke-linejoin" -> join = v
                    "fill-rule" -> eo = v.equals("evenodd", true)
                }
            }
            a.forEach { (k, v) -> put(k, v.trim()) }
            a["style"]?.split(';')?.forEach { part ->
                val i = part.indexOf(':')
                if (i > 0) put(part.substring(0, i).trim(), part.substring(i + 1).trim())
            }
        }
        return Style(fill, stroke, sw, cap, join, eo)
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): String? =
        if (rx <= 0f || ry <= 0f) null
        else "M${cx - rx},$cy A$rx,$ry 0 1,0 ${cx + rx},$cy A$rx,$ry 0 1,0 ${cx - rx},$cy Z"

    private fun rect(a: Map<String, String>): String? {
        val x = num(a, "x")
        val y = num(a, "y")
        val w = num(a, "width")
        val h = num(a, "height")
        if (w <= 0f || h <= 0f) return null
        var rx = a["rx"]?.trim()?.toFloatOrNull()
        var ry = a["ry"]?.trim()?.toFloatOrNull()
        if (rx == null && ry == null) {
            rx = 0f; ry = 0f
        } else if (rx == null) rx = ry else if (ry == null) ry = rx
        val rxc = (rx ?: 0f).coerceIn(0f, w / 2f)
        val ryc = (ry ?: 0f).coerceIn(0f, h / 2f)
        return if (rxc <= 0f || ryc <= 0f) "M$x,$y H${x + w} V${y + h} H$x Z"
        else "M${x + rxc},$y H${x + w - rxc} A$rxc,$ryc 0 0 1 ${x + w},${y + ryc} V${y + h - ryc} " +
                "A$rxc,$ryc 0 0 1 ${x + w - rxc},${y + h} H${x + rxc} A$rxc,$ryc 0 0 1 $x,${y + h - ryc} " +
                "V${y + ryc} A$rxc,$ryc 0 0 1 ${x + rxc},$y Z"
    }

    private fun points(raw: String?, close: Boolean): String? {
        val n = raw?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toFloatOrNull() } ?: return null
        if (n.size < 4) return null
        val sb = StringBuilder("M${n[0]},${n[1]}")
        var i = 2
        while (i + 1 < n.size) {
            sb.append(" L${n[i]},${n[i + 1]}")
            i += 2
        }
        if (close) sb.append(" Z")
        return sb.toString()
    }

    private fun shapeData(name: String, a: Map<String, String>): String? = when (name) {
        "path" -> a["d"] ?: a["pathData"]
        "circle" -> ellipse(num(a, "cx"), num(a, "cy"), num(a, "r"), num(a, "r"))
        "ellipse" -> ellipse(num(a, "cx"), num(a, "cy"), num(a, "rx"), num(a, "ry"))
        "rect" -> rect(a)
        "line" -> "M${num(a, "x1")},${num(a, "y1")} L${num(a, "x2")},${num(a, "y2")}"
        "polygon" -> points(a["points"], true)
        "polyline" -> points(a["points"], false)
        else -> null
    }

    private fun doParse(bytes: ByteArray): VectorIcon? {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(bytes), null)
        var rooted = false
        var android = false
        var minX = 0f
        var minY = 0f
        var w = 0f
        var h = 0f
        var cur = Style()
        val stack = ArrayList<Style>()
        val shapes = ArrayList<VectorShape>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                val name = p.name.substringAfter(':').lowercase()
                val a = attrs(p)
                if ((name == "svg" || name == "vector") && !rooted) {
                    rooted = true
                    android = name == "vector"
                    if (android) {
                        w = num(a, "viewportWidth")
                        h = num(a, "viewportHeight")
                    } else {
                        val vb = a["viewBox"]?.trim()?.split(Regex("[\\s,]+"))?.mapNotNull { it.toFloatOrNull() }
                        if (vb != null && vb.size == 4) {
                            minX = vb[0]; minY = vb[1]; w = vb[2]; h = vb[3]
                        } else {
                            w = num(a, "width")
                            h = num(a, "height")
                        }
                    }
                    cur = readStyle(a, cur, android)
                } else if (rooted && (name == "g" || name == "group")) {
                    stack.add(cur)
                    cur = readStyle(a, cur, android)
                } else if (rooted && shapes.size < MAX_SHAPES) {
                    val d = shapeData(name, a)
                    if (d != null) {
                        val s = readStyle(a, cur, android)
                        val fillOn = if (s.fill == null) !android else !isNone(s.fill)
                        val sw = s.strokeWidth ?: if (android) 0f else 1f
                        val strokeOn = s.stroke != null && !isNone(s.stroke) && sw > 0f
                        if (fillOn || strokeOn) {
                            val path = PathParser().parsePathString(d).toPath()
                            path.fillType = if (s.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
                            shapes.add(
                                VectorShape(
                                    path = path,
                                    fill = fillOn,
                                    stroke = strokeOn,
                                    strokeWidth = sw,
                                    cap = when (s.cap?.lowercase()) {
                                        "round" -> StrokeCap.Round
                                        "square" -> StrokeCap.Square
                                        else -> StrokeCap.Butt
                                    },
                                    join = when (s.join?.lowercase()) {
                                        "round" -> StrokeJoin.Round
                                        "bevel" -> StrokeJoin.Bevel
                                        else -> StrokeJoin.Miter
                                    },
                                ),
                            )
                        }
                    }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                val name = p.name.substringAfter(':').lowercase()
                if ((name == "g" || name == "group") && stack.isNotEmpty()) cur = stack.removeAt(stack.lastIndex)
            }
            ev = p.next()
        }
        if (w <= 0f || h <= 0f || shapes.isEmpty()) return null
        return VectorIcon(minX, minY, w, h, shapes)
    }
}

/** Рисует векторную иконку одним цветом по центру, с полями (inset — доля доступного места). */
@Composable
fun VectorIconView(icon: VectorIcon, tint: Color, modifier: Modifier = Modifier, inset: Float = 0.62f) {
    Canvas(modifier) {
        val k = minOf(size.width / icon.width, size.height / icon.height) * inset
        val dx = (size.width - icon.width * k) / 2f - icon.minX * k
        val dy = (size.height - icon.height * k) / 2f - icon.minY * k
        withTransform({
            translate(dx, dy)
            scale(k, k, Offset.Zero)
        }) {
            icon.shapes.forEach { s ->
                if (s.fill) drawPath(s.path, tint)
                if (s.stroke) drawPath(s.path, tint, style = Stroke(width = s.strokeWidth, cap = s.cap, join = s.join))
            }
        }
    }
}
