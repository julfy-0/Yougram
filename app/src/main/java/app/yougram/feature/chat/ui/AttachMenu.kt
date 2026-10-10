package app.yougram.feature.chat.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.IntOffset
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
import kotlinx.coroutines.delay
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

private class AttachAction(
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit,
)

/**
 * Меню кнопки «+» в стиле Expressive: цветные «таблетки» действий, которые по очереди выезжают
 * пружинным движением (как пункты FAB Menu).
 * TODO: заменить на FloatingActionButtonMenu / FloatingActionButtonMenuItem, когда подтвердим их наличие в material3 1.4.0.
 */
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
    val c = MaterialTheme.colorScheme
    val actions = listOf(
        AttachAction(Icons.Filled.AttachFile, "Файл", c.primaryContainer, c.onPrimaryContainer, onFile),
        AttachAction(Icons.Filled.Image, "Фото или видео", c.primaryContainer, c.onPrimaryContainer, onMedia),
        AttachAction(Icons.Filled.Poll, "Опрос", c.secondaryContainer, c.onSecondaryContainer, onPoll),
        AttachAction(Icons.Filled.LocationOn, "Геопозиция", c.secondaryContainer, c.onSecondaryContainer, onLocation),
        AttachAction(Icons.Filled.Mic, "Голосовое сообщение", c.tertiaryContainer, c.onTertiaryContainer, onVoice),
        AttachAction(Icons.Filled.Videocam, "Видеосообщение (кружок)", c.tertiaryContainer, c.onTertiaryContainer, onRound),
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { index, action ->
                AttachItem(index, action) { onDismiss(); action.onClick() }
            }
        }
    }
}

@Composable
private fun AttachItem(index: Int, action: AttachAction, onClick: () -> Unit) {
    val lowTier = rememberDeviceTier() == DeviceTier.Low
    val motion = MaterialTheme.motionScheme
    val fadeSpec: FiniteAnimationSpec<Float> = if (lowTier) tween(120) else motion.defaultEffectsSpec()
    val slideSpec: FiniteAnimationSpec<IntOffset> = if (lowTier) tween(150) else motion.defaultSpatialSpec()
    var shown by remember { mutableStateOf(lowTier) }
    LaunchedEffect(Unit) {
        if (!shown) {
            delay(index * 35L)
            shown = true
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(fadeSpec) + slideInVertically(slideSpec) { it / 2 },
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = action.container,
            contentColor = action.content,
        ) {
            Row(
                Modifier.heightIn(min = 56.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(action.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(action.label, style = MaterialTheme.typography.titleMedium)
            }
        }
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