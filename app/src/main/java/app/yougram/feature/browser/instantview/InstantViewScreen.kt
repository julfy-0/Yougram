package app.yougram.feature.browser.instantview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yougram.core.di.AppContainer
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.ui.component.rememberFileBitmap
import app.yougram.feature.chat.data.FileState

/** Экран Instant View: статья страницы, разобранная TDLib, в нативной вёрстке. */
@Composable
fun InstantViewScreen(
    container: AppContainer,
    url: String,
    onClose: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val repo = container.chatRepository
    var page by remember(url) { mutableStateOf(repo.cachedInstantView(url)) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        if (page == null) {
            page = runCatching { repo.loadInstantView(url, onlyLocal = false) }.getOrNull()
            failed = page == null
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barHeight = topInset + 56.dp

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val p = page
        when {
            p != null -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp,
                    top = barHeight + 8.dp, bottom = bottomInset + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(p.blocks) { block -> IvBlockView(block, container, onOpenLink, onOpenInBrowser, url) }
            }
            failed -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Не удалось загрузить статью", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onOpenInBrowser(url) }) { Text("Открыть в браузере") }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        }
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = topInset)
                .height(56.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад"))
            }
            Text(
                page?.title.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            TextButton(onClick = { onOpenInBrowser(url) }) { Text("В браузере") }
        }
    }
}

@Composable
private fun annotated(t: IvText, onOpen: (String) -> Unit): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val markColor = MaterialTheme.colorScheme.tertiaryContainer
    val fixedBg = MaterialTheme.colorScheme.surfaceVariant
    return remember(t, linkColor, markColor, fixedBg, onOpen) {
        buildAnnotatedString {
            append(t.text)
            for (s in t.spans) {
                val start = s.start.coerceIn(0, t.text.length)
                val end = s.end.coerceIn(start, t.text.length)
                if (end <= start) continue
                when (s.style) {
                    IvStyle.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    IvStyle.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    IvStyle.Underline -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    IvStyle.Strike -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                    IvStyle.Fixed -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = fixedBg), start, end)
                    IvStyle.Marked -> addStyle(SpanStyle(background = markColor), start, end)
                    IvStyle.Link -> {
                        val url = s.url
                        if (!url.isNullOrEmpty()) {
                            addLink(
                                LinkAnnotation.Clickable(
                                    tag = "url",
                                    styles = TextLinkStyles(
                                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                                    ),
                                    linkInteractionListener = { onOpen(url) },
                                ),
                                start, end,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IvBlockView(
    block: IvBlock,
    container: AppContainer,
    onOpen: (String) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    pageUrl: String,
) {
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    val body = ty.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp)
    val muted = cs.onSurfaceVariant
    when (block) {
        is IvBlock.Title -> Text(annotated(block.text, onOpen), style = ty.headlineMedium, fontWeight = FontWeight.Bold)
        is IvBlock.Subtitle -> Text(annotated(block.text, onOpen), style = ty.titleMedium, color = muted)
        is IvBlock.Kicker -> Text(annotated(block.text, onOpen), style = ty.labelLarge, color = cs.primary)
        is IvBlock.Header -> Text(
            annotated(block.text, onOpen),
            style = if (block.level == 1) ty.titleLarge else ty.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp),
        )
        is IvBlock.AuthorDate -> Text(block.line, style = ty.bodyMedium, color = muted)
        is IvBlock.Paragraph -> Text(annotated(block.text, onOpen), style = body)
        is IvBlock.Footer -> Text(annotated(block.text, onOpen), style = ty.bodySmall, color = muted)
        is IvBlock.Preformatted -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant)
                .horizontalScroll(rememberScrollState()).padding(12.dp),
        ) {
            Text(
                annotated(block.text, onOpen),
                style = ty.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                softWrap = false,
            )
        }
        IvBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
        is IvBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (!block.pull) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(cs.primary))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    annotated(block.text, onOpen),
                    style = if (block.pull) ty.titleLarge else body,
                    fontStyle = FontStyle.Italic,
                )
                if (!block.credit.isBlank) Text(annotated(block.credit, onOpen), style = ty.bodySmall, color = muted)
            }
        }
        is IvBlock.BlockQuote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(cs.primary))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.blocks.forEach { IvBlockView(it, container, onOpen, onOpenInBrowser, pageUrl) }
                if (!block.credit.isBlank) Text(annotated(block.credit, onOpen), style = ty.bodySmall, color = muted)
            }
        }
        is IvBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (item in block.items) {
                Row {
                    Text(item.label.ifBlank { "•" }, style = body, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.blocks.forEach { IvBlockView(it, container, onOpen, onOpenInBrowser, pageUrl) }
                    }
                }
            }
        }
        is IvBlock.Photo -> IvPhoto(block, container, onOpen)
        is IvBlock.Details -> {
            var open by remember(block) { mutableStateOf(block.open) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (open) "▾" else "▸", style = body, modifier = Modifier.width(24.dp))
                    Text(annotated(block.header, onOpen), style = body, fontWeight = FontWeight.SemiBold)
                }
                if (open) block.blocks.forEach { IvBlockView(it, container, onOpen, onOpenInBrowser, pageUrl) }
            }
        }
        is IvBlock.Related -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!block.header.isBlank) Text(annotated(block.header, onOpen), style = ty.titleMedium, fontWeight = FontWeight.Bold)
            for (a in block.items) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant)
                        .clickable { onOpen(a.url) }.padding(12.dp),
                ) {
                    if (a.title.isNotBlank()) Text(a.title, style = ty.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (a.description.isNotBlank()) Text(a.description, style = ty.bodySmall, color = muted, maxLines = 3)
                }
            }
        }
        is IvBlock.Table -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!block.caption.isBlank) Text(annotated(block.caption, onOpen), style = ty.titleSmall, fontWeight = FontWeight.SemiBold)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                for (row in block.rows) {
                    Row {
                        for (cell in row) {
                            Text(
                                annotated(cell, onOpen),
                                style = ty.bodyMedium,
                                modifier = Modifier.width(140.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        is IvBlock.Unsupported -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant)
                .clickable { onOpenInBrowser(pageUrl) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(block.label, style = ty.bodyMedium, modifier = Modifier.weight(1f))
            Text("Открыть в браузере", style = ty.labelLarge, color = cs.primary)
        }
    }
}

@Composable
private fun IvPhoto(block: IvBlock.Photo, container: AppContainer, onOpen: (String) -> Unit) {
    val repo = container.chatRepository
    val state by repo.fileState(block.fileId).collectAsState(initial = FileState())
    LaunchedEffect(block.fileId) { repo.download(block.fileId) }
    val bitmap = rememberFileBitmap(state.path, 1600)
    val ratio = if (block.width > 0 && block.height > 0) block.width.toFloat() / block.height else 1.5f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxSize())
            }
        }
        if (!block.caption.isBlank) {
            Text(annotated(block.caption, onOpen), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!block.credit.isBlank) {
            Text(annotated(block.credit, onOpen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
