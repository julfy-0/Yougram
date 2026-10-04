package app.yougram.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.yougram.data.AppContainer
import app.yougram.data.AuthStep
import app.yougram.ui.auth.AuthScreen
import app.yougram.ui.auth.AuthViewModel
import app.yougram.ui.chat.ChatScreen
import app.yougram.ui.chat.ChatViewModel
import app.yougram.ui.main.MainScreen

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_AUTH = "auth"
private const val ROUTE_CHATS = "chats"
private const val ROUTE_CHAT = "chat/{chatId}"

private const val NavMillis = 320
private const val FadeMillis = 220

@Composable
fun YougramNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val step by container.authRepository.step.collectAsState(initial = AuthStep.Loading)

    // Авторизация управляет верхним уровнем навигации: вошли -> чаты, вышли -> экран входа.
    LaunchedEffect(step) {
        val current = navController.currentDestination?.route
        when (step) {
            AuthStep.Ready -> if (current != ROUTE_CHATS && current != ROUTE_CHAT) {
                navController.navigate(ROUTE_CHATS) { popUpTo(0) { inclusive = true } }
            }
            AuthStep.Loading -> Unit
            else -> if (current != ROUTE_AUTH) {
                navController.navigate(ROUTE_AUTH) { popUpTo(0) { inclusive = true } }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_SPLASH,
        // По умолчанию (заставка, вход) — плавное затухание.
        enterTransition = { fadeIn(tween(FadeMillis)) },
        exitTransition = { fadeOut(tween(FadeMillis)) },
        popEnterTransition = { fadeIn(tween(FadeMillis)) },
        popExitTransition = { fadeOut(tween(FadeMillis)) },
    ) {
        composable(ROUTE_SPLASH) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        composable(ROUTE_AUTH) {
            val vm: AuthViewModel = viewModel(factory = AuthViewModel.factory(container.authRepository))
            AuthScreen(viewModel = vm)
        }
        composable(
            route = ROUTE_CHATS,
            // Когда сверху открывается чат, главный экран слегка сдвигается влево и гаснет.
            exitTransition = {
                slideOutHorizontally(tween(NavMillis, easing = FastOutSlowInEasing)) { -it / 4 } +
                        fadeOut(tween(NavMillis))
            },
            popEnterTransition = {
                slideInHorizontally(tween(NavMillis, easing = FastOutSlowInEasing)) { -it / 4 } +
                        fadeIn(tween(NavMillis))
            },
        ) {
            MainScreen(
                container = container,
                onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
            )
        }
        composable(
            route = ROUTE_CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
            // Чат выезжает справа и уезжает обратно вправо.
            enterTransition = {
                slideInHorizontally(tween(NavMillis, easing = FastOutSlowInEasing)) { it } +
                        fadeIn(tween(NavMillis / 2))
            },
            popExitTransition = {
                slideOutHorizontally(tween(NavMillis, easing = FastOutSlowInEasing)) { it } +
                        fadeOut(tween(NavMillis / 2))
            },
        ) { entry ->
            val chatId = entry.arguments!!.getLong("chatId")
            val vm: ChatViewModel = viewModel(
                key = "chat-$chatId",
                factory = ChatViewModel.factory(container.chatRepository, chatId),
            )
            ChatScreen(viewModel = vm, settings = container.settings, onBack = { navController.popBackStack() })
        }
    }
}