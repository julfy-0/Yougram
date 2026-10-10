package app.yougram.core.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * true, если бесконечные/декоративные анимации надо отключить: слабое устройство
 * или системная настройка «Удалить анимации» (масштаб анимации = 0).
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        val scale = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        scale == 0f || DeviceProfile.tier(ctx) == DeviceTier.Low
    }
}
