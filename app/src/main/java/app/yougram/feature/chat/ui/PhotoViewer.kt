package app.yougram.feature.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yougram.core.ui.component.rememberFileBitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import app.yougram.core.settings.GlassSettings
import app.yougram.core.ui.glass.SystemBarsGlass
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.MediaItem
import kotlinx.coroutines.flow.flowOf

private val ViewerGlass = GlassSettings(blurRadius = 28f, opacity = 0.35f)

/**
 * Полноэкранный просмотр фото чата: листание влево-вправо между всеми загруженными фото,
 * счётчик «N из M», зум щипком и двойным тапом (в приближении листание отключается, жест двигает картинку).
 */
@Composable
fun PhotoViewer(items: List<MediaItem>, startIndex: Int, viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var zoomed by remember { mutableStateOf(false) }
    val backdrop = rememberBackdropState()
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        app.yougram.core.ui.SecureWindowEffect()
        SystemBarsGlass(Modifier.fillMaxSize(), background = Color.Black) {
            // Источник для размытия: только фото, без счётчика и кнопки.
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = !zoomed,
                    beyondViewportPageCount = 1,
                    key = { items[it].fileId },
                ) { page ->
                    ZoomablePhoto(
                        media = items[page],
                        viewModel = viewModel,
                        isCurrent = page == pagerState.currentPage,
                        onZoomChange = { if (page == pagerState.currentPage) zoomed = it },
                    )
                }
            }
            if (items.size > 1) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp)
                        .glass(backdrop, ViewerGlass, CircleShape, Color.Black)
                        .border(0.6.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                ) {
                    Text(
                        "${pagerState.currentPage + 1} из ${items.size}",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 6.dp, start = 12.dp)
                    .size(40.dp)
                    .glass(backdrop, ViewerGlass, CircleShape, Color.Black)
                    .border(0.6.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
            }
        }
    }
}

/** Одна страница просмотра: превью, пока грузится оригинал; зум и перетаскивание. */
@Composable
private fun ZoomablePhoto(media: MediaItem, viewModel: ChatViewModel, isCurrent: Boolean, onZoomChange: (Boolean) -> Unit) {
    val full by remember(media.fileId) { viewModel.fileState(media.fileId) }.collectAsState(FileState())
    val preview by remember(media.previewFileId) {
        media.previewFileId?.let(viewModel::fileState) ?: flowOf(FileState())
    }.collectAsState(FileState())
    // Соседние страницы подгружаем с меньшим приоритетом, чтобы листание не ждало загрузки.
    LaunchedEffect(media.fileId, isCurrent) { viewModel.download(media.fileId, if (isCurrent) 32 else 16) }
    LaunchedEffect(media.previewFileId) { media.previewFileId?.let { viewModel.download(it, 24) } }

    val fullBitmap = rememberFileBitmap(full.path, 2048)
    val previewBitmap = rememberFileBitmap(preview.path, 1024)
    val bitmap = fullBitmap ?: previewBitmap

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(scale > 1.01f) { onZoomChange(scale > 1.01f) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Один палец при масштабе 1 не перехватываем: это жест листания пейджера.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val multiTouch = event.changes.size > 1
                                if (multiTouch || scale > 1f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    scale = (scale * zoom).coerceIn(1f, 6f)
                                    val maxX = size.width * (scale - 1f) / 2f
                                    val maxY = size.height * (scale - 1f) / 2f
                                    offset = if (scale <= 1f) Offset.Zero else Offset(
                                        (offset.x + pan.x).coerceIn(-maxX, maxX),
                                        (offset.y + pan.y).coerceIn(-maxY, maxY),
                                    )
                                    event.changes.forEach { c -> if (c.positionChanged()) c.consume() }
                                }
                            } while (event.changes.any { c -> c.pressed })
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else scale = 2.5f
                        })
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }
        if (fullBitmap == null && isCurrent) {
            LoadingIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                    .size(32.dp),
                color = Color.White,
            )
        }
    }
}
