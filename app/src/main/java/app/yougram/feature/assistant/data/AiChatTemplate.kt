package app.yougram.feature.assistant.data

import app.yougram.BuildConfig

/** Реплика диалога для сборки запроса. */
data class AiTurn(val role: Role, val text: String) {
    enum class Role { USER, ASSISTANT }
}

// Служебные токены DeepSeek (полноширинная «｜» и «▁» заданы кодами, чтобы не потерять их при копировании).
private const val DS_USER = "<｜User｜>"
private const val DS_ASSISTANT = "<｜Assistant｜>"
private const val DS_EOS = "<｜end▁of▁sentence｜>"

/** Шаблон чата локальной модели. Должен совпадать с файлом модели (см. [AiModel.template]). */
enum class AiChatTemplate(val id: String, val stopTokens: List<String>) {
    /** Gemma: system-роли нет, системная инструкция идёт в начало первого user-хода. */
    GEMMA("gemma", listOf("<end_of_turn>")),

    /** ChatML (Qwen2.5): отдельная system-роль. */
    CHATML("chatml", listOf("<|im_end|>")),

    /** DeepSeek R1 Distill: без system-роли, ответ содержит блок рассуждений <think>…</think>. */
    DEEPSEEK("deepseek", listOf(DS_EOS));

    /** Собирает промпт; в конце всегда открывает ход модели. */
    fun build(system: String, history: List<AiTurn>): String = when (this) {
        GEMMA -> buildString {
            var systemPending = system.isNotBlank()
            for (t in history) {
                when (t.role) {
                    AiTurn.Role.USER -> {
                        append("<start_of_turn>user\n")
                        if (systemPending) {
                            append(system.trim()).append("\n\n")
                            systemPending = false
                        }
                        append(t.text.trim()).append("<end_of_turn>\n")
                    }
                    AiTurn.Role.ASSISTANT ->
                        append("<start_of_turn>model\n").append(t.text.trim()).append("<end_of_turn>\n")
                }
            }
            append("<start_of_turn>model\n")
        }

        CHATML -> buildString {
            if (system.isNotBlank()) append("<|im_start|>system\n").append(system.trim()).append("<|im_end|>\n")
            for (t in history) {
                val role = if (t.role == AiTurn.Role.USER) "user" else "assistant"
                append("<|im_start|>").append(role).append('\n').append(t.text.trim()).append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }

        DEEPSEEK -> buildString {
            var systemPending = system.isNotBlank()
            for (t in history) {
                when (t.role) {
                    AiTurn.Role.USER -> {
                        append(DS_USER)
                        if (systemPending) {
                            append(system.trim()).append("\n\n")
                            systemPending = false
                        }
                        append(t.text.trim())
                    }
                    AiTurn.Role.ASSISTANT -> append(DS_ASSISTANT).append(t.text.trim()).append(DS_EOS)
                }
            }
            append(DS_ASSISTANT)
        }
    }

    /** Убирает стоп-токены (и блок рассуждений у DeepSeek) из ответа модели. */
    fun clean(output: String): String {
        var s = output
        for (stop in stopTokens) s = s.substringBefore(stop)
        if (this == DEEPSEEK) s = stripThink(s)
        return s.trim()
    }

    /** Пока модель «думает» (есть <think>, но нет </think>) — пусто, в чате будет «…». */
    private fun stripThink(text: String): String {
        val close = text.lastIndexOf("</think>")
        return when {
            close >= 0 -> text.substring(close + "</think>".length)
            text.contains("<think>") -> ""
            else -> text
        }
    }

    companion object {
        /** Шаблон из local.properties (по умолчанию gemma). */
        fun fromBuildConfig(): AiChatTemplate = fromId(BuildConfig.AI_MODEL_TEMPLATE)

        fun fromId(id: String): AiChatTemplate =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: GEMMA
    }
}
