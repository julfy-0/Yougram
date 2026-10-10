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
import androidx.compose.material.icons.filled.Archive
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
    Appearance("Внешний вид"),
    Account("Аккаунт"),
    ChatSettings("Настройки чатов"),
    Privacy("Конфиденциальность"),
    Security("Безопасность"),
    Notifications("Уведомления"),
    DataStorage("Данные и память"),
    Plugins("Плагины"),
    Folders("Папки с чатами"),
    Archive("Архив"),
    Devices("Устройства"),
    PowerSaving("Энергосбережение"),
    CreateGroup("Создать группу"),
    CreateChannel("Создать канал"),
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
    TelegramHub("Telegram"),
    Update("Обновление");

    /** Страница, на которую ведёт «Назад». */
    val parent: SettingsPage
        get() = when (this) {
            Blocked, Websites -> Privacy
            Ghost, Spy, MessageFilters, Banner -> Extras
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
        ProfileHeader(
            state = state,
            viewModel = viewModel,
            accountManager = accountManager,
            settings = settings,
            onChangeAvatar = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onOpenAccountManager = openAccountManager,
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

        val all = listOf(
            HomeEntry("appearance", "Настройки Yougram", "Тема, цвета, панели", Icons.Filled.Tune, null, SettingsPage.Appearance),
            HomeEntry("extras", "Призрак, шпион, фильтры", "Скрытность и локальный архив", Icons.Filled.VisibilityOff, null, SettingsPage.Extras),
            HomeEntry("plugins", "Плагины", "Плагины на C++", Icons.Filled.Extension, null, SettingsPage.Plugins),
            HomeEntry("power", "Энергосбережение", "Экономия заряда", Icons.Filled.BatterySaver, null, SettingsPage.PowerSaving),
            HomeEntry("account", "Аккаунт", "Номер телефона, имя пользователя", Icons.Filled.Person, null, SettingsPage.Account),
            HomeEntry("privacy", "Конфиденциальность", "Кто видит ваши данные", Icons.Filled.VpnKey, null, SettingsPage.Privacy),
            HomeEntry("security", "Безопасность", "Пин-код, графический ключ, отпечаток", Icons.Filled.Lock, null, SettingsPage.Security),
            HomeEntry("devices", "Устройства", "Активные сеансы", Icons.Filled.Laptop, state.devices?.toString(), SettingsPage.Devices),
            HomeEntry("createGroup", "Создать группу", "Новая группа с участниками", Icons.Filled.Group, null, SettingsPage.CreateGroup),
            HomeEntry("createChannel", "Создать канал", "Публичный или приватный", Icons.Filled.Campaign, null, SettingsPage.CreateChannel),
            HomeEntry("chatSettings", "Настройки чатов", "Размер текста, анимации", Icons.Filled.ChatBubble, null, SettingsPage.ChatSettings),
            HomeEntry("folders", "Папки с чатами", "Сортировка чатов по папкам", Icons.Filled.Folder, null, SettingsPage.Folders),
            HomeEntry("archive", "Архив", "Архивные чаты", Icons.Filled.Archive, null, SettingsPage.Archive),
            HomeEntry("notifications", "Уведомления", "Звуки, сигналы, бейджи", Icons.Filled.Notifications, null, SettingsPage.Notifications),
            HomeEntry("data", "Данные и память", "Кэш, автозагрузка медиа", Icons.Filled.PieChart, null, SettingsPage.DataStorage),
            HomeEntry("language", "Язык", null, Icons.Filled.Language, language, SettingsPage.Language),
            HomeEntry("telegram", "Telegram", "Premium, Звёзды, Бизнес, подарки, помощь", Icons.Filled.Star, null, SettingsPage.TelegramHub),
        )
        val byId = all.associateBy { it.id }
        val savedLayout by settings.homeLayout.collectAsState()
        val layout = remember(savedLayout) { HomeLayout.normalize(HomeLayout.parse(savedLayout), all.map { it.id }) }
        val save = { l: List<HomeSection> -> settings.setHomeLayout(HomeLayout.toJson(l)) }
        var editing by remember { mutableStateOf(false) }
        var renaming by remember { mutableStateOf<Int?>(null) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { editing = !editing }) { Text(if (editing) "Готово" else "Изменить порядок") }
        }

        layout.forEachIndexed { si, section ->
            if (editing) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        section.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    IconButton(onClick = { renaming = si }) { Icon(Icons.Filled.Edit, "Переименовать") }
                    IconButton(onClick = { save(HomeLayout.moveSection(layout, si, -1)) }, enabled = si > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, "Категория выше")
                    }
                    IconButton(onClick = { save(HomeLayout.moveSection(layout, si, 1)) }, enabled = si < layout.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Категория ниже")
                    }
                    IconButton(onClick = { save(HomeLayout.deleteSection(layout, si)) }, enabled = layout.size > 1) {
                        Icon(Icons.Filled.Delete, "Удалить категорию")
                    }
                }
            } else if (section.entries.isNotEmpty()) {
                SectionLabel(section.title)
            }
            if (section.entries.isEmpty()) {
                if (editing) {
                    Text(
                        "Пусто: переместите сюда вкладки стрелками",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SettingGroup {
                    section.entries.forEachIndexed { ei, id ->
                        val e = byId[id] ?: return@forEachIndexed
                        item {
                            if (editing) {
                                SettingRow(
                                    e.title, subtitle = e.subtitle, icon = e.icon,
                                    trailing = {
                                        Row {
                                            IconButton(
                                                onClick = { save(HomeLayout.moveEntry(layout, si, ei, -1)) },
                                                enabled = si > 0 || ei > 0,
                                            ) { Icon(Icons.Filled.KeyboardArrowUp, "Выше") }
                                            IconButton(
                                                onClick = { save(HomeLayout.moveEntry(layout, si, ei, 1)) },
                                                enabled = si < layout.lastIndex || ei < section.entries.lastIndex,
                                            ) { Icon(Icons.Filled.KeyboardArrowDown, "Ниже") }
                                        }
                                    },
                                )
                            } else {
                                HomeEntryRow(e, onNavigate)
                            }
                        }
                    }
                }
            }
        }

        if (editing) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { save(HomeLayout.addSection(layout)) }) { Text("Добавить категорию") }
                TextButton(onClick = { settings.setHomeLayout(null) }) { Text("Сбросить") }
            }
        }

        renaming?.let { idx ->
            var text by remember(idx) { mutableStateOf(layout.getOrNull(idx)?.title.orEmpty()) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Название категории") },
                text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        if (text.isNotBlank()) save(HomeLayout.rename(layout, idx, text.trim()))
                        renaming = null
                    }) { Text("Готово") }
                },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text("Отмена") } },
            )
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

private class HomeEntry(
    val id: String,
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

/** Карточка профиля: аватар в «цветочном» ореоле, имя крупно, кнопка аккаунтов — акцентное пятно. */
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
            Box(
                Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Изменить аватар", onClick = onChangeAvatar),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(scheme.primary.copy(alpha = 0.22f)),
                )
                FileAvatar(
                    title = profile?.name.orEmpty(),
                    fileId = profile?.avatarFileId,
                    fileState = viewModel::fileState,
                    size = 96.dp,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
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
                    modifier = Modifier.weight(1f, fill = false),
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

            Surface(
                onClick = onOpenAccountManager,
                modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp),
                shape = CircleShape,
                color = scheme.primary,
                contentColor = scheme.onPrimary,
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.SwitchAccount, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Аккаунты (${accounts.size})", style = MaterialTheme.typography.labelLargeEmphasized)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Содержимое менеджера аккаунтов; контейнер (стеклянная панель) задаёт MainScreen. */
@Composable
fun AccountManagerContent(
    accountManager: AccountManager,
    fileState: (Int) -> Flow<FileState>,
    onDismiss: () -> Unit,
) {
    val accounts by accountManager.accounts.collectAsState()
    val activeId by accountManager.activeAccountId.collectAsState()
    var removeTarget by remember { mutableStateOf<AccountEntry?>(null) }

    run {
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
                    style = MaterialTheme.typography.titleLargeEmphasized,
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
                modifier = Modifier.heightIn(max = 340.dp),
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
                        shape = MaterialTheme.shapes.large,
                        // Полупрозрачные плитки: блюр панели виден и под ними.
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
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