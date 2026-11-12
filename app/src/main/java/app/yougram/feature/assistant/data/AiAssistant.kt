package app.yougram.feature.assistant.data

import android.content.Context
import app.yougram.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiMessage(
    val fromUser: Boolean,
    val text: String,
    val actions: List<AiAction> = emptyList(),
    val time: Long = System.currentTimeMillis(),
)

/** Состояние ассистента: загрузка модели, история диалога, генерация. Живёт весь процесс. */
class AiAssistant(
    context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
) {
    private val appContext = context.applicationContext
    val models = AiModelManager(appContext)

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var engine: AiEngine? = null
    private var downloadJob: Job? = null
    private var genJob: Job? = null

    /** MediaPipe GenAI собран только под 64-бит. */
    val supported: Boolean get() = android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

    fun download() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch { models.download() }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    /** Переключает нейросеть: отменяет загрузку и генерацию, выгружает текущую модель из памяти. */
    fun select(model: AiModel) {
        if (model == models.selected.value) return
        downloadJob?.cancel()
        genJob?.cancel()
        closeEngine()
        _busy.value = false
        _error.value = null
        models.select(model)
    }

    fun deleteModel() {
        genJob?.cancel()
        closeEngine()
        models.delete()
    }

    fun clear() {
        genJob?.cancel()
        _messages.value = emptyList()
        _busy.value = false
        _error.value = null
    }

    fun stop() { genJob?.cancel() }

    /** Выполняет предложенное моделью изменение настройки — вызывается только по нажатию пользователя. */
    fun apply(action: AiAction.Set) {
        action.setting.apply(settings, action.value)
        _messages.update { it + AiMessage(false, "Готово: ${action.setting.name} — ${action.value}.") }
    }

    /** Сообщение от ассистента без генерации. */
    fun report(text: String) { _messages.update { it + AiMessage(false, text) } }

    fun send(text: String) {
        val q = text.trim()
        val ready = models.state.value as? AiModelState.Ready ?: return
        if (q.isEmpty() || _busy.value) return
        _error.value = null
        val intent = AiActions.fromUserText(q)
        if (intent.any { it is AiAction.Set }) {
            // Команда однозначна — отвечаем сразу, без генерации (быстро и без выдумок модели).
            _messages.update { it + AiMessage(true, q) + AiMessage(false, "Могу сделать это — нажмите кнопку.", intent) }
            return
        }
        // Справка о проекте: на вопрос «расскажи / как / что такое» отвечаем напрямую из базы знаний —
        // слабая модель с контекстом 1280 токенов иначе выдумывает. Страницу настроек предлагаем кнопкой.
        val facts = AiKnowledge.find(q)
        val lower = q.lowercase().replace('ё', 'е')
        if (AiKnowledge.isInfoQuery(q) && (lower.contains("что ты знаешь") || lower.contains("о чем можно") || lower.contains("о чём можно"))) {
            _messages.update { it + AiMessage(true, q) + AiMessage(false, AiKnowledge.overview()) }
            return
        }
        if (facts.isNotEmpty() && AiKnowledge.isInfoQuery(q)) {
            val answer = facts.joinToString("\n\n") { it.first.text }
            val openers = facts.mapNotNull { (en, _) -> AiActions.pages.entries.firstOrNull { (name, _) -> name.equals(en.title, ignoreCase = true) } }
                .map { AiAction.Open(it.key, it.value) }
            _messages.update { it + AiMessage(true, q) + AiMessage(false, answer, (intent + openers).distinct().take(3)) }
            return
        }
        val context = AiKnowledge.contextFor(q)
        _messages.update { it + AiMessage(true, q) + AiMessage(false, "") }
        _busy.value = true
        genJob = scope.launch {
            try {
                val m = models.selected.value
                val eng = engine ?: AiEngine(appContext, ready.file, template = m.template, maxTokens = m.maxTokens)
                    .also { engine = it }
                // Контекст модели 1280 токенов на вход + ответ, поэтому история обрезается по символам.
                val history = trimmed(_messages.value.dropLast(1), budgetChars = if (context.isEmpty()) 1000 else 600)
                eng.stream(AiActions.systemPrompt + context, history).collect { raw ->
                    val (clean, actions) = AiActions.parse(raw)
                    setLast(AiMessage(false, clean, (intent + actions).distinct().take(3)))
                }
                if (_messages.value.lastOrNull()?.text.isNullOrBlank()) setLast(AiMessage(false, "(пустой ответ)"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) { // в т.ч. UnsatisfiedLinkError на неподдерживаемом устройстве
                _error.value = t.message ?: t.javaClass.simpleName
                _messages.update { list -> if (list.lastOrNull()?.let { !it.fromUser && it.text.isEmpty() } == true) list.dropLast(1) else list }
                closeEngine()
            } finally {
                _busy.value = false
            }
        }
    }

    private fun setLast(m: AiMessage) = _messages.update { it.dropLast(1) + m.copy(time = it.last().time) }

    private fun trimmed(all: List<AiMessage>, budgetChars: Int = 1000): List<AiTurn> {
        val out = ArrayDeque<AiTurn>()
        var used = 0
        for (m in all.asReversed()) {
            val len = m.text.length
            if (used + len > budgetChars && out.isNotEmpty()) break
            out.addFirst(AiTurn(if (m.fromUser) AiTurn.Role.USER else AiTurn.Role.ASSISTANT, m.text.take(budgetChars)))
            used += len
        }
        // Диалог должен начинаться с реплики пользователя.
        while (out.firstOrNull()?.role == AiTurn.Role.ASSISTANT) out.removeFirst()
        return out.toList()
    }

    private fun closeEngine() { engine?.close(); engine = null }
}
