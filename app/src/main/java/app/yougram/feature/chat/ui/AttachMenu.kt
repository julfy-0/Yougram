package app.yougram.feature.chat.ui

import app.yougram.core.i18n.tr
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
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingActionButtonMenuScope
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.MutableTransitionState
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
    val onClick: () -> Unit,
)

/**
 * Меню кнопки «+» в том же стиле, что и меню сообщения: окно на весь экран с размытием фона,
 * сгруппированный список с крупными внешними и малыми внутренними скруглениями.
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
    val lowTier = rememberDeviceTier() == DeviceTier.Low
    val motion = MaterialTheme.motionScheme
    val fadeSpec: FiniteAnimationSpec<Float> = if (lowTier) tween(120) else motion.defaultEffectsSpec()
    val scaleSpec: FiniteAnimationSpec<Float> = if (lowTier) tween(150) else motion.defaultSpatialSpec()
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    val actions = listOf(
        AttachAction(Icons.Filled.AttachFile, tr("Файл"), onFile),
        AttachAction(Icons.Filled.Image, tr("Фото или видео"), onMedia),
        AttachAction(Icons.Filled.Poll, tr("Опрос"), onPoll),
        AttachAction(Icons.Filled.LocationOn, tr("Геопозиция"), onLocation),
        AttachAction(Icons.Filled.Mic, tr("Голосовое сообщение"), onVoice),
        AttachAction(Icons.Filled.Videocam, tr("Видеосообщение (кружок)"), onRound),
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        if (!lowTier) DialogWindowBlur(radius = 28.dp, dim = 0.30f) else DialogWindowBlur(radius = 0.dp, dim = 0.45f)
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = appear,
                enter = fadeIn(fadeSpec) + scaleIn(scaleSpec, initialScale = 0.85f),
            ) {
                Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    actions.forEachIndexed { index, action ->
                        val shape = RoundedCornerShape(
                            topStart = if (index == 0) 24.dp else 6.dp,
                            topEnd = if (index == 0) 24.dp else 6.dp,
                            bottomStart = if (index == actions.lastIndex) 24.dp else 6.dp,
                            bottomEnd = if (index == actions.lastIndex) 24.dp else 6.dp,
                        )
                        Surface(
                            onClick = { onDismiss(); action.onClick() },
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(16.dp))
                                Text(action.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
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
                                    tr("Вариант {0}", index + 1),
                                    Modifier.weight(1f),
                                )
                                if (options.size > 2) {
                                    IconButton(onClick = { options.removeAt(index) }) {
                                        Icon(Icons.Filled.Close, contentDescription = tr("Убрать вариант"))
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