package app.yougram.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.yougram.plugin.NativePluginManager
import app.yougram.feature.stories.StoriesRepository

/** Простой ручной DI-контейнер: один TDLib-клиент на всё приложение. */
class AppContainer(private val context: Context) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context.applicationContext)
    val plugins = NativePluginManager(context.applicationContext)
    val plates = PlateRepository(context.applicationContext)
    val appLock = AppLock(context.applicationContext)
    val accountManager = AccountManager(context.applicationContext)
    val telegram = TelegramClient(context.applicationContext, appScope, accountManager.activeAccountId.value)
    val authRepository = AuthRepository(telegram)
    val spy = SpyStore(context.applicationContext)
    val updater = AppUpdater(context.applicationContext, appScope)
    val stories = StoriesRepository(telegram)
    val chatRepository = ChatRepository(telegram, appScope, settings, spy, context.applicationContext, plugins)
    val accountRepository = AccountRepository(telegram, accountManager)
    val callAudio = CallAudio(context.applicationContext)
    val notificationCenter = NotificationCenter(context.applicationContext, telegram, appScope, chatRepository, settings)
    val callManager = CallManager(telegram, appScope, chatRepository, context.applicationContext, callAudio)
        .also { it.engine = NTgCallsEngine() }

    /** Чат, который нужно открыть по тапу на уведомление; навигация сбрасывает значение после открытия. */
    val pendingOpenChat = MutableStateFlow<Long?>(null)

    fun start() {
        CallLog.init(context.applicationContext)
        plugins.loadAll()
        telegram.start()
        // Прокси из настроек (в том числе заданный на экране входа) передаём в TDLib при каждом изменении.
        appScope.launch {
            settings.dataPrefs
                .map { listOf(it.proxyType, it.proxyServer, it.proxyPort, it.proxyUser, it.proxyPass) }
                .distinctUntilChanged()
                .collect { runCatching { telegram.applyProxy(settings.dataPrefs.value) } }
        }
        chatRepository.start()
        notificationCenter.start()
        callManager.start()
        // После входа записываем имя, телефон и аватарку в список аккаунтов, чтобы переключатель их показывал.
        appScope.launch {
            authRepository.step.first { it == AuthStep.Ready }
            runCatching { accountRepository.loadAccount() }
            // Соединение поднимаем здесь, а не только из Activity: так оно работает и после перезапуска процесса системой.
            if (settings.notifications.value.backgroundConnection) ConnectionService.start(context.applicationContext)
        }
    }
}