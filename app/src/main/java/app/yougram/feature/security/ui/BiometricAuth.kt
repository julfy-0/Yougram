package app.yougram.feature.security.ui

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import app.yougram.feature.security.data.AppLock

/**
 * Показывает системный запрос отпечатка с привязкой к ключу Keystore.
 * Возвращает false, если запрос показать нельзя (нет ключа или отпечатков).
 * [onCancel] вызывается при отмене, ошибке и неудачной проверке ключа.
 */
fun showBiometricPrompt(
    context: Context,
    appLock: AppLock,
    title: String,
    onSuccess: () -> Unit,
    onCancel: () -> Unit = {},
): Boolean {
    val cipher = appLock.biometricCipher() ?: return false
    val executor = context.mainExecutor
    val prompt = BiometricPrompt.Builder(context)
        .setTitle(title)
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .setNegativeButton("Использовать код", executor) { _, _ -> onCancel() }
        .build()
    prompt.authenticate(
        BiometricPrompt.CryptoObject(cipher),
        CancellationSignal(),
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                // Проверяем, что ключ Keystore действительно разблокирован отпечатком.
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
        },
    )
    return true
}