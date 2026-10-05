package app.yougram.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH }

/** Аудио-режим звонка: фокус, режим связи и выбор устройства (разговорный динамик, громкая связь, Bluetooth). */
class CallAudio(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)

    private val _route = MutableStateFlow(AudioRoute.EARPIECE)
    val route: StateFlow<AudioRoute> = _route.asStateFlow()

    private val _bluetooth = MutableStateFlow(false)
    val bluetoothAvailable: StateFlow<Boolean> = _bluetooth.asStateFlow()

    private var focus: AudioFocusRequest? = null
    private var running = false
    private var video = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = devicesChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = devicesChanged()
    }

    @Synchronized
    fun start(isVideo: Boolean) {
        if (running) return
        running = true
        video = isVideo
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        am.requestAudioFocus(request)
        focus = request
        am.registerAudioDeviceCallback(deviceCallback, null)
        _bluetooth.value = bluetoothDevice() != null
        val fallback = if (isVideo) AudioRoute.SPEAKER else AudioRoute.EARPIECE
        val preferred = if (_bluetooth.value) AudioRoute.BLUETOOTH else fallback
        setRoute(preferred)
        // Bluetooth без разрешения BLUETOOTH_CONNECT не включится: откатываемся на обычный динамик.
        if (_route.value != preferred) setRoute(fallback)
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        runCatching { am.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { am.clearCommunicationDevice() }
        focus?.let { am.abandonAudioFocusRequest(it) }
        focus = null
        am.mode = AudioManager.MODE_NORMAL
        _route.value = AudioRoute.EARPIECE
        _bluetooth.value = false
    }

    fun setRoute(target: AudioRoute) {
        val device = when (target) {
            AudioRoute.SPEAKER -> byType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            AudioRoute.EARPIECE -> byType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
            AudioRoute.BLUETOOTH -> bluetoothDevice()
        } ?: return
        // SecurityException без BLUETOOTH_CONNECT раньше валил приложение прямо во время звонка.
        val ok = runCatching { am.setCommunicationDevice(device) }.getOrDefault(false)
        if (ok) _route.value = target
    }

    /** Переключает по кругу: разговорный динамик → громкая связь → Bluetooth (если подключён и доступен). */
    fun cycle() {
        val order = listOfNotNull(AudioRoute.EARPIECE, AudioRoute.SPEAKER, AudioRoute.BLUETOOTH.takeIf { _bluetooth.value })
        val from = order.indexOf(_route.value)
        for (i in 1..order.size) {
            val next = order[(from + i) % order.size]
            setRoute(next)
            if (_route.value == next) return
        }
    }

    private fun devicesChanged() {
        val hasBluetooth = bluetoothDevice() != null
        val had = _bluetooth.value
        _bluetooth.value = hasBluetooth
        if (!running) return
        when {
            hasBluetooth && !had -> setRoute(AudioRoute.BLUETOOTH)
            !hasBluetooth && _route.value == AudioRoute.BLUETOOTH -> setRoute(if (video) AudioRoute.SPEAKER else AudioRoute.EARPIECE)
        }
    }

    private fun byType(type: Int): AudioDeviceInfo? = am.availableCommunicationDevices.firstOrNull { it.type == type }

    private fun bluetoothDevice(): AudioDeviceInfo? = am.availableCommunicationDevices.firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }
}