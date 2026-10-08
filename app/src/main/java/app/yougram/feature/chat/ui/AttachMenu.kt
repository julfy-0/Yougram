package app.yougram.feature.chat.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.yougram.core.ui.component.TextButton
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SwitchRow
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val MaxPollOptions = 10
private const val MaxQuestion = 255
private const val MaxOption = 100

/** Меню кнопки «+» в том же стиле, что и меню сообщения: скруглённая панель с рядами «иконка + название». */
@Composable
fun AttachMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onFile: () -> Unit,
    onMedia: () -> Unit,
    onPoll: () -> Unit,
    onLocation: () -> Unit,
    onVoice: () -> Unit,
    onRound: () -> Unit,
) {
    if (!expanded) return
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.width(260.dp).padding(vertical = 6.dp)) {
                AttachItem(Icons.Filled.AttachFile, "Файл") { onDismiss(); onFile() }
                AttachItem(Icons.Filled.Image, "Фото или видео") { onDismiss(); onMedia() }
                AttachItem(Icons.Filled.Poll, "Опрос") { onDismiss(); onPoll() }
                AttachItem(Icons.Filled.LocationOn, "Геопозиция") { onDismiss(); onLocation() }
                AttachItem(Icons.Filled.Mic, "Голосовое сообщение") { onDismiss(); onVoice() }
                AttachItem(Icons.Filled.Videocam, "Видеосообщение (кружок)") { onDismiss(); onRound() }
            }
        }
    }
}

@Composable
private fun AttachItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Поле внутри сегмента-плашки: без собственного фона и рамки, как строки в настройках. */
@Composable
private fun PlateField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

/**
 * Создание опроса в стиле настроек: разделы с заголовками, сегменты-плашки с крупными внешними
 * и малыми внутренними углами, строки-переключатели. Вопрос до 255, вариантов 2–10 по 100 символов.
 */
@Composable
fun PollDialog(
    onDismiss: () -> Unit,
    onSend: (question: String, options: List<String>, anonymous: Boolean, multiple: Boolean) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var anonymous by remember { mutableStateOf(true) }
    var multiple by remember { mutableStateOf(false) }
    val filled = options.map { it.trim() }.filter { it.isNotEmpty() }
    val valid = question.isNotBlank() && filled.size >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        // Плашки настроек рисуются цветом surfaceContainerHigh, поэтому фон диалога — surface, иначе они сольются.
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Новый опрос") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Вопрос")
                SettingGroup {
                    item {
                        PlateField(question, { question = it.take(MaxQuestion) }, "Задайте вопрос", Modifier.fillMaxWidth())
                    }
                }
                SectionLabel("Варианты ответа")
                SettingGroup {
                    options.forEachIndexed { index, value ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlateField(
                                    value,
                                    { options[index] = it.take(MaxOption) },
                                    "Вариант ${index + 1}",
                                    Modifier.weight(1f),
                                )
                                if (options.size > 2) {
                                    IconButton(onClick = { options.removeAt(index) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Убрать вариант")
                                    }
                                }
                            }
                        }
                    }
                    if (options.size < MaxPollOptions) {
                        item {
                            SettingRow(title = "Добавить вариант", icon = Icons.Filled.Add, onClick = { options.add("") })
                        }
                    }
                }
                SectionLabel("Настройки")
                SettingGroup {
                    item { SwitchRow("Анонимное голосование", anonymous, { anonymous = it }) }
                    item { SwitchRow("Несколько ответов", multiple, { multiple = it }) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSend(question.trim(), filled, anonymous, multiple)
                    onDismiss()
                },
            ) { Text("Отправить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/**
 * Текущая геопозиция без Google Play Services: сначала свежее определение (до 15 с),
 * при неудаче — последняя известная. Нужно разрешение на геолокацию; без него вернёт null.
 */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): Location? = withContext(Dispatchers.Main) {
    val lm = context.getSystemService(LocationManager::class.java) ?: return@withContext null
    fun lastKnown(): Location? = runCatching {
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }.getOrNull()

    val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        ?: return@withContext lastKnown()

    val fresh = runCatching {
        withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
            }
        }
    }.getOrNull()
    fresh ?: lastKnown()
}