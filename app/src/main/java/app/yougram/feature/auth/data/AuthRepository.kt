package app.yougram.feature.auth.data

import app.yougram.core.telegram.TelegramClient
import app.yougram.core.telegram.getOrThrow
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Упрощённые шаги авторизации для UI. */
sealed interface AuthStep {
    data object Loading : AuthStep
    data object MissingCredentials : AuthStep
    data object EnterPhone : AuthStep
    data class EnterCode(val phoneHint: String) : AuthStep
    data class EnterPassword(val hint: String) : AuthStep
    data object Ready : AuthStep
    data class Unsupported(val description: String) : AuthStep
}

class AuthRepository(private val telegram: TelegramClient) {

    val step: Flow<AuthStep> = combine(telegram.authState, telegram.credentialsMissing) { state, missing ->
        if (missing) AuthStep.MissingCredentials else state.toStep()
    }

    suspend fun sendPhone(phone: String) {
        telegram.client.setAuthenticationPhoneNumber(
            phoneNumber = phone.trim(),
            settings = null,
        ).getOrThrow()
    }

    suspend fun sendCode(code: String) {
        telegram.client.checkAuthenticationCode(code = code.trim()).getOrThrow()
    }

    suspend fun sendPassword(password: String) {
        telegram.client.checkAuthenticationPassword(password = password).getOrThrow()
    }

    private fun AuthorizationState?.toStep(): AuthStep = when (this) {
        null -> AuthStep.Loading
        is AuthorizationStateWaitPhoneNumber -> AuthStep.EnterPhone
        is AuthorizationStateWaitCode -> AuthStep.EnterCode(phoneHint = "")
        is AuthorizationStateWaitPassword -> AuthStep.EnterPassword(hint = passwordHint)
        is AuthorizationStateReady -> AuthStep.Ready
        else -> {
            // WaitTdlibParameters, Closing и т.п. — переходные состояния;
            // регистрация нового аккаунта, QR/e-mail вход в первой версии не поддерживаются.
            val name = this::class.simpleName.orEmpty()
            if (name.contains("WaitTdlibParameters") || name.contains("Closing") || name.contains("Closed")) {
                AuthStep.Loading
            } else {
                AuthStep.Unsupported(name)
            }
        }
    }
}
