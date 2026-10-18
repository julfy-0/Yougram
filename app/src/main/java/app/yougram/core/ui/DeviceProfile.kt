package app.yougram.core.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Класс устройства по железу: от него зависят тяжёлые эффекты (размытие, бесконечные анимации). */
enum class DeviceTier { Low, Mid, High }

object DeviceProfile {
    @Volatile private var cached: DeviceTier? = null

    /**
     * «Улучшенный интерфейс»: пользователь вручную включил размытие и анимации на слабом устройстве.
     * Состояние Compose, поэтому экраны перерисовываются сразу после переключения.
     */
    var enhanced by mutableStateOf(false)

    /** Класс устройства по железу, без учёта ручного включения. */
    fun hardwareTier(context: Context): DeviceTier = cached ?: compute(context.applicationContext).also { cached = it }

    /** Итоговый класс: на слабом устройстве «улучшенный интерфейс» поднимает его до среднего. */
    fun tier(context: Context): DeviceTier {
        val hw = hardwareTier(context)
        return if (hw == DeviceTier.Low && enhanced) DeviceTier.Mid else hw
    }

    /** Максимальная частота ядер в ГГц (0, если система её не отдаёт). */
    private fun maxCpuGhz(): Double = runCatching {
        java.io.File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }.orEmpty()
            .mapNotNull { java.io.File(it, "cpufreq/cpuinfo_max_freq").takeIf { f -> f.canRead() }?.readText()?.trim()?.toLongOrNull() }
            .maxOrNull()?.let { it / 1_000_000.0 } ?: 0.0
    }.getOrDefault(0.0)

    private fun compute(context: Context): DeviceTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        val heapMb = am?.memoryClass ?: 128
        val ghz = maxCpuGhz()
        return when {
            // Бюджетное железо: мало памяти, мало ядер или медленный процессор.
            am?.isLowRamDevice == true || totalGb < 3.5 || cores <= 4 || heapMb < 192 || (ghz > 0.0 && ghz < 2.0) -> DeviceTier.Low
            totalGb >= 7.0 && cores >= 8 && heapMb >= 256 -> DeviceTier.High
            else -> DeviceTier.Mid
        }
    }
}

@Composable
fun rememberDeviceTier(): DeviceTier {
    val ctx = LocalContext.current
    val enhanced = DeviceProfile.enhanced
    return remember(ctx, enhanced) { DeviceProfile.tier(ctx) }
}
