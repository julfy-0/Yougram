package app.yougram.feature.chat.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yougram.core.di.AppContainer
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr

/** Поиск сообщений по хэштегу (нажатие на #тег в сообщении): те же результаты, что у глобального поиска. */
@Composable
fun HashtagSearchScreen(
    container: AppContainer,
    tag: String,
    onBack: () -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
) {
    val vm: GlobalSearchViewModel = viewModel(
        key = "hashtag-$tag",
        factory = GlobalSearchViewModel.factory(container.chatRepository),
    )
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barHeight = topInset + 56.dp

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GlobalSearchScreen(
            viewModel = vm,
            query = tag,
            contentPadding = PaddingValues(top = barHeight + 8.dp, bottom = 24.dp),
            fileState = container.chatRepository::fileState,
            onOpenChat = { onOpenMessage(it, 0L) },
            onOpenMessage = onOpenMessage,
        )
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = topInset)
                .height(56.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад")) }
            Text(tag, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
