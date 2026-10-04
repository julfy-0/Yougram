package app.yougram.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val mode: ThemeMode = ThemeMode.System,
    val dynamic: Boolean = true,
    /** Индекс в списке акцентов; используется, когда [dynamic] выключен. */
    val accent: Int = 0,
)

/** Локальные настройки уведомлений (применятся, когда появятся push-уведомления). */
data class NotificationPrefs(
    val privateChats: Boolean = true,
    val groups: Boolean = true,
    val channels: Boolean = true,
    val preview: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true,
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

/** Автозагрузка медиа. */
data class DataPrefs(
    val autoPhotos: Boolean = true,
    val autoVideos: Boolean = false,
    val autoFiles: Boolean = false,
    val onlyWifi: Boolean = true,
)

class SettingsRepository(context: Context) {

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
            mode = ThemeMode.entries.getOrElse(prefs.getInt(KEY_MODE, 0)) { ThemeMode.System },
            dynamic = prefs.getBoolean(KEY_DYNAMIC, true),
            accent = prefs.getInt(KEY_ACCENT, 0),
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

    fun resetGlass() {
        setBlur(GlassSettings.DEFAULT_BLUR)
        setOpacity(GlassSettings.DEFAULT_OPACITY)
    }

    // Уведомления.
    private val _notifications = MutableStateFlow(
        NotificationPrefs(
            privateChats = prefs.getBoolean("n_private", true),
            groups = prefs.getBoolean("n_groups", true),
            channels = prefs.getBoolean("n_channels", true),
            preview = prefs.getBoolean("n_preview", true),
            sound = prefs.getBoolean("n_sound", true),
            vibration = prefs.getBoolean("n_vibration", true),
        )
    )
    val notifications: StateFlow<NotificationPrefs> = _notifications.asStateFlow()

    fun updateNotifications(transform: (NotificationPrefs) -> NotificationPrefs) {
        val n = transform(_notifications.value)
        _notifications.value = n
        prefs.edit()
            .putBoolean("n_private", n.privateChats)
            .putBoolean("n_groups", n.groups)
            .putBoolean("n_channels", n.channels)
            .putBoolean("n_preview", n.preview)
            .putBoolean("n_sound", n.sound)
            .putBoolean("n_vibration", n.vibration)
            .apply()
    }

    // Настройки чатов.
    private val _chatPrefs = MutableStateFlow(
        ChatPrefs(
            textSize = prefs.getInt("c_text_size", 16),
            bubbleRadius = prefs.getInt("c_bubble_radius", 24),
            nameColor = prefs.getInt("c_name_color", 3),
            wallpaper = prefs.getLong("c_wallpaper", 0L),
            listLines = prefs.getInt("c_list_lines", 2),
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

    // Автозагрузка медиа.
    private val _dataPrefs = MutableStateFlow(
        DataPrefs(
            autoPhotos = prefs.getBoolean("d_photos", true),
            autoVideos = prefs.getBoolean("d_videos", false),
            autoFiles = prefs.getBoolean("d_files", false),
            onlyWifi = prefs.getBoolean("d_wifi", true),
        )
    )
    val dataPrefs: StateFlow<DataPrefs> = _dataPrefs.asStateFlow()

    fun updateDataPrefs(transform: (DataPrefs) -> DataPrefs) {
        val d = transform(_dataPrefs.value)
        _dataPrefs.value = d
        prefs.edit()
            .putBoolean("d_photos", d.autoPhotos)
            .putBoolean("d_videos", d.autoVideos)
            .putBoolean("d_files", d.autoFiles)
            .putBoolean("d_wifi", d.onlyWifi)
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

    private companion object {
        const val KEY_BLUR = "glass_blur"
        const val KEY_OPACITY = "glass_opacity"
        const val KEY_MODE = "theme_mode"
        const val KEY_DYNAMIC = "theme_dynamic"
        const val KEY_ACCENT = "theme_accent"
        const val KEY_POWER = "power_saving"
        const val KEY_POWER_PREV_BLUR = "power_prev_blur"
    }
}