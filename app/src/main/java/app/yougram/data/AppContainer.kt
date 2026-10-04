package app.yougram.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Простой ручной DI-контейнер: один TDLib-клиент на всё приложение. */
class AppContainer(private val context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context.applicationContext)
    val appLock = AppLock(context.applicationContext)
    val telegram = TelegramClient(context.applicationContext, appScope)
    val authRepository = AuthRepository(telegram)
    val chatRepository = ChatRepository(telegram, appScope)
    val accountRepository = AccountRepository(telegram)

    fun start() {
        telegram.start()
        chatRepository.start()
    }
}