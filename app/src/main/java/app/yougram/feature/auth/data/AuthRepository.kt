package app.yougram.feature.auth.data

import android.telephony.PhoneNumberUtils
import android.util.Log
import app.yougram.core.telegram.TelegramClient
import app.yougram.core.telegram.getOrThrow
import app.yougram.feature.account.data.AccountManager
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
     * [nextVia] — какой именно (SMS / звонок), [phoneHint] — номер, на который ушёл код (чтобы заметить опечатку).
     */
    data class EnterCode(
        val phoneHint: String,
        val via: CodeVia = CodeVia.Unknown,
        val timeoutSec: Int = 0,
        val canResend: Boolean = false,
        val nextVia: CodeVia? = null,
    ) : AuthStep
    data class EnterPassword(val hint: String) : AuthStep
    data object Ready : AuthStep
    data class Unsupported(val description: String) : AuthStep
}

enum class CodeVia { Telegram, Sms, Call, MissedCall, Firebase, Fragment, Unknown }

/** Номер уже добавлен как аккаунт (в оригинале диалог «AccountAlreadyLoggedIn» с переключением). */
class AccountAlreadyAddedException(val accountId: String) : Exception(MESSAGE) {
    companion object {
        const val MESSAGE = "ACCOUNT_ALREADY_ADDED"
    }
}

class AuthRepository(
    private val telegram: TelegramClient,
    private val codeSettings: PhoneAuthSettingsFactory,
    private val accounts: AccountManager,
) {

    private val lastPhone = MutableStateFlow("")

    /** Fallback с Firebase уже пробовали для текущего номера (защита от цикла повторных запросов). */
    private var firebaseFallbackUsed = false

    val step: Flow<AuthStep> = combine(telegram.authState, telegram.credentialsMissing, lastPhone) { state, missing, phone ->
        if (missing) AuthStep.MissingCredentials else state.toStep(phone)
    }

    /** Работает и из шага «код» (смена номера), и из шага QR (переход на вход по номеру). */
    suspend fun sendPhone(phone: String) {
        val p = phone.trim()
        val digits = p.filter { it.isDigit() }

        // Как в LoginActivity: номер, который уже залогинен в другом аккаунте, второй раз не добавляем.
        val duplicate = accounts.accounts.value.firstOrNull {
            !it.isBlank && it.phone.isNotEmpty() && PhoneNumberUtils.compare(digits, it.phone)
        }
        if (duplicate != null) throw AccountAlreadyAddedException(duplicate.id)

        firebaseFallbackUsed = false
        telegram.client.setAuthenticationPhoneNumber(
            phoneNumber = p,
            settings = codeSettings.create(digits),
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

    /**
     * Порт `resendCodeFromSafetyNet`: сервер выбрал доставку кода через Firebase, а получить его можно только
     * с токеном Play Integrity для проекта Telegram, который у стороннего клиента недоступен. Официальный клиент
     * в таком случае сразу просит сервер выслать код другим способом — делаем то же, один раз на номер.
     * Ошибки глушим: если TDLib ещё не разрешает повтор (не истёк таймаут), пользователь увидит подсказку
     * на экране кода и сможет запросить SMS вручную.
     */
    suspend fun fallbackFromFirebase() {
        if (firebaseFallbackUsed) return
        firebaseFallbackUsed = true
        try {
            telegram.client.resendAuthenticationCode().getOrThrow()
        } catch (e: Exception) {
            Log.w("YougramAuth", "firebase fallback failed: ${e.message}")
        }
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
            val via = codeInfo.type.toVia()
            // Как в LoginActivity.fillNextCodeParams: для кода «в приложении» таймера нет, а для остальных способов
            // сервер иногда присылает 0 — тогда ждём стандартные 60 с, иначе «отправить ещё раз» нажмут сразу.
            val timeout = if (via == CodeVia.Telegram) codeInfo.timeout else codeInfo.timeout.takeIf { it > 0 } ?: DEFAULT_RESEND_TIMEOUT_SEC
            AuthStep.EnterCode(
                phoneHint = phone,
                via = via,
                timeoutSec = timeout,
                canResend = codeInfo.nextType != null,
                nextVia = codeInfo.nextType?.toVia(),
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

    private companion object {
        const val DEFAULT_RESEND_TIMEOUT_SEC = 60
    }
}
