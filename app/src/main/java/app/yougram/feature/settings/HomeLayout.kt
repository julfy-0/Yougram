package app.yougram.feature.settings

import org.json.JSONArray
import org.json.JSONObject

/** Категория главного экрана настроек: название и упорядоченный список id вкладок. */
data class HomeSection(val title: String, val entries: List<String>)

/** Раскладка вкладок главного экрана настроек: хранение в JSON и операции редактирования. */
object HomeLayout {
    val Default = listOf(
        HomeSection("Yougram", listOf("appearance", "extras", "plugins", "power")),
        HomeSection("Аккаунт и защита", listOf("account", "privacy", "security", "devices")),
        HomeSection("Чаты", listOf("chatSettings", "folders", "archive", "notifications")),
        HomeSection("Данные и язык", listOf("data", "language")),
        HomeSection("Telegram", listOf("telegram")),
    )

    fun parse(json: String?): List<HomeSection> {
        if (json == null) return Default
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val e = o.getJSONArray("e")
                HomeSection(o.getString("t"), List(e.length()) { k -> e.getString(k) })
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: Default
    }

    fun toJson(layout: List<HomeSection>): String = JSONArray().apply {
        layout.forEach { s ->
            put(JSONObject().put("t", s.title).put("e", JSONArray(s.entries)))
        }
    }.toString()

    /** Убирает неизвестные и повторяющиеся вкладки; вкладки, которых нет в раскладке (новые), добавляет в конец. */
    fun normalize(layout: List<HomeSection>, allIds: List<String>): List<HomeSection> {
        val known = allIds.toSet()
        val seen = mutableSetOf<String>()
        val cleaned = layout.map { s -> s.copy(entries = s.entries.filter { it in known && seen.add(it) }) }
        val missing = allIds.filter { it !in seen }
        if (missing.isEmpty()) return cleaned
        if (cleaned.isEmpty()) return listOf(HomeSection("Настройки", missing))
        return cleaned.dropLast(1) + cleaned.last().let { it.copy(entries = it.entries + missing) }
    }

    fun moveSection(l: List<HomeSection>, si: Int, dir: Int): List<HomeSection> {
        val to = si + dir
        if (si !in l.indices || to !in l.indices) return l
        return l.toMutableList().apply { val t = this[si]; this[si] = this[to]; this[to] = t }
    }

    /** Сдвиг вкладки на одну позицию; на краю категории вкладка переходит в соседнюю категорию. */
    fun moveEntry(l: List<HomeSection>, si: Int, ei: Int, dir: Int): List<HomeSection> {
        val section = l.getOrNull(si) ?: return l
        val id = section.entries.getOrNull(ei) ?: return l
        val out = l.toMutableList()
        val to = ei + dir
        if (to in section.entries.indices) {
            val e = section.entries.toMutableList()
            e[ei] = e[to]; e[to] = id
            out[si] = section.copy(entries = e)
            return out
        }
        val ns = si + dir
        val neighbour = l.getOrNull(ns) ?: return l
        out[si] = section.copy(entries = section.entries - id)
        out[ns] = neighbour.copy(entries = if (dir < 0) neighbour.entries + id else listOf(id) + neighbour.entries)
        return out
    }

    fun rename(l: List<HomeSection>, si: Int, title: String): List<HomeSection> =
        l.mapIndexed { i, s -> if (i == si) s.copy(title = title) else s }

    /** Удаляет категорию; её вкладки переходят в соседнюю. */
    fun deleteSection(l: List<HomeSection>, si: Int): List<HomeSection> {
        if (l.size <= 1 || si !in l.indices) return l
        val s = l[si]
        val target = if (si > 0) si - 1 else 1
        return l.mapIndexedNotNull { i, x ->
            when (i) {
                si -> null
                target -> x.copy(entries = if (target < si) x.entries + s.entries else s.entries + x.entries)
                else -> x
            }
        }
    }

    fun addSection(l: List<HomeSection>): List<HomeSection> = l + HomeSection("Новая категория", emptyList())
}