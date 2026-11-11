package app.yougram.feature.assistant.data

import app.yougram.core.settings.SettingsRepository
import app.yougram.core.settings.ThemeMode
import app.yougram.feature.settings.SettingsPage

/** Настройка, которой ассистент может управлять (всё остальное ему недоступно). */
class AiSetting(
    val name: String,
    val options: List<String>,
    /** Корни слов (в нижнем регистре, «ё» → «е»), по которым настройка узнаётся в запросе пользователя. */
    val keywords: List<String>,
    val apply: (SettingsRepository, String) -> Unit,
)

/** Действие, которое ассистент предлагает пользователю; выполняется только после нажатия кнопки. */
sealed interface AiAction {
    val label: String

    data class Open(val name: String, val page: SettingsPage) : AiAction {
        override val label get() = "Открыть: $name"
    }

    data class Set(val setting: AiSetting, val value: String) : AiAction {
        override val label get() = "Применить: ${setting.name} → $value"
    }

    /** Открывает выбор файла .ygplugin и устанавливает плагин (после подтверждения пользователя). */
    data object ImportPlugin : AiAction {
        override val label get() = "Импортировать плагин…"
    }
}

/**
 * Белые списки для меток в ответе модели:
 *  [[открыть:ИМЯ]]            — перейти в раздел настроек;
 *  [[установить:ИМЯ=ЗНАЧЕНИЕ]] — изменить настройку.
 * Метка принимается, только если ИМЯ (и ЗНАЧЕНИЕ) есть в списках ниже (регистр не важен).
 * Слабая модель ошибается, поэтому она лишь предлагает: изменение происходит по кнопке пользователя.
 */
object AiActions {
    val pages: Map<String, SettingsPage> = linkedMapOf(
        "Внешний вид" to SettingsPage.Appearance,
        "Уведомления" to SettingsPage.Notifications,
        "Конфиденциальность" to SettingsPage.Privacy,
        "Данные и память" to SettingsPage.DataStorage,
        "Язык" to SettingsPage.Language,
        "Энергосбережение" to SettingsPage.PowerSaving,
        "Устройства" to SettingsPage.Devices,
        "Чёрный список" to SettingsPage.Blocked,
        "Архив" to SettingsPage.Archive,
        "Плагины" to SettingsPage.Plugins,
        "Режим призрака" to SettingsPage.Ghost,
        "Настройки клиента" to SettingsPage.Extras,
        "Аккаунт" to SettingsPage.Account,
        "Обновление" to SettingsPage.Update,
        "О приложении" to SettingsPage.About,
    )

    private const val ON = "вкл"
    private const val OFF = "выкл"
    private val onOff = listOf(ON, OFF)

    val settings: List<AiSetting> = listOf(
        AiSetting("Тема", listOf("светлая", "тёмная", "системная"), listOf("тем", "светл")) { s, v ->
            s.setThemeMode(
                when (v) {
                    "светлая" -> ThemeMode.Light
                    "тёмная" -> ThemeMode.Dark
                    else -> ThemeMode.System
                },
            )
        },
        AiSetting("Динамические цвета", onOff, listOf("динамическ", "material", "monet", "обои")) { s, v -> s.setDynamicColor(v == ON) },
        AiSetting("Энергосбережение", onOff, listOf("энергосбереж", "экономи")) { s, v -> s.setPowerSaving(v == ON) },
        AiSetting("Встроенный браузер", onOff, listOf("браузер")) { s, v -> s.setInAppBrowser(v == ON) },
    ) + AiToggles.all

    private val quoted = Regex("""[«"“]([^»"”]{2,30})[»"”]""")
    private val openTag = Regex("""\[\[\s*открыть\s*:\s*([^\]]{1,40}?)\s*]]""", RegexOption.IGNORE_CASE)
    private val setTag = Regex("""\[\[\s*установить\s*:\s*([^=\]]{1,40}?)\s*=\s*([^\]]{1,20}?)\s*]]""", RegexOption.IGNORE_CASE)

    private fun normalizeValue(v: String): String = when (v.trim().lowercase()) {
        "включить", "включено", "on", "да" -> ON
        "выключить", "выключено", "off", "нет" -> OFF
        "темная" -> "тёмная"
        "светлая тема" -> "светлая"
        else -> v.trim().lowercase()
    }

    /** Возвращает текст без меток и список валидных действий (без дублей). */
    fun parse(reply: String): Pair<String, List<AiAction>> {
        val actions = LinkedHashSet<AiAction>()
        for (m in openTag.findAll(reply)) {
            val key = pages.keys.firstOrNull { it.equals(m.groupValues[1], ignoreCase = true) } ?: continue
            actions += AiAction.Open(key, pages.getValue(key))
        }
        for (m in setTag.findAll(reply)) {
            val def = settings.firstOrNull { it.name.equals(m.groupValues[1], ignoreCase = true) } ?: continue
            val value = normalizeValue(m.groupValues[2])
            if (value in def.options) actions += AiAction.Set(def, value)
        }
        // Модель часто называет раздел словами, не ставя метку: ловим точные названия из белого списка.
        for (q in quoted.findAll(reply)) {
            val key = pages.keys.firstOrNull { it.equals(q.groupValues[1].trim(), ignoreCase = true) } ?: continue
            actions += AiAction.Open(key, pages.getValue(key))
        }
        // Недописанная метка в конце потокового ответа тоже не должна мелькать.
        val text = setTag.replace(openTag.replace(reply, ""), "").replace(Regex("""\[\[[^\]]*$"""), "").trim()
        return text to actions.toList().take(3)
    }

    private val questionStarts = listOf("как ", "что ", "зачем", "почему", "где ", "какой", "какая", "какие", "можно ли", "расскажи", "объясни", "что такое")
    private val offWords = listOf("выключ", "отключ", "выкл", "убери", "отруби", "деактив", "не надо", "без ")
    private val onWords = listOf("включ", "вкл", "актив", "поставь", "установи", "сделай", "хочу", "верни")

    private val importWords = listOf("импорт", "установ", "загруз", "добав", "подключ", "поставь")

    /** «Импортируй плагин», «установи плагин» и т.п. — команда, а не вопрос. */
    fun isPluginImport(t: String): Boolean =
        (t.contains("плагин") || t.contains("модул") || t.contains("ygplugin")) &&
            importWords.any { t.contains(it) } &&
            !(t.contains('?') || questionStarts.any { t.trimStart().startsWith(it) })

    /** Основы слов из названия настройки (≥4 букв, первые 5): позволяет находить любую настройку по её имени. */
    private fun nameStems(name: String): List<String> =
        name.lowercase().replace('ё', 'е')
            .split(Regex("[^а-яa-z0-9]+"))
            .filter { it.length >= 4 }
            .map { it.take(5) }

    private val pageKeywords: List<Pair<List<String>, String>> = listOf(
        listOf("уведомлен", "пуш") to "Уведомления",
        listOf("язык", "локализ") to "Язык",
        listOf("архив") to "Архив",
        listOf("модул", "плагин") to "Плагины",
        listOf("призрак") to "Режим призрака",
        listOf("черный список", "заблокирован") to "Чёрный список",
        listOf("устройств", "сеанс") to "Устройства",
        listOf("обновлен", "обнови") to "Обновление",
        listOf("о приложении", "версия") to "О приложении",
        listOf("аккаунт") to "Аккаунт",
        listOf("конфиденциальн", "приватн", "безопасн") to "Конфиденциальность",
        listOf("кэш", "кеш", "память", "данные") to "Данные и память",
        listOf("внешний вид", "оформлен", "шрифт", "размыт", "прозрачн") to "Внешний вид",
        listOf("настройки клиента") to "Настройки клиента",
        listOf("энергосбереж") to "Энергосбережение",
    )

    /**
     * Разбор запроса пользователя без нейросети: слабая модель не ставит метки, а команда вроде
     * «включи светлую тему» однозначна. Set — предложить изменение, иначе Open — предложить раздел.
     */
    fun fromUserText(raw: String): List<AiAction> {
        val t = raw.lowercase().replace('ё', 'е')
        if (isPluginImport(t)) return listOf(AiAction.ImportPlugin, AiAction.Open("Плагины", SettingsPage.Plugins))
        val sets = ArrayList<AiAction>()
        val isQuestion = t.contains('?') || questionStarts.any { t.trimStart().startsWith(it) }
        if (!isQuestion) {
            // Тема: особые значения.
            val scored = settings.mapNotNull { def ->
                val kwScore = def.keywords.maxOfOrNull { k -> if (k.split('+').all { t.contains(it) }) k.replace("+", "").length else 0 } ?: 0
                val stems = nameStems(def.name)
                val nameScore = if (stems.isNotEmpty() && stems.all { t.contains(it) }) stems.sumOf { it.length } else 0
                val score = maxOf(kwScore, nameScore)
                if (score > 0) def to score else null
            }.sortedByDescending { it.second }
            val best = scored.firstOrNull()?.second ?: 0
            for ((def, score) in scored) {
                if (score < best) break
                val value = when (def.name) {
                    "Тема" -> when {
                        t.contains("светл") -> "светлая"
                        t.contains("темн") -> "тёмная"
                        t.contains("систем") || t.contains("авто") -> "системная"
                        else -> null
                    }
                    // Без глагола (опечатка в «включи») считаем, что хотят включить.
                    else -> if (offWords.any { t.contains(it) }) OFF else ON
                }
                if (value != null) sets += AiAction.Set(def, value)
            }
        }
        if (sets.isNotEmpty()) return sets.take(2)
        return pageKeywords.filter { (keys, _) -> keys.any { t.contains(it) } }
            .mapNotNull { (_, name) -> pages[name]?.let { AiAction.Open(name, it) } }
            .take(2)
    }

    /** Системная инструкция: короткая, потому что контекст модели всего 1280 токенов. */
    val systemPrompt: String =
        "Ты — официальный ИИ MaterialGram, встроенный помощник приложения MaterialGram (клиент Telegram). " +
            "Если спрашивают, кто ты, отвечай: «Я официальный ИИ MaterialGram». Не называй себя Gemma, Qwen, Google, Alibaba или другой моделью и компанией. " +
            "Отвечай по-русски, кратко, 1–3 предложения, только по существу. " +
            "Включением и выключением настроек занимается само приложение: если пользователь просит включить что-то, " +
            "посоветуй написать «включи …». Чтобы направить в раздел, назови его в кавычках: «ИМЯ». " +
            "Разделы: ${pages.keys.joinToString(", ")}. Других имён не придумывай. Если есть «Справка» — отвечай по ней. Если не знаешь ответа — так и скажи."
}
