package app.yougram.feature.chat.data

import app.yougram.core.settings.FilterPrefs
import java.util.concurrent.ConcurrentHashMap

/** Решает, нужно ли скрыть сообщение в чате по настройкам фильтров. */
object MessageFilters {

    private val regexCache = ConcurrentHashMap<String, Regex?>()

    fun isHidden(message: MessageItem, prefs: FilterPrefs, blockedUserIds: Set<Long>): Boolean {
        if (!prefs.enabled || message.isOutgoing) return false
        val sender = message.senderUserId
        if (sender != null) {
            if (prefs.shadowBanned.any { it.userId == sender }) return true
            if (prefs.hideBlocked && sender in blockedUserIds) return true
        }
        if (prefs.sharedInChats && prefs.patterns.isNotEmpty()) {
            val text = message.text
            if (text.isNotEmpty() && prefs.patterns.any { matches(text, it) }) return true
        }
        return false
    }

    /** Шаблон вида /выражение/ — регулярное выражение, иначе — подстрока без учёта регистра. */
    private fun matches(text: String, pattern: String): Boolean {
        if (pattern.length > 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
            val regex = regexCache.getOrPut(pattern) {
                runCatching { Regex(pattern.substring(1, pattern.length - 1), RegexOption.IGNORE_CASE) }.getOrNull()
            } ?: return false
            return regex.containsMatchIn(text)
        }
        return text.contains(pattern, ignoreCase = true)
    }

    /** true, если шаблон — корректное регулярное выражение или обычная строка. */
    fun isValid(pattern: String): Boolean {
        if (pattern.isBlank()) return false
        if (pattern.length > 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
            return runCatching { Regex(pattern.substring(1, pattern.length - 1)) }.isSuccess
        }
        return true
    }
}
