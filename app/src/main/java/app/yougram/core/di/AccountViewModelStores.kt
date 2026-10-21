package app.yougram.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Хранилище ViewModel, привязанное к поколению [AppContainer]. Живёт в Activity (переживает поворот),
 * а при смене аккаунта старое хранилище очищается, чтобы ViewModel не держали репозитории закрытого клиента.
 */
class AccountViewModelStores : ViewModel() {
    private var generation = -1
    private var owner: ViewModelStoreOwner? = null

    fun ownerFor(generation: Int): ViewModelStoreOwner {
        val current = owner
        if (current != null && this.generation == generation) return current
        current?.viewModelStore?.clear()
        this.generation = generation
        return object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }.also { owner = it }
    }

    override fun onCleared() {
        owner?.viewModelStore?.clear()
        owner = null
    }
}
