package app.yougram.core.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Класс устройства по железу: от него зависят тяжёлые эффекты (размытие, бесконечные анимации). */
enum class DeviceTier { Low, Mid, High }

object DeviceProfile {
    @Volatile private var cached: DeviceTier? = null

    fun tier(context: Context): DeviceTier = cached ?: compute(context.applicationContext).also { cached = it }

    private fun compute(context: Context): DeviceTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        val heapMb = am?.memoryClass ?: 128
        return when {
            am?.isLowRamDevice == true || totalGb < 3.5 || cores <= 4 || heapMb < 192 -> DeviceTier.Low
            totalGb >= 7.0 && cores >= 8 && heapMb >= 256 -> DeviceTier.High
            else -> DeviceTier.Mid
        }
    }
}

@Composable
fun rememberDeviceTier(): DeviceTier {
    val ctx = LocalContext.current
    return remember(ctx) { DeviceProfile.tier(ctx) }
}
