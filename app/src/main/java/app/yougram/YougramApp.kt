package app.yougram

import android.app.Application
import app.yougram.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class YougramApp : Application() {

    private lateinit var mutableContainer: MutableStateFlow<AppContainer>

    /** Текущий контейнер активного аккаунта; меняется на лету при смене аккаунта, без перезапуска процесса и Activity. */
    lateinit var containerState: StateFlow<AppContainer>
        private set

    val container: AppContainer get() = mutableContainer.value

    private val reloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val reloadMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        // Нативный tgcalls читает контекст из этого поля.
        org.telegram.messenger.ApplicationLoader.applicationContext = applicationContext
        app.yougram.core.i18n.Localization.init(this)
        // Служебный процесс перезапуска не должен поднимать TDLib: база аккаунта занята основным процессом.
        if (getProcessName().endsWith(":restart")) return
        app.yougram.feature.splash.ui.HalloweenEvent.init(this)
        val first = AppContainer(this)
        mutableContainer = MutableStateFlow(first)
        containerState = mutableContainer.asStateFlow()
        first.accountManager.onReload = ::reloadContainer
        first.start()
    }

    /**
     * Пересобирает всё, что привязано к аккаунту (TDLib-клиент, репозитории, уведомления, звонки),
     * под аккаунт, выбранный в [app.yougram.feature.account.data.AccountManager]. Процесс и Activity остаются.
     */
    fun reloadContainer() {
        reloadScope.launch {
            reloadMutex.withLock {
                val old = mutableContainer.value
                val sameDir = old.telegram.accountDirName == old.accountManager.activeAccountId.value
                // Та же папка TDLib (выход из единственного аккаунта) — старый клиент должен закрыться раньше нового.
                if (sameDir) old.shutdown()
                val fresh = AppContainer(this@YougramApp, old)
                fresh.start()
                mutableContainer.value = fresh
                if (!sameDir) old.shutdown()
            }
        }
    }
}