package app.yougram.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.ui.draw.blur
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yougram.data.AppContainer
import app.yougram.feature.stories.StoriesViewModel
import app.yougram.feature.stories.StoriesBar
import app.yougram.feature.stories.StoryRef
import app.yougram.data.ChatFolderItem
import app.yougram.data.GlassSettings
import app.yougram.ui.calls.CallsScreen
import app.yougram.ui.calls.CallsViewModel
import app.yougram.ui.chats.ChatListScreen
import app.yougram.ui.chats.ChatListViewModel
import app.yougram.ui.chats.GlobalSearchScreen
import app.yougram.ui.settings.SettingsSearchResults
import app.yougram.ui.chats.GlobalSearchViewModel
import app.yougram.ui.contacts.ContactsScreen
import app.yougram.ui.contacts.ContactsViewModel
import app.yougram.ui.glass.BackdropState
import app.yougram.ui.glass.backdropSource
import app.yougram.ui.glass.glass
import app.yougram.ui.glass.rememberBackdropState
import app.yougram.ui.settings.SettingsPage
import app.yougram.ui.settings.UpdateScreen
import app.yougram.ui.settings.SettingsPageContent
import kotlin.math.roundToInt
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

enum class MainTab(val title: String, val icon: ImageVector) {
    Contacts("Контакты", Icons.Filled.People),
    Calls("Звонки", Icons.Filled.Call),
    Chats("Чаты", Icons.Filled.ChatBubble),
    Settings("Настройки", Icons.Filled.Settings),
}

private val TopBarContentHeight = 56.dp
private val FolderBarHeight = 48.dp
private val StoriesBarHeight = 88.dp
private val BottomBarHeight = 60.dp
private val BottomBarMargin = 8.dp

/** Отступ внутри «таблетки» — одинаковый со всех сторон, чтобы выбранная плашка была концентрична её краям. */
private val PillPadding = 6.dp

/** Кнопка поиска: квадрат со скруглёнными углами. */
private val SearchButtonShape = RoundedCornerShape(20.dp)
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
    val folderId = selectedFolder?.takeIf { id -> folders.any { it.id == id } }
    val showFolders = tab == MainTab.Chats && folders.isNotEmpty()
    val showStories = tab == MainTab.Chats && storyRefs.isNotEmpty() && !searching
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
        targetValue = if (barsVisible || searching || tab == MainTab.Settings) 1f else 0f,
        animationSpec = tween(BarsAnimationMillis, easing = FastOutSlowInEasing),
        label = "barsProgress",
    )

    val inSettingsSubpage = tab == MainTab.Settings && settingsPage != SettingsPage.Home

    // Пока открыт поиск, всё, что под ним (список чатов / настройки), плавно размывается.
    val underSearchBlur by animateDpAsState(
        targetValue = if (searching) SearchBlurRadius else 0.dp,
        animationSpec = tween(SearchAnimationMillis, easing = FastOutSlowInEasing),
        label = "underSearchBlur",
    )

    BackHandler(enabled = inSettingsSubpage) {
        settingsPage = settingsPage.parent
    }

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
                            slideInHorizontally(tween(ScreenAnimationMillis, easing = FastOutSlowInEasing)) { dir * it } +
                                    fadeIn(tween(ScreenAnimationMillis))
                            ).togetherWith(
                            slideOutHorizontally(tween(ScreenAnimationMillis, easing = FastOutSlowInEasing)) { -dir * it / 3 } +
                                    fadeOut(tween(ScreenAnimationMillis / 2)),
                        )
                },
                label = "mainScreen",
            ) { (currentTab, currentPage) ->
                when (currentTab) {
                    MainTab.Chats -> Box(Modifier.fillMaxSize()) {
                        // Список остаётся в композиции под результатами поиска, поэтому прокрутка не теряется.
                        Box(Modifier.fillMaxSize().blur(underSearchBlur)) {
                            ChatListScreen(
                                viewModel = chatListViewModel,
                                query = "",
                                contentPadding = chatsPadding,
                                onOpenChat = onOpenChat,
                                lines = chatPrefs.listLines,
                                selectedChatId = selectedChatId,
                            )
                        }
                        AnimatedVisibility(
                            visible = searching && query.isNotBlank(),
                            enter = fadeIn(tween(SearchAnimationMillis)),
                            exit = fadeOut(tween(SearchAnimationMillis / 2)),
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
                        Box(Modifier.fillMaxSize().blur(underSearchBlur)) {
                            SettingsPageContent(
                                page = currentPage,
                                container = container,
                                contentPadding = contentPadding,
                                onNavigate = { settingsPage = it },
                                onOpenChat = onOpenChat,
                            )
                        }
                        AnimatedVisibility(
                            visible = searching && query.isNotBlank(),
                            enter = fadeIn(tween(SearchAnimationMillis)),
                            exit = fadeOut(tween(SearchAnimationMillis / 2)),
                        ) {
                            SettingsSearchResults(
                                query = query,
                                contentPadding = contentPadding,
                                onOpen = {
                                    settingsPage = it
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
                { settingsPage = settingsPage.parent }
            } else null,
            folders = if (showFolders) folders else emptyList(),
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

        // Нижняя панель остаётся на месте при прокрутке: прячется только верхняя.
        GlassBottomBar(
            selected = tab,
            onSelect = {
                tab = it
                searching = false
                query = ""
            },
            onSearch = { searching = true },
            showSearch = !chatPrefs.searchOnTop,
            backdrop = backdrop,
            glass = glass,
            bottomInset = bottomInset,
            progress = { 1f },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Стекло под системной панелью навигации. Рисуется поверх «таблетки», поэтому она уезжает «под» него.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset)
                .glass(backdrop, glass, RectangleShape),
        )

        // «Обновление клиента»: на весь экран, поверх верхней и нижней панелей.
        AnimatedVisibility(
            visible = tab == MainTab.Settings && settingsPage == SettingsPage.Update,
            enter = slideInVertically(tween(ScreenAnimationMillis, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(ScreenAnimationMillis)),
            exit = slideOutVertically(tween(ScreenAnimationMillis, easing = FastOutSlowInEasing)) { it / 6 } + fadeOut(tween(ScreenAnimationMillis / 2)),
        ) {
            UpdateScreen(
                updater = container.updater,
                client = container.telegram.client,
                onBack = { settingsPage = settingsPage.parent },
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
                        fadeIn(tween(SearchAnimationMillis, delayMillis = 60)) +
                            slideInHorizontally(tween(SearchAnimationMillis, easing = FastOutSlowInEasing)) { it / 5 } +
                            scaleIn(tween(SearchAnimationMillis, easing = FastOutSlowInEasing), initialScale = 0.94f)
                        ).togetherWith(
                        fadeOut(tween(SearchAnimationMillis / 2)) +
                            slideOutHorizontally(tween(SearchAnimationMillis, easing = FastOutSlowInEasing)) { -it / 8 },
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
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        )
                        IconButton(onClick = onCloseSearch) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть поиск")
                        }
                    } else {
                        if (onBack != null) {
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
                        }
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (searchOnTop && onBack == null) {
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onSearch) {
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
        FolderChip("Все", selected == null) { onSelect(null) }
        folders.forEach { folder ->
            FolderChip(folder.title, selected == folder.id) { onSelect(folder.id) }
        }
    }
}

@Composable
private fun FolderChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun GlassBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    onSearch: () -> Unit,
    showSearch: Boolean,
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Основная «таблетка» с вкладками: у выбранной вкладки рядом с иконкой показывается название.
        Row(
            Modifier
                .weight(1f)
                .height(BottomBarHeight)
                .glass(backdrop, glass, CircleShape)
                .padding(PillPadding),
            // SpaceBetween: крайняя плашка прижата к краю с тем же отступом, что и сверху/снизу.
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                TabItem(tab = tab, selected = tab == selected, onClick = { onSelect(tab) })
            }
        }

        // Отдельная круглая кнопка поиска справа, той же высоты, что и «таблетка».
        if (showSearch) Box(
            Modifier
                .size(BottomBarHeight)
                .glass(backdrop, glass, SearchButtonShape)
                .clip(SearchButtonShape)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Поиск")
        }
    }
}

@Composable
private fun TabItem(tab: MainTab, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .animateContentSize()
            // Высота = внутренняя высота «таблетки», поэтому зазор до её краёв везде одинаковый.
            .height(BottomBarHeight - PillPadding * 2)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 14.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(24.dp))
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(tab.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}