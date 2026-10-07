package app.yougram.feature.settings.general

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.yougram.core.ui.component.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.yougram.feature.account.data.AccountManager
import app.yougram.feature.account.data.BIO_MAX_LENGTH
import app.yougram.feature.account.data.BirthdateInfo
import app.yougram.feature.account.data.PersonalChatOption
import app.yougram.feature.account.data.PrivacyKey
import app.yougram.feature.account.data.PrivacyLevel
import app.yougram.feature.badge.data.YougramBadge
import app.yougram.feature.settings.SettingsDetailsViewModel
import app.yougram.feature.settings.component.ChoiceDialog
import app.yougram.feature.settings.component.ConfirmDialog
import app.yougram.feature.settings.component.EditDialog
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsPageColumn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class AccountDialog { Username, Phone, Birthdate, Channels, AddAccount, Logout }

@Composable
fun AccountScreen(viewModel: SettingsDetailsViewModel, accountManager: AccountManager, contentPadding: PaddingValues) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) {
        viewModel.loadAccount()
        viewModel.loadPrivacy(listOf(PrivacyKey.Birthdate, PrivacyKey.Bio))
    }
    var dialog by remember { mutableStateOf<AccountDialog?>(null) }
    var privacyEdit by remember { mutableStateOf<PrivacyKey?>(null) }
    val account = state.account

    // Локальные копии полей: правятся прямо в карточках, сохраняются при уходе из поля и с экрана.
    var first by remember(account?.firstName) { mutableStateOf(account?.firstName.orEmpty()) }
    var last by remember(account?.lastName) { mutableStateOf(account?.lastName.orEmpty()) }
    // Метка и баннер Yougram лежат в конце bio невидимыми символами: в поле их не показываем и при сохранении возвращаем.
    val bioTail = YougramBadge.tail(account?.bio.orEmpty())
    var bio by remember(account?.bio) { mutableStateOf(YougramBadge.strip(account?.bio.orEmpty())) }

    val flush by rememberUpdatedState {
        val a = account
        if (a != null) {
            if (first.isNotBlank() && (first.trim() != a.firstName || last.trim() != a.lastName)) {
                viewModel.saveName(first, last)
            }
            if (bio.trim() != YougramBadge.strip(a.bio)) viewModel.saveBio(bio)
        }
    }
    DisposableEffect(Unit) { onDispose { flush() } }

    val birthdateFootnote = when (state.privacy[PrivacyKey.Birthdate]) {
        PrivacyLevel.Everybody -> "Ваш день рождения могут видеть все. "
        PrivacyLevel.Contacts -> "Ваш день рождения могут видеть только контакты. "
        PrivacyLevel.Nobody -> "Ваш день рождения никто не видит. "
        null -> "Ваш день рождения могут видеть выбранные вами пользователи. "
    }

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item {
                SettingRow(
                    account?.phone?.ifEmpty { "—" } ?: "…",
                    subtitle = "Нажмите, чтобы изменить номер телефона",
                    icon = Icons.Filled.Phone,
                    onClick = { if (account != null) dialog = AccountDialog.Phone },
                )
            }
            item {
                SettingRow(
                    account?.username?.let { "@$it" } ?: "Не задано",
                    subtitle = "Имя пользователя",
                    icon = Icons.Filled.AlternateEmail,
                    onClick = { if (account != null) dialog = AccountDialog.Username },
                )
            }
            item {
                SettingRow(
                    account?.birthdate?.format() ?: "Не указан",
                    subtitle = "День рождения",
                    icon = Icons.Filled.Cake,
                    onClick = { if (account != null) dialog = AccountDialog.Birthdate },
                )
            }
        }
        LinkFootnote(
            text = birthdateFootnote,
            link = "Изменить ›",
            suffix = "",
            onClick = { privacyEdit = PrivacyKey.Birthdate },
        )

        SectionLabel("Ваше имя")
        SettingGroup {
            item {
                InlineField(
                    value = first,
                    onValueChange = { first = it },
                    placeholder = "Имя",
                    onFocusLost = { flush() },
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    singleLine = true,
                )
            }
            item {
                InlineField(
                    value = last,
                    onValueChange = { last = it },
                    placeholder = "Фамилия",
                    onFocusLost = { flush() },
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    singleLine = true,
                )
            }
        }

        SectionLabel("О себе")
        SettingGroup {
            item {
                InlineField(
                    value = bio,
                    onValueChange = { if (it.length <= BIO_MAX_LENGTH - bioTail.length) bio = it },
                    placeholder = "Расскажите о себе",
                    onFocusLost = { flush() },
                    singleLine = false,
                    counter = BIO_MAX_LENGTH - bioTail.length - bio.length,
                )
            }
        }
        LinkFootnote(
            text = "Вы можете добавить несколько строк о себе. В ",
            link = "настройках",
            suffix = " можно выбрать, кому они будут видны.",
            onClick = { privacyEdit = PrivacyKey.Bio },
        )

        SettingGroup {
            item {
                if (account?.personalChatTitle != null) {
                    SettingRow(
                        "Личный канал",
                        subtitle = account.personalChatTitle,
                        icon = Icons.Filled.Campaign,
                        onClick = { openChannels(viewModel) { dialog = AccountDialog.Channels } },
                    )
                } else {
                    ActionRow(
                        "Добавить личный канал",
                        Icons.Filled.Campaign,
                        MaterialTheme.colorScheme.primary,
                    ) { if (account != null) openChannels(viewModel) { dialog = AccountDialog.Channels } }
                }
            }
        }

        SettingGroup {
            item {
                ActionRow("Добавить аккаунт", Icons.Filled.PersonAdd, MaterialTheme.colorScheme.primary) {
                    dialog = AccountDialog.AddAccount
                }
            }
            item {
                ActionRow("Выход", Icons.AutoMirrored.Filled.Logout, MaterialTheme.colorScheme.error) {
                    dialog = AccountDialog.Logout
                }
            }
        }
    }

    when (dialog) {
        AccountDialog.Username -> EditDialog(
            title = "Имя пользователя",
            labels = listOf("Имя пользователя"),
            initial = listOf(account?.username.orEmpty()),
            onConfirm = { values ->
                viewModel.saveUsername(values[0])
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AccountDialog.Phone -> EditDialog(
            title = "Новый номер телефона",
            labels = listOf("Номер телефона"),
            initial = listOf("+"),
            onConfirm = { values ->
                viewModel.requestPhoneChange(values[0])
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AccountDialog.Birthdate -> BirthdateDialog(
            initial = account?.birthdate,
            onSave = { value ->
                viewModel.saveBirthdate(value)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        AccountDialog.Channels -> {
            val chats = state.personalChats
            if (chats != null) {
                if (chats.isEmpty()) {
                    ConfirmDialog(
                        title = "Личный канал",
                        text = "Нет каналов, которые можно выбрать. Создайте канал в Telegram и вернитесь сюда.",
                        confirmLabel = "OK",
                        onConfirm = { dialog = null },
                        onDismiss = { dialog = null },
                    )
                } else {
                    val none = PersonalChatOption(0L, "Не показывать")
                    val options = if (account?.personalChatId != 0L) listOf(none) + chats else chats
                    ChoiceDialog(
                        title = "Личный канал",
                        options = options,
                        selected = options.firstOrNull { it.id == account?.personalChatId },
                        label = { it.title },
                        onSelect = { option ->
                            viewModel.setPersonalChat(option.id)
                            dialog = null
                        },
                        onDismiss = { dialog = null },
                    )
                }
            }
        }
        AccountDialog.AddAccount -> ConfirmDialog(
            title = "Добавить аккаунт",
            text = "Приложение перезапустится и откроет экран входа. Текущий аккаунт останется в списке, переключаться между аккаунтами можно в настройках.",
            confirmLabel = "Добавить",
            onConfirm = {
                dialog = null
                accountManager.addAndRestart()
            },
            onDismiss = { dialog = null },
        )
        AccountDialog.Logout -> ConfirmDialog(
            title = "Выход",
            text = "Вы выйдете из аккаунта на этом устройстве. Локальные данные будут удалены.",
            confirmLabel = "Выйти",
            onConfirm = {
                dialog = null
                viewModel.logOut { accountManager.finishLogoutAndRestart() }
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    if (state.phoneCodeSent) {
        EditDialog(
            title = "Код подтверждения",
            labels = listOf("Код из Telegram"),
            initial = listOf(""),
            onConfirm = { values -> viewModel.confirmPhoneChange(values[0]) },
            onDismiss = viewModel::cancelPhoneChange,
        )
    }

    privacyEdit?.let { key ->
        ChoiceDialog(
            title = key.title,
            options = PrivacyLevel.entries,
            selected = state.privacy[key],
            label = { it.label },
            onSelect = { level ->
                viewModel.setPrivacy(key, level)
                privacyEdit = null
            },
            onDismiss = { privacyEdit = null },
        )
    }
}

private fun openChannels(viewModel: SettingsDetailsViewModel, show: () -> Unit) {
    viewModel.clearPersonalChats()
    viewModel.loadPersonalChats()
    show()
}

private fun BirthdateInfo.format(): String {
    val hasYear = year > 0
    val date = LocalDate.of(if (hasYear) year else 2000, month, day)
    return date.format(
        DateTimeFormatter.ofPattern(if (hasYear) "d MMMM yyyy" else "d MMMM", Locale.forLanguageTag("ru")),
    )
}

/** Поле прямо в карточке группы: без рамки, необязательный счётчик оставшихся символов справа. */
@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onFocusLost: () -> Unit,
    singleLine: Boolean,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    counter: Int? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        trailingIcon = counter?.let {
            { Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = keyboardActions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) onFocusLost() },
    )
}

/** Строка-действие без плитки: иконка и текст одного цвета. */
@Composable
private fun ActionRow(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = color)
    }
}

/** Пояснение со ссылкой посередине: [text] + [link] + [suffix]. */
@Composable
private fun LinkFootnote(text: String, link: String, suffix: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Text(
        buildAnnotatedString {
            append(text)
            withLink(
                LinkAnnotation.Clickable(
                    tag = "link",
                    styles = TextLinkStyles(SpanStyle(color = primary, fontWeight = FontWeight.Medium)),
                    linkInteractionListener = { onClick() },
                ),
            ) { append(link) }
            append(suffix)
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdateDialog(
    initial: BirthdateInfo?,
    onSave: (BirthdateInfo?) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialMillis = remember {
        LocalDate.of(
            initial?.year?.takeIf { it > 0 } ?: 2000,
            initial?.month ?: 1,
            initial?.day ?: 1,
        ).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        yearRange = 1900..LocalDate.now().year,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = picker.selectedDateMillis
                if (millis == null) {
                    onDismiss()
                } else {
                    val d = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onSave(BirthdateInfo(day = d.dayOfMonth, month = d.monthValue, year = d.year))
                }
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onSave(null) }) { Text("Удалить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    ) { DatePicker(state = picker) }
}