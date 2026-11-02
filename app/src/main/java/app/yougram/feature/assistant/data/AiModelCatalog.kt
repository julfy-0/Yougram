package app.yougram.feature.assistant.data

import app.yougram.BuildConfig

/**
 * Три нейросети на выбор (MediaPipe .task, litert-community). Ссылки и размеры берутся из
 * local.properties (AI_URL_* / AI_SIZE_*). Файл модели и [template] обязаны совпадать.
 */
enum class AiModel(
    val id: String,
    val title: String,
    val approxSizeMb: Int,
    val template: AiChatTemplate,
    /** Контекст модели в токенах: должен совпадать с ekv в имени файла (ekv1280 → 1280). */
    val maxTokens: Int,
    val minRamGb: Int,
    val note: String,
    val url: String,
    val expectedSize: Long,
) {
    QWEN(
        "qwen", "Qwen 2.5 0.5B", 500, AiChatTemplate.CHATML, 1280, 0,
        "Быстрая и лёгкая. Русский слабее, зато работает почти на любом телефоне.",
        BuildConfig.AI_URL_QWEN, BuildConfig.AI_SIZE_QWEN,
    ),
    GEMMA(
        "gemma", "Gemma 3 1B", 530, AiChatTemplate.GEMMA, 1280, 0,
        "Лучший русский и следование инструкциям среди лёгких моделей.",
        BuildConfig.AI_URL_GEMMA, BuildConfig.AI_SIZE_GEMMA,
    ),
    DEEPSEEK(
        "deepseek", "DeepSeek R1 Distill 1.5B", 1800, AiChatTemplate.DEEPSEEK, 4096, 6,
        "Умеет рассуждать, но отвечает дольше и греет телефон. Нужно от 6 ГБ ОЗУ.",
        BuildConfig.AI_URL_DEEPSEEK, BuildConfig.AI_SIZE_DEEPSEEK,
    );

    val configured: Boolean get() = url.startsWith("https://")

    companion object {
        /** Первая модель, для которой задана ссылка (иначе Qwen). */
        val DEFAULT: AiModel get() = entries.firstOrNull { it.configured } ?: QWEN

        fun fromId(id: String?): AiModel = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
