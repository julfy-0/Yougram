package app.yougram.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.data.AuthRepository
import app.yougram.data.AuthStep
import app.yougram.data.DataPrefs
import app.yougram.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val step: StateFlow<AuthStep> = repository.step
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthStep.Loading)

    /** Текущие настройки прокси (то же хранилище, что и в «Данные и память»). */
    val proxy: StateFlow<DataPrefs> = settings.dataPrefs

    data class Ui(val busy: Boolean = false, val error: String? = null)

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun submitPhone(phone: String) = run { repository.sendPhone(phone) }
    fun submitCode(code: String) = run { repository.sendCode(code) }
    fun submitPassword(password: String) = run { repository.sendPassword(password) }

    /** Сохраняет прокси; в TDLib он применяется автоматически (см. AppContainer.start). */
    fun saveProxy(type: String, server: String, port: String, user: String, pass: String) {
        settings.updateDataPrefs {
            it.copy(proxyType = type, proxyServer = server, proxyPort = port, proxyUser = user, proxyPass = pass)
        }
    }

    /** Ошибка исчезает, как только пользователь начинает править поле. */
    fun clearError() {
        _ui.update { if (it.error != null) it.copy(error = null) else it }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { Ui(busy = true) }
            try {
                block()
                _ui.update { Ui() }
            } catch (e: Exception) {
                _ui.update { Ui(error = humanize(e.message)) }
            }
        }
    }

    private fun humanize(raw: String?): String {
        val m = raw.orEmpty()
        return when {
            m.contains("PHONE_NUMBER_INVALID", true) -> "Неверный номер телефона"
            m.contains("PHONE_NUMBER_BANNED", true) -> "Этот номер заблокирован в Telegram"
            m.contains("PHONE_CODE_INVALID", true) -> "Неверный код"
            m.contains("PHONE_CODE_EXPIRED", true) -> "Код устарел, запросите новый"
            m.contains("PASSWORD_HASH_INVALID", true) -> "Неверный пароль"
            m.contains("FLOOD", true) || m.contains("Too Many Requests", true) -> "Слишком много попыток, подождите немного"
            m.isBlank() -> "Неизвестная ошибка"
            else -> m
        }
    }

    companion object {
        fun factory(repository: AuthRepository, settings: SettingsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(repository, settings) }
        }
    }
}