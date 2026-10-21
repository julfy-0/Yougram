package app.yougram.feature.calls.ui

import app.yougram.core.i18n.tr
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.yougram.core.settings.GlassSettings
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.glass.BackdropState
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.glass
import app.yougram.feature.calls.data.ActiveCall
import app.yougram.feature.calls.data.AudioRoute
import app.yougram.feature.calls.data.CallManager
import app.yougram.feature.calls.data.CallPhase
import app.yougram.feature.calls.data.TgCallsEngine
import app.yougram.feature.chat.data.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Полноэкранный экран видео/аудио звонка: кнопки с подписями и анимации появления, нажатий и смены состояний. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CallOverlay(manager: CallManager, chats: ChatRepository, backdrop: BackdropState) {
    val context = LocalContext.current
    val call by manager.call.collectAsState()
    val route by manager.route.collectAsState()

    fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // Какой ответ выбрал пользователь, пока шёл запрос разрешений («Видео» или «Голос»).
    var pendingVideo by remember { mutableStateOf(false) }
    val acceptPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (has(Manifest.permission.RECORD_AUDIO)) {
            // Без разрешения на камеру принимаем звонок без видео, а не роняем его.
            manager.accept(withVideo = pendingVideo && has(Manifest.permission.CAMERA))
        } else {
            Toast.makeText(context, tr("Нужен доступ к микрофону"), Toast.LENGTH_SHORT).show()
        }
    }

    val requestPermissions: (Boolean) -> Unit = { withVideo ->
        // Bluetooth не обязателен: без него звонок идёт через динамик, поэтому его отказ не должен
        // заново открывать системное окно при каждом ответе.
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (withVideo) add(Manifest.permission.CAMERA)
        }
        if (required.all(::has)) {
            manager.accept(withVideo = withVideo)
        } else {
            pendingVideo = withVideo
            val ask = required.filterNot(::has) +
                    listOfNotNull(Manifest.permission.BLUETOOTH_CONNECT.takeUnless(::has))
            acceptPermissions.launch(ask.toTypedArray())
        }
    }

    val remoteVideoFlow = remember(manager.engine) {
        (manager.engine as? TgCallsEngine)?.remoteVideoActive ?: MutableStateFlow(false)
    }
    val remoteVideo by remoteVideoFlow.collectAsState()
    val frontFlow = remember(manager.engine) {
        (manager.engine as? TgCallsEngine)?.frontCamera ?: MutableStateFlow(true)
    }
    val frontCamera by frontFlow.collectAsState()

    // Последний звонок держим, чтобы экран успел проиграть анимацию закрытия.
    var lastCall by remember { mutableStateOf<ActiveCall?>(null) }
    if (call != null) lastCall = call

    val base = LocalGlass.current
    val overlayGlass = remember(base) {
        GlassSettings(blurRadius = maxOf(base.blurRadius, 40f), opacity = 0.55f)
    }

    MaterialExpressiveTheme(motionScheme = MotionScheme.expressive()) {
        AnimatedVisibility(
            visible = call != null,
            enter = fadeIn(tween(260)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 10 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(220)) { it / 10 },
        ) {
            val c = lastCall ?: return@AnimatedVisibility
            CallContent(
                c = c,
                manager = manager,
                chats = chats,
                backdrop = backdrop,
                glass = overlayGlass,
                route = route,
                remoteVideo = remoteVideo,
                frontCamera = frontCamera,
                onAccept = requestPermissions,
            )
        }
    }
}

@Composable
private fun CallContent(
    c: ActiveCall,
    manager: CallManager,
    chats: ChatRepository,
    backdrop: BackdropState,
    glass: GlassSettings,
    route: AudioRoute,
    remoteVideo: Boolean,
    frontCamera: Boolean,
    onAccept: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
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
    // Ключ смены текста: таймер не перерисовывается каждую секунду, анимируется только смена этапа.
    val statusKey = if (c.phase == CallPhase.ACTIVE) "timer" else status

    Box(
        Modifier
            .fillMaxSize()
            .glass(backdrop, glass, RectangleShape, scheme.surfaceContainerLowest)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        // 1. Удалённый видеопоток. Флаг «собеседник шлёт видео» приходит раньше кадров (а иногда кадры не
        // приходят вовсе), поэтому аватар прячем только после первого реально отрисованного кадра.
        val wantRemote = c.phase == CallPhase.ACTIVE && remoteVideo
        var remoteFrameShown by remember(c.id) { mutableStateOf(false) }
        LaunchedEffect(wantRemote) { if (!wantRemote) remoteFrameShown = false }
        val showRemote = wantRemote && remoteFrameShown
        if (wantRemote) {
            VideoSurface(
                videoSinkSetter = { sink -> (manager.engine as? TgCallsEngine)?.remoteVideoSink = sink },
                modifier = Modifier.fillMaxSize(),
                onFirstFrame = { remoteFrameShown = true },
            )
        }

        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // 2. Аватар, имя и статус: при видео плавно уходят, пока нет кадра — на экране.
            AnimatedVisibility(
                visible = !showRemote,
                enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.92f, animationSpec = tween(250)),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Column(
                    Modifier.padding(top = 56.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CallAvatar(c, chats)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        c.title.ifEmpty { "Звонок" },
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    AnimatedContent(
                        targetState = statusKey,
                        transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(160))) },
                        label = "callStatus",
                    ) {
                        if (status.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = if (c.phase == CallPhase.ACTIVE) scheme.primaryContainer else scheme.secondaryContainer,
                                contentColor = if (c.phase == CallPhase.ACTIVE) scheme.onPrimaryContainer else scheme.onSecondaryContainer,
                            ) {
                                Text(
                                    status,
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = (c.phase == CallPhase.ACTIVE || c.phase == CallPhase.CONNECTING) &&
                                c.message == null && c.emojis.isNotEmpty(),
                        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(24.dp))
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurface,
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(c.emojis.joinToString("  "), fontSize = 32.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Сверьте эмодзи с собеседником",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Своё видео: плавно появляется, перетаскивается и пружинно возвращается в угол.
            AnimatedVisibility(
                visible = c.isVideo && c.phase == CallPhase.ACTIVE,
                enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.8f, animationSpec = tween(250)),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                val scope = rememberCoroutineScope()
                val offsetX = remember { Animatable(0f) }
                val offsetY = remember { Animatable(0f) }
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                        .padding(16.dp)
                        .size(110.dp, 160.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .border(2.dp, scheme.surfaceContainerHighest, RoundedCornerShape(28.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                                    scope.launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessLow)) }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        offsetX.snapTo(offsetX.value + dragAmount.x)
                                        offsetY.snapTo(offsetY.value + dragAmount.y)
                                    }
                                },
                            )
                        },
                ) {
                    VideoSurface(
                        videoSinkSetter = { sink -> (manager.engine as? TgCallsEngine)?.localVideoSink = sink },
                        isOverlay = true,
                        mirror = frontCamera,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // 4. Кнопки (M3 Expressive): входящий — связанная группа сегментов, активный — панель переключателей и широкая «Завершить».
            val incoming = !c.isOutgoing && c.phase == CallPhase.RINGING
            val green = Color(0xFF2EB85C)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = incoming,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { it / 4 })
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "callControls",
                ) { isIncoming ->
                    if (isIncoming) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CallSegmentButton(
                                icon = Icons.Filled.CallEnd,
                                label = "Отклонить",
                                onClick = { manager.hangUp() },
                                container = scheme.error,
                                content = scheme.onError,
                                modifier = Modifier.weight(1f),
                                leftEdge = true,
                            )
                            CallSegmentButton(
                                icon = Icons.Filled.Call,
                                label = "Голос",
                                onClick = { onAccept(false) },
                                container = green,
                                content = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                            CallSegmentButton(
                                icon = Icons.Filled.Videocam,
                                label = "Видео",
                                onClick = { onAccept(true) },
                                container = green,
                                content = Color.White,
                                modifier = Modifier.weight(1f),
                                rightEdge = true,
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(36.dp),
                                color = scheme.surfaceContainer.copy(alpha = 0.92f),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    CallActionButton(
                                        icon = if (c.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                        label = if (c.muted) "Включить" else "Микрофон",
                                        onClick = { manager.setMuted(!c.muted) },
                                        active = c.muted,
                                    )
                                    CallActionButton(
                                        icon = if (c.isVideo) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                        label = "Камера",
                                        onClick = { manager.toggleCamera(!c.isVideo) },
                                        active = c.isVideo,
                                    )
                                    AnimatedVisibility(
                                        visible = c.phase == CallPhase.ACTIVE,
                                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.6f) + expandHorizontally(),
                                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.6f) + shrinkHorizontally(),
                                    ) {
                                        val (routeIcon, routeLabel) = when (route) {
                                            AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp to "Динамик"
                                            AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth to "Bluetooth"
                                            AudioRoute.EARPIECE -> Icons.Filled.PhoneInTalk to "Разговор"
                                        }
                                        CallActionButton(
                                            icon = routeIcon,
                                            label = routeLabel,
                                            onClick = { manager.cycleRoute() },
                                            active = route != AudioRoute.EARPIECE,
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = c.isVideo,
                                        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.6f) + expandHorizontally(),
                                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.6f) + shrinkHorizontally(),
                                    ) {
                                        CallActionButton(
                                            icon = Icons.Filled.Cameraswitch,
                                            label = "Перевернуть",
                                            onClick = { manager.switchCamera() },
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            CallWideButton(
                                icon = Icons.Filled.CallEnd,
                                label = "Завершить",
                                onClick = { manager.hangUp() },
                                container = scheme.error,
                                content = scheme.onError,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Аватар в форме Cookie на «солнечном» фоне M3 Expressive; ничего не пульсирует и не крутится. */
@Composable
private fun CallAvatar(c: ActiveCall, chats: ChatRepository) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(232.dp)
                .clip(MaterialShapes.Sunny.toShape())
                .background(scheme.secondaryContainer),
        )
        FileAvatar(
            title = c.title,
            fileId = c.avatarFileId,
            fileState = { id -> chats.fileState(id) },
            size = 168.dp,
            shape = MaterialShapes.Cookie9Sided.toShape(),
        )
    }
}
