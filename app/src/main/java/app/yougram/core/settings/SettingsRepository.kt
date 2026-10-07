package app.yougram.core.settings

import android.content.Context
import app.yougram.feature.badge.data.YougramBanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/** Настройки «стеклянных» панелей. */
data class GlassSettings(
    /** Радиус размытия фона под панелями, dp. 0 — без размытия. */
    val blurRadius: Float = DEFAULT_BLUR,
    /** Плотность затемнения/подкраски панели: 0 — полностью прозрачная, 1 — сплошная. */
    val opacity: Float = DEFAULT_OPACITY,
) {
    companion object {
        const val DEFAULT_BLUR = 24f
        const val DEFAULT_OPACITY = 0.45f
        const val MAX_BLUR = 60f
    }
}

enum class ThemeMode { System, Light, Dark }

/** Настройки темы: режим, системные цвета (Material You) или свой акцентный цвет. */
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.Dark,
    val dynamic: Boolean = true,
    /** Индекс в списке акцентов; используется, когда [dynamic] выключен. */
    val accent: Int = 0,
    /** Путь к своему шрифту (копия в хранилище приложения); null — системный шрифт. */
    val fontPath: String? = null,
    /** Имя файла шрифта для показа в настройках. */
    val fontName: String? = null,
)

/** Локальные настройки уведомлений (применятся, когда появятся push-уведомления). */
data class NotificationPrefs(
    val privateChats: Boolean = true,
    val groups: Boolean = true,
    val channels: Boolean = true,
    val stories: Boolean = true,
    val reactions: Boolean = true,
    val callVibration: Int = 0,
    val ringtone: Int = 0,
    val showCounter: Boolean = true,
    val countMuted: Boolean = false,
    val countMessages: Boolean = true,
    val preview: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val chatSound: Boolean = true,
    val popups: Boolean = true,
    val contactJoined: Boolean = false,
    val pinnedMessages: Boolean = true,
    val restartOnClose: Boolean = true,
    val backgroundConnection: Boolean = true,
    val repeatMinutes: Int = 60,
)

enum class SwipeAction(val label: String) {
    Delete("Удалить"),
    ChangeFolder("Сменить папку"),
    Pin("Закрепить"),
}

enum class MicrophoneSource(val label: String) {
    Builtin("Встроенный"),
    External("Внешний"),
}

enum class DistanceUnit(val label: String) {
    Auto("Автоматически"),
    Kilometers("Километры"),
    Miles("Мили"),
}

/** Настройки чатов. */
data class ChatPrefs(
    /** Размер текста сообщений, sp. */
    val textSize: Int = 16,
    /** Внешний радиус углов пузырей, dp. */
    val bubbleRadius: Int = 24,
    /** Индекс цвета имени в палитре экрана настроек чатов. */
    val nameColor: Int = 3,
    /** 0 — обоев нет; иначе метка версии файла обоев. */
    val wallpaper: Long = 0L,
    /** 2 или 3 строки в строке списка чатов. */
    val listLines: Int = 2,
    /** true — кнопка поиска в верхней панели, false — рядом с нижней панелью вкладок. */
    val searchOnTop: Boolean = false,
    val swipeAction: SwipeAction = SwipeAction.ChangeFolder,
    val nightAuto: Boolean = false,
    val inAppBrowser: Boolean = true,
    val mediaTapFlip: Boolean = true,
    val raiseToListen: Boolean = true,
    val raiseToSpeak: Boolean = false,
    val pauseMusicOnRecord: Boolean = true,
    val pauseMusicOnMedia: Boolean = true,
    val microphone: MicrophoneSource = MicrophoneSource.Builtin,
    val directShare: Boolean = true,
    val showSensitive: Boolean = true,
    val enterToSend: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.Auto,
    /** Непрозрачность блоков с сообщениями, %: 100 — сплошные. */
    val bubbleOpacity: Int = 100,
    /** Блоки с сообщениями размывают фон чата (обои) под собой. */
    val bubbleBlur: Boolean = false,
)

enum class MapPreview(val label: String) {
    None("Не использовать"),
    Telegram("Telegram"),
    Google("Google"),
    Yandex("Яндекс"),
}

/** Локальные настройки контактов и секретных чатов (на сервер не отправляются). */
data class PrivacyPrefs(
    val syncContacts: Boolean = false,
    val mapPreview: MapPreview = MapPreview.None,
    val linkPreview: Boolean = false,
)

/** Данные и память: автозагрузка, сохранение в галерею, стриминг, экономия трафика в звонках, прокси. */
data class DataPrefs(
    val autoMobile: Boolean = true,
    val autoWifi: Boolean = true,
    val autoRoaming: Boolean = true,
    val saveToGalleryPrivate: Boolean = false,
    val saveToGalleryGroups: Boolean = false,
    val saveToGalleryChannels: Boolean = false,
    val streamMedia: Boolean = true,
    val streamMkv: Boolean = false,
    val streamAll: Boolean = true,
    /** 0 — никогда, 1 — только в роуминге, 2 — всегда. */
    val callsDataSaving: Int = 1,
    val proxyServer: String = "",
    val proxyPort: String = "",
    val proxyUser: String = "",
    /** Пароль для SOCKS5/HTTP или секрет для MTProto. */
    val proxyPass: String = "",
    /** "socks5" | "http" | "mtproto". */
    val proxyType: String = "socks5",
)

/** Режим призрака: не выдаём факт прочтения. */
data class GhostPrefs(
    val enabled: Boolean = false,
    /** Прочитать чат автоматически, когда пользователь сам отправляет сообщение. */
    val readOnAction: Boolean = true,
)

/** Режим шпиона: что сохранять локально. */
data class SpyPrefs(
    val saveDeleted: Boolean = true,
    val saveEdits: Boolean = true,
    val saveInBots: Boolean = true,
    val saveReadDate: Boolean = false,
    val saveLastOnline: Boolean = false,
    val saveAttachments: Boolean = true,
    /** Сохранять вложения из каналов (по умолчанию выключено — их слишком много). */
    val attachmentsChannels: Boolean = false,
    val folderName: String = DEFAULT_FOLDER,
    /** Индекс в [MAX_FOLDER_LABELS]; последний — без ограничения. */
    val maxFolderIndex: Int = MAX_FOLDER_LABELS.lastIndex,
) {
    /** Лимит размера папки в байтах или null — без ограничения. */
    val maxFolderBytes: Long? get() = MAX_FOLDER_BYTES.getOrNull(maxFolderIndex)?.takeIf { it > 0 }

    companion object {
        const val DEFAULT_FOLDER = "Saved Attachments"
        val MAX_FOLDER_LABELS = listOf("300 МБ", "1 ГБ", "2 ГБ", "5 ГБ", "16 ГБ", "∞")
        val MAX_FOLDER_BYTES = listOf(300L shl 20, 1L shl 30, 2L shl 30, 5L shl 30, 16L shl 30, 0L)
    }
}

/** Пользователь, чьи сообщения скрываются («теневой бан»). */
data class ShadowBanned(val userId: Long, val name: String)

/** Фильтры сообщений. */
data class FilterPrefs(
    val enabled: Boolean = false,
    /** Применять общие фильтры ([patterns]) в чатах. */
    val sharedInChats: Boolean = false,
    /** Скрывать сообщения пользователей из чёрного списка. */
    val hideBlocked: Boolean = false,
    val patterns: List<String> = emptyList(),
    val shadowBanned: List<ShadowBanned> = emptyList(),
)

class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("yougram_settings", Context.MODE_PRIVATE)

    private val _glass = MutableStateFlow(
        GlassSettings(
            blurRadius = prefs.getFloat(KEY_BLUR, GlassSettings.DEFAULT_BLUR),
            opacity = prefs.getFloat(KEY_OPACITY, GlassSettings.DEFAULT_OPACITY),
        )
    )
    val glass: StateFlow<GlassSettings> = _glass.asStateFlow()

    fun setBlur(value: Float) {
        _glass.update { it.copy(blurRadius = value) }
        prefs.edit().putFloat(KEY_BLUR, value).apply()
    }

    fun setOpacity(value: Float) {
        _glass.update { it.copy(opacity = value) }
        prefs.edit().putFloat(KEY_OPACITY, value).apply()
    }

    private val _theme = MutableStateFlow(
        ThemeSettings(
            mode = ThemeMode.entries.getOrElse(prefs.getInt(KEY_MODE, ThemeMode.Dark.ordinal)) { ThemeMode.Dark },
            dynamic = prefs.getBoolean(KEY_DYNAMIC, true),
            accent = prefs.getInt(KEY_ACCENT, 0),
            fontPath = prefs.getString(KEY_FONT_PATH, null)?.takeIf { java.io.File(it).exists() },
            fontName = prefs.getString(KEY_FONT_NAME, null),
        )
    )
    val theme: StateFlow<ThemeSettings> = _theme.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _theme.update { it.copy(mode = mode) }
        prefs.edit().putInt(KEY_MODE, mode.ordinal).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _theme.update { it.copy(dynamic = enabled) }
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun setAccent(index: Int) {
        _theme.update { it.copy(accent = index) }
        prefs.edit().putInt(KEY_ACCENT, index).apply()
    }

    /** Свой шрифт для всего приложения; [path] = null возвращает системный. */
    fun setCustomFont(path: String?, name: String?) {
        _theme.update { it.copy(fontPath = path, fontName = if (path == null) null else name) }
        prefs.edit().putString(KEY_FONT_PATH, path).putString(KEY_FONT_NAME, if (path == null) null else name).apply()
    }

    fun resetGlass() {
        setBlur(GlassSettings.DEFAULT_BLUR)
        setOpacity(GlassSettings.DEFAULT_OPACITY)
    }

    // Уведомления.
    private fun loadNotifications() = NotificationPrefs(
        privateChats = prefs.getBoolean("n_private", true),
        groups = prefs.getBoolean("n_groups", true),
        channels = prefs.getBoolean("n_channels", true),
        stories = prefs.getBoolean("n_stories", true),
        reactions = prefs.getBoolean("n_reactions", true),
        callVibration = prefs.getInt("n_call_vibration", 0),
        ringtone = prefs.getInt("n_ringtone", 0),
        showCounter = prefs.getBoolean("n_show_counter", true),
        countMuted = prefs.getBoolean("n_count_muted", false),
        countMessages = prefs.getBoolean("n_count_messages", true),
        preview = prefs.getBoolean("n_preview", true),
        sound = prefs.getBoolean("n_sound", true),
        vibration = prefs.getBoolean("n_vibration", true),
        chatSound = prefs.getBoolean("n_chat_sound", true),
        popups = prefs.getBoolean("n_popups", true),
        contactJoined = prefs.getBoolean("n_contact_joined", false),
        pinnedMessages = prefs.getBoolean("n_pinned", true),
        restartOnClose = prefs.getBoolean("n_restart", true),
        backgroundConnection = prefs.getBoolean("n_background", true),
        repeatMinutes = prefs.getInt("n_repeat", 60),
    )

    private val _notifications = MutableStateFlow(loadNotifications())
    val notifications: StateFlow<NotificationPrefs> = _notifications.asStateFlow()

    fun updateNotifications(transform: (NotificationPrefs) -> NotificationPrefs) {
        val n = transform(_notifications.value)
        _notifications.value = n
        prefs.edit()
            .putBoolean("n_private", n.privateChats)
            .putBoolean("n_groups", n.groups)
            .putBoolean("n_channels", n.channels)
            .putBoolean("n_stories", n.stories)
            .putBoolean("n_reactions", n.reactions)
            .putInt("n_call_vibration", n.callVibration)
            .putInt("n_ringtone", n.ringtone)
            .putBoolean("n_show_counter", n.showCounter)
            .putBoolean("n_count_muted", n.countMuted)
            .putBoolean("n_count_messages", n.countMessages)
            .putBoolean("n_preview", n.preview)
            .putBoolean("n_sound", n.sound)
            .putBoolean("n_vibration", n.vibration)
            .putBoolean("n_chat_sound", n.chatSound)
            .putBoolean("n_popups", n.popups)
            .putBoolean("n_contact_joined", n.contactJoined)
            .putBoolean("n_pinned", n.pinnedMessages)
            .putBoolean("n_restart", n.restartOnClose)
            .putBoolean("n_background", n.backgroundConnection)
            .putInt("n_repeat", n.repeatMinutes)
            .apply()
    }

    fun resetNotifications() = updateNotifications { NotificationPrefs() }

    // Защита от «вылета при входе в чат»: перед открытием чата с размытием под сообщениями ставим метку
    // и снимаем её, когда чат благополучно отрисован. Если при запуске метка осталась — прошлый вход
    // закончился падением, и размытие под сообщениями выключается, чтобы приложение не падало снова.
    init {
        if (prefs.getBoolean(KEY_CHAT_GUARD, false)) {
            prefs.edit().putBoolean("c_bubble_blur", false).putBoolean(KEY_CHAT_GUARD, false).commit()
        }
    }

    /** Ставит метку синхронно: при падении процесса запись apply() могла бы не успеть на диск. */
    fun armChatGuard() {
        prefs.edit().putBoolean(KEY_CHAT_GUARD, true).commit()
    }

    fun disarmChatGuard() {
        if (prefs.getBoolean(KEY_CHAT_GUARD, false)) prefs.edit().putBoolean(KEY_CHAT_GUARD, false).apply()
    }

    // Настройки чатов.
    private val _chatPrefs = MutableStateFlow(
        ChatPrefs(
            textSize = prefs.getInt("c_text_size", 16).coerceIn(10, 40),
            bubbleRadius = prefs.getInt("c_bubble_radius", 24).coerceIn(0, 28),
            nameColor = prefs.getInt("c_name_color", 3),
            wallpaper = prefs.getLong("c_wallpaper", 0L),
            listLines = prefs.getInt("c_list_lines", 2),
            searchOnTop = prefs.getBoolean("c_search_top", false),
            swipeAction = SwipeAction.entries.getOrElse(prefs.getInt("c_swipe", 1)) { SwipeAction.ChangeFolder },
            nightAuto = prefs.getBoolean("c_night_auto", false),
            inAppBrowser = prefs.getBoolean("c_in_app_browser", true),
            mediaTapFlip = prefs.getBoolean("c_media_tap_flip", true),
            raiseToListen = prefs.getBoolean("c_raise_listen", true),
            raiseToSpeak = prefs.getBoolean("c_raise_speak", false),
            pauseMusicOnRecord = prefs.getBoolean("c_pause_record", true),
            pauseMusicOnMedia = prefs.getBoolean("c_pause_media", true),
            microphone = MicrophoneSource.entries.getOrElse(prefs.getInt("c_mic", 0)) { MicrophoneSource.Builtin },
            directShare = prefs.getBoolean("c_direct_share", true),
            showSensitive = prefs.getBoolean("c_sensitive", true),
            enterToSend = prefs.getBoolean("c_enter_send", false),
            distanceUnit = DistanceUnit.entries.getOrElse(prefs.getInt("c_distance", 0)) { DistanceUnit.Auto },
            bubbleOpacity = prefs.getInt("c_bubble_opacity", 100).coerceIn(20, 100),
            bubbleBlur = prefs.getBoolean("c_bubble_blur", false),
        )
    )
    val chatPrefs: StateFlow<ChatPrefs> = _chatPrefs.asStateFlow()

    fun updateChatPrefs(transform: (ChatPrefs) -> ChatPrefs) {
        val c = transform(_chatPrefs.value)
        _chatPrefs.value = c
        prefs.edit()
            .putInt("c_text_size", c.textSize)
            .putInt("c_bubble_radius", c.bubbleRadius)
            .putInt("c_name_color", c.nameColor)
            .putLong("c_wallpaper", c.wallpaper)
            .putInt("c_list_lines", c.listLines)
            .putBoolean("c_search_top", c.searchOnTop)
            .putInt("c_swipe", c.swipeAction.ordinal)
            .putBoolean("c_night_auto", c.nightAuto)
            .putBoolean("c_in_app_browser", c.inAppBrowser)
            .putBoolean("c_media_tap_flip", c.mediaTapFlip)
            .putBoolean("c_raise_listen", c.raiseToListen)
            .putBoolean("c_raise_speak", c.raiseToSpeak)
            .putBoolean("c_pause_record", c.pauseMusicOnRecord)
            .putBoolean("c_pause_media", c.pauseMusicOnMedia)
            .putInt("c_mic", c.microphone.ordinal)
            .putBoolean("c_direct_share", c.directShare)
            .putBoolean("c_sensitive", c.showSensitive)
            .putBoolean("c_enter_send", c.enterToSend)
            .putInt("c_distance", c.distanceUnit.ordinal)
            .putInt("c_bubble_opacity", c.bubbleOpacity)
            .putBoolean("c_bubble_blur", c.bubbleBlur)
            .apply()
    }

    // Контакты и секретные чаты.
    private val _privacyPrefs = MutableStateFlow(
        PrivacyPrefs(
            syncContacts = prefs.getBoolean("p_sync_contacts", false),
            mapPreview = MapPreview.entries.getOrElse(prefs.getInt("p_map_preview", 0)) { MapPreview.None },
            linkPreview = prefs.getBoolean("p_link_preview", false),
        )
    )
    val privacyPrefs: StateFlow<PrivacyPrefs> = _privacyPrefs.asStateFlow()

    fun updatePrivacyPrefs(transform: (PrivacyPrefs) -> PrivacyPrefs) {
        val p = transform(_privacyPrefs.value)
        _privacyPrefs.value = p
        prefs.edit()
            .putBoolean("p_sync_contacts", p.syncContacts)
            .putInt("p_map_preview", p.mapPreview.ordinal)
            .putBoolean("p_link_preview", p.linkPreview)
            .apply()
    }

    // Данные и память.
    private val _dataPrefs = MutableStateFlow(
        DataPrefs(
            autoMobile = prefs.getBoolean("d_auto_mobile", true),
            autoWifi = prefs.getBoolean("d_auto_wifi", true),
            autoRoaming = prefs.getBoolean("d_auto_roaming", true),
            saveToGalleryPrivate = prefs.getBoolean("d_gallery_private", false),
            saveToGalleryGroups = prefs.getBoolean("d_gallery_groups", false),
            saveToGalleryChannels = prefs.getBoolean("d_gallery_channels", false),
            streamMedia = prefs.getBoolean("d_stream_media", true),
            streamMkv = prefs.getBoolean("d_stream_mkv", false),
            streamAll = prefs.getBoolean("d_stream_all", true),
            callsDataSaving = prefs.getInt("d_calls_saving", 1),
            proxyServer = prefs.getString("d_proxy_server", "").orEmpty(),
            proxyPort = prefs.getString("d_proxy_port", "").orEmpty(),
            proxyUser = prefs.getString("d_proxy_user", "").orEmpty(),
            proxyPass = prefs.getString("d_proxy_pass", "").orEmpty(),
            proxyType = prefs.getString("d_proxy_type", "socks5").orEmpty().ifBlank { "socks5" },
        )
    )
    val dataPrefs: StateFlow<DataPrefs> = _dataPrefs.asStateFlow()

    fun updateDataPrefs(transform: (DataPrefs) -> DataPrefs) {
        val d = transform(_dataPrefs.value)
        _dataPrefs.value = d
        prefs.edit()
            .putBoolean("d_auto_mobile", d.autoMobile)
            .putBoolean("d_auto_wifi", d.autoWifi)
            .putBoolean("d_auto_roaming", d.autoRoaming)
            .putBoolean("d_gallery_private", d.saveToGalleryPrivate)
            .putBoolean("d_gallery_groups", d.saveToGalleryGroups)
            .putBoolean("d_gallery_channels", d.saveToGalleryChannels)
            .putBoolean("d_stream_media", d.streamMedia)
            .putBoolean("d_stream_mkv", d.streamMkv)
            .putBoolean("d_stream_all", d.streamAll)
            .putInt("d_calls_saving", d.callsDataSaving)
            .putString("d_proxy_server", d.proxyServer)
            .putString("d_proxy_port", d.proxyPort)
            .putString("d_proxy_user", d.proxyUser)
            .putString("d_proxy_pass", d.proxyPass)
            .putString("d_proxy_type", d.proxyType)
            .apply()
    }

    // Энергосбережение: выключает размытие панелей и запоминает прежнее значение.
    private val _powerSaving = MutableStateFlow(prefs.getBoolean(KEY_POWER, false))
    val powerSaving: StateFlow<Boolean> = _powerSaving.asStateFlow()

    fun setPowerSaving(enabled: Boolean) {
        if (enabled == _powerSaving.value) return
        if (enabled) {
            prefs.edit().putFloat(KEY_POWER_PREV_BLUR, _glass.value.blurRadius).apply()
            setBlur(0f)
        } else {
            setBlur(prefs.getFloat(KEY_POWER_PREV_BLUR, GlassSettings.DEFAULT_BLUR))
        }
        _powerSaving.value = enabled
        prefs.edit().putBoolean(KEY_POWER, enabled).apply()
    }

    // Режим призрака.
    private val _ghost = MutableStateFlow(
        GhostPrefs(
            enabled = prefs.getBoolean("g_enabled", false),
            readOnAction = prefs.getBoolean("g_read_on_action", true),
        )
    )
    val ghost: StateFlow<GhostPrefs> = _ghost.asStateFlow()

    fun updateGhost(transform: (GhostPrefs) -> GhostPrefs) {
        val g = transform(_ghost.value)
        _ghost.value = g
        prefs.edit()
            .putBoolean("g_enabled", g.enabled)
            .putBoolean("g_read_on_action", g.readOnAction)
            .apply()
    }

    // Режим шпиона.
    private val _spy = MutableStateFlow(
        SpyPrefs(
            saveDeleted = prefs.getBoolean("s_deleted", true),
            saveEdits = prefs.getBoolean("s_edits", true),
            saveInBots = prefs.getBoolean("s_bots", true),
            saveReadDate = prefs.getBoolean("s_read_date", false),
            saveLastOnline = prefs.getBoolean("s_last_online", false),
            saveAttachments = prefs.getBoolean("s_attachments", true),
            attachmentsChannels = prefs.getBoolean("s_attachments_channels", false),
            folderName = prefs.getString("s_folder", SpyPrefs.DEFAULT_FOLDER).orEmpty().ifBlank { SpyPrefs.DEFAULT_FOLDER },
            maxFolderIndex = prefs.getInt("s_max_folder", SpyPrefs.MAX_FOLDER_LABELS.lastIndex)
                .coerceIn(0, SpyPrefs.MAX_FOLDER_LABELS.lastIndex),
        )
    )
    val spyPrefs: StateFlow<SpyPrefs> = _spy.asStateFlow()

    fun updateSpy(transform: (SpyPrefs) -> SpyPrefs) {
        val s = transform(_spy.value)
        _spy.value = s
        prefs.edit()
            .putBoolean("s_deleted", s.saveDeleted)
            .putBoolean("s_edits", s.saveEdits)
            .putBoolean("s_bots", s.saveInBots)
            .putBoolean("s_read_date", s.saveReadDate)
            .putBoolean("s_last_online", s.saveLastOnline)
            .putBoolean("s_attachments", s.saveAttachments)
            .putBoolean("s_attachments_channels", s.attachmentsChannels)
            .putString("s_folder", s.folderName)
            .putInt("s_max_folder", s.maxFolderIndex)
            .apply()
    }

    // Фильтры сообщений.
    private fun loadFilters(): FilterPrefs {
        val patterns = runCatching {
            val arr = JSONArray(prefs.getString("f_patterns", "[]"))
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
        val banned = runCatching {
            val arr = JSONArray(prefs.getString("f_banned", "[]"))
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                ShadowBanned(o.getLong("id"), o.optString("name"))
            }
        }.getOrDefault(emptyList())
        return FilterPrefs(
            enabled = prefs.getBoolean("f_enabled", false),
            sharedInChats = prefs.getBoolean("f_shared", false),
            hideBlocked = prefs.getBoolean("f_hide_blocked", false),
            patterns = patterns,
            shadowBanned = banned,
        )
    }

    private val _filters = MutableStateFlow(loadFilters())
    val filterPrefs: StateFlow<FilterPrefs> = _filters.asStateFlow()

    fun updateFilters(transform: (FilterPrefs) -> FilterPrefs) {
        val f = transform(_filters.value)
        _filters.value = f
        val bannedJson = JSONArray()
        f.shadowBanned.forEach { bannedJson.put(JSONObject().put("id", it.userId).put("name", it.name)) }
        prefs.edit()
            .putBoolean("f_enabled", f.enabled)
            .putBoolean("f_shared", f.sharedInChats)
            .putBoolean("f_hide_blocked", f.hideBlocked)
            .putString("f_patterns", JSONArray(f.patterns).toString())
            .putString("f_banned", bannedJson.toString())
            .apply()
    }

    fun addPattern(pattern: String) {
        val p = pattern.trim()
        if (p.isEmpty()) return
        updateFilters { if (p in it.patterns) it else it.copy(patterns = it.patterns + p) }
    }

    fun removePattern(pattern: String) = updateFilters { it.copy(patterns = it.patterns - pattern) }

    /** Добавляет пользователя в теневой бан и включает фильтры, иначе бан ничего бы не делал. */
    fun addShadowBan(userId: Long, name: String) {
        updateFilters { f ->
            val rest = f.shadowBanned.filterNot { it.userId == userId }
            f.copy(enabled = true, shadowBanned = rest + ShadowBanned(userId, name))
        }
    }

    fun removeShadowBan(userId: Long) =
        updateFilters { f -> f.copy(shadowBanned = f.shadowBanned.filterNot { it.userId == userId }) }

    private val _localPremium = MutableStateFlow(prefs.getBoolean(KEY_LOCAL_PREMIUM, false))
    /** Локальный Premium: статус и звезда только в этом клиенте, сервер Telegram не затрагивается. */
    val localPremium: StateFlow<Boolean> = _localPremium.asStateFlow()

    fun setLocalPremium(enabled: Boolean) {
        _localPremium.value = enabled
        prefs.edit().putBoolean(KEY_LOCAL_PREMIUM, enabled).apply()
    }

    private companion object {
        const val KEY_LOCAL_PREMIUM = "local_premium"
        const val KEY_BLUR = "glass_blur"
        const val KEY_OPACITY = "glass_opacity"
        const val KEY_MODE = "theme_mode"
        const val KEY_DYNAMIC = "theme_dynamic"
        const val KEY_ACCENT = "theme_accent"
        const val KEY_FONT_PATH = "theme_font_path"
        const val KEY_FONT_NAME = "theme_font_name"
        const val KEY_POWER = "power_saving"
        const val KEY_CHAT_GUARD = "chat_guard"
        const val KEY_POWER_PREV_BLUR = "power_prev_blur"
    }
    private val _badge = MutableStateFlow(prefs.getBoolean("yougram_badge", true))
    /** Показывать значок Yougram и ставить метку в своём bio. */
    val badge: StateFlow<Boolean> = _badge.asStateFlow()

    fun setBadge(value: Boolean) {
        _badge.value = value
        prefs.edit().putBoolean("yougram_badge", value).apply()
    }

    private val _banner = MutableStateFlow(
        prefs.getInt("yougram_banner", -1).takeIf { it >= 0 }?.let {
            YougramBanner(palette = it and 15, pattern = (it shr 4) and 15, shape = (it shr 8) and 15)
        },
    )
    /** Собственный баннер профиля; null — без баннера. */
    val banner: StateFlow<YougramBanner?> = _banner.asStateFlow()

    fun setBanner(value: YougramBanner?) {
        _banner.value = value
        val packed = value?.let { (it.palette and 15) or ((it.pattern and 15) shl 4) or ((it.shape and 15) shl 8) } ?: -1
        prefs.edit().putInt("yougram_banner", packed).apply()
    }

    private val _customBanner = MutableStateFlow(prefs.getLong("yougram_banner_custom", 0L))
    /** Версия своей картинки-баннера (0 — нет). Картинка видна только на этом устройстве. */
    val customBanner: StateFlow<Long> = _customBanner.asStateFlow()

    fun setCustomBanner(version: Long) {
        _customBanner.value = version
        prefs.edit().putLong("yougram_banner_custom", version).apply()
    }

    private val _inAppBrowser = MutableStateFlow(prefs.getBoolean("in_app_browser", true))
    /** Открывать ссылки из чатов во встроенном браузере. */
    val inAppBrowser: StateFlow<Boolean> = _inAppBrowser.asStateFlow()

    fun setInAppBrowser(value: Boolean) {
        _inAppBrowser.value = value
        prefs.edit().putBoolean("in_app_browser", value).apply()
    }
}