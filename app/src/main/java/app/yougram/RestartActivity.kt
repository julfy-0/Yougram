package app.yougram

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentCompat

/**
 * Перезапуск приложения без вылета. Живёт в отдельном процессе ":restart", поэтому основной процесс
 * можно завершить сразу, а эта активность запускает MainActivity уже в новом процессе
 * (с TDLib-клиентом выбранного аккаунта).
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val next = IntentCompat.getParcelableExtra(intent, EXTRA_INTENT, Intent::class.java)
        if (next != null) startActivity(next)
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val EXTRA_INTENT = "app.yougram.restart_intent"
    }
}