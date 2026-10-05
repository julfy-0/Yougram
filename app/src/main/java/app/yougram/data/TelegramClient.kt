package app.yougram.data

import android.content.Context
import android.os.Build
import app.yougram.BuildConfig
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationState
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Единственная точка контакта с TDLib. Всё остальное приложение работает через
 * репозитории и не знает про generated-классы библиотеки — если API TDLib
 * поменяется в новой версии, править придётся только этот слой.
 */
class TelegramClient(
    private val context: Context,
    private val scope: CoroutineScope,
    val accountDirName: String = "tdlib",
) {
    val client: TdlClient = TdlClient.create()

    private val _authState = MutableStateFlow<AuthorizationState?>(null)
    val authState: StateFlow<AuthorizationState?> = _authState.asStateFlow()

    /** true, если в local.properties не заданы TG_API_ID / TG_API_HASH. */
    private val _credentialsMissing = MutableStateFlow(false)
    val credentialsMissing: StateFlow<Boolean> = _credentialsMissing.asStateFlow()

    fun start() {
        // ВАЖНО: подписка на обновления должна начаться до первого запроса.
        scope.launch {
            client.authorizationStateUpdates.collect { update ->
                _authState.value = update.authorizationState
                if (update.authorizationState is AuthorizationStateWaitTdlibParameters) {
                    sendTdlibParameters()
                }
            }
        }
        scope.launch {
            // Запрашиваем текущее состояние, чтобы запустить цепочку авторизации.
            val result = client.getAuthorizationState()
            if (result is TdlResult.Success) {
                _authState.value = result.result
                if (result.result is AuthorizationStateWaitTdlibParameters) {
                    sendTdlibParameters()
                }
            }
        }
    }

    private suspend fun sendTdlibParameters() {
        if (BuildConfig.TG_API_ID == 0 || BuildConfig.TG_API_HASH.isEmpty()) {
            // Не роняем приложение: экран входа покажет, что нужно сделать.
            _credentialsMissing.value = true
            return
        }
        val dbDir = File(context.filesDir, accountDirName).apply { mkdirs() }
        val filesDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "${accountDirName}_files")
            .apply { mkdirs() }
        client.setTdlibParameters(
            useTestDc = false,
            databaseDirectory = dbDir.absolutePath,
            filesDirectory = filesDir.absolutePath,
            databaseEncryptionKey = byteArrayOf(),
            useFileDatabase = true,
            useChatInfoDatabase = true,
            useMessageDatabase = true,
            useSecretChats = false,
            apiId = BuildConfig.TG_API_ID,
            apiHash = BuildConfig.TG_API_HASH,
            systemLanguageCode = Locale.getDefault().language.ifEmpty { "en" },
            deviceModel = Build.MODEL,
            systemVersion = Build.VERSION.RELEASE,
            applicationVersion = BuildConfig.VERSION_NAME,
        )
    }
}

/** Разворачивает TdlResult: успех -> значение, ошибка -> исключение с текстом от Telegram. */
fun <T> TdlResult<T>.getOrThrow(): T = when (this) {
    is TdlResult.Success -> result
    is TdlResult.Failure -> throw TelegramException(code, message)
}

class TelegramException(val code: Int, override val message: String) : Exception(message)
