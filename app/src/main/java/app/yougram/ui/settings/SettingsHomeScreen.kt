package app.yougram.ui.settings

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
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import app.yougram.data.SettingsRepository
import app.yougram.plugin.NativePluginManager
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
import app.yougram.data.AccountEntry
import app.yougram.data.AccountManager
import app.yougram.data.ChatRepository
import app.yougram.data.FileState
import app.yougram.data.ProfileItem
import app.yougram.ui.FileAvatar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Страницы вкладки «Настройки»; [title] показывается в верхней панели подэкрана. */
enum class SettingsPage(val title: String) {
    Home("Настройки"),
    Appearance("Внешний вид"),
    Account("Аккаунт"),
    ChatSettings("Настройки чатов"),
    Privacy("Конфиденциальность"),
    Security("Безопасность"),
    Notifications("Уведомления"),
    DataStorage("Данные и память"),
    Plugins("Плагины"),
    Folders("Папки с чатами"),
    Devices("Устройства"),
    PowerSaving("Энергосбережение"),
    Language("Язык"),
    About("О приложении"),
    Blocked("Чёрный список"),
    Websites("Авторизованные сайты"),
    Extras("Режим призрака и шпион"),
    Ghost("Режим призрака"),
    Spy("Шпион"),
    MessageFilters("Фильтры сообщений"),
    SharedFilters("Общие фильтры"),
    ShadowBan("Теневой бан"),
    Banner("Баннер профиля"),
    Premium("Telegram Premium"),
    Stars("Звёзды Telegram"),
    Business("Telegram для бизнеса"),
    TelegramHub("Telegram");

    /** Страница, на которую ведёт «Назад». */
    val parent: SettingsPage
        get() = when (this) {
            Blocked, Websites -> Privacy
            Ghost, Spy, MessageFilters, Banner -> Extras
            SharedFilters, ShadowBan -> MessageFilters
            Premium, Stars, Business -> TelegramHub
            else -> Home
        }
}

@Composable
fun SettingsHomeScreen(
    viewModel: SettingsHomeViewModel,
    accountManager: AccountManager,
    settings: SettingsRepository,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var accountSheetOpen by remember { mutableStateOf(false) }

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
        ProfileHeader(
            state = state,
            viewModel = viewModel,
            accountManager = accountManager,
            settings = settings,
            onChangeAvatar = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onOpenAccountManager = { accountSheetOpen = true },
        )

        val sections = listOf(
            "Yougram" to listOf(
                HomeEntry("Настройки Yougram", "Тема, цвета, панели", Icons.Filled.Tune, null, SettingsPage.Appearance),
                HomeEntry("Призрак, шпион, фильтры", "Скрытность и локальный архив", Icons.Filled.VisibilityOff, null, SettingsPage.Extras),
                HomeEntry("Плагины", "Плагины на C++", Icons.Filled.Extension, null, SettingsPage.Plugins),
                HomeEntry("Энергосбережение", "Экономия заряда", Icons.Filled.BatterySaver, null, SettingsPage.PowerSaving),
            ),
            "Аккаунт и защита" to listOf(
                HomeEntry("Аккаунт", "Номер телефона, имя пользователя", Icons.Filled.Person, null, SettingsPage.Account),
                HomeEntry("Конфиденциальность", "Кто видит ваши данные", Icons.Filled.VpnKey, null, SettingsPage.Privacy),
                HomeEntry("Безопасность", "Пин-код, графический ключ, отпечаток", Icons.Filled.Lock, null, SettingsPage.Security),
                HomeEntry("Устройства", "Активные сеансы", Icons.Filled.Laptop, state.devices?.toString(), SettingsPage.Devices),
            ),
            "Чаты" to listOf(
                HomeEntry("Настройки чатов", "Размер текста, анимации", Icons.Filled.ChatBubble, null, SettingsPage.ChatSettings),
                HomeEntry("Папки с чатами", "Сортировка чатов по папкам", Icons.Filled.Folder, null, SettingsPage.Folders),
                HomeEntry("Уведомления", "Звуки, сигналы, бейджи", Icons.Filled.Notifications, null, SettingsPage.Notifications),
            ),
            "Данные и язык" to listOf(
                HomeEntry("Данные и память", "Кэш, автозагрузка медиа", Icons.Filled.PieChart, null, SettingsPage.DataStorage),
                HomeEntry("Язык", null, Icons.Filled.Language, language, SettingsPage.Language),
            ),
        )

        sections.forEach { (title, entries) ->
            SectionLabel(title)
            SettingGroup { entries.forEach { e -> item { HomeEntryRow(e, onNavigate) } } }
        }

        SettingGroup {
            item { SettingRow("Telegram", subtitle = "Premium, Звёзды, Бизнес, подарки, помощь", icon = Icons.Filled.Star, onClick = { onNavigate(SettingsPage.TelegramHub) }) }
            item { SettingRow("О приложении", subtitle = "Версия, баннер, информация", icon = Icons.Filled.Info, onClick = { onNavigate(SettingsPage.About) }) }
        }

        Text(
            "Yougram ${BuildConfig.VERSION_NAME}",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(SettingsPage.About) }
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (accountSheetOpen) {
        AccountManagerSheet(
            accountManager = accountManager,
            fileState = viewModel::fileState,
            onDismiss = { accountSheetOpen = false },
        )
    }
}

private class HomeEntry(
    val title: String,
    val subtitle: String?,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: String?,
    val page: SettingsPage,
)

@Composable
private fun HomeEntryRow(e: HomeEntry, onNavigate: (SettingsPage) -> Unit) {
    SettingRow(e.title, subtitle = e.subtitle, icon = e.icon, value = e.value, onClick = { onNavigate(e.page) })
}

@Composable
private fun ProfileHeader(
    state: SettingsHomeState,
    viewModel: SettingsHomeViewModel,
    accountManager: AccountManager,
    settings: SettingsRepository,
    onChangeAvatar: () -> Unit,
    onOpenAccountManager: () -> Unit,
) {
    val profile = state.profile
    val accounts by accountManager.accounts.collectAsState()
    val premium by settings.localPremium.collectAsState()

    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.clickable(onClick = onChangeAvatar)) {
            FileAvatar(
                title = profile?.name.orEmpty(),
                fileId = profile?.avatarFileId,
                fileState = viewModel::fileState,
                size = 96.dp,
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                if (state.avatarUpdating) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Изменить аватар", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                profile?.name.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (premium) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Premium",
                    tint = androidx.compose.ui.graphics.Color(0xFFB36BFF),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        val sub = listOfNotNull(
            profile?.phone?.takeIf { it.isNotEmpty() },
            profile?.username?.let { "@$it" },
        ).joinToString(" • ")
        if (sub.isNotEmpty()) {
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .clickable(onClick = onOpenAccountManager)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.SwitchAccount, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("Аккаунты (${accounts.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerSheet(
    accountManager: AccountManager,
    fileState: (Int) -> Flow<FileState>,
    onDismiss: () -> Unit,
) {
    val accounts by accountManager.accounts.collectAsState()
    val activeId by accountManager.activeAccountId.collectAsState()
    var removeTarget by remember { mutableStateOf<AccountEntry?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Управление аккаунтами",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                }
            }

            Text(
                text = "Переключайтесь между аккаунтами или добавьте новый:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(accounts, key = { it.id }) { acc ->
                    val isActive = acc.id == activeId
                    Surface(
                        onClick = {
                            if (!isActive) {
                                onDismiss()
                                accountManager.switchAndRestart(acc.id)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FileAvatar(
                                title = acc.name.ifEmpty { "Аккаунт" },
                                // id файла принадлежит базе своего аккаунта: у неактивных аватарку показать нельзя.
                                fileId = if (isActive) acc.avatarFileId else null,
                                fileState = fileState,
                                size = 44.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = acc.name.ifEmpty { "Аккаунт" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (acc.phone.isNotEmpty() || !acc.username.isNullOrEmpty()) {
                                    Text(
                                        text = listOfNotNull(acc.phone.takeIf { it.isNotEmpty() }, acc.username?.let { "@$it" }).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                            if (isActive) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Активен",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            } else if (accounts.size > 1) {
                                IconButton(
                                    onClick = { removeTarget = acc },
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Удалить аккаунт",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    onDismiss()
                    accountManager.addAndRestart()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Добавить аккаунт", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    removeTarget?.let { acc ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Убрать аккаунт?") },
            text = {
                Text(
                    "«${acc.name.ifEmpty { "Аккаунт" }}» будет убран из списка, локальные данные удалятся. " +
                            "Сеанс в Telegram останется активным, его можно закрыть в разделе «Устройства».",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    accountManager.removeAccount(acc.id)
                    removeTarget = null
                }) { Text("Убрать") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Отмена") } },
        )
    }
}

data class SettingsHomeState(
    val profile: ProfileItem? = null,
    val devices: Int? = null,
    val avatarUpdating: Boolean = false,
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

    companion object {
        fun factory(repository: ChatRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsHomeViewModel(repository) }
        }
    }
}