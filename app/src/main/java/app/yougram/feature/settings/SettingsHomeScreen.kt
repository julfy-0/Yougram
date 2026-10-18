@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SystemUpdate
import app.yougram.core.ui.component.LinearWavyProgressIndicator
import app.yougram.feature.update.data.AppUpdater
import app.yougram.feature.update.data.UpdateState
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.AlertDialog
import app.yougram.core.ui.component.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import app.yougram.core.ui.component.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.BuildConfig
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.ui.component.FileAvatar
import app.yougram.feature.account.data.AccountEntry
import app.yougram.feature.account.data.AccountManager
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.ProfileItem
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.plugin.NativePluginManager
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Страницы вкладки «Настройки»; [title] показывается в верхней панели подэкрана. */
enum class SettingsPage(val title: String) {
    Home("Настройки"),
    HomeLayoutEditor("Порядок вкладок"),
    Appearance("Внешний вид и чаты"),
    Account("Аккаунт"),
    Privacy("Безопасность и конфиденциальность"),
    Notifications("Уведомления"),
    DataStorage("Данные и память"),
    Plugins("Плагины"),
    Archive("Архив"),
    Devices("Устройства"),
    PowerSaving("Энергосбережение"),
    CreateGroup("Создать группу"),
    CreateChannel("Создать канал"),
    Language("Язык"),
    About("О приложении"),
    Blocked("Чёрный список"),
    Websites("Авторизованные сайты"),
    Extras("Настройки клиента"),
    Ghost("Режим призрака"),
    Spy("Шпион"),
    MessageFilters("Фильтры сообщений"),
    SharedFilters("Общие фильтры"),
    ShadowBan("Теневой бан"),
    Premium("Telegram Premium"),
    Stars("Звёзды Telegram"),
    Business("Telegram для бизнеса"),
    TelegramHub("Telegram"),
    CreateHub("Создать / Добавить"),
    SystemHub("Система"),
    Update("Обновление");

    /** Страница, на которую ведёт «Назад». */
    val parent: SettingsPage
        get() = when (this) {
            Blocked, Websites -> Privacy
            DataStorage, Language, PowerSaving -> SystemHub
            CreateGroup, CreateChannel -> CreateHub
            Ghost, Spy, MessageFilters -> Extras
            SharedFilters, ShadowBan -> MessageFilters
            Premium, Stars, Business -> TelegramHub
            Update -> Home
            else -> Home
        }
}

/** Открыть менеджер аккаунтов: сама панель рисуется в MainScreen, чтобы размывать фон под собой. */
val LocalOpenAccountManager = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun SettingsHomeScreen(
    viewModel: SettingsHomeViewModel,
    accountManager: AccountManager,
    settings: SettingsRepository,
    updater: AppUpdater,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
    onOpenProfile: (Long) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val update by updater.state.collectAsState()
    LaunchedEffect(Unit) { updater.check() }
    val context = LocalContext.current
    val openAccountManager = LocalOpenAccountManager.current

    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    val language = Locale.getDefault().let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.uppercase() } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val file = java.io.File(context.cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
                val ok = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    } != null
                }.getOrDefault(false)
                if (ok) viewModel.setAvatar(file.absolutePath)
                else Toast.makeText(context, "Не удалось прочитать изображение", Toast.LENGTH_SHORT).show()
            }
        }
        var showAvatars by remember { mutableStateOf(false) }
        val changeAvatar = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        if (showAvatars && state.avatars.isNotEmpty()) {
            app.yougram.feature.chat.ui.PhotoViewer(
                items = state.avatars,
                startIndex = 0,
                fileState = { viewModel.fileState(it) },
                download = { id, priority -> viewModel.download(id, priority) },
                onDismiss = { showAvatars = false },
                allowSave = true,
            )
        }
        ProfileHeader(
            state = state,
            viewModel = viewModel,
            accountManager = accountManager,
            settings = settings,
            // Тап по аватару — просмотр (если фото нет, сразу выбор нового), значок камеры — смена.
            onOpenAvatar = { if (state.avatars.isNotEmpty()) showAvatars = true else changeAvatar() },
            onChangeAvatar = changeAvatar,
            onOpenAccountManager = openAccountManager,
            onOpenProfile = { state.profile?.id?.let(onOpenProfile) },
        )

        // «О приложении» и обновление — сразу под профилем, в одной группе.
        SectionLabel("Обновления и ПО")
        val openUpdate = { onNavigate(SettingsPage.Update) }
        SettingGroup {
            item { SettingRow("О приложении", subtitle = "Версия, баннер, информация", icon = Icons.Filled.Info, onClick = { onNavigate(SettingsPage.About) }) }
            item {
                when (val u = update) {
                    UpdateState.Idle, UpdateState.Checking -> SettingRow(
                        title = "Проверка обновлений…", icon = Icons.Filled.SystemUpdate,
                        onClick = openUpdate,
                    )
                    UpdateState.UpToDate -> SettingRow(
                        title = "Установлена последняя версия", icon = Icons.Filled.SystemUpdate,
                        value = "Открыть", onClick = openUpdate,
                    )
                    is UpdateState.Available -> SettingRow(
                        title = "Доступна версия ${u.info.versionName}",
                        subtitle = "Нажмите, чтобы посмотреть изменения",
                        icon = Icons.Filled.SystemUpdate,
                        value = "Открыть", onClick = openUpdate,
                    )
                    is UpdateState.Downloading -> SettingRow(
                        title = "Загрузка ${(u.progress * 100).toInt()}%", icon = Icons.Filled.SystemUpdate,
                        onClick = openUpdate,
                        below = {
                            LinearWavyProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth())
                        },
                    )
                    is UpdateState.Ready -> SettingRow(
                        title = "Версия ${u.info.versionName} скачана",
                        subtitle = "Нажмите, чтобы установить",
                        icon = Icons.Filled.SystemUpdate,
                        value = "Установить", onClick = openUpdate,
                    )
                    is UpdateState.Error -> SettingRow(
                        title = u.message, icon = Icons.Filled.SystemUpdate,
                        value = "Открыть", onClick = openUpdate,
                    )
                }
            }
        }

        val all = homeEntries(state.devices?.toString(), language)
        val byId = all.associateBy { it.id }
        val savedLayout by settings.homeLayout.collectAsState()
        val layout = remember(savedLayout) { HomeLayout.normalize(HomeLayout.parse(savedLayout), all.map { it.id }) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onNavigate(SettingsPage.HomeLayoutEditor) }) { Text("Изменить порядок") }
        }

        layout.forEach { section ->
            if (section.entries.isNotEmpty()) {
                SectionLabel(section.title)
                SettingGroup {
                    section.entries.forEach { id ->
                        val e = byId[id] ?: return@forEach
                        item { HomeEntryRow(e, onNavigate) }
                    }
                }
            }
        }

        Text(
            "Yougram ${BuildConfig.VERSION_NAME}",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onNavigate(SettingsPage.About) }
                .heightIn(min = 48.dp)
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal class HomeEntry(
    val id: String,
    val title: String,
    val subtitle: String?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: String?,
    val page: SettingsPage,
)

/** Все вкладки главного экрана настроек; [devices] и [language] — значения справа у «Устройства» и «Язык». */
internal fun homeEntries(devices: String?, language: String?): List<HomeEntry> = listOf(
    HomeEntry("appearance", "Внешний вид и чаты", "Тема, панели, настройки чатов, папки", Icons.Filled.Tune, null, SettingsPage.Appearance),
    HomeEntry("client", "Настройки клиента", "Призрак, шпион, фильтры и др.", Icons.Filled.VisibilityOff, null, SettingsPage.Extras),
    HomeEntry("plugins", "Плагины", "Плагины на C++", Icons.Filled.Extension, null, SettingsPage.Plugins),
    HomeEntry("account", "Аккаунт", "Номер телефона, имя пользователя", Icons.Filled.Person, null, SettingsPage.Account),
    HomeEntry("security", "Безопасность и конфиденциальность", "Пин-код, отпечаток, кто видит ваши данные", Icons.Filled.Lock, null, SettingsPage.Privacy),
    HomeEntry("devices", "Устройства", "Активные сеансы", Icons.Filled.Laptop, devices, SettingsPage.Devices),
    HomeEntry("create", "Создать / Добавить", "Группа, канал, аккаунт", Icons.Filled.Add, null, SettingsPage.CreateHub),
    HomeEntry("archive", "Архивы", "Архивные чаты", Icons.Filled.Archive, null, SettingsPage.Archive),
    HomeEntry("notifications", "Уведомления", "Звуки, сигналы, бейджи", Icons.Filled.Notifications, null, SettingsPage.Notifications),
    HomeEntry("system", "Система", "Данные и память, язык, энергосбережение", Icons.Filled.Settings, language, SettingsPage.SystemHub),
    HomeEntry("telegram", "Telegram", "Premium, Звёзды, Бизнес, подарки, помощь", Icons.Filled.Star, null, SettingsPage.TelegramHub),
)

@Composable
private fun HomeEntryRow(e: HomeEntry, onNavigate: (SettingsPage) -> Unit) {
    SettingRow(e.title, subtitle = e.subtitle, icon = e.icon, value = e.value, onClick = { onNavigate(e.page) })
}

/** Карточка профиля: аватар в «цветочном» ореоле, имя крупно, кнопка аккаунтов — акцентное пятно. */
@Composable
private fun ProfileHeader(
    state: SettingsHomeState,
    viewModel: SettingsHomeViewModel,
    accountManager: AccountManager,
    settings: SettingsRepository,
    onOpenAvatar: () -> Unit,
    onChangeAvatar: () -> Unit,
    onOpenAccountManager: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val profile = state.profile
    val accounts by accountManager.accounts.collectAsState()
    val premium by settings.localPremium.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Родитель не обрезаем: иначе бейдж камеры в углу срезается формой аватара.
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                // Аватар целиком заполняет «звезду» (Cookie9Sided), без ореола и круглой вставки.
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .clickable(role = Role.Button, onClickLabel = "Открыть аватар", onClick = onOpenAvatar),
                ) {
                    FileAvatar(
                        title = profile?.name.orEmpty(),
                        fileId = profile?.avatarFileId,
                        fileState = viewModel::fileState,
                        size = 116.dp,
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(scheme.primary)
                        .clickable(role = Role.Button, onClickLabel = "Изменить аватар", onClick = onChangeAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.avatarUpdating) {
                        LoadingIndicator(Modifier.size(18.dp), color = scheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Изменить аватар", tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(role = Role.Button, onClickLabel = "Открыть профиль", onClick = onOpenProfile),
                )
                if (premium) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(scheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Premium",
                            tint = scheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            val sub = listOfNotNull(
                profile?.phone?.takeIf { it.isNotEmpty() },
                profile?.username?.let { "@$it" },
            ).joinToString(" • ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }

            // Кнопка Expressive (Medium): форма перетекает при нажатии.
            androidx.compose.material3.Button(
                onClick = onOpenAccountManager,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .heightIn(min = androidx.compose.material3.ButtonDefaults.MediumContainerHeight),
                shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                contentPadding = androidx.compose.material3.ButtonDefaults
                    .contentPaddingFor(androidx.compose.material3.ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(Icons.Filled.SwitchAccount, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Аккаунты (${accounts.size})", style = MaterialTheme.typography.titleMediumEmphasized)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

data class SettingsHomeState(
    val profile: ProfileItem? = null,
    val devices: Int? = null,
    val avatarUpdating: Boolean = false,
    /** Мои аватарки (от новых к старым) для просмотра и скачивания. */
    val avatars: List<app.yougram.feature.chat.data.MediaItem> = emptyList(),
    val error: String? = null,
)

class SettingsHomeViewModel(private val repository: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsHomeState())
    val state: StateFlow<SettingsHomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val profile = repository.loadProfile()
                _state.update { it.copy(profile = profile) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
        refreshAvatars()
        viewModelScope.launch {
            val count = repository.activeSessionsCount()
            _state.update { it.copy(devices = count) }
        }
    }

    fun setAvatar(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(avatarUpdating = true) }
            try {
                val profile = repository.setProfilePhoto(path)
                _state.update { it.copy(profile = profile, avatarUpdating = false) }
                refreshAvatars()
            } catch (e: Exception) {
                _state.update { it.copy(avatarUpdating = false, error = e.message) }
            } finally {
                java.io.File(path).delete()
            }
        }
    }

    fun openSupport(onResult: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                onResult(repository.openSupportChat())
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun fileState(fileId: Int): Flow<FileState> = repository.fileState(fileId)

    fun download(fileId: Int, priority: Int = 8) = repository.download(fileId, priority)

    private fun refreshAvatars() {
        viewModelScope.launch {
            val list = runCatching { repository.ownAvatarHistory() }.getOrDefault(emptyList())
            _state.update { it.copy(avatars = list) }
        }
    }

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsHomeViewModel(repository) }
        }
    }
}