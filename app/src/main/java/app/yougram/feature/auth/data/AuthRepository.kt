package app.yougram.feature.auth.data

import app.yougram.core.telegram.TelegramClient
import app.yougram.core.telegram.getOrThrow
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Упрощённые шаги авторизации для UI. */
sealed interface AuthStep {
    data object Loading : AuthStep
    data object MissingCredentials : AuthStep
    data object EnterPhone : AuthStep
    /** Вход по QR: [link] (tg://login?token=…) TDLib сам обновляет, пока QR не отсканирован. */
    data class QrLogin(val link: String) : AuthStep
    /**
     * [via] — куда Telegram отправил код (по имени типа из codeInfo), [timeoutSec] — через сколько секунд
     * можно запросить код повторно другим способом, [canResend] — есть ли следующий способ доставки,
     * [phone] — номер, на который ушёл код (чтобы заметить опечатку).
     */
    data class EnterCode(
        val phoneHint: String,
        val via: CodeVia = CodeVia.Unknown,
        val timeoutSec: Int = 0,
        val canResend: Boolean = false,
    ) : AuthStep
    data class EnterPassword(val hint: String) : AuthStep
    data object Ready : AuthStep
    data class Unsupported(val description: String) : AuthStep
}

enum class CodeVia { Telegram, Sms, Call, MissedCall, Firebase, Fragment, Unknown }

class AuthRepository(private val telegram: TelegramClient) {

    private val lastPhone = MutableStateFlow("")

    val step: Flow<AuthStep> = combine(telegram.authState, telegram.credentialsMissing, lastPhone) { state, missing, phone ->
        if (missing) AuthStep.MissingCredentials else state.toStep(phone)
    }

    /** Работает и из шага «код» (смена номера), и из шага QR (переход на вход по номеру). */
    suspend fun sendPhone(phone: String) {
        val p = phone.trim()
        telegram.client.setAuthenticationPhoneNumber(
            phoneNumber = p,
            settings = null,
        ).getOrThrow()
        lastPhone.value = p
    }

    /** Запрос QR-входа: дальше TDLib сам присылает AuthorizationStateWaitOtherDeviceConfirmation со ссылкой. */
    suspend fun requestQr() {
        telegram.client.requestQrCodeAuthentication(otherUserIds = longArrayOf()).getOrThrow()
    }

    /** Повторный запрос кода следующим способом (SMS / звонок), когда в Telegram код не пришёл. */
    suspend fun resendCode() {
        telegram.client.resendAuthenticationCode().getOrThrow()
    }

    suspend fun sendCode(code: String) {
        telegram.client.checkAuthenticationCode(code = code.trim()).getOrThrow()
    }

    suspend fun sendPassword(password: String) {
        telegram.client.checkAuthenticationPassword(password = password).getOrThrow()
    }

    private fun Any?.toVia(): CodeVia {
        val n = this?.let { it::class.simpleName }.orEmpty()
        return when {
            n.contains("TelegramMessage") -> CodeVia.Telegram
            n.contains("Firebase") -> CodeVia.Firebase
            n.contains("Fragment") -> CodeVia.Fragment
            n.contains("MissedCall") -> CodeVia.MissedCall
            n.contains("FlashCall") -> CodeVia.Call
            n.contains("Sms") -> CodeVia.Sms
            n.contains("Call") -> CodeVia.Call
            else -> CodeVia.Unknown
        }
    }

    private fun AuthorizationState?.toStep(phone: String): AuthStep = when (this) {
        null -> AuthStep.Loading
        is AuthorizationStateWaitPhoneNumber -> AuthStep.EnterPhone
        is AuthorizationStateWaitOtherDeviceConfirmation -> AuthStep.QrLogin(link)
        is AuthorizationStateWaitCode -> {
            // Диагностика «код не приходит»: каким способом Telegram решил его доставить.
            android.util.Log.i(
                "YougramAuth",
                "WaitCode: type=${codeInfo.type::class.simpleName} next=${codeInfo.nextType?.let { it::class.simpleName }} timeout=${codeInfo.timeout}",
            )
            AuthStep.EnterCode(
                phoneHint = phone,
                via = codeInfo.type.toVia(),
                timeoutSec = codeInfo.timeout,
                canResend = codeInfo.nextType != null,
            )
        }
        is AuthorizationStateWaitPassword -> AuthStep.EnterPassword(hint = passwordHint)
        is AuthorizationStateReady -> AuthStep.Ready
        else -> {
            // WaitTdlibParameters, Closing и т.п. — переходные состояния;
            // регистрация нового аккаунта и e-mail вход пока не поддерживаются.
            val name = this::class.simpleName.orEmpty()
            if (name.contains("WaitTdlibParameters") || name.contains("Closing") || name.contains("Closed")) {
                AuthStep.Loading
            } else {
                AuthStep.Unsupported(name)
            }
        }
    }
}