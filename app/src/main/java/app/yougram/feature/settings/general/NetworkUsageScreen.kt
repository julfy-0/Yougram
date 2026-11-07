package app.yougram.feature.settings.general

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.yougram.core.i18n.tr
import app.yougram.core.telegram.getOrThrow
import app.yougram.feature.settings.component.ConfirmDialog
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.component.SwitchRow
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryCall
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryFile
import dev.g000sha256.tdl.dto.NetworkType
import dev.g000sha256.tdl.dto.NetworkTypeMobile
import dev.g000sha256.tdl.dto.NetworkTypeMobileRoaming
import dev.g000sha256.tdl.dto.NetworkTypeWiFi
import java.util.Date
import kotlinx.coroutines.launch

/** Трафик по типам сети: отправлено / получено, отдельно звонки. Данные — из TDLib (getNetworkStatistics). */
private data class Traffic(var sent: Long = 0L, var received: Long = 0L) {
    val total get() = sent + received
}

private data class NetworkUsage(
    val sinceDateSec: Int,
    val mobile: Traffic,
    val roaming: Traffic,
    val wifi: Traffic,
    val other: Traffic,
    val calls: Traffic,
    val callSeconds: Double,
)

private fun bucket(type: NetworkType, mobile: Traffic, roaming: Traffic, wifi: Traffic, other: Traffic): Traffic = when (type) {
    is NetworkTypeMobile -> mobile
    is NetworkTypeMobileRoaming -> roaming
    is NetworkTypeWiFi -> wifi
    else -> other
}

private suspend fun loadUsage(client: TdlClient, onlyCurrent: Boolean): NetworkUsage {
    val stats = client.getNetworkStatistics(onlyCurrent = onlyCurrent).getOrThrow()
    val mobile = Traffic(); val roaming = Traffic(); val wifi = Traffic(); val other = Traffic(); val calls = Traffic()
    var seconds = 0.0
    for (e in stats.entries) {
        when (e) {
            is NetworkStatisticsEntryFile -> bucket(e.networkType, mobile, roaming, wifi, other).also {
                it.sent += e.sentBytes; it.received += e.receivedBytes
            }
            is NetworkStatisticsEntryCall -> {
                bucket(e.networkType, mobile, roaming, wifi, other).also { it.sent += e.sentBytes; it.received += e.receivedBytes }
                calls.sent += e.sentBytes; calls.received += e.receivedBytes
                seconds += e.duration
            }
            else -> Unit
        }
    }
    return NetworkUsage(stats.sinceDate, mobile, roaming, wifi, other, calls, seconds)
}

@Composable
fun NetworkUsageScreen(client: TdlClient, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var onlyCurrent by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf<NetworkUsage?>(null) }
    var failed by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    suspend fun refresh() {
        failed = false
        runCatching { loadUsage(client, onlyCurrent) }
            .onSuccess { usage = it }
            .onFailure { failed = true }
    }
    LaunchedEffect(onlyCurrent) { refresh() }

    fun size(b: Long) = Formatter.formatFileSize(context, b)
    fun line(t: Traffic) = tr("↑ {0}  ↓ {1}", size(t.sent), size(t.received))
    fun minutes(sec: Double) = tr("{0} мин", (sec / 60).toInt())

    SettingsPageColumn(contentPadding) {
        SectionLabel("Период")
        SettingGroup {
            item {
                SwitchRow(
                    "Только с запуска",
                    onlyCurrent,
                    { onlyCurrent = it },
                    subtitle = "Иначе — за всё время с последнего сброса",
                )
            }
        }

        val u = usage
        SectionLabel("Трафик по типам сети")
        SettingGroup {
            item { SettingRow("Мобильная сеть", icon = Icons.Filled.CellTower, value = u?.let { size(it.mobile.total) } ?: "…", subtitle = u?.let { line(it.mobile) }) }
            item { SettingRow("Wi-Fi", icon = Icons.Filled.Wifi, value = u?.let { size(it.wifi.total) } ?: "…", subtitle = u?.let { line(it.wifi) }) }
            item { SettingRow("Роуминг", icon = Icons.Filled.SwapVert, value = u?.let { size(it.roaming.total) } ?: "…", subtitle = u?.let { line(it.roaming) }) }
            item { SettingRow("Другая сеть", icon = Icons.Filled.Public, value = u?.let { size(it.other.total) } ?: "…", subtitle = u?.let { line(it.other) }) }
        }

        SectionLabel("Звонки")
        SettingGroup {
            item {
                SettingRow(
                    "Трафик звонков",
                    icon = Icons.Filled.Call,
                    value = u?.let { size(it.calls.total) } ?: "…",
                    subtitle = u?.let { tr("{0} · {1}", line(it.calls), minutes(it.callSeconds)) },
                )
            }
        }

        if (failed) SettingsFootnote("Не удалось получить статистику. Проверьте соединение.")
        else if (u != null && !onlyCurrent && u.sinceDateSec > 0) {
            val date = DateFormat.getMediumDateFormat(context).format(Date(u.sinceDateSec * 1000L))
            SettingsFootnote(tr("Статистика ведётся с {0}.", date))
        }

        SectionLabel("Сброс")
        SettingGroup {
            item { SettingRow("Сбросить статистику", subtitle = "Счётчики начнутся с нуля", icon = Icons.Filled.Delete, onClick = { confirmReset = true }) }
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Сбросить статистику?",
            text = "Счётчики трафика обнулятся. Это не удаляет файлы и сообщения.",
            confirmLabel = "Сбросить",
            onConfirm = {
                confirmReset = false
                scope.launch {
                    runCatching { client.resetNetworkStatistics().getOrThrow() }
                    refresh()
                }
            },
            onDismiss = { confirmReset = false },
        )
    }
}
