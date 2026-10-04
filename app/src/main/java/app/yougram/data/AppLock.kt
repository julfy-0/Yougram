package app.yougram.data

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class LockType { None, Pin, Pattern }

data class LockSettings(
    val type: LockType = LockType.None,
    val biometric: Boolean = false,
    /** Через сколько секунд в фоне блокировать приложение; 0 — сразу. */
    val autoLockSeconds: Int = 0,
    /** Скрывать содержимое в «Недавних» и запрещать скриншоты (FLAG_SECURE). */
    val secureScreen: Boolean = false,
)

sealed interface VerifyResult {
    data object Success : VerifyResult
    data object Wrong : VerifyResult
    data class LockedOut(val remainingMs: Long) : VerifyResult
}

/**
 * Блокировка приложения: пин-код или графический ключ (хранится только хеш PBKDF2 с солью),
 * отпечаток через ключ Android Keystore, защита от подбора и автоблокировка при уходе в фон.
 */
class AppLock(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("yougram_lock", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<LockSettings> = _settings.asStateFlow()

    // При запуске процесса приложение заблокировано, если блокировка включена.
    private val _locked = MutableStateFlow(_settings.value.type != LockType.None)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** Момент (мс, системные часы), до которого вход заблокирован после неверных попыток. */
    private val _lockoutUntil = MutableStateFlow(prefs.getLong(KEY_LOCKOUT_UNTIL, 0L))
    val lockoutUntil: StateFlow<Long> = _lockoutUntil.asStateFlow()

    private var backgroundAt = 0L

    private fun readSettings(): LockSettings {
        val hasSecret = prefs.getString(KEY_HASH, null) != null && prefs.getString(KEY_SALT, null) != null
        val type = if (hasSecret) {
            LockType.entries.getOrElse(prefs.getInt(KEY_TYPE, 0)) { LockType.None }
        } else {
            LockType.None
        }
        return LockSettings(
            type = type,
            biometric = type != LockType.None && prefs.getBoolean(KEY_BIOMETRIC, false),
            autoLockSeconds = prefs.getInt(KEY_AUTO_LOCK, 0),
            secureScreen = prefs.getBoolean(KEY_SECURE, false),
        )
    }

    // --- Жизненный цикл: вызывается из MainActivity ---

    fun onBackground() {
        if (_settings.value.type == LockType.None) return
        backgroundAt = SystemClock.elapsedRealtime()
        if (_settings.value.autoLockSeconds == 0) _locked.value = true
    }

    fun onForeground() {
        val s = _settings.value
        if (s.type == LockType.None || _locked.value) return
        val away = SystemClock.elapsedRealtime() - backgroundAt
        if (backgroundAt != 0L && away >= s.autoLockSeconds * 1000L) _locked.value = true
    }

    // --- Код ---

    private fun hash(type: LockType, secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec("${type.name}:$secret".toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Устанавливает или меняет пин-код / графический ключ. */
    suspend fun setSecret(type: LockType, secret: String) {
        withContext(Dispatchers.Default) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val hash = hash(type, secret, salt)
            prefs.edit()
                .putInt(KEY_TYPE, type.ordinal)
                .putString(KEY_SALT, Base64.getEncoder().encodeToString(salt))
                .putString(KEY_HASH, Base64.getEncoder().encodeToString(hash))
                .putInt(KEY_FAILED, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .commit()
        }
        _lockoutUntil.value = 0L
        _settings.update { it.copy(type = type) }
    }

    /** Проверяет код; при успехе снимает блокировку. */
    suspend fun verify(secret: String): VerifyResult = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val until = _lockoutUntil.value
        if (until > now) return@withContext VerifyResult.LockedOut(until - now)

        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.getDecoder().decode(it) }
        val stored = prefs.getString(KEY_HASH, null)?.let { Base64.getDecoder().decode(it) }
        if (salt == null || stored == null) return@withContext VerifyResult.Wrong

        val ok = MessageDigest.isEqual(hash(_settings.value.type, secret, salt), stored)
        if (ok) {
            clearFailures()
            _locked.value = false
            VerifyResult.Success
        } else {
            registerFailure(now)
        }
    }

    private fun clearFailures() {
        prefs.edit().putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0L).commit()
        _lockoutUntil.value = 0L
    }

    /** Каждая пятая неверная попытка запускает блокировку, время которой растёт (до часа). */
    private fun registerFailure(now: Long): VerifyResult {
        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAILED, failed)
        if (failed % 5 != 0) {
            editor.commit()
            return VerifyResult.Wrong
        }
        val step = (failed / 5 - 1).coerceAtMost(7)
        val delayMs = minOf(30_000L * (1L shl step), 3_600_000L)
        editor.putLong(KEY_LOCKOUT_UNTIL, now + delayMs).commit()
        _lockoutUntil.value = now + delayMs
        return VerifyResult.LockedOut(delayMs)
    }

    /** Полностью отключает блокировку и удаляет ключи. */
    fun disable() {
        prefs.edit()
            .remove(KEY_TYPE).remove(KEY_SALT).remove(KEY_HASH)
            .remove(KEY_BIOMETRIC).putInt(KEY_FAILED, 0).putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()
        deleteBiometricKey()
        _lockoutUntil.value = 0L
        _locked.value = false
        _settings.update { it.copy(type = LockType.None, biometric = false) }
    }

    // --- Параметры ---

    fun setAutoLockSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK, seconds).apply()
        _settings.update { it.copy(autoLockSeconds = seconds) }
    }

    fun setSecureScreen(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SECURE, enabled).apply()
        _settings.update { it.copy(secureScreen = enabled) }
    }

    fun setBiometric(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).commit()
        if (!enabled) deleteBiometricKey()
        _settings.update { it.copy(biometric = enabled) }
    }

    // --- Отпечаток ---

    fun biometricAvailable(): Boolean {
        val manager = appContext.getSystemService(BiometricManager::class.java) ?: return false
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Создаёт ключ Keystore, который требует отпечаток при каждом использовании. */
    fun prepareBiometricKey(): Boolean = try {
        deleteBiometricKey()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        generator.generateKey()
        true
    } catch (_: Exception) {
        false
    }

    /** Шифр, привязанный к отпечатку. Если набор отпечатков изменился, ключ аннулируется и вход по отпечатку выключается. */
    fun biometricCipher(): Cipher? {
        return try {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val key = store.getKey(KEY_ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            setBiometric(false)
            null
        } catch (_: Exception) {
            null
        }
    }

    fun deleteBiometricKey() {
        try {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {
        }
    }

    /** Вызывается после успешной проверки отпечатка. */
    fun unlockByBiometric() {
        clearFailures()
        _locked.value = false
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "yougram_biometric_key"
        const val ITERATIONS = 120_000
        const val KEY_TYPE = "type"
        const val KEY_SALT = "salt"
        const val KEY_HASH = "hash"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_AUTO_LOCK = "auto_lock_seconds"
        const val KEY_SECURE = "secure_screen_v2"
        const val KEY_FAILED = "failed_attempts"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}