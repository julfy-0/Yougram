package app.yougram.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yougram.data.AppLock
import app.yougram.data.LockType
import app.yougram.data.MapPreview
import app.yougram.data.PrivacyDetail
import app.yougram.data.PrivacyKey
import app.yougram.data.PrivacyLevel
import app.yougram.data.SettingsRepository
import kotlin.math.roundToInt

private enum class PrivacyDialog { Password, AutoDelete, Email, Ttl, PaymentData, ImportedContacts, MapPreview }

private val AutoDeleteOptions = listOf(0, 86_400, 604_800, 2_678_400)
private val TtlOptionsDays = listOf(30, 90, 180, 365, 548, 730)

@Composable
fun PrivacyScreen(
    viewModel: SettingsDetailsViewModel,
    appLock: AppLock,
    settings: SettingsRepository,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val lock by appLock.settings.collectAsState()
    val prefs by settings.privacyPrefs.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.loadPrivacy()
        viewModel.loadSecurity()
        viewModel.loadSessions()
    }
    var dialog by remember { mutableStateOf<PrivacyDialog?>(null) }
    var editing by remember { mutableStateOf<PrivacyKey?>(null) }
    val sec = state.security
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    SettingsPageColumn(contentPadding) {
        SectionLabel("Безопасность")
        SettingGroup {
            item {
                SettingRow(
                    "Облачный пароль",
                    icon = Icons.Filled.VerifiedUser,
                    value = onOff(sec.hasPassword),
                    onClick = { dialog = PrivacyDialog.Password },
                )
            }
            item {
                SettingRow(
                    "Автоудаление сообщений",
                    icon = Icons.Filled.Timer,
                    value = sec.autoDeleteSeconds?.let(::autoDeleteLabel) ?: "…",
                    onClick = { dialog = PrivacyDialog.AutoDelete },
                )
            }
            item {
                SettingRow(
                    "Код-пароль",
                    icon = Icons.Filled.Lock,
                    value = if (lock.type != LockType.None) "Вкл." else "Выкл.",
                    onClick = { onNavigate(SettingsPage.Security) },
                )
            }
            item {
                SettingRow(
                    "Ключи доступа",
                    icon = Icons.Filled.Key,
                    value = sec.passkeys?.let { if (it == 0) "Выкл." else it.toString() } ?: "…",
                    onClick = { toast("Добавление ключей доступа пока не поддерживается") },
                )
            }
            item {
                SettingRow(
                    "Почта для входа",
                    icon = Icons.Filled.Email,
                    value = sec.loginEmail?.ifEmpty { "Не задана" } ?: "…",
                    onClick = { dialog = PrivacyDialog.Email },
                )
            }
            item {
                SettingRow(
                    "Чёрный список",
                    icon = Icons.Filled.Block,
                    value = sec.blocked?.toString() ?: "…",
                    onClick = { onNavigate(SettingsPage.Blocked) },
                )
            }
            item {
                SettingRow(
                    "Устройства",
                    icon = Icons.Filled.Laptop,
                    value = state.sessions?.size?.toString() ?: "…",
                    onClick = { onNavigate(SettingsPage.Devices) },
                )
            }
        }
        SettingsFootnote("Просмотреть список устройств, на которых Ваш аккаунт авторизован в приложении Telegram.")

        SectionLabel("Конфиденциальность")
        SettingGroup {
            PrivacyKey.entries.forEach { key ->
                item { PrivacyRow(key, state.privacyDetail[key]) { editing = key } }
            }
        }

        SectionLabel("Удалить мой аккаунт")
        SettingGroup {
            item {
                SettingRow(
                    "Если я не захожу",
                    value = sec.ttlDays?.let { monthsLabel(it) } ?: "…",
                    onClick = { dialog = PrivacyDialog.Ttl },
                )
            }
        }
        SettingsFootnote("Если Вы ни разу не заглянете в Telegram за это время, аккаунт будет удалён вместе со всеми сообщениями и контактами.")

        SectionLabel("Боты и сайты")
        SettingGroup {
            item { SettingRow("Удалить данные о платежах и доставке", onClick = { dialog = PrivacyDialog.PaymentData }) }
            item { SettingRow("Авторизованные сайты", onClick = { onNavigate(SettingsPage.Websites) }) }
        }
        SettingsFootnote("Сайты, где Вы авторизовались через Telegram.")

        SectionLabel("Контакты")
        SettingGroup {
            item { SettingRow("Удалить импортированные контакты", onClick = { dialog = PrivacyDialog.ImportedContacts }) }
            item {
                SwitchRow("Синхронизировать контакты", prefs.syncContacts, { v ->
                    settings.updatePrivacyPrefs { it.copy(syncContacts = v) }
                })
            }
            item { SwitchRow("Подсказка людей при поиске", sec.topChats ?: true, viewModel::setTopChats) }
        }
        SettingsFootnote("Показывать пользователей, которым Вы часто пишете, вверху в разделе поиска.")

        SectionLabel("Секретные чаты")
        SettingGroup {
            item { SettingRow("Предпросмотр карты", value = prefs.mapPreview.label, onClick = { dialog = PrivacyDialog.MapPreview }) }
            item {
                SwitchRow("Предпросмотр ссылок", prefs.linkPreview, { v ->
                    settings.updatePrivacyPrefs { it.copy(linkPreview = v) }
                })
            }
        }
    }

    when (dialog) {
        PrivacyDialog.Password -> if (sec.hasPassword == true) {
            EditDialog(
                title = "Облачный пароль",
                labels = listOf("Текущий пароль", "Новый пароль (пусто — отключить)", "Подсказка"),
                initial = listOf("", "", ""),
                secretIndices = setOf(0, 1),
                onConfirm = { v ->
                    viewModel.setCloudPassword(v[0], v[1], v[2])
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        } else {
            EditDialog(
                title = "Установить пароль",
                labels = listOf("Пароль", "Подсказка"),
                initial = listOf("", ""),
                secretIndices = setOf(0),
                onConfirm = { v ->
                    if (v[0].isNotEmpty()) viewModel.setCloudPassword("", v[0], v[1])
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        PrivacyDialog.AutoDelete -> ChoiceDialog(
            title = "Автоудаление сообщений",
            options = AutoDeleteOptions,
            selected = sec.autoDeleteSeconds,
            label = ::autoDeleteLabel,
            onSelect = { v ->
                viewModel.setAutoDelete(v)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        PrivacyDialog.Email -> EditDialog(
            title = "Почта для входа",
            labels = listOf("Новый адрес"),
            initial = listOf(""),
            onConfirm = { v ->
                viewModel.requestLoginEmail(v[0])
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        PrivacyDialog.Ttl -> ChoiceDialog(
            title = "Если я не захожу",
            options = TtlOptionsDays,
            selected = sec.ttlDays,
            label = ::monthsLabel,
            onSelect = { v ->
                viewModel.setAccountTtl(v)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        PrivacyDialog.PaymentData -> ConfirmDialog(
            title = "Данные о платежах",
            text = "Сохранённые данные доставки и платёжная информация будут удалены.",
            confirmLabel = "Удалить",
            onConfirm = {
                dialog = null
                viewModel.clearPaymentData { toast("Данные удалены") }
            },
            onDismiss = { dialog = null },
        )
        PrivacyDialog.ImportedContacts -> ConfirmDialog(
            title = "Импортированные контакты",
            text = "Контакты, загруженные в Telegram с устройства, будут удалены с серверов.",
            confirmLabel = "Удалить",
            onConfirm = {
                dialog = null
                viewModel.clearImportedContacts { toast("Контакты удалены") }
            },
            onDismiss = { dialog = null },
        )
        PrivacyDialog.MapPreview -> ChoiceDialog(
            title = "Предпросмотр карты",
            options = MapPreview.entries,
            selected = prefs.mapPreview,
            label = { it.label },
            onSelect = { v ->
                settings.updatePrivacyPrefs { it.copy(mapPreview = v) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    if (state.emailCodeSent) {
        EditDialog(
            title = "Код подтверждения",
            labels = listOf("Код из письма"),
            initial = listOf(""),
            onConfirm = { v -> viewModel.confirmLoginEmail(v[0]) },
            onDismiss = viewModel::cancelLoginEmail,
        )
    }

    editing?.let { key ->
        ChoiceDialog(
            title = key.title,
            options = PrivacyLevel.entries,
            selected = state.privacy[key],
            label = { it.label },
            onSelect = { level ->
                viewModel.setPrivacy(key, level)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Строка приватности без иконки: название (для Premium со звездой) и значение справа. */
@Composable
private fun PrivacyRow(key: PrivacyKey, detail: PrivacyDetail?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        if (key.premium) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Star, contentDescription = "Premium", tint = Color(0xFFB36BFF), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            detail?.valueLabel() ?: "…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** «Никто (+25)»: общее правило и число исключений (разрешить «+», запретить «−»). */
private fun PrivacyDetail.valueLabel(): String {
    val parts = buildList {
        if (allowCount > 0) add("+$allowCount")
        if (restrictCount > 0) add("-$restrictCount")
    }
    return if (parts.isEmpty()) level.label else "${level.label} (${parts.joinToString(" ")})"
}

private fun onOff(value: Boolean?): String = when (value) {
    null -> "…"
    true -> "Вкл."
    false -> "Выкл."
}

private fun autoDeleteLabel(seconds: Int): String = when (seconds) {
    0 -> "Выкл."
    86_400 -> "1 день"
    604_800 -> "1 неделя"
    2_678_400 -> "1 месяц"
    else -> "${seconds / 86_400} дн."
}

private fun monthsLabel(days: Int): String {
    val m = (days / 30.4).roundToInt().coerceAtLeast(1)
    val word = when {
        m % 10 == 1 && m % 100 != 11 -> "месяц"
        m % 10 in 2..4 && m % 100 !in 12..14 -> "месяца"
        else -> "месяцев"
    }
    return "$m $word"
}