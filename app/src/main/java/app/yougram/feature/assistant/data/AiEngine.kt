package app.yougram.feature.assistant.data

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Обёртка над MediaPipe LlmInference. Модель тяжёлая: создаём лениво, один экземпляр,
 * закрываем через [close], когда ассистент не нужен.
 * Один запрос за раз: LlmInference не поддерживает параллельную генерацию.
 */
class AiEngine(
    context: Context,
    private val modelFile: File,
    val template: AiChatTemplate = AiChatTemplate.fromBuildConfig(),
    private val maxTokens: Int = 1280, // = ekv1280 в имени файла модели
) {
    private val appContext = context.applicationContext
    @Volatile private var llm: LlmInference? = null

    private suspend fun ensureLoaded(): LlmInference = withContext(Dispatchers.Default) {
        llm ?: synchronized(this@AiEngine) {
            llm ?: LlmInference.createFromOptions(
                appContext,
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(maxTokens)
                    .build(),
            ).also { llm = it }
        }
    }

    /** Полный ответ одним куском (шаблон применяется здесь, стоп-токены вырезаются). */
    suspend fun ask(system: String, history: List<AiTurn>): String {
        val engine = ensureLoaded()
        val prompt = template.build(system, history)
        return withContext(Dispatchers.Default) { template.clean(engine.generateResponse(prompt)) }
    }

    /** Потоковый ответ: каждое значение — накопленный текст на данный момент. */
    fun stream(system: String, history: List<AiTurn>): Flow<String> = callbackFlow {
        val engine = ensureLoaded()
        val prompt = template.build(system, history)
        val acc = StringBuilder()
        engine.generateResponseAsync(prompt) { part, done ->
            acc.append(part)
            trySend(template.clean(acc.toString()))
            if (done) close()
        }
        awaitClose { }
    }

    fun close() {
        synchronized(this) {
            llm?.close()
            llm = null
        }
    }
}
