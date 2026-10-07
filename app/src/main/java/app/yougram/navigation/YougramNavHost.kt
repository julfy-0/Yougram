package app.yougram.navigation

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.yougram.core.di.AppContainer
import app.yougram.core.ui.adaptive.DetailRoute
import app.yougram.core.ui.adaptive.TwoPaneShell
import app.yougram.core.ui.adaptive.decodeDetail
import app.yougram.core.ui.adaptive.encodeDetail
import app.yougram.core.ui.adaptive.rememberIsTwoPane
import app.yougram.core.ui.component.LocalOpenLink
import app.yougram.core.ui.component.LocalOpenUsername
import app.yougram.core.ui.component.isTelegramUrl
import app.yougram.core.ui.component.isWebUrl
import app.yougram.core.ui.component.normalizeUrl
import app.yougram.core.ui.component.openExternally
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.feature.auth.data.AuthStep
import app.yougram.feature.auth.ui.AuthScreen
import app.yougram.feature.auth.ui.AuthViewModel
import app.yougram.feature.browser.ui.BrowserScreen
import app.yougram.feature.calls.ui.CallOverlay
import app.yougram.feature.chat.comments.navigation.commentsRoute
import app.yougram.feature.chat.ui.ChatOrTopics
import app.yougram.feature.chat.ui.ChatScreen
import app.yougram.feature.chat.ui.ChatViewModel
import app.yougram.feature.main.ui.MainScreen
import app.yougram.feature.profile.ui.ProfileScreen
import app.yougram.feature.profile.ui.ProfileViewModel
import app.yougram.feature.stories.ui.StoriesScreen
import app.yougram.feature.stories.ui.StoryViewerViewModel
import kotlinx.coroutines.launch

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_AUTH = "auth"
private const val ROUTE_CHATS = "chats"
private const val ROUTE_CHAT = "chat/{chatId}?messageId={messageId}&topicId={topicId}"
private const val ROUTE_PROFILE = "profile/{chatId}"
private const val ROUTE_BROWSER = "browser/{url}"
private const val ROUTE_STORIES = "stories/{chatId}/{storyId}"

private const val NavMillis = 320
private const val FadeMillis = 220

@Composable
fun YougramNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val step by container.authRepository.step.collectAsState(initial = AuthStep.Loading)

    // Планшет / разложенный фолд: список слева, чат или профиль справа (стек хранится строкой).
    val twoPane = rememberIsTwoPane()
    val twoPaneNow = rememberUpdatedState(twoPane)
    var detailRaw by rememberSaveable { mutableStateOf("") }
    val detail = remember(detailRaw) { decodeDetail(detailRaw) }
    val setDetail: (List<DetailRoute>) -> Unit = remember { { list -> detailRaw = list.encodeDetail() } }
    val openChat: (Long, Long) -> Unit = remember(navController) {
        { chatId: Long, messageId: Long ->
            if (twoPaneNow.value) {
                setDetail(listOf(DetailRoute.Chat(chatId, messageId)))
            } else {
                navController.navigate(if (messageId != 0L) "chat/$chatId?messageId=$messageId" else "chat/$chatId")
            }
        }
    }

    // Сложили / разложили устройство: переносим открытые чат и профиль между режимами.
    LaunchedEffect(twoPane) {
        if (step != AuthStep.Ready) return@LaunchedEffect
        if (twoPane) {
            val items = listOfNotNull(navController.previousBackStackEntry, navController.currentBackStackEntry)
                .mapNotNull { it.toDetailRoute() }
            if (items.isNotEmpty()) {
                detailRaw = items.encodeDetail()
                navController.popBackStack(ROUTE_CHATS, inclusive = false)
            }
        } else {
            val items = decodeDetail(detailRaw)
            if (items.isNotEmpty()) {
                detailRaw = ""
                items.forEach { r ->
                    navController.navigate(
                        when (r) {
                            is DetailRoute.Chat -> "chat/${r.chatId}?messageId=${r.messageId}&topicId=${r.topicId}"
                            is DetailRoute.Profile -> "profile/${r.chatId}"
                        },
                    )
                }
            }
        }
    }

    // Авторизация управляет верхним уровнем навигации: вошли -> чаты, вышли -> экран входа.
    LaunchedEffect(step) {
        val current = navController.currentDestination?.route
        when (step) {
            AuthStep.Ready -> if (current != ROUTE_CHATS && current != ROUTE_CHAT && current != ROUTE_PROFILE && current != ROUTE_BROWSER) {
                navController.navigate(ROUTE_CHATS) { popUpTo(0) { inclusive = true } }
            }
            AuthStep.Loading -> Unit
            else -> if (current != ROUTE_AUTH) {
                navController.navigate(ROUTE_AUTH) { popUpTo(0) { inclusive = true } }
            }
        }
    }

    // Тап по уведомлению: открываем нужный чат поверх списка, когда вход выполнен.
    val pendingChat by container.pendingOpenChat.collectAsState()
    LaunchedEffect(pendingChat, step) {
        val id = pendingChat ?: return@LaunchedEffect
        if (step != AuthStep.Ready) return@LaunchedEffect
        if (twoPane) setDetail(listOf(DetailRoute.Chat(id))) else navController.navigate("chat/$id") { launchSingleTop = true }
        container.pendingOpenChat.value = null
    }

    val context = LocalContext.current
    val inAppBrowser by container.settings.inAppBrowser.collectAsState()
    val openLink: (String) -> Unit = remember(inAppBrowser, navController, context) {
        { raw: String ->
            val url = normalizeUrl(raw)
            if (inAppBrowser && isWebUrl(url) && !isTelegramUrl(url)) {
                navController.navigate("browser/${Uri.encode(url)}")
            } else {
                openExternally(context, url)
            }
        }
    }

    // Тап по @username: ищем в Telegram и открываем профиль (в планшетном режиме — в правой панели).
    val scope = rememberCoroutineScope()
    val detailNow = rememberUpdatedState(detail)
    val openUsername: (String) -> Unit = remember(navController, context) {
        { username: String ->
            scope.launch {
                val id = runCatching { container.chatRepository.resolveUsername(username) }.getOrNull()
                if (id == null) {
                    Toast.makeText(context, "@$username не найден", Toast.LENGTH_SHORT).show()
                } else if (twoPaneNow.value) {
                    setDetail(detailNow.value + DetailRoute.Profile(id))
                } else {
                    navController.navigate("profile/$id")
                }
            }
        }
    }

    CompositionLocalProvider(LocalOpenLink provides openLink, LocalOpenUsername provides openUsername) {
        val callBackdrop = rememberBackdropState()
        Box(Modifier.fillMaxSize()) {
            NavHost(
                modifier = Modifier.backdropSource(callBackdrop),
                navController = navController,
                startDestination = ROUTE_SPLASH,
                // По умолчанию (заставка, вход) — плавное затухание.
                enterTransition = { fadeIn(tween(FadeMillis)) },
                exitTransition = { fadeOut(tween(FadeMillis)) },
                popEnterTransition = { fadeIn(tween(FadeMillis)) },
                popExitTransition = { fadeOut(tween(FadeMillis)) },
            ) {
                composable(ROUTE_SPLASH) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                }
                composable(ROUTE_AUTH) {
                    val vm: AuthViewModel = viewModel(
                        factory = AuthViewModel.factory(container.authRepository, container.settings),
                    )
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
                    // movableContentOf: вкладка, прокрутка и поиск не сбрасываются при складывании/раскладывании.
                    val mainPane = remember {
                        movableContentOf { selected: Long? ->
                            MainScreen(
                                container = container,
                                onOpenChat = { chatId -> openChat(chatId, 0L) },
                                onOpenMessage = { chatId, messageId -> openChat(chatId, messageId) },
                                selectedChatId = selected,
                                onOpenStories = { chatId, storyId -> navController.navigate("stories/$chatId/$storyId") },
                            )
                        }
                    }
                    val selectedChat = detail.firstNotNullOfOrNull { (it as? DetailRoute.Chat)?.chatId }
                    if (twoPane) {
                        BackHandler(enabled = detail.isNotEmpty()) { setDetail(detail.dropLast(1)) }
                        TwoPaneShell(
                            detail = detail.lastOrNull(),
                            listPane = { mainPane(selectedChat) },
                            detailPane = { route -> DetailPane(route, detail, setDetail, container) },
                        )
                    } else {
                        mainPane(selectedChat)
                    }
                }
                composable(
                    route = ROUTE_STORIES,
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType }, navArgument("storyId") { type = NavType.IntType }),
                ) { entry ->
                    val chatId = entry.arguments?.getLong("chatId") ?: 0L
                    val storyId = entry.arguments?.getInt("storyId") ?: 0
                    val vm: StoryViewerViewModel = viewModel(
                        key = "story-$chatId-$storyId",
                        factory = StoryViewerViewModel.factory(container.stories, container.chatRepository, chatId, storyId),
                    )
                    StoriesScreen(viewModel = vm, onBack = { navController.popBackStack() })
                }
                composable(
                    route = ROUTE_CHAT,
                    arguments = listOf(
                        navArgument("chatId") { type = NavType.LongType },
                        navArgument("messageId") {
                            type = NavType.LongType
                            defaultValue = 0L
                        },
                        navArgument("topicId") {
                            type = NavType.IntType
                            defaultValue = 0
                        },
                    ),
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
                    val messageId = entry.arguments!!.getLong("messageId")
                    val topicId = entry.arguments!!.getInt("topicId")
                    ChatOrTopics(
                        container = container,
                        chatId = chatId,
                        messageId = messageId,
                        topicId = topicId,
                        onBack = { navController.popBackStack() },
                        onOpenTopic = { id -> navController.navigate("chat/$chatId?topicId=$id") },
                        onOpenChatProfile = { navController.navigate("profile/$chatId") },
                        onOpenProfile = { id -> navController.navigate("profile/$id") },
                        onCall = { userId, video -> container.callManager.startCall(userId, video) },
                        onOpenComments = { c, m -> navController.navigate("comments/$c/$m") },
                    )
                }

                commentsRoute(navController, container)

                composable(
                    route = ROUTE_BROWSER,
                    arguments = listOf(navArgument("url") { type = NavType.StringType }),
                    enterTransition = {
                        slideInHorizontally(tween(NavMillis, easing = FastOutSlowInEasing)) { it } +
                                fadeIn(tween(NavMillis / 2))
                    },
                    popExitTransition = {
                        slideOutHorizontally(tween(NavMillis, easing = FastOutSlowInEasing)) { it } +
                                fadeOut(tween(NavMillis / 2))
                    },
                ) { entry ->
                    BrowserScreen(
                        initialUrl = entry.arguments?.getString("url").orEmpty(),
                        onClose = { navController.popBackStack() },
                    )
                }
                composable(
                    route = ROUTE_PROFILE,
                    arguments = listOf(navArgument("chatId") { type = NavType.LongType }),
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
                    val vm: ProfileViewModel = viewModel(
                        key = "profile-$chatId",
                        factory = ProfileViewModel.factory(container.chatRepository, chatId),
                    )
                    ProfileScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() },
                        onOpenChat = {
                            // Если профиль открыт из этого же чата — просто возвращаемся, иначе открываем чат.
                            val previous = navController.previousBackStackEntry?.arguments?.getLong("chatId")
                            if (previous == chatId) navController.popBackStack() else navController.navigate("chat/$chatId")
                        },
                        onLeft = { navController.popBackStack(ROUTE_CHATS, inclusive = false) },
                        onCall = { userId, video -> container.callManager.startCall(userId, video) },
                    )
                }
            }
            CallOverlay(container.callManager, container.chatRepository, callBackdrop)
        }
    }
}

private fun NavBackStackEntry.toDetailRoute(): DetailRoute? {
    val id = arguments?.getLong("chatId") ?: return null
    return when (destination.route) {
        ROUTE_CHAT -> DetailRoute.Chat(id, arguments?.getLong("messageId") ?: 0L, arguments?.getInt("topicId") ?: 0)
        ROUTE_PROFILE -> DetailRoute.Profile(id)
        else -> null
    }
}

/** Правая панель: чат или профиль, навигация — по собственному стеку. */
@Composable
private fun DetailPane(
    route: DetailRoute,
    stack: List<DetailRoute>,
    setStack: (List<DetailRoute>) -> Unit,
    container: AppContainer,
) {
    when (route) {
        is DetailRoute.Chat -> {
            ChatOrTopics(
                container = container,
                chatId = route.chatId,
                messageId = route.messageId,
                topicId = route.topicId,
                onBack = { setStack(stack.dropLast(1)) },
                // Корневой чат правой панели не имеет «Назад»; чат, открытый из профиля, — имеет.
                showBack = stack.size > 1,
                onOpenTopic = { id -> setStack(stack + DetailRoute.Chat(route.chatId, 0L, id)) },
                onOpenChatProfile = { setStack(stack + DetailRoute.Profile(route.chatId)) },
                onOpenProfile = { id -> setStack(stack + DetailRoute.Profile(id)) },
                onCall = { userId, video -> container.callManager.startCall(userId, video) },
            )
        }
        is DetailRoute.Profile -> {
            val vm: ProfileViewModel = viewModel(
                key = "profile-${route.chatId}",
                factory = ProfileViewModel.factory(container.chatRepository, route.chatId),
            )
            // На широкой панели профиль не растягиваем: колонка до 640dp по центру.
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxHeight()) {
                    ProfileScreen(
                        viewModel = vm,
                        onBack = { setStack(stack.dropLast(1)) },
                        onOpenChat = {
                            val prev = stack.getOrNull(stack.size - 2)
                            if (prev is DetailRoute.Chat && prev.chatId == route.chatId) setStack(stack.dropLast(1))
                            else setStack(listOf(DetailRoute.Chat(route.chatId)))
                        },
                        onLeft = { setStack(emptyList()) },
                        onCall = { userId, video -> container.callManager.startCall(userId, video) },
                    )
                }
            }
        }
    }
}