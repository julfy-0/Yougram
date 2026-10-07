package app.yougram.feature.security.ui

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import app.yougram.feature.security.data.AppLock

/**
 * Показывает системный запрос отпечатка/лица.
 * Обычный режим ([weak] = false): привязка к ключу Keystore (BIOMETRIC_STRONG — отпечаток и «сильное» лицо).
 * Режим [weak]: BIOMETRIC_WEAK без ключа — так работает разблокировка лицом на большинстве телефонов.
 * Возвращает false, если запрос показать нельзя. [onCancel] вызывается при отмене, ошибке и неудачной проверке.
 */
fun showBiometricPrompt(
    context: Context,
    appLock: AppLock,
    title: String,
    onSuccess: () -> Unit,
    onCancel: () -> Unit = {},
    weak: Boolean = appLock.settings.value.biometricWeak,
): Boolean {
    val cipher = if (weak) null else (appLock.biometricCipher() ?: return false)
    val executor = context.mainExecutor
    val prompt = BiometricPrompt.Builder(context)
        .setTitle(title)
        .setAllowedAuthenticators(
            if (weak) BiometricManager.Authenticators.BIOMETRIC_WEAK else BiometricManager.Authenticators.BIOMETRIC_STRONG,
        )
        .setConfirmationRequired(false)
        .setNegativeButton("Использовать код", executor) { _, _ -> onCancel() }
        .build()
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            if (weak) {
                onSuccess()
                return
            }
            // Проверяем, что ключ Keystore действительно разблокирован биометрией.
            val usable = result.cryptoObject?.cipher?.let {
                try {
                    it.doFinal(ByteArray(1))
                    true
                } catch (_: Exception) {
                    false
                }
            } ?: false
            if (usable) onSuccess() else onCancel()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            onCancel()
        }
    }
    if (cipher != null) {
        prompt.authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), executor, callback)
    } else {
        prompt.authenticate(CancellationSignal(), executor, callback)
    }
    return true
}
