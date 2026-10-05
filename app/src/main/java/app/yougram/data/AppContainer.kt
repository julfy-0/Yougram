package app.yougram.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Простой ручной DI-контейнер: один TDLib-клиент на всё приложение. */
class AppContainer(private val context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context.applicationContext)
    val plates = PlateRepository(context.applicationContext)
    val appLock = AppLock(context.applicationContext)
    val accountManager = AccountManager(context.applicationContext)
    val telegram = TelegramClient(context.applicationContext, appScope, accountManager.activeAccountId.value)
    val authRepository = AuthRepository(telegram)
    val spy = SpyStore(context.applicationContext)
    val updater = AppUpdater(context.applicationContext, appScope)
    val chatRepository = ChatRepository(telegram, appScope, settings, spy, context.applicationContext)
    val accountRepository = AccountRepository(telegram, accountManager)
    val callAudio = CallAudio(context.applicationContext)
    val notificationCenter = NotificationCenter(context.applicationContext, telegram, appScope, chatRepository, settings)
    val callManager = CallManager(telegram, appScope, chatRepository, context.applicationContext, callAudio)
        .also { it.engine = NTgCallsEngine() }

    /** Чат, который нужно открыть по тапу на уведомление; навигация сбрасывает значение после открытия. */
    val pendingOpenChat = MutableStateFlow<Long?>(null)

    fun start() {
        telegram.start()
        chatRepository.start()
        notificationCenter.start()
        callManager.start()
        // После входа записываем имя, телефон и аватарку в список аккаунтов, чтобы переключатель их показывал.
        appScope.launch {
            authRepository.step.first { it == AuthStep.Ready }
            runCatching { accountRepository.loadAccount() }
        }
    }
}