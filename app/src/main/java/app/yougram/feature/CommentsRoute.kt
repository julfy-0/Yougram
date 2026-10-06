package app.yougram.feature.chat.comments

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.yougram.data.AppContainer
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser

const val COMMENTS_ROUTE = "comments/{chatId}/{postId}"

fun NavGraphBuilder.commentsRoute(navController: NavHostController, container: AppContainer) {
    composable(
        route = COMMENTS_ROUTE,
        arguments = listOf(
            navArgument("chatId") { type = NavType.LongType },
            navArgument("postId") { type = NavType.LongType },
        ),
    ) { entry ->
        val chatId = entry.arguments!!.getLong("chatId")
        val postId = entry.arguments!!.getLong("postId")
        val chatsRepo = container.chatRepository
        val vm: CommentsViewModel = viewModel(
            key = "comments_${chatId}_$postId",
            factory = viewModelFactory {
                initializer {
                    CommentsViewModel(
                        repo = CommentsRepository(container.telegram.client),
                        channelChatId = chatId,
                        postId = postId,
                        resolve = { m ->
                            when (val s = m.senderId) {
                                is MessageSenderUser -> SenderInfo(
                                    name = chatsRepo.userName(s.userId),
                                    // У личного чата id совпадает с id пользователя.
                                    avatarPath = chatsRepo.chats.value.firstOrNull { it.id == s.userId }?.avatarPath,
                                )
                                is MessageSenderChat -> {
                                    val chat = chatsRepo.chats.value.firstOrNull { it.id == s.chatId }
                                    SenderInfo(chat?.title ?: "Канал", chat?.avatarPath)
                                }
                                else -> SenderInfo("", null)
                            }
                        },
                    )
                }
            },
        )
        val glass by container.settings.glass.collectAsState()
        val prefs by container.settings.chatPrefs.collectAsState()
        CommentsScreen(vm, glass = glass, prefs = prefs, onBack = { navController.popBackStack() })
    }
}
