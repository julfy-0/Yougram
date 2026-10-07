package app.yougram.feature.chat.comments.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.yougram.core.di.AppContainer
import app.yougram.feature.chat.ui.ChatScreen
import app.yougram.feature.chat.ui.ChatViewModel

const val COMMENTS_ROUTE = "comments/{chatId}/{postId}"

/**
 * Комментарии — это обычный экран чата, открытый на ветке поста в чате обсуждения:
 * те же пузыри, шапка, поле ввода, ответы, реакции, медиа и меню сообщений.
 */
fun NavGraphBuilder.commentsRoute(navController: NavHostController, container: AppContainer) {
    composable(
        route = COMMENTS_ROUTE,
        arguments = listOf(
            navArgument("chatId") { type = NavType.LongType },
            navArgument("postId") { type = NavType.LongType },
        ),
    ) { entry ->
        val channelId = entry.arguments!!.getLong("chatId")
        val postId = entry.arguments!!.getLong("postId")

        // null — ещё грузится; (0, 0) — комментарии недоступны.
        val thread by produceState<Pair<Long, Long>?>(null, channelId, postId) {
            value = container.chatRepository.openCommentThread(channelId, postId) ?: (0L to 0L)
        }
        val resolved = thread
        when {
            resolved == null -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            resolved.second == 0L -> Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Комментарии недоступны. Назад")
                }
            }

            else -> {
                val discussionChatId = resolved.first
                val threadId = resolved.second
                val vm: ChatViewModel = viewModel(
                    key = "comments-$discussionChatId-$threadId",
                    factory = ChatViewModel.factory(
                        repository = container.chatRepository,
                        chatId = discussionChatId,
                        settings = container.settings,
                        threadId = threadId,
                    ),
                )
                val channelTitle by produceState("", channelId) {
                    value = runCatching { container.chatRepository.getChatTitle(channelId) }.getOrDefault("")
                }
                ChatScreen(
                    viewModel = vm,
                    settings = container.settings,
                    onBack = { navController.popBackStack() },
                    onOpenChatProfile = { navController.navigate("profile/$channelId") },
                    onOpenProfile = { id -> navController.navigate("profile/$id") },
                    titleOverride = "Комментарии",
                    subtitleOverride = channelTitle,
                    threadMode = true,
                )
            }
        }
    }
}
