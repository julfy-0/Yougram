package app.yougram.feature.chat.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PendingKind { PHOTO, VIDEO, GIF }

/** Выбранный, но ещё не отправленный файл. */
data class PendingMedia(val path: String, val kind: PendingKind)

private const val MaxCaption = 1024
private const val ExportMaxSide = 4096
private const val PreviewMaxSide = 2048
private const val MinCrop = 0.1f

/** Нормализованный прямоугольник кадрирования (0..1) в координатах уже повёрнутой картинки. */
private val FullCrop = Rect(0f, 0f, 1f, 1f)

private enum class Handle { NONE, MOVE, TL, TR, BL, BR }

/**
 * Экран перед отправкой: превью, подпись, для фото ещё поворот и обрезка.
 * Для видео и GIF — превью и подпись (без редактирования).
 */
@Composable
fun MediaComposerDialog(
    pending: PendingMedia,
    onDismiss: () -> Unit,
    onSend: (path: String, caption: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var turns by remember { mutableIntStateOf(0) } // поворот по часовой, в четвертях
    var crop by remember { mutableStateOf(FullCrop) }
    var cropMode by remember { mutableStateOf(false) }
    var ratioLock by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    val editable = pending.kind == PendingKind.PHOTO

    // Исходник (с учётом EXIF) для превью; полноразмерный декодируется только при экспорте.
    val source by produceState<Bitmap?>(null, pending.path) {
        value = withContext(Dispatchers.IO) {
            when (pending.kind) {
                PendingKind.VIDEO -> runCatching {
                    MediaMetadataRetriever().run {
                        try {
                            setDataSource(pending.path)
                            getFrameAtTime(0)
                        } finally {
                            release()
                        }
                    }
                }.getOrNull()
                else -> loadOriented(pending.path, PreviewMaxSide)
            }
        }
    }
    val shown = remember(source, turns) { source?.rotated(turns) }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().imePadding(),
        ) {
            // Верхняя панель.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, enabled = !busy) {
                    Icon(Icons.Filled.Close, "Закрыть", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (editable) {
                    IconButton(onClick = { turns = (turns + 1) % 4; crop = FullCrop }, enabled = !busy) {
                        Icon(Icons.Filled.RotateRight, "Повернуть", tint = Color.White)
                    }
                    IconButton(onClick = { cropMode = !cropMode }, enabled = !busy) {
                        Icon(
                            Icons.Filled.Crop, "Обрезать",
                            tint = if (cropMode) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    IconButton(
                        onClick = { turns = 0; crop = FullCrop; ratioLock = null },
                        enabled = !busy && (turns != 0 || crop != FullCrop),
                    ) { Icon(Icons.Filled.Refresh, "Сбросить", tint = Color.White) }
                }
            }

            // Область превью.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = shown
                if (bmp != null) {
                    val density = LocalDensity.current
                    val scale = min(constraints.maxWidth / bmp.width.toFloat(), constraints.maxHeight / bmp.height.toFloat())
                    val w = bmp.width * scale
                    val h = bmp.height * scale
                    Box(Modifier.size(with(density) { w.toDp() }, with(density) { h.toDp() })) {
                        Image(
                            bmp.asImageBitmap(), null,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                        )
                        if (editable && cropMode) {
                            CropOverlay(
                                crop = crop,
                                aspect = bmp.width.toFloat() / bmp.height,
                                lock = ratioLock,
                                onChange = { crop = it },
                            )
                        } else if (editable && crop != FullCrop) {
                            // Вне режима обрезки показываем затемнение вокруг выбранной области.
                            Canvas(Modifier.fillMaxSize()) { drawDim(crop) }
                        }
                    }
                }
            }

            // Пропорции для обрезки.
            if (editable && cropMode) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val presets = listOf<Pair<String, Float?>>(
                        "Свободно" to null, "1:1" to 1f, "4:3" to 4f / 3, "3:4" to 3f / 4,
                        "16:9" to 16f / 9, "9:16" to 9f / 16,
                    )
                    presets.forEach { (label, r) ->
                        FilterChip(
                            selected = ratioLock == r,
                            onClick = {
                                ratioLock = r
                                val b = shown
                                if (r != null && b != null) crop = centeredCrop(r, b.width.toFloat() / b.height)
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // Подпись и отправка.
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = caption,
                    onValueChange = { caption = it.take(MaxCaption) },
                    placeholder = { Text("Добавить подпись…") },
                    maxLines = 5,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                Box(
                    Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable(enabled = !busy && (source != null || !editable)) {
                            busy = true
                            scope.launch {
                                val out = if (editable) {
                                    withContext(Dispatchers.IO) { exportEdited(pending.path, turns, crop) }
                                } else pending.path
                                onSend(out, caption.trim())
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** Рамка обрезки: тянем углы или двигаем всю область. */
@Composable
private fun CropOverlay(crop: Rect, aspect: Float, lock: Float?, onChange: (Rect) -> Unit) {
    val density = LocalDensity.current
    val grab = with(density) { 36.dp.toPx() }
    var active by remember { mutableStateOf(Handle.NONE) }
    val cur by rememberUpdatedState(crop)
    val accent = MaterialTheme.colorScheme.primary

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(aspect, lock) {
                detectDragGestures(
                    onDragStart = { p ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val r = cur
                        fun near(cx: Float, cy: Float) = abs(p.x - cx * w) < grab && abs(p.y - cy * h) < grab
                        active = when {
                            near(r.left, r.top) -> Handle.TL
                            near(r.right, r.top) -> Handle.TR
                            near(r.left, r.bottom) -> Handle.BL
                            near(r.right, r.bottom) -> Handle.BR
                            p.x in r.left * w..r.right * w && p.y in r.top * h..r.bottom * h -> Handle.MOVE
                            else -> Handle.NONE
                        }
                    },
                    onDragEnd = { active = Handle.NONE },
                    onDragCancel = { active = Handle.NONE },
                ) { change, drag ->
                    change.consume()
                    val dx = drag.x / size.width
                    val dy = drag.y / size.height
                    onChange(applyDrag(cur, active, dx, dy, lock, aspect))
                }
            },
    ) {
        drawDim(crop)
        val tl = Offset(crop.left * size.width, crop.top * size.height)
        val sz = Size(crop.width * size.width, crop.height * size.height)
        drawRect(Color.White, tl, sz, style = Stroke(2f))
        // Сетка 3×3.
        for (i in 1..2) {
            val x = tl.x + sz.width * i / 3
            val y = tl.y + sz.height * i / 3
            drawLine(Color.White.copy(alpha = 0.4f), Offset(x, tl.y), Offset(x, tl.y + sz.height), 1f)
            drawLine(Color.White.copy(alpha = 0.4f), Offset(tl.x, y), Offset(tl.x + sz.width, y), 1f)
        }
        listOf(
            tl, Offset(tl.x + sz.width, tl.y), Offset(tl.x, tl.y + sz.height), Offset(tl.x + sz.width, tl.y + sz.height),
        ).forEach { drawCircle(accent, 11f, it); drawCircle(Color.White, 11f, it, style = Stroke(2f)) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDim(crop: Rect) {
    val dim = Color.Black.copy(alpha = 0.6f)
    val l = crop.left * size.width
    val t = crop.top * size.height
    val r = crop.right * size.width
    val b = crop.bottom * size.height
    drawRect(dim, Offset.Zero, Size(size.width, t))
    drawRect(dim, Offset(0f, b), Size(size.width, size.height - b))
    drawRect(dim, Offset(0f, t), Size(l, b - t))
    drawRect(dim, Offset(r, t), Size(size.width - r, b - t))
}

/** Максимальная центрированная область с пропорцией [ratio] (ширина/высота в пикселях). */
private fun centeredCrop(ratio: Float, imageAspect: Float): Rect {
    // В нормализованных координатах: nw/nh = ratio / imageAspect.
    val k = ratio / imageAspect
    val (nw, nh) = if (k >= 1f) 1f to 1f / k else k to 1f
    return Rect((1f - nw) / 2, (1f - nh) / 2, (1f + nw) / 2, (1f + nh) / 2)
}

private fun applyDrag(r: Rect, handle: Handle, dx: Float, dy: Float, lock: Float?, aspect: Float): Rect {
    if (handle == Handle.NONE) return r
    if (handle == Handle.MOVE) {
        val mx = dx.coerceIn(-r.left, 1f - r.right)
        val my = dy.coerceIn(-r.top, 1f - r.bottom)
        return r.translate(mx, my)
    }
    var l = r.left
    var t = r.top
    var rr = r.right
    var b = r.bottom
    when (handle) {
        Handle.TL -> { l += dx; t += dy }
        Handle.TR -> { rr += dx; t += dy }
        Handle.BL -> { l += dx; b += dy }
        Handle.BR -> { rr += dx; b += dy }
        else -> Unit
    }
    l = l.coerceIn(0f, rr - MinCrop)
    rr = rr.coerceIn(l + MinCrop, 1f)
    t = t.coerceIn(0f, b - MinCrop)
    b = b.coerceIn(t + MinCrop, 1f)
    if (lock != null) {
        // Ширина ведущая; высота выводится из пропорции, якорь — противоположный угол.
        val k = lock / aspect // nw / nh
        var nw = rr - l
        var nh = nw / k
        val anchorBottom = handle == Handle.TL || handle == Handle.TR
        val maxH = if (anchorBottom) b else 1f - t
        if (nh > maxH) { nh = maxH; nw = nh * k }
        val anchorRight = handle == Handle.TL || handle == Handle.BL
        if (anchorRight) l = rr - nw else rr = l + nw
        if (anchorBottom) t = b - nh else b = t + nh
        if (l < 0f || rr > 1f || t < 0f || b > 1f) return r
    }
    return Rect(l, t, rr, b)
}

private fun Bitmap.rotated(turns: Int): Bitmap {
    if (turns % 4 == 0) return this
    val m = Matrix().apply { postRotate(90f * (turns % 4)) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
}

/** Декодирует картинку с уменьшением до [maxSide] и применяет EXIF-ориентацию. */
internal fun loadOriented(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
    val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val m = Matrix()
    when (runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
    }
    return if (m.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

/** Применяет поворот и кадрирование к полноразмерной картинке. Без правок возвращает исходный путь. */
private fun exportEdited(path: String, turns: Int, crop: Rect): String {
    if (turns % 4 == 0 && crop == FullCrop) return path
    return runCatching {
        val base = loadOriented(path, ExportMaxSide) ?: return path
        val rot = base.rotated(turns)
        val x = (crop.left * rot.width).toInt().coerceIn(0, rot.width - 1)
        val y = (crop.top * rot.height).toInt().coerceIn(0, rot.height - 1)
        val w = (crop.width * rot.width).toInt().coerceIn(1, rot.width - x)
        val h = (crop.height * rot.height).toInt().coerceIn(1, rot.height - y)
        val out = Bitmap.createBitmap(rot, x, y, w, h)
        val file = File(File(path).parentFile, "edited_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        file.absolutePath
    }.getOrDefault(path)
}
