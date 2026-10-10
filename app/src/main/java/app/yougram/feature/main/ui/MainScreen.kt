package app.yougram.feature.main.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.yougram.core.ui.component.ExpressiveIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.yougram.core.ui.component.TextButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import app.yougram.core.ui.shape.LocalShapeBag
import app.yougram.core.ui.shape.ShapeBag
import app.yougram.core.ui.skipWhenOffscreen
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yougram.core.di.AppContainer
import app.yougram.core.settings.GlassSettings
import app.yougram.core.ui.glass.BackdropState
import app.yougram.core.ui.glass.LocalPlates
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.feature.calls.ui.CallsScreen
import app.yougram.feature.calls.ui.CallsViewModel
import app.yougram.feature.chat.data.ChatFolderItem
import app.yougram.feature.chat.search.ui.GlobalSearchScreen
import app.yougram.feature.chat.search.ui.GlobalSearchViewModel
import app.yougram.feature.chat.ui.GlassPickerPanel
import app.yougram.feature.chatlist.ui.ChatListScreen
import app.yougram.feature.chatlist.ui.ChatListViewModel
import app.yougram.feature.contacts.ui.ContactsScreen
import app.yougram.feature.contacts.ui.ContactsViewModel
import app.yougram.feature.settings.AccountManagerContent
import app.yougram.feature.settings.LocalOpenAccountManager
import app.yougram.feature.settings.SettingsPage
import app.yougram.feature.settings.SettingsPageContent
import app.yougram.feature.settings.SettingsSearchResults
import app.yougram.feature.settings.about.UpdateGlassBar
import app.yougram.feature.stories.data.StoryRef
import app.yougram.feature.stories.ui.StoriesBar
import app.yougram.feature.stories.ui.StoriesViewModel
import kotlin.math.roundToInt

enum class MainTab(val title: String, val icon: ImageVector) {
    Contacts("Контакты", Icons.Filled.People),
    Calls("Звонки", Icons.Filled.Call),
    Chats("Чаты", Icons.Filled.ChatBubble),
    Settings("Настройки", Icons.Filled.Settings),
}

private val TopBarContentHeight = 56.dp
private val FolderBarHeight = 48.dp
private val StoriesBarHeight = 88.dp
private val BottomBarHeight = 64.dp
private val BottomBarMargin = 8.dp

/** Кнопка поиска: квадрат со скруглёнными углами. */
private val SearchButtonShape = RoundedCornerShape(13.dp)
/** Кнопка поиска внизу меньше «таблетки» на 30%. */
private const val SearchButtonScale = 0.7f
private val SearchBlurRadius = 24.dp
private const val SearchAnimationMillis = 260
private const val SearchResultsAlpha = 0.78f

private const val BarsAnimationMillis = 260

/** Длительность перехода между вкладками и подэкранами настроек (соответствует анимации чата). */
private const val ScreenAnimationMillis = 320

/**
 * Следит за прокруткой дочернего содержимого, ничего не потребляя: вниз по списку — прячем панели,
 * вверх — показываем. Накопительный порог гасит дребезг от мелких движений пальца.
 */
private class HideOnScrollConnection(
    private val thresholdPx: Float,
    private val onVisibleChange: (Boolean) -> Unit,
) : NestedScrollConnection {
    private var accumulated = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy == 0f) return Offset.Zero
        // Смена направления сбрасывает накопленное значение.
        accumulated = if (accumulated * dy < 0f) dy else accumulated + dy
        when {
            accumulated <= -thresholdPx -> onVisibleChange(false)
            accumulated >= thresholdPx -> onVisibleChange(true)
        }
        return Offset.Zero
    }
}

@Composable
fun MainScreen(
    container: AppContainer,
    onOpenChat: (Long) -> Unit,
    /** Открыть чат и прокрутить к сообщению (из результатов поиска). */
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    /** Чат, открытый в правой панели (планшет/фолд): подсвечивается в списке. */
    selectedChatId: Long? = null,
    onOpenStories: (Long, Int) -> Unit = { _, _ -> },
) {
    val glass by container.settings.glass.collectAsState()
    val chatPrefs by container.settings.chatPrefs.collectAsState()
    val backdrop = rememberBackdropState()
    val badgeOn by container.settings.badge.collectAsState()
    val ownBanner by container.settings.banner.collectAsState()
    val badgeContext = LocalContext.current
    LaunchedEffect(badgeOn, ownBanner) {
        val ok = runCatching { container.chatRepository.syncOwnBadge(badgeOn, ownBanner) }.getOrDefault(false)
        if (!ok && badgeOn) {
            Toast.makeText(badgeContext, "Не удалось обновить метку или баннер Yougram в профиле (проверьте длину «О себе»)", Toast.LENGTH_LONG).show()
        }
    }

    var tab by rememberSaveable { mutableStateOf(MainTab.Chats) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var barsVisible by rememberSaveable { mutableStateOf(true) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Home) }

    // История переходов (вкладка + страница настроек): жест «назад» возвращает на предыдущий экран.
    val backStack = remember { mutableStateListOf<Pair<MainTab, SettingsPage>>() }
    fun navigateTo(newTab: MainTab, newPage: SettingsPage) {
        if (newTab == tab && newPage == settingsPage) return
        backStack.add(tab to settingsPage)
        if (backStack.size > 30) backStack.removeAt(0)
        tab = newTab
        settingsPage = newPage
    }
    var accountSheetOpen by remember { mutableStateOf(false) }

    val chatListViewModel: ChatListViewModel = viewModel(
        factory = ChatListViewModel.factory(container.chatRepository),
    )
    val storiesViewModel: StoriesViewModel = viewModel(
        factory = StoriesViewModel.factory(container.stories, container.chatRepository),
    )
    val storyRefs by storiesViewModel.stories.collectAsState()
    val searchViewModel: GlobalSearchViewModel = viewModel(
        factory = GlobalSearchViewModel.factory(container.chatRepository),
    )

    val density = LocalDensity.current

    // Пружинное движение Expressive; на слабых устройствах остаются прежние tween.
    val motion = MaterialTheme.motionScheme
    val lowTier = app.yougram.core.ui.rememberDeviceTier() == app.yougram.core.ui.DeviceTier.Low
    val screenSlide: FiniteAnimationSpec<IntOffset> =
        if (lowTier) tween(ScreenAnimationMillis, easing = FastOutSlowInEasing) else motion.defaultSpatialSpec()
    val screenFade: FiniteAnimationSpec<Float> =
        if (lowTier) tween(ScreenAnimationMillis) else motion.defaultEffectsSpec()
    val fastFade: FiniteAnimationSpec<Float> =
        if (lowTier) tween(SearchAnimationMillis / 2) else motion.fastEffectsSpec()
    val searchFade: FiniteAnimationSpec<Float> =
        if (lowTier) tween(SearchAnimationMillis) else motion.defaultEffectsSpec()
    // Прогресс панелей идёт по effects-пружине без перелёта: он двигает панель и меняет прозрачность.
    val barsSpec: FiniteAnimationSpec<Float> =
        if (lowTier) tween(BarsAnimationMillis, easing = FastOutSlowInEasing) else motion.defaultEffectsSpec()
    val blurSpec: FiniteAnimationSpec<Dp> =
        if (lowTier) tween(SearchAnimationMillis, easing = FastOutSlowInEasing) else motion.defaultEffectsSpec()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Контент заезжает под панели, поэтому отступы берём с запасом на высоту панелей.
    val contentPadding = PaddingValues(
        top = topInset + TopBarContentHeight + 8.dp,
        bottom = bottomInset + BottomBarHeight + 24.dp,
    )

    // Папки — часть верхней панели (только на вкладке «Чаты»), поэтому списку чатов нужен отступ больше.
    val folders by chatListViewModel.folders.collectAsState()
    val selectedFolder by chatListViewModel.selectedFolder.collectAsState()
    val allChats by chatListViewModel.chats.collectAsState()
    val selection by chatListViewModel.selected.collectAsState()
    val selectionActive = tab == MainTab.Chats && selection.isNotEmpty()
    val selectedChats = remember(allChats, selection) { allChats.filter { it.id in selection } }
    var confirmDelete by remember { mutableStateOf(false) }
    // Число чатов с непрочитанными (без учёта чатов без звука) для бейджей на вкладках папок.
    val folderUnread = remember(allChats, folders) {
        val unread = allChats.filter { it.unreadCount > 0 && !it.muted }
        buildMap<Int?, Int> {
            put(null, unread.size)
            folders.forEach { f -> put(f.id, unread.count { (it.folderOrders[f.id] ?: 0L) != 0L }) }
        }
    }
    // Режим выбора: панель всегда видна и сбрасывается при смене вкладки.
    LaunchedEffect(tab) { chatListViewModel.clearSelection() }
    val folderId = selectedFolder?.takeIf { id -> folders.any { it.id == id } }
    val showFolders = tab == MainTab.Chats && folders.isNotEmpty()

    // Свайп влево-вправо по списку переключает папки; чипы вверху и пейджер синхронизированы в обе стороны.
    val folderPage = folders.indexOfFirst { it.id == folderId } + 1
    val folderPager = rememberPagerState(initialPage = folderPage) { folders.size + 1 }
    val foldersNow = rememberUpdatedState(folders)
    LaunchedEffect(folderPage, folders.size) {
        if (folderPager.currentPage != folderPage) folderPager.animateScrollToPage(folderPage)
    }
    LaunchedEffect(folderPager) {
        snapshotFlow { folderPager.settledPage }.collect { page ->
            val id = foldersNow.value.getOrNull(page - 1)?.id
            if (id != chatListViewModel.selectedFolder.value) chatListViewModel.selectFolder(id)
        }
    }
    val showStories = tab == MainTab.Chats && storyRefs.isNotEmpty() && !searching && !chatPrefs.hideStories
    val storiesTop = if (showStories) StoriesBarHeight else 0.dp
    val chatsPadding = if (folders.isNotEmpty()) {
        PaddingValues(
            top = topInset + TopBarContentHeight + FolderBarHeight + storiesTop + 8.dp,
            bottom = contentPadding.calculateBottomPadding(),
        )
    } else PaddingValues(
        top = contentPadding.calculateTopPadding() + storiesTop,
        bottom = contentPadding.calculateBottomPadding(),
    )

    val scrollConnection = remember(density) {
        HideOnScrollConnection(with(density) { 24.dp.toPx() }) { barsVisible = it }
    }

    // При смене вкладки панели всегда показываем.
    LaunchedEffect(tab) { barsVisible = true }

    // Анимация 0..1 читается только в фазе layout (через лямбды), поэтому не вызывает рекомпозиций.
    val barsProgress = animateFloatAsState(
        // В настройках верхняя панель не прячется никогда.
        targetValue = if (barsVisible || searching || selectionActive || tab == MainTab.Settings) 1f else 0f,
        animationSpec = barsSpec,
        label = "barsProgress",
    )

    val inSettingsSubpage = tab == MainTab.Settings && settingsPage != SettingsPage.Home

    // Пока открыт поиск, всё, что под ним (список чатов / настройки), плавно размывается.
    val underSearchBlur by animateDpAsState(
        targetValue = if (searching && app.yougram.core.ui.rememberDeviceTier() != app.yougram.core.ui.DeviceTier.Low) SearchBlurRadius else 0.dp,
        animationSpec = blurSpec,
        label = "underSearchBlur",
    )

    fun goBack() {
        if (backStack.isNotEmpty()) {
            val (previousTab, previousPage) = backStack.removeAt(backStack.lastIndex)
            tab = previousTab
            settingsPage = previousPage
        } else if (inSettingsSubpage) {
            settingsPage = settingsPage.parent
        }
    }

    BackHandler(enabled = backStack.isNotEmpty() || inSettingsSubpage) { goBack() }

    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    // Что сейчас показано: вкладка и (для настроек) подэкран. Смена этого значения запускает анимацию.
    val screen = tab to (if (tab == MainTab.Settings) settingsPage else SettingsPage.Home)

    Box(Modifier.fillMaxSize()) {
        // Слой с содержимым: именно его размытая копия видна под панелями.
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollConnection)
                .backdropSource(backdrop)
                .background(MaterialTheme.colorScheme.background),
        ) {
            AnimatedContent(
                targetState = screen,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    // Вперёд: вкладка правее или более глубокий подэкран; назад — наоборот.
                    val forward = if (initialState.first == targetState.first) {
                        targetState.second.ordinal > initialState.second.ordinal
                    } else {
                        targetState.first.ordinal > initialState.first.ordinal
                    }
                    val dir = if (forward) 1 else -1
                    (
                            slideInHorizontally(screenSlide) { dir * it } + fadeIn(screenFade)
                            ).togetherWith(
                            slideOutHorizontally(screenSlide) { -dir * it / 3 } + fadeOut(fastFade),
                        )
                },
                label = "mainScreen",
            ) { (currentTab, currentPage) ->
                Box(Modifier.fillMaxSize().skipWhenOffscreen()) {
                    when (currentTab) {
                        MainTab.Chats -> Box(Modifier.fillMaxSize()) {
                            // Список остаётся в композиции под результатами поиска, поэтому прокрутка не теряется.
                            Box(Modifier.fillMaxSize().then(if (underSearchBlur > 0.dp) Modifier.blur(underSearchBlur) else Modifier)) {
                                HorizontalPager(
                                    state = folderPager,
                                    modifier = Modifier.fillMaxSize(),
                                    key = { page -> if (page == 0) -1 else foldersNow.value.getOrNull(page - 1)?.id ?: (-2 - page) },
                                ) { page ->
                                    Box(Modifier.fillMaxSize().skipWhenOffscreen()) {
                                        ChatListScreen(
                                            viewModel = chatListViewModel,
                                            query = "",
                                            contentPadding = chatsPadding,
                                            onOpenChat = onOpenChat,
                                            lines = chatPrefs.listLines,
                                            swipeAction = chatPrefs.swipeAction,
                                            onSwipeAction = { action ->
                                                when (action) {
                                                    app.yougram.core.settings.SwipeAction.Delete,
                                                    app.yougram.core.settings.SwipeAction.ChangeFolder -> confirmDelete = true
                                                    app.yougram.core.settings.SwipeAction.Pin -> chatListViewModel.pinSelected()
                                                    app.yougram.core.settings.SwipeAction.Archive -> chatListViewModel.archiveSelected()
                                                    app.yougram.core.settings.SwipeAction.Mute -> chatListViewModel.muteSelected()
                                                    app.yougram.core.settings.SwipeAction.Off -> Unit
                                                }
                                            },
                                            selectedChatId = selectedChatId,
                                            folderId = foldersNow.value.getOrNull(page - 1)?.id,
                                        )
                                    }
                                }
                            }
                            AnimatedVisibility(
                                visible = searching && query.isNotBlank(),
                                enter = fadeIn(searchFade),
                                exit = fadeOut(fastFade),
                            ) {
                                GlobalSearchScreen(
                                    viewModel = searchViewModel,
                                    query = query,
                                    contentPadding = chatsPadding,
                                    fileState = container.chatRepository::fileState,
                                    onOpenChat = onOpenChat,
                                    onOpenMessage = onOpenMessage,
                                    // Полупрозрачная подложка: сквозь неё видно размытый список.
                                    modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = SearchResultsAlpha)),
                                )
                            }
                        }
                        MainTab.Contacts -> ContactsScreen(
                            viewModel = viewModel<ContactsViewModel>(factory = ContactsViewModel.factory(container.chatRepository)),
                            contentPadding = contentPadding,
                            onOpenChat = onOpenChat,
                            query = if (searching) query else "",
                        )
                        MainTab.Calls -> CallsScreen(
                            viewModel = viewModel<CallsViewModel>(factory = CallsViewModel.factory(container.chatRepository)),
                            contentPadding = contentPadding,
                            onOpenChat = onOpenChat,
                            query = if (searching) query else "",
                        )
                        MainTab.Settings -> Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().then(if (underSearchBlur > 0.dp) Modifier.blur(underSearchBlur) else Modifier)) {
                                // На каждой странице настроек формы значков случайные, и каждая встречается не больше двух раз.
                                val shapeBag = remember(currentPage) { ShapeBag() }
                                CompositionLocalProvider(
                                    LocalOpenAccountManager provides { accountSheetOpen = true },
                                    LocalShapeBag provides shapeBag,
                                ) {
                                    SettingsPageContent(
                                        page = currentPage,
                                        container = container,
                                        contentPadding = contentPadding,
                                        onNavigate = { navigateTo(MainTab.Settings, it) },
                                        onOpenChat = onOpenChat,
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = searching && query.isNotBlank(),
                                enter = fadeIn(searchFade),
                                exit = fadeOut(fastFade),
                            ) {
                                SettingsSearchResults(
                                    query = query,
                                    contentPadding = contentPadding,
                                    onOpen = {
                                        navigateTo(MainTab.Settings, it)
                                        searching = false
                                        query = ""
                                    },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = SearchResultsAlpha)),
                                )
                            }
                        }
                    }
                }
            }
        }

        GlassTopBar(
            title = if (inSettingsSubpage) settingsPage.title else tab.title,
            searching = searching,
            searchHint = when (tab) {
                MainTab.Chats -> "Чаты, контакты, сообщения"
                MainTab.Contacts -> "Поиск контактов"
                MainTab.Calls -> "Поиск звонков"
                MainTab.Settings -> "Поиск по настройкам"
            },
            query = query,
            onQueryChange = { query = it },
            onCloseSearch = {
                searching = false
                query = ""
            },
            onBack = if (inSettingsSubpage) {
                { goBack() }
            } else null,
            folders = if (showFolders) folders else emptyList(),
            folderUnread = folderUnread,
            selectionCount = if (selectionActive) selection.size else 0,
            selectionAllMuted = selectedChats.isNotEmpty() && selectedChats.all { it.muted },
            selectionAllPinned = selectedChats.isNotEmpty() && selectedChats.all { (folderId ?: 0) in it.pinnedLists },
            onClearSelection = chatListViewModel::clearSelection,
            onMuteSelected = chatListViewModel::muteSelected,
            onArchiveSelected = chatListViewModel::archiveSelected,
            onDeleteSelected = { confirmDelete = true },
            onPinSelected = chatListViewModel::pinSelected,
            canReadAll = tab == MainTab.Chats && allChats.any { it.unreadCount > 0 },
            onReadAll = chatListViewModel::markAllRead,
            selectedFolder = folderId,
            onSelectFolder = chatListViewModel::selectFolder,
            stories = if (showStories) storyRefs else emptyList(),
            onOpenStory = onOpenStories,
            onAddStory = { onOpenStories(0L, 0) },
            searchOnTop = chatPrefs.searchOnTop,
            onSearch = { searching = true },
            backdrop = backdrop,
            glass = glass,
            topInset = topInset,
            progress = { barsProgress.value },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Удалить чаты?") },
                text = { Text("Будет удалено чатов: ${selection.size}. Из групп и каналов вы выйдете, переписка у вас очистится.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        chatListViewModel.deleteSelected()
                    }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
            )
        }

        // Нижняя панель остаётся на месте при прокрутке: прячется только верхняя.
        // На странице «Обновление» вместо навигации — панель с кнопкой в том же стеклянном стиле.
        if (tab == MainTab.Settings && settingsPage == SettingsPage.Update) {
            UpdateGlassBar(
                updater = container.updater,
                backdrop = backdrop,
                glass = glass,
                bottomInset = bottomInset,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            GlassBottomBar(
                selected = tab,
                onSelect = {
                    navigateTo(it, settingsPage)
                    searching = false
                    query = ""
                },
                onSearch = { searching = true },
                showSearch = !chatPrefs.searchOnTop,
                chatsUnread = folderUnread[null] ?: 0,
                backdrop = backdrop,
                glass = glass,
                bottomInset = bottomInset,
                progress = { 1f },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Стекло под системной панелью навигации. Рисуется поверх «таблетки», поэтому она уезжает «под» него.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset)
                .glass(backdrop, glass, RectangleShape),
        )

        // Менеджер аккаунтов: полупрозрачная панель с размытием, выезжает снизу.
        GlassPickerPanel(
            visible = accountSheetOpen && tab == MainTab.Settings,
            backdrop = backdrop,
            glass = glass,
            transparency = LocalPlates.current.accounts,
            onDismiss = { accountSheetOpen = false },
        ) {
            AccountManagerContent(
                accountManager = container.accountManager,
                fileState = container.chatRepository::fileState,
                onDismiss = { accountSheetOpen = false },
            )
        }
    }
}

@Composable
private fun GlassTopBar(
    title: String,
    searching: Boolean,
    searchHint: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onBack: (() -> Unit)?,
    folders: List<ChatFolderItem>,
    folderUnread: Map<Int?, Int>,
    selectionCount: Int,
    selectionAllMuted: Boolean,
    selectionAllPinned: Boolean,
    onClearSelection: () -> Unit,
    onMuteSelected: () -> Unit,
    onArchiveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onPinSelected: () -> Unit,
    canReadAll: Boolean,
    onReadAll: () -> Unit,
    selectedFolder: Int?,
    onSelectFolder: (Int?) -> Unit,
    stories: List<StoryRef>,
    onOpenStory: (Long, Int) -> Unit,
    onAddStory: () -> Unit,
    searchOnTop: Boolean,
    onSearch: () -> Unit,
    backdrop: BackdropState,
    glass: GlassSettings,
    topInset: Dp,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val lowTier = app.yougram.core.ui.rememberDeviceTier() == app.yougram.core.ui.DeviceTier.Low
    val searchSlide: FiniteAnimationSpec<IntOffset> =
        if (lowTier) tween(SearchAnimationMillis, easing = FastOutSlowInEasing) else motion.defaultSpatialSpec()
    val searchScale: FiniteAnimationSpec<Float> =
        if (lowTier) tween(SearchAnimationMillis, easing = FastOutSlowInEasing) else motion.defaultSpatialSpec()
    val searchFadeIn: FiniteAnimationSpec<Float> =
        if (lowTier) tween(SearchAnimationMillis, delayMillis = 60) else motion.defaultEffectsSpec()
    val searchFadeOut: FiniteAnimationSpec<Float> =
        if (lowTier) tween(SearchAnimationMillis / 2) else motion.fastEffectsSpec()
    Box(
        modifier
            // Уезжает вверх только строка с заголовком: стекло под статус-баром и папки остаются.
            .offset {
                val hide = TopBarContentHeight + if (stories.isNotEmpty()) StoriesBarHeight else 0.dp
                IntOffset(0, -(hide.roundToPx() * (1f - progress())).roundToInt())
            }
            .fillMaxWidth()
            .height(
                topInset + TopBarContentHeight +
                        (if (stories.isNotEmpty()) StoriesBarHeight else 0.dp) +
                        (if (folders.isNotEmpty()) FolderBarHeight else 0.dp),
            )
            .glass(backdrop, glass, RectangleShape),
    ) {
        Row(
            Modifier
                .padding(top = topInset)
                .height(TopBarContentHeight)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                // Текст плавно исчезает, пока панель уезжает.
                .graphicsLayer { alpha = progress() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = searching,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (
                            fadeIn(searchFadeIn) +
                                    slideInHorizontally(searchSlide) { it / 5 } +
                                    scaleIn(searchScale, initialScale = 0.94f)
                            ).togetherWith(
                            fadeOut(searchFadeOut) +
                                    slideOutHorizontally(searchSlide) { -it / 8 },
                        )
                },
                label = "searchField",
            ) { isSearching ->
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    if (isSearching) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = { Text(searchHint) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        )
                        ExpressiveIconButton(onClick = onCloseSearch) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть поиск")
                        }
                    } else if (selectionCount > 0) {
                        ExpressiveIconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Отменить выбор")
                        }
                        Text(selectionCount.toString(), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(start = 8.dp))
                        Spacer(Modifier.weight(1f))
                        ExpressiveIconButton(onClick = onMuteSelected) {
                            Icon(
                                if (selectionAllMuted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                                contentDescription = if (selectionAllMuted) "Включить звук" else "Выключить звук",
                            )
                        }
                        ExpressiveIconButton(onClick = onArchiveSelected) {
                            Icon(Icons.Filled.Archive, contentDescription = "В архив")
                        }
                        ExpressiveIconButton(onClick = onDeleteSelected) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                        }
                        var moreOpen by remember { mutableStateOf(false) }
                        Box {
                            ExpressiveIconButton(onClick = { moreOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
                            }
                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (selectionAllPinned) "Открепить" else "Закрепить") },
                                    onClick = {
                                        moreOpen = false
                                        onPinSelected()
                                    },
                                )
                            }
                        }
                    } else {
                        if (onBack != null) {
                            ExpressiveIconButton(
                                onClick = onBack,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                            }
                            Spacer(Modifier.width(16.dp))
                        }
                        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
                        if (onBack == null && (searchOnTop || canReadAll)) Spacer(Modifier.weight(1f))
                        if (canReadAll && onBack == null) {
                            ExpressiveIconButton(onClick = onReadAll) {
                                Icon(Icons.Filled.DoneAll, contentDescription = "Прочитать всё")
                            }
                        }
                        if (searchOnTop && onBack == null) {
                            ExpressiveIconButton(onClick = onSearch) {
                                Icon(Icons.Filled.Search, contentDescription = "Поиск")
                            }
                        }
                    }
                }
            }
        }
        // Истории — между заголовком и папками; прячутся вместе с заголовком.
        if (stories.isNotEmpty()) {
            StoriesBar(
                stories = stories,
                onOpen = onOpenStory,
                onAdd = onAddStory,
                modifier = Modifier
                    .padding(top = topInset + TopBarContentHeight)
                    .height(StoriesBarHeight)
                    .graphicsLayer { alpha = progress() },
            )
        }
        if (folders.isNotEmpty()) {
            FolderTabs(
                folders = folders,
                unread = folderUnread,
                selected = selectedFolder,
                onSelect = onSelectFolder,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

/** Вкладки папок: «Все» и папки пользователя; выбранная залита акцентным цветом. */
@Composable
private fun FolderTabs(
    folders: List<ChatFolderItem>,
    unread: Map<Int?, Int>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(FolderBarHeight)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderChip("Все", selected == null, unread[null] ?: 0) { onSelect(null) }
        folders.forEach { folder ->
            FolderChip(folder.title, selected == folder.id, unread[folder.id] ?: 0) { onSelect(folder.id) }
        }
    }
}

@Composable
private fun FolderChip(text: String, selected: Boolean, unread: Int, onClick: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    // Выбранная папка становится «таблеткой», остальные — более квадратными (connected button group).
    val corner by animateIntAsState(
        targetValue = if (selected) 50 else 28,
        animationSpec = motion.fastSpatialSpec(),
        label = "folderChipShape",
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
        animationSpec = motion.defaultEffectsSpec(),
        label = "folderChipColor",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner.coerceIn(0, 50)),
        color = container,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics {
            role = Role.Tab
            this.selected = selected
        },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1)
            if (unread > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp),
                ) {
                    Text(
                        if (unread > 99) "99+" else unread.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private val NavSlot = 48.dp
private val NavGap = 2.dp
private val NavPadVertical = 8.dp
private val NavPadHorizontal = 8.dp

/**
 * Вкладки навбара. У каждой вкладки своя «таблетка»: при нажатии на кнопку плашка раскрывается пружиной,
 * а подпись плавно появляется (и плавно исчезает при переключении на другую вкладку),
 * фон плавно переходит между «залитым» и прозрачным. Слот вкладки 48 dp, зазор 2 dp.
 */
@Composable
private fun NavTabs(selected: MainTab, onSelect: (MainTab) -> Unit, chatsUnread: Int) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLargeEmphasized
    Row(
        Modifier.padding(horizontal = NavPadHorizontal, vertical = NavPadVertical),
        horizontalArrangement = Arrangement.spacedBy(NavGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab ->
            // Дополнительная ширина под подпись: сама подпись + правый отступ 8 dp.
            val px = remember(tab, labelStyle) { measurer.measure(tab.title, labelStyle, maxLines = 1).size.width }
            val extra = with(density) { px.toDp() } + 8.dp
            NavTab(
                tab,
                selected = tab == selected,
                extra = extra,
                style = labelStyle,
                badge = if (tab == MainTab.Chats) chatsUnread else 0,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun NavTab(tab: MainTab, selected: Boolean, extra: Dp, style: TextStyle, badge: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Быстрая пружина с лёгким перелётом, как в образце.
    val frac by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = 0.72f, stiffness = 650f),
        label = "navFrac",
    )
    val textAlpha by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(200, easing = FastOutSlowInEasing),
        label = "navTextAlpha",
    )
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f),
        tween(160),
        label = "navContainer",
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        tween(160),
        label = "navContent",
    )
    Box(
        Modifier
            .width(NavSlot + extra * frac.coerceIn(0f, 1.12f))
            .height(NavSlot)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        Row(
            // requiredWidth центрировал строку в слоте и сдвигал иконку влево на extra/2 (за пределы clip),
            // поэтому у неактивных вкладок иконки пропадали. Здесь содержимое всегда прижато к левому краю.
            Modifier.align(Alignment.CenterStart).wrapContentWidth(Alignment.Start, unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(NavSlot), contentAlignment = Alignment.Center) {
                Icon(
                    tab.icon,
                    contentDescription = if (selected) null else tab.title,
                    tint = content,
                    modifier = Modifier.size(24.dp),
                )
                if (badge > 0) {
                    // Плашка числа чатов с непрочитанными, как на вкладках папок.
                    Surface(
                        shape = CircleShape,
                        color = scheme.primary,
                        contentColor = scheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 2.dp)
                            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (badge > 99) "99+" else badge.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (selected || frac > 0.001f) {
                Text(
                    tab.title,
                    modifier = Modifier.graphicsLayer { alpha = textAlpha },
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    style = style,
                )
            }
        }
    }
}

@Composable
private fun GlassBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    onSearch: () -> Unit,
    showSearch: Boolean,
    chatsUnread: Int,
    backdrop: BackdropState,
    glass: GlassSettings,
    bottomInset: Dp,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            // Уезжает вниз на всю свою высоту вместе с отступами.
            .offset {
                val distance = (BottomBarHeight + BottomBarMargin + bottomInset).roundToPx()
                IntOffset(0, (distance * (1f - progress())).roundToInt())
            }
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomInset + BottomBarMargin),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Стеклянная «таблетка» по ширине вкладок: стекло даёт Modifier.glass.
        Box(
            Modifier
                .height(BottomBarHeight)
                .glass(backdrop, glass, CircleShape)
                .clip(CircleShape),
        ) {
            Box(Modifier.align(Alignment.Center)) { NavTabs(selected, onSelect, chatsUnread) }
        }

        // Отдельная кнопка поиска справа, той же высоты, что и «таблетка».
        if (showSearch) Box(
            Modifier
                .size(BottomBarHeight * SearchButtonScale)
                .glass(backdrop, glass, SearchButtonShape)
                .clip(SearchButtonShape)
                .clickable(onClick = onSearch, role = Role.Button, onClickLabel = "Поиск"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Поиск", modifier = Modifier.size(20.dp))
        }
    }
}