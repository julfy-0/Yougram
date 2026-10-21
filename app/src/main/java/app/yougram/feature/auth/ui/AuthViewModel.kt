package app.yougram.feature.auth.ui

import app.yougram.core.i18n.tr
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.core.settings.ProxyEntry
import app.yougram.core.settings.SettingsRepository
import app.yougram.feature.auth.data.AuthRepository
import app.yougram.feature.auth.data.AuthStep
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

    /** Сохранённые прокси и активный (то же хранилище, что и в «Данные и память»). */
    val proxies: StateFlow<List<ProxyEntry>> = settings.proxies
    val activeProxyId: StateFlow<String?> = settings.activeProxyId

    data class Ui(val busy: Boolean = false, val error: String? = null)

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun submitPhone(phone: String) = run { repository.sendPhone(phone) }
    fun submitCode(code: String) = run { repository.sendCode(code) }
    fun submitPassword(password: String) = run { repository.sendPassword(password) }

    /** Активный прокси в TDLib применяется автоматически (см. AppContainer.start). */
    fun saveProxy(entry: ProxyEntry) = settings.saveProxy(entry)
    fun deleteProxy(id: String) = settings.deleteProxy(id)
    fun selectProxy(id: String?) = settings.selectProxy(id)

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
            m.contains("PHONE_NUMBER_INVALID", true) -> tr("Неверный номер телефона")
            m.contains("PHONE_NUMBER_BANNED", true) -> tr("Этот номер заблокирован в Telegram")
            m.contains("PHONE_CODE_INVALID", true) -> tr("Неверный код")
            m.contains("PHONE_CODE_EXPIRED", true) -> tr("Код устарел, запросите новый")
            m.contains("PASSWORD_HASH_INVALID", true) -> tr("Неверный пароль")
            m.contains("FLOOD", true) || m.contains("Too Many Requests", true) -> tr("Слишком много попыток, подождите немного")
            m.isBlank() -> tr("Неизвестная ошибка")
            else -> m
        }
    }

    companion object {
        fun factory(repository: AuthRepository, settings: SettingsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(repository, settings) }
        }
    }
}