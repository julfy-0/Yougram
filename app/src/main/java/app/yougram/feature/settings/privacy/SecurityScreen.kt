package app.yougram.feature.settings.privacy

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yougram.feature.security.data.AppLock
import app.yougram.feature.security.data.LockType
import app.yougram.feature.security.data.VerifyResult
import app.yougram.feature.security.ui.PatternPad
import app.yougram.feature.security.ui.PinEntry
import app.yougram.feature.security.data.BiometricLevel
import app.yougram.feature.security.ui.showBiometricPrompt
import app.yougram.feature.settings.component.ChoiceDialog
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.component.SwitchRow
import kotlinx.coroutines.launch

private sealed interface Then {
    data object Disable : Then
    data class Change(val type: LockType) : Then
}

private sealed interface Mode {
    data class Setup(val type: LockType) : Mode
    data class Verify(val then: Then) : Mode
}

private val TimeoutOptions = listOf(0, 60, 300, 3600)

private fun timeoutLabel(seconds: Int): String = when (seconds) {
    0 -> "Сразу"
    60 -> "Через 1 минуту"
    300 -> "Через 5 минут"
    3600 -> "Через 1 час"
    else -> "Через $seconds с"
}

@Composable
fun SecurityScreen(appLock: AppLock, contentPadding: PaddingValues) {
    val settings by appLock.settings.collectAsState()
    val context = LocalContext.current
    var mode by remember { mutableStateOf<Mode?>(null) }
    var showTimeout by remember { mutableStateOf(false) }
    val enabled = settings.type != LockType.None

    BackHandler(enabled = mode != null) { mode = null }

    val currentMode = mode
    if (currentMode != null) {
        SecurityFlow(
            appLock = appLock,
            mode = currentMode,
            contentPadding = contentPadding,
            onDone = { mode = null },
            onNext = { mode = it },
        )
        return
    }

    fun start(type: LockType): Mode =
        if (enabled) Mode.Verify(Then.Change(type)) else Mode.Setup(type)

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun onBiometricChange(on: Boolean) {
        if (!on) {
            appLock.setBiometric(false)
            return
        }
        if (!appLock.biometricAvailable()) {
            toast("Отпечаток или лицо не настроены в системе")
            return
        }
        if (appLock.biometricLevel() == BiometricLevel.Weak) {
            // Лицо без «сильной» биометрии: Keystore-ключ недоступен, проверяем системным запросом.
            val ok = showBiometricPrompt(
                context = context,
                appLock = appLock,
                title = "Подтвердите лицо или отпечаток",
                onSuccess = { appLock.setBiometric(true, weak = true) },
                weak = true,
            )
            if (!ok) toast("Не удалось включить вход по лицу")
            return
        }
        val shown = appLock.prepareBiometricKey() && showBiometricPrompt(
            context = context,
            appLock = appLock,
            title = "Подтвердите отпечаток или лицо",
            onSuccess = { appLock.setBiometric(true) },
            onCancel = { appLock.deleteBiometricKey() },
            weak = false,
        )
        if (!shown) {
            appLock.deleteBiometricKey()
            toast("Не удалось включить отпечаток")
        }
    }

    SettingsPageColumn(contentPadding) {
        SectionLabel("Способ блокировки")
        SettingGroup {
            item {
                SettingRow(
                    "Пин-код",
                    subtitle = "Цифры, от 4 знаков",
                    icon = Icons.Filled.Pin,
                    value = if (settings.type == LockType.Pin) "Включён" else null,
                    onClick = { mode = start(LockType.Pin) },
                )
            }
            item {
                SettingRow(
                    "Графический ключ",
                    subtitle = "Рисунок из точек, от 4 точек",
                    icon = Icons.Filled.Gesture,
                    value = if (settings.type == LockType.Pattern) "Включён" else null,
                    onClick = { mode = start(LockType.Pattern) },
                )
            }
            if (enabled) {
                item {
                    SwitchRow(
                        "Отпечаток или лицо",
                        settings.biometric,
                        ::onBiometricChange,
                        subtitle = "Разблокировка без ввода кода",
                        icon = Icons.Filled.Fingerprint,
                    )
                }
            }
        }

        if (enabled) {
            SectionLabel("Блокировка при выходе")
            SettingGroup {
                item {
                    SettingRow(
                        "Автоблокировка",
                        subtitle = "Когда приложение уходит в фон",
                        icon = Icons.Filled.Timer,
                        value = timeoutLabel(settings.autoLockSeconds),
                        onClick = { showTimeout = true },
                    )
                }
                item {
                    SwitchRow(
                        "Скрывать содержимое",
                        settings.secureScreen,
                        appLock::setSecureScreen,
                        subtitle = "Пустое превью в «Недавних», запрет скриншотов",
                        icon = Icons.Filled.VisibilityOff,
                    )
                }
            }
            SettingGroup {
                item {
                    SettingRow(
                        "Отключить блокировку",
                        icon = Icons.Filled.LockOpen,
                        onClick = { mode = Mode.Verify(Then.Disable) },
                    )
                }
            }
        }
        SettingsFootnote(
            "При значении «Сразу» приложение блокируется, как только вы сворачиваете его или выключаете экран. " +
                    "После 5 неверных попыток вход блокируется на время, которое растёт с каждой серией.",
        )
    }

    if (showTimeout) {
        ChoiceDialog(
            title = "Автоблокировка",
            options = TimeoutOptions,
            selected = settings.autoLockSeconds,
            label = ::timeoutLabel,
            onSelect = {
                appLock.setAutoLockSeconds(it)
                showTimeout = false
            },
            onDismiss = { showTimeout = false },
        )
    }
}

/** Ввод нового кода (дважды) или проверка текущего перед изменением и отключением. */
@Composable
private fun SecurityFlow(
    appLock: AppLock,
    mode: Mode,
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    onNext: (Mode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val type = when (mode) {
        is Mode.Setup -> mode.type
        is Mode.Verify -> appLock.settings.value.type
    }
    var input by remember(mode) { mutableStateOf("") }
    var first by remember(mode) { mutableStateOf<String?>(null) }
    var message by remember(mode) { mutableStateOf<String?>(null) }
    var isError by remember(mode) { mutableStateOf(false) }

    fun sizeOf(secret: String) = if (type == LockType.Pattern) secret.split(",").size else secret.length

    fun handle(secret: String) {
        when (mode) {
            is Mode.Setup -> when {
                sizeOf(secret) < 4 -> {
                    message = if (type == LockType.Pattern) "Соедините минимум 4 точки" else "Минимум 4 цифры"
                    isError = true
                    input = ""
                }
                first == null -> {
                    first = secret
                    input = ""
                    message = null
                    isError = false
                }
                first == secret -> scope.launch {
                    appLock.setSecret(type, secret)
                    onDone()
                }
                else -> {
                    first = null
                    input = ""
                    message = "Не совпадает. Начните заново"
                    isError = true
                }
            }
            is Mode.Verify -> scope.launch {
                when (val result = appLock.verify(secret)) {
                    VerifyResult.Success -> when (val then = mode.then) {
                        Then.Disable -> {
                            appLock.disable()
                            onDone()
                        }
                        is Then.Change -> onNext(Mode.Setup(then.type))
                    }
                    VerifyResult.Wrong -> {
                        message = "Неверный код"
                        isError = true
                        input = ""
                    }
                    is VerifyResult.LockedOut -> {
                        message = "Слишком много попыток. Повторите через ${result.remainingMs / 1000 + 1} с"
                        isError = true
                        input = ""
                    }
                }
            }
        }
    }

    val title = when (mode) {
        is Mode.Setup -> when {
            first != null -> "Повторите для подтверждения"
            type == LockType.Pattern -> "Нарисуйте новый графический ключ"
            else -> "Придумайте пин-код"
        }
        is Mode.Verify -> if (type == LockType.Pattern) "Нарисуйте текущий ключ" else "Введите текущий пин-код"
    }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message ?: " ",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (type == LockType.Pattern) {
            PatternPad(
                onComplete = ::handle,
                modifier = Modifier.size(280.dp),
                isError = isError,
                onStart = {
                    isError = false
                    message = null
                },
            )
        } else {
            PinEntry(
                value = input,
                onValueChange = {
                    input = it
                    isError = false
                    message = null
                },
                onSubmit = { handle(input) },
                isError = isError,
            )
        }
        TextButton(onClick = onDone) { Text("Отмена") }
    }
}