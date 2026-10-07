package app.yougram.feature.chat.comments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yougram.core.settings.ChatPrefs
import app.yougram.core.settings.GlassSettings
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.Avatar
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.plateColor
import app.yougram.core.ui.glass.rememberBackdropState
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageText
import java.text.DateFormat
import java.util.Date

private val BarHeight = 56.dp
private val ComposerHeight = 52.dp

/**
 * Комментарии к посту в стиле клиента: стеклянные верхняя панель и поле ввода поверх списка,
 * пузыри с радиусом и размером текста из настроек чатов, аватарки авторов.
 */
@Composable
fun CommentsScreen(
    viewModel: CommentsViewModel,
    glass: GlassSettings,
    prefs: ChatPrefs,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val backdrop = rememberBackdropState()
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Снизу — либо клавиатура, либо системная навигация.
    val bottomInset = with(density) {
        maxOf(WindowInsets.ime.getBottom(density), WindowInsets.navigationBars.getBottom(density)).toDp()
    }

    LaunchedEffect(state.comments.size) {
        if (state.comments.isNotEmpty()) listState.animateScrollToItem(state.comments.lastIndex)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop).background(MaterialTheme.colorScheme.background)) {
            when {
                state.loading && state.comments.isEmpty() -> LoadingIndicator(Modifier.align(Alignment.Center))
                state.error -> Text(
                    "Комментарии недоступны",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.comments.isEmpty() -> Text(
                    "Комментариев пока нет",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = topInset + BarHeight + 12.dp,
                        bottom = bottomInset + ComposerHeight + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.comments, key = { it.id }) { msg ->
                        CommentRow(msg, state.senders[senderKey(msg)], prefs)
                    }
                }
            }
        }

        // Верхняя стеклянная панель.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topInset + BarHeight)
                .glass(backdrop, glass, RectangleShape)
                .padding(top = topInset)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Комментарии", style = MaterialTheme.typography.titleLarge)
                if (state.comments.isNotEmpty()) {
                    Text(
                        "${state.comments.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Поле ввода: стеклянная «таблетка» и круглая кнопка отправки.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = ComposerHeight)
                    .glass(backdrop, glass, RoundedCornerShape(26.dp))
                    .clip(RoundedCornerShape(26.dp)),
                placeholder = { Text("Комментарий") },
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            val canSend = input.isNotBlank()
            Box(
                Modifier
                    .size(ComposerHeight)
                    .clip(CircleShape)
                    .background(
                        if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                    .clickable(enabled = canSend) {
                        viewModel.send(input)
                        input = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CommentRow(msg: Message, sender: SenderInfo?, prefs: ChatPrefs) {
    val mine = msg.isOutgoing
    val r = prefs.bubbleRadius.dp
    val shape = RoundedCornerShape(
        topStart = r,
        topEnd = r,
        bottomStart = if (mine) r else 6.dp,
        bottomEnd = if (mine) 6.dp else r,
    )
    val name = sender?.name.orEmpty()
    val text = (msg.content as? MessageText)?.text?.text ?: "[вложение]"
    val time = remember(msg.date) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(msg.date * 1000L)) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            Avatar(title = name.ifEmpty { "?" }, path = sender?.avatarPath, size = 36.dp)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            shape = shape,
            color = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else plateColor(PlateArea.Chats),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (!mine && name.isNotEmpty()) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(text, fontSize = prefs.textSize.sp)
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                )
            }
        }
    }
}
