package app.yougram.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.yougram.data.ActiveCall
import app.yougram.data.AudioRoute
import app.yougram.data.CallManager
import app.yougram.data.CallPhase
import app.yougram.data.ChatRepository
import app.yougram.ui.FileAvatar
import kotlinx.coroutines.delay

/** Полноэкранный экран звонка поверх всей навигации; виден, пока есть активный звонок. */
@Composable
fun CallOverlay(manager: CallManager, chats: ChatRepository) {
    val context = LocalContext.current
    val call by manager.call.collectAsState()
    val route by manager.route.collectAsState()
    val bluetooth by manager.bluetoothAvailable.collectAsState()

    // Принять входящий можно только с доступом к микрофону: без него собеседник услышит тишину.
    val acceptPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) manager.accept()
        else Toast.makeText(context, "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }
    val accept: () -> Unit = {
        val c = call
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            manager.accept()
        } else {
            val needed = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                if (c?.isVideo == true) add(Manifest.permission.CAMERA)
            }
            acceptPermissions.launch(needed.toTypedArray())
        }
    }

    val c = call ?: return

    var elapsed by remember(c.startedAtMillis) { mutableStateOf(0) }
    LaunchedEffect(c.startedAtMillis, c.phase) {
        val start = c.startedAtMillis ?: return@LaunchedEffect
        while (c.phase == CallPhase.ACTIVE) {
            elapsed = ((System.currentTimeMillis() - start) / 1000).toInt()
            delay(500)
        }
    }

    val status = when {
        c.phase == CallPhase.ACTIVE -> "%d:%02d".format(elapsed / 60, elapsed % 60)
        c.phase == CallPhase.RINGING && !c.isOutgoing -> if (c.isVideo) "Входящий видеозвонок" else "Входящий звонок"
        else -> c.message ?: ""
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 56.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PulsingAvatar(c, chats, pulsing = c.phase == CallPhase.RINGING || c.phase == CallPhase.CONNECTING)
                Spacer(Modifier.height(12.dp))
                Text(c.title.ifEmpty { "Звонок" }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.phase == CallPhase.ACTIVE && c.emojis.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(c.emojis.joinToString("  "), fontSize = 32.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Сверьте эмодзи с собеседником",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (c.phase == CallPhase.ACTIVE && c.message != null) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        c.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                val incoming = !c.isOutgoing && c.phase == CallPhase.RINGING
                if (c.phase != CallPhase.ENDED) {
                    if (incoming) {
                        CallButton(Icons.Filled.CallEnd, "Отклонить", MaterialTheme.colorScheme.error, Color.White) { manager.hangUp() }
                        CallButton(Icons.Filled.Call, "Принять", Color(0xFF2E9E5B), Color.White) { accept() }
                    } else {
                        CallButton(
                            if (c.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            if (c.muted) "Включить" else "Микрофон",
                            if (c.muted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh,
                            if (c.muted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        ) { manager.setMuted(!c.muted) }
                        if (c.phase == CallPhase.ACTIVE) {
                            val (icon, label) = when (route) {
                                AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp to "Громкая связь"
                                AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth to "Bluetooth"
                                AudioRoute.EARPIECE -> Icons.Filled.PhoneInTalk to "Динамик"
                            }
                            CallButton(
                                icon, label,
                                if (route == AudioRoute.EARPIECE) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
                                if (route == AudioRoute.EARPIECE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                            ) { manager.cycleRoute() }
                        }
                        CallButton(Icons.Filled.CallEnd, "Завершить", MaterialTheme.colorScheme.error, Color.White) { manager.hangUp() }
                    }
                }
            }
        }
    }
}

/** Аватар с расходящимися кругами, пока звонок соединяется или звонит. */
@Composable
private fun PulsingAvatar(c: ActiveCall, chats: ChatRepository, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseProgress",
    )
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .size(128.dp)
                    .scale(1f + progress * 0.6f)
                    .alpha((1f - progress) * 0.35f)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        FileAvatar(
            title = c.title,
            fileId = c.avatarFileId,
            fileState = { id -> chats.fileState(id) },
            size = 128.dp,
        )
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, container: Color, content: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, shape = CircleShape, color = container, contentColor = content, modifier = Modifier.size(68.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
