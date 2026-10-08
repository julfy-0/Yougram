package app.yougram.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Экраны, у которых есть подложки под строками. */
enum class PlateArea(val key: String, val title: String, val default: Float = 0f) {
    Chats("plate_chats", "Чаты"),
    Contacts("plate_contacts", "Контакты"),
    Calls("plate_calls", "Звонки"),
    Settings("plate_settings", "Настройки"),
    /** Меню эмодзи, стикеров и GIF в чате: по умолчанию полупрозрачное, с размытием фона. */
    Picker("plate_picker", "Меню эмодзи и стикеров", 0.4f),
    /** Менеджер аккаунтов: полупрозрачная панель с размытием. */
    Accounts("plate_accounts", "Менеджер аккаунтов", 0.4f),
    /** Меню команд бота в чате: полупрозрачная панель с размытием фона. */
    Commands("plate_commands", "Меню команд бота", 0.4f),
}

/** Прозрачность подложек по экранам: 0 — сплошная (как раньше), 1 — полностью прозрачная. */
data class PlateTransparency(
    val chats: Float = 0f,
    val contacts: Float = 0f,
    val calls: Float = 0f,
    val settings: Float = 0f,
    val picker: Float = PlateArea.Picker.default,
    val accounts: Float = PlateArea.Accounts.default,
    val commands: Float = PlateArea.Commands.default,
) {
    fun of(area: PlateArea): Float = when (area) {
        PlateArea.Chats -> chats
        PlateArea.Contacts -> contacts
        PlateArea.Calls -> calls
        PlateArea.Settings -> settings
        PlateArea.Picker -> picker
        PlateArea.Accounts -> accounts
        PlateArea.Commands -> commands
    }

    fun with(area: PlateArea, value: Float): PlateTransparency = when (area) {
        PlateArea.Chats -> copy(chats = value)
        PlateArea.Contacts -> copy(contacts = value)
        PlateArea.Calls -> copy(calls = value)
        PlateArea.Settings -> copy(settings = value)
        PlateArea.Picker -> copy(picker = value)
        PlateArea.Accounts -> copy(accounts = value)
        PlateArea.Commands -> copy(commands = value)
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
            picker = prefs.getFloat(PlateArea.Picker.key, PlateArea.Picker.default),
            accounts = prefs.getFloat(PlateArea.Accounts.key, PlateArea.Accounts.default),
            commands = prefs.getFloat(PlateArea.Commands.key, PlateArea.Commands.default),
        ),
    )
    val plates: StateFlow<PlateTransparency> = _plates.asStateFlow()

    fun set(area: PlateArea, value: Float) {
        val v = value.coerceIn(0f, 1f)
        _plates.update { it.with(area, v) }
        prefs.edit().putFloat(area.key, v).apply()
    }

    fun reset() {
        PlateArea.entries.forEach { set(it, it.default) }
    }
}
