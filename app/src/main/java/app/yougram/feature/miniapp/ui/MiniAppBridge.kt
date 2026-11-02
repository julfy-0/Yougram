package app.yougram.feature.miniapp.ui

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * Мост Telegram Web Apps: страница (telegram-web-app.js) вызывает TelegramWebviewProxy.postEvent(тип, json),
 * ответы клиента уходят в страницу через Telegram.WebView.receiveEvent(тип, данные).
 */
internal class MiniAppBridge(private val onEvent: (String, JSONObject) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postEvent(eventType: String?, eventData: String?) {
        val type = eventType ?: return
        val data = runCatching { JSONObject(if (eventData.isNullOrBlank()) "{}" else eventData) }
            .getOrDefault(JSONObject())
        main.post { onEvent(type, data) }
    }
}

/** Отправляет событие в страницу мини-приложения. */
internal fun WebView.sendEvent(type: String, data: JSONObject = JSONObject()) {
    evaluateJavascript(
        "window.Telegram&&window.Telegram.WebView&&window.Telegram.WebView.receiveEvent(${JSONObject.quote(type)},$data)",
        null,
    )
}
