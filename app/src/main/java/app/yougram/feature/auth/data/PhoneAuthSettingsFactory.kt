package app.yougram.feature.auth.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import dev.g000sha256.tdl.dto.PhoneNumberAuthenticationSettings

/**
 * Порт блока формирования `TL_codeSettings` из `LoginActivity.PhoneView.onNextPressed` (Telegram для Android,
 * Java) на Kotlin. В оригинале настройки уходят в `auth.sendCode`, здесь — в `setAuthenticationPhoneNumber`
 * (TDLib сам собирает из них `codeSettings`).
 *
 * Соответствие полей:
 *  - `allow_flashcall`   -> `allowFlashCall`
 *  - `allow_missed_call` -> `allowMissedCall`
 *  - `current_number`    -> `isCurrentPhoneNumber`
 *  - `unknown_number`    -> `hasUnknownPhoneNumber`
 *  - `allow_app_hash`    -> `allowSmsRetrieverApi`
 *  - `allow_firebase`    -> `firebaseAuthenticationSettings != null`
 *  - `logout_tokens`     -> `authenticationTokens`
 *
 * Что намеренно выключено (см. флаги в [Companion]) — это способы доставки, у которых есть «приёмная» половина
 * в самом приложении, не перенесённая в Yougram.
 */
class PhoneAuthSettingsFactory(
    context: Context,
    private val tokens: AuthTokenStore,
    /** Есть ли уже вошедшие аккаунты (в оригинале `UserConfig.getActivatedAccountsCount() > 0`). */
    private val hasActivatedAccounts: () -> Boolean,
) {
    private val context = context.applicationContext

    /** [phoneDigits] — номер только из цифр, без «+» (в оригинале `PhoneFormat.stripExceptNumbers`). */
    fun create(phoneDigits: String): PhoneNumberAuthenticationSettings {
        val simAvailable = isSimAvailable(context)
        val allowCall = granted(Manifest.permission.READ_PHONE_STATE)
        val allowCancelCall = granted(Manifest.permission.CALL_PHONE)
        // minSdk 29 >= Android P, поэтому проверка `SDK_INT < P` из оригинала не нужна.
        val allowReadCallLog = granted(Manifest.permission.READ_CALL_LOG)

        var allowFlashCall = FLASH_CALL_SUPPORTED && simAvailable && allowCall && allowCancelCall && allowReadCallLog
        val allowMissedCall = MISSED_CALL_SUPPORTED && simAvailable && allowCall
        var isCurrentNumber = false
        var hasUnknownNumber = false

        // Для flash-звонка серверу нужно знать, принадлежит ли номер этому телефону.
        if (allowFlashCall) {
            try {
                val numbers = userPhoneNumbers()
                if (numbers.isNotEmpty()) {
                    hasUnknownNumber = false
                    isCurrentNumber = numbers.any { PhoneNumberUtils.compare(phoneDigits, it) }
                } else {
                    hasUnknownNumber = true
                    if (hasActivatedAccounts()) allowFlashCall = false else isCurrentNumber = false
                }
            } catch (e: Exception) {
                hasUnknownNumber = true
                Log.w(TAG, "can't read device phone numbers: ${e.message}")
            }
        }

        // Firebase SMS в оригинале включается только при наличии SAFETYNET_KEY и Google-сервисов (ключ принадлежит
        // Telegram): стороннему клиенту токен Play Integrity для проекта Telegram не получить, поэтому null.
        return PhoneNumberAuthenticationSettings(
            allowFlashCall = allowFlashCall,
            allowMissedCall = allowMissedCall,
            isCurrentPhoneNumber = isCurrentNumber,
            hasUnknownPhoneNumber = hasUnknownNumber,
            allowSmsRetrieverApi = SMS_RETRIEVER_SUPPORTED,
            firebaseAuthenticationSettings = null,
            authenticationTokens = tokens.all().toTypedArray(),
        )
    }

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Порт `LoginActivity.getUserPhoneNumbers`: номера SIM-карт, если их отдаёт система. */
    @Suppress("DEPRECATION", "MissingPermission")
    private fun userPhoneNumbers(): Set<String> {
        val numbers = HashSet<String>()
        try {
            val line1 = context.getSystemService(TelephonyManager::class.java)?.line1Number
            if (!line1.isNullOrEmpty()) numbers.add(line1)
        } catch (e: Exception) {
            Log.w(TAG, "line1Number: ${e.message}")
        }
        try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
            if (manager != null) {
                var infos: List<SubscriptionInfo>? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) infos = manager.completeActiveSubscriptionInfoList
                if (infos.isNullOrEmpty()) infos = manager.accessibleSubscriptionInfoList
                if (infos.isNullOrEmpty()) infos = manager.activeSubscriptionInfoList
                infos?.forEach { info ->
                    val number = info.number
                    if (!number.isNullOrEmpty()) numbers.add(number)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "subscriptions: ${e.message}")
        }
        return numbers
    }

    companion object {
        private const val TAG = "YougramAuth"

        /**
         * Flash-звонок: код зашит в номер входящего вызова и должен перехватываться приложением
         * (в оригинале `CallReceiver` + чтение журнала звонков + сброс вызова). В Yougram этой «приёмной» части
         * нет, а вручную код из номера не вытащить — поэтому выключено. Если порт приёма появится, достаточно
         * поставить `true`: расчёт `current_number` / `unknown_number` и запрос разрешений уже перенесены.
         */
        val FLASH_CALL_SUPPORTED = false

        /**
         * Пропущенный звонок: пользователь сам вводит последние цифры номера звонившего
         * (экран кода это уже умеет, см. [CodeVia.MissedCall]). Нужно разрешение READ_PHONE_STATE — как в оригинале.
         */
        val MISSED_CALL_SUPPORTED = true

        /**
         * SMS Retriever API: SMS приходит в особом формате с хешем подписи приложения, хеш для api_id
         * известен серверу только у официальных клиентов, а перехватчика SMS в Yougram нет — выключено.
         */
        val SMS_RETRIEVER_SUPPORTED = false

        /** Порт `AndroidUtilities.isSimAvailable`. */
        fun isSimAvailable(context: Context): Boolean {
            val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
            val state = tm.simState
            val airplane = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
            return state != TelephonyManager.SIM_STATE_ABSENT &&
                state != TelephonyManager.SIM_STATE_UNKNOWN &&
                tm.phoneType != TelephonyManager.PHONE_TYPE_NONE &&
                !airplane
        }

        /**
         * Разрешения, которые стоит запросить перед отправкой кода (порт `permissionsItems` из `onNextPressed`).
         * Пусто — спрашивать нечего: SIM нет или всё уже выдано.
         */
        fun missingPermissions(context: Context): List<String> {
            if (!isSimAvailable(context)) return emptyList()
            val wanted = buildList {
                if (MISSED_CALL_SUPPORTED || FLASH_CALL_SUPPORTED) add(Manifest.permission.READ_PHONE_STATE)
                if (FLASH_CALL_SUPPORTED) {
                    add(Manifest.permission.CALL_PHONE)
                    add(Manifest.permission.READ_CALL_LOG)
                    add(Manifest.permission.READ_PHONE_NUMBERS)
                }
            }
            return wanted.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        }
    }
}
