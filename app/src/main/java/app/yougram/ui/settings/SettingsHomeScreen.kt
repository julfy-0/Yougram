package app.yougram.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.BuildConfig
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
    Folders("Папки с чатами"),
    Devices("Устройства"),
    PowerSaving("Энергосбережение"),
    Language("Язык"),
    About("О приложении"),
    Blocked("Чёрный список"),
    Websites("Авторизованные сайты");

    /** Страница, на которую ведёт «Назад». */
    val parent: SettingsPage
        get() = when (this) {
            Blocked, Websites -> Privacy
            else -> Home
        }
}

@Composable
fun SettingsHomeScreen(
    viewModel: SettingsHomeViewModel,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
    onOpenChat: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val soon = { Toast.makeText(context, "Скоро", Toast.LENGTH_SHORT).show() }
    val openUrl = { url: String ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }
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
        ProfileHeader(state, viewModel)

        SettingGroup {
            item { SettingRow("Настройки Yougram", subtitle = "Тема, цвета, панели", icon = Icons.Filled.Tune, onClick = { onNavigate(SettingsPage.Appearance) }) }
        }

        SettingGroup {
            item { SettingRow("Аккаунт", subtitle = "Номер телефона, имя пользователя", icon = Icons.Filled.AccountCircle, onClick = { onNavigate(SettingsPage.Account) }) }
            item { SettingRow("Настройки чатов", subtitle = "Размер текста, анимации", icon = Icons.Filled.ChatBubble, onClick = { onNavigate(SettingsPage.ChatSettings) }) }
            item { SettingRow("Конфиденциальность", subtitle = "Кто видит ваши данные", icon = Icons.Filled.VpnKey, onClick = { onNavigate(SettingsPage.Privacy) }) }
            item { SettingRow("Безопасность", subtitle = "Пин-код, графический ключ, отпечаток", icon = Icons.Filled.Lock, onClick = { onNavigate(SettingsPage.Security) }) }
            item { SettingRow("Уведомления", subtitle = "Звуки, сигналы, бейджи", icon = Icons.Filled.Notifications, onClick = { onNavigate(SettingsPage.Notifications) }) }
            item { SettingRow("Данные и память", subtitle = "Кэш, автозагрузка медиа", icon = Icons.Filled.PieChart, onClick = { onNavigate(SettingsPage.DataStorage) }) }
            item { SettingRow("Папки с чатами", subtitle = "Сортировка чатов по папкам", icon = Icons.Filled.Folder, onClick = { onNavigate(SettingsPage.Folders) }) }
            item { SettingRow("Устройства", subtitle = "Активные сеансы", icon = Icons.Filled.Laptop, value = state.devices?.toString(), onClick = { onNavigate(SettingsPage.Devices) }) }
            item { SettingRow("Энергосбережение", subtitle = "Экономия заряда", icon = Icons.Filled.BatterySaver, onClick = { onNavigate(SettingsPage.PowerSaving) }) }
            item { SettingRow("Язык", icon = Icons.Filled.Language, value = language, onClick = { onNavigate(SettingsPage.Language) }) }
        }

        SettingGroup {
            item { SettingRow("Telegram Premium", icon = Icons.Filled.Star, onClick = soon) }
            item { SettingRow("Звёзды Telegram", icon = Icons.Filled.Star, onClick = soon) }
            item { SettingRow("Telegram для бизнеса", icon = Icons.Filled.Storefront, onClick = soon) }
            item { SettingRow("Отправить подарок", icon = Icons.Filled.CardGiftcard, onClick = soon) }
        }

        SectionLabel("Помощь")
        SettingGroup {
            item { SettingRow("Задать вопрос", icon = Icons.Filled.ChatBubble, onClick = { viewModel.openSupport(onOpenChat) }) }
            item { SettingRow("Вопросы о Telegram", icon = Icons.Filled.QuestionMark, onClick = { openUrl("https://telegram.org/faq") }) }
            item { SettingRow("Возможности Telegram", icon = Icons.Filled.Lightbulb, onClick = { openUrl("https://telegram.org/tour") }) }
            item { SettingRow("Политика конфиденциальности", icon = Icons.Filled.VerifiedUser, onClick = { openUrl("https://telegram.org/privacy") }) }
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
}

@Composable
private fun ProfileHeader(state: SettingsHomeState, viewModel: SettingsHomeViewModel) {
    val profile = state.profile
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileAvatar(
            title = profile?.name.orEmpty(),
            fileId = profile?.avatarFileId,
            fileState = viewModel::fileState,
            size = 96.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            profile?.name.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        val sub = listOfNotNull(
            profile?.phone?.takeIf { it.isNotEmpty() },
            profile?.username?.let { "@$it" },
        ).joinToString(" • ")
        if (sub.isNotEmpty()) {
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class SettingsHomeState(
    val profile: ProfileItem? = null,
    val devices: Int? = null,
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