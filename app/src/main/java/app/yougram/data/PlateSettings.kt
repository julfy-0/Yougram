package app.yougram.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Экраны, у которых есть подложки под строками. */
enum class PlateArea(val key: String, val title: String) {
    Chats("plate_chats", "Чаты"),
    Contacts("plate_contacts", "Контакты"),
    Calls("plate_calls", "Звонки"),
    Settings("plate_settings", "Настройки"),
}

/** Прозрачность подложек по экранам: 0 — сплошная (как раньше), 1 — полностью прозрачная. */
data class PlateTransparency(
    val chats: Float = 0f,
    val contacts: Float = 0f,
    val calls: Float = 0f,
    val settings: Float = 0f,
) {
    fun of(area: PlateArea): Float = when (area) {
        PlateArea.Chats -> chats
        PlateArea.Contacts -> contacts
        PlateArea.Calls -> calls
        PlateArea.Settings -> settings
    }

    fun with(area: PlateArea, value: Float): PlateTransparency = when (area) {
        PlateArea.Chats -> copy(chats = value)
        PlateArea.Contacts -> copy(contacts = value)
        PlateArea.Calls -> copy(calls = value)
        PlateArea.Settings -> copy(settings = value)
    }
}

class PlateRepository(context: Context) {
    private val prefs = context.getSharedPreferences("yougram_plates", Context.MODE_PRIVATE)

    private val _plates = MutableStateFlow(
        PlateTransparency(
            chats = prefs.getFloat(PlateArea.Chats.key, 0f),
            contacts = prefs.getFloat(PlateArea.Contacts.key, 0f),
            calls = prefs.getFloat(PlateArea.Calls.key, 0f),
            settings = prefs.getFloat(PlateArea.Settings.key, 0f),
        ),
    )
    val plates: StateFlow<PlateTransparency> = _plates.asStateFlow()

    fun set(area: PlateArea, value: Float) {
        val v = value.coerceIn(0f, 1f)
        _plates.update { it.with(area, v) }
        prefs.edit().putFloat(area.key, v).apply()
    }

    fun reset() {
        PlateArea.entries.forEach { set(it, 0f) }
    }
}
