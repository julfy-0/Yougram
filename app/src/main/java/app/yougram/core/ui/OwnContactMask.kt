package app.yougram.core.ui

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.function.Consumer

const val OWN_PHONE_MASK = "+•• ••• ••• ••••"
const val OWN_USERNAME_MASK = "@••••••••"

/**
 * Скрытие собственных номера телефона и юзернейма в интерфейсе клиента.
 * Скрывается всегда ([hideAlways]) или только пока идёт запись/трансляция экрана ([hideOnRecord] + [recording]).
 *
 * Android 15+: точное определение через WindowManager.addScreenRecordingCallback (разрешение DETECT_SCREEN_RECORDING).
 * Android 12–14: эвристика — любой дополнительный дисплей (виртуальный дисплей записи, Cast, внешний монитор).
 */
object OwnContactMask {
    val hideAlways = MutableStateFlow(false)
    val hideOnRecord = MutableStateFlow(true)
    val recording = MutableStateFlow(false)

    private var recordingCallback: Consumer<Int>? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    fun set(always: Boolean, onRecord: Boolean) {
        hideAlways.value = always
        hideOnRecord.value = onRecord
    }

    fun startWatching(activity: Activity) {
        stopWatching(activity)
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching {
                val wm = activity.windowManager
                val cb = Consumer<Int> { state ->
                    recording.value = state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
                }
                val initial = wm.addScreenRecordingCallback(activity.mainExecutor, cb)
                recordingCallback = cb
                recording.value = initial == WindowManager.SCREEN_RECORDING_STATE_VISIBLE
            }
        } else {
            runCatching {
                val dm = activity.getSystemService(DisplayManager::class.java) ?: return
                val update = { recording.value = dm.displays.any { it.displayId != Display.DEFAULT_DISPLAY } }
                val listener = object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = update()
                    override fun onDisplayRemoved(displayId: Int) = update()
                    override fun onDisplayChanged(displayId: Int) = update()
                }
                dm.registerDisplayListener(listener, null)
                displayListener = listener
                update()
            }
        }
    }

    fun stopWatching(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 35) {
            recordingCallback?.let { cb -> runCatching { activity.windowManager.removeScreenRecordingCallback(cb) } }
            recordingCallback = null
        } else {
            displayListener?.let { l ->
                runCatching { activity.getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(l) }
            }
            displayListener = null
        }
        recording.value = false
    }
}

/** true, если свои номер и юзернейм сейчас нужно скрыть. */
@Composable
fun rememberOwnContactsHidden(): Boolean {
    val always by OwnContactMask.hideAlways.collectAsState()
    val onRecord by OwnContactMask.hideOnRecord.collectAsState()
    val recording by OwnContactMask.recording.collectAsState()
    return always || (onRecord && recording)
}
