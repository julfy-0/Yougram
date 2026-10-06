package app.yougram.feature.chat.comments

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.g000sha256.tdl.TdlClient

const val COMMENTS_ROUTE = "comments/{chatId}/{postId}"

fun NavGraphBuilder.commentsRoute(navController: NavHostController, tdlClient: TdlClient) {
    composable(
        route = COMMENTS_ROUTE,
        arguments = listOf(
            navArgument("chatId") { type = NavType.LongType },
            navArgument("postId") { type = NavType.LongType },
        ),
    ) { entry ->
        val chatId = entry.arguments!!.getLong("chatId")
        val postId = entry.arguments!!.getLong("postId")
        val vm: CommentsViewModel = viewModel(
            key = "comments_${chatId}_$postId",
            factory = viewModelFactory {
                initializer { CommentsViewModel(CommentsRepository(tdlClient), chatId, postId) }
            },
        )
        CommentsScreen(vm, onBack = { navController.popBackStack() })
    }
}
