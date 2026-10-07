package app.yougram.core.di

import android.content.Context
import app.yougram.core.settings.PlateRepository
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.telegram.TelegramClient
import app.yougram.feature.account.data.AccountManager
import app.yougram.feature.account.data.AccountRepository
import app.yougram.feature.auth.data.AuthRepository
import app.yougram.feature.auth.data.AuthStep
import app.yougram.feature.calls.data.CallAudio
import app.yougram.feature.calls.data.CallLog
import app.yougram.feature.calls.data.CallManager
import app.yougram.feature.calls.data.NTgCallsEngine
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.chat.data.SpyStore
import app.yougram.feature.notifications.ConnectionService
import app.yougram.feature.notifications.NotificationCenter
import app.yougram.feature.security.data.AppLock
import app.yougram.feature.stories.data.StoriesRepository
import app.yougram.feature.update.data.AppUpdater
import app.yougram.plugin.NativePluginManager
import app.yougram.plugin.PluginApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Простой ручной DI-контейнер: один TDLib-клиент на всё приложение. */
class AppContainer(private val context: Context) {

    // Без обработчика любое необработанное исключение в корутинах приложения (в т.ч. при разборе потока
    // обновлений TDLib сразу после входа) роняет весь процесс. Логируем и продолжаем работу.
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
                kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.e("YougramApp", "uncaught coroutine error", e) },
    )

    val settings = SettingsRepository(context.applicationContext)
    val plugins = NativePluginManager(context.applicationContext)
    val plates = PlateRepository(context.applicationContext)
    val appLock = AppLock(context.applicationContext)
    val accountManager = AccountManager(context.applicationContext)
    val telegram = TelegramClient(context.applicationContext, appScope, accountManager.activeAccountId.value)
    val authRepository = AuthRepository(telegram)
    val spy = SpyStore(context.applicationContext)
    val updater = AppUpdater(context.applicationContext, appScope)
    val stories = StoriesRepository(telegram, appScope)
    val chatRepository = ChatRepository(telegram, appScope, settings, spy, context.applicationContext, plugins)

    /** API плагинов: команды, пункты меню, хуки. UI читает pluginApi.commands / pluginApi.actions. */
    val pluginApi = PluginApi(context.applicationContext, telegram, chatRepository, plugins)
        .also { plugins.api = it }

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