package app.yougram.feature.stories.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.Avatar
import app.yougram.core.ui.component.rememberFileBitmap
import app.yougram.feature.stories.data.StoryRef
import java.io.File

/** Лента историй для верхней панели: «Моя история» и круги чатов; непросмотренные — с цветным кольцом. */
@Composable
fun StoriesBar(
    stories: List<StoryRef>,
    onOpen: (Long, Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        // Если кружки помещаются в ширину — стоят по центру панели, иначе прокручиваются с начала.
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "own") {
            StoryTile(title = "Моя история", avatarPath = null, unread = false, add = true, onClick = onAdd)
        }
        items(stories, key = { "${it.chatId}:${it.info.storyId}" }) { ref ->
            StoryTile(
                title = ref.title,
                avatarPath = ref.avatarPath,
                unread = ref.unread,
                add = false,
                onClick = { onOpen(ref.chatId, ref.info.storyId) },
            )
        }
    }
}

@Composable
private fun StoryTile(title: String, avatarPath: String?, unread: Boolean, add: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ring = if (unread) {
        Brush.sweepGradient(listOf(scheme.primary, scheme.tertiary, scheme.primary))
    } else {
        Brush.linearGradient(listOf(scheme.onSurface.copy(alpha = 0.25f), scheme.onSurface.copy(alpha = 0.25f)))
    }
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(58.dp)
                .then(if (add) Modifier else Modifier.border(2.5.dp, ring, CircleShape))
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (add) {
                Box(
                    Modifier.fillMaxSize().clip(CircleShape).background(scheme.primary.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, contentDescription = "Опубликовать историю", tint = scheme.primary) }
            } else {
                Avatar(title = title, path = avatarPath, size = 48.dp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun StoriesScreen(viewModel: StoriesViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val story by viewModel.story.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val file = File(context.cacheDir, "story_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
            viewModel.publishPhoto(file.absolutePath)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        story?.let { current ->
            // Media is downloaded by the repository; this screen intentionally keeps the viewer simple and stable.
            val path = (viewModel.mediaPath.collectAsState().value)
            val bitmap = rememberFileBitmap(path, 1080)
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                when (current.content) {
                    is dev.g000sha256.tdl.dto.StoryContentPhoto -> if (bitmap != null) androidx.compose.foundation.Image(bitmap, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)))
                    is dev.g000sha256.tdl.dto.StoryContentVideo -> if (path != null) androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx -> android.widget.VideoView(ctx).apply { setVideoPath(path); start() } },
                        modifier = Modifier.fillMaxWidth().height(520.dp).clip(RoundedCornerShape(24.dp)),
                    )
                    else -> Text("Этот тип истории пока не поддерживается", color = Color.White)
                }
                Text("История", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(current.caption.text, color = Color.White)
            }
        } ?: Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(onClick = { launcher.launch("image/*") }) { Icon(Icons.Default.Add, "Опубликовать историю") }
            Spacer(Modifier.height(12.dp))
            Text("Выберите фото для истории", color = Color.White)
        }
        FilledIconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) { Icon(Icons.Default.Close, "Закрыть") }
    }
}
