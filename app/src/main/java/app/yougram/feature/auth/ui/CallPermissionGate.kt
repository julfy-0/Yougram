package app.yougram.feature.auth.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.yougram.core.i18n.Text
import app.yougram.feature.auth.data.PhoneAuthSettingsFactory

/**
 * Порт начала `LoginActivity.PhoneView.onNextPressed`: перед отправкой номера один раз объясняем, зачем нужны
 * разрешения на звонки, и запрашиваем их. Что бы пользователь ни ответил, вход продолжается ([onReady]):
 * без разрешений просто не будет доступна доставка кода звонком, остаются Telegram и SMS.
 *
 * Использование: `val gate = rememberCallPermissionGate(onReady = viewModel::submitPhone)`,
 * поле номера отправляет через `gate.request(phone)`, а где-нибудь в дереве вызывается `gate.Dialog()`.
 */
@Stable
class CallPermissionGate internal constructor(
    private val context: Context,
    private val onReady: (String) -> Unit,
) {
    internal var launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>? = null

    /** Номер и список разрешений, ожидающие подтверждения в диалоге-пояснении. */
    private var rationale by mutableStateOf<Pair<String, List<String>>?>(null)

    /** Номер, который отправим, когда система вернёт результат запроса разрешений. */
    private var awaiting: String? = null

    fun request(phone: String) {
        val missing = PhoneAuthSettingsFactory.missingPermissions(context)
        if (missing.isEmpty()) {
            onReady(phone)
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val firstTime = prefs.getBoolean(KEY_FIRST_LOGIN, true)
        val activity = context.findActivity()
        val shouldExplain = activity != null && missing.any { activity.shouldShowRequestPermissionRationale(it) }
        if (firstTime || shouldExplain) {
            prefs.edit().putBoolean(KEY_FIRST_LOGIN, false).apply()
            rationale = phone to missing
        } else {
            launch(phone, missing)
        }
    }

    private fun launch(phone: String, permissions: List<String>) {
        val l = launcher
        if (l == null) {
            onReady(phone)
            return
        }
        awaiting = phone
        try {
            l.launch(permissions.toTypedArray())
        } catch (e: Exception) {
            awaiting = null
            onReady(phone)
        }
    }

    internal fun onResult() {
        val phone = awaiting ?: return
        awaiting = null
        onReady(phone)
    }

    @Composable
    fun Dialog() {
        val pending = rationale ?: return
        // Как в оригинале: закрытие диалога любым способом ведёт к системному запросу разрешений.
        val proceed = {
            rationale = null
            launch(pending.first, pending.second)
        }
        AlertDialog(
            onDismissRequest = proceed,
            title = { Text("Код подтверждения") },
            text = {
                Text(
                    "Telegram может отправить код пропущенным звонком. Для этого разрешите Yougram доступ к состоянию " +
                        "телефона. Если откажетесь — код придёт в приложение Telegram или SMS.",
                )
            },
            confirmButton = { TextButton(onClick = proceed) { Text("Далее") } },
        )
    }

    private companion object {
        const val PREFS = "yougram_auth_ui"
        const val KEY_FIRST_LOGIN = "firstlogin"
    }
}

@Composable
fun rememberCallPermissionGate(onReady: (String) -> Unit): CallPermissionGate {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onReady)
    val gate = remember(context) { CallPermissionGate(context) { phone -> latest(phone) } }
    gate.launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { gate.onResult() }
    return gate
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
