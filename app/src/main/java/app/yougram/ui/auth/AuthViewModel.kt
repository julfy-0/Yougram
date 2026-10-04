package app.yougram.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.yougram.data.AuthRepository
import app.yougram.data.AuthStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    val step: StateFlow<AuthStep> = repository.step
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthStep.Loading)

    data class Ui(val busy: Boolean = false, val error: String? = null)

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun submitPhone(phone: String) = run { repository.sendPhone(phone) }
    fun submitCode(code: String) = run { repository.sendCode(code) }
    fun submitPassword(password: String) = run { repository.sendPassword(password) }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { Ui(busy = true) }
            try {
                block()
                _ui.update { Ui() }
            } catch (e: Exception) {
                _ui.update { Ui(error = e.message ?: "Неизвестная ошибка") }
            }
        }
    }

    companion object {
        fun factory(repository: AuthRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(repository) }
        }
    }
}
