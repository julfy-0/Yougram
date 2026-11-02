package app.yougram.feature.miniapp.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Запрос на открытие мини-приложения (Telegram Web App). */
data class MiniAppRequest(
    val chatId: Long,
    val botUserId: Long,
    /** Пусто — имя бота подтянется само. */
    val botName: String = "",
    /** Готовый адрес (inline-кнопка, кнопка меню бота, кнопка клавиатуры). */
    val url: String = "",
    /** Текст кнопки клавиатуры, открывшей приложение: нужен для web_app_data_send. */
    val buttonText: String? = null,
    /** t.me/бот/приложение: короткое имя; null вместе с [fromLink] — главное приложение бота. */
    val shortName: String? = null,
    val startParameter: String = "",
    /** true — адрес получаем через getWebAppLinkUrl / getMainWebApp, а не берём из [url]. */
    val fromLink: Boolean = false,
)

/** Открывает мини-приложение поверх текущего экрана (хост лежит в корне навигации). */
val LocalOpenMiniApp = staticCompositionLocalOf<(MiniAppRequest) -> Unit> { {} }
