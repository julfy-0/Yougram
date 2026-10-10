@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.security.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import app.yougram.core.ui.component.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.yougram.feature.security.data.AppLock
import app.yougram.feature.security.data.LockType
import app.yougram.feature.security.data.VerifyResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Оборачивает всё приложение: пока оно заблокировано, поверх рисуется экран блокировки.
 * Содержимое остаётся в композиции (чтобы сохранился стек навигации), но скрыто от касаний и экранных читалок.
 */
@Composable
fun LockGate(appLock: AppLock, content: @Composable () -> Unit) {
    val locked by appLock.locked.collectAsState()
    val focusManager = LocalFocusManager.current
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(locked) { if (locked) focusManager.clearFocus(force = true) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (locked) Modifier.clearAndSetSemantics {} else Modifier),
        ) { content() }
        AnimatedVisibility(
            visible = locked,
            enter = fadeIn(effects) + scaleIn(initialScale = 0.94f, animationSpec = spatial),
            exit = fadeOut(effects) + scaleOut(targetScale = 0.94f, animationSpec = spatial),
        ) {
            LockScreen(appLock)
        }
    }
}

@Composable
private fun LockScreen(appLock: AppLock) {
    val settings by appLock.settings.collectAsState()
    val lockedUntil by appLock.lockoutUntil.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var autoTried by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val remainingSec = ((lockedUntil - now + 999) / 1000).toInt().coerceAtLeast(0)
    val lockedOut = remainingSec > 0

    fun tryBiometric() {
        if (settings.biometric) {
            showBiometricPrompt(context, appLock, "Разблокировка Yougram", onSuccess = { appLock.unlockByBiometric() })
        }
    }

    // Отпечаток предлагается один раз при появлении экрана; дальше — кнопкой.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!autoTried) {
            autoTried = true
            tryBiometric()
        }
    }

    // «Назад» на экране блокировки сворачивает приложение, а не двигает навигацию под ним.
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    fun submit(secret: String) {
        scope.launch {
            when (appLock.verify(secret)) {
                VerifyResult.Success -> Unit
                VerifyResult.Wrong -> {
                    message = "Неверный код"
                    isError = true
                    pin = ""
                }
                is VerifyResult.LockedOut -> {
                    message = null
                    isError = true
                    pin = ""
                }
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = scheme.background) {
        // Прокрутка нужна на маленьких экранах и при крупном шрифте; по центру содержимое остаётся, пока оно помещается.
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
            val minHeight = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(if (isError) scheme.errorContainer else scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (isError) scheme.onErrorContainer else scheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Yougram заблокирован", style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                val hint = when {
                    lockedOut -> "Слишком много попыток. Повторите через $remainingSec с"
                    message != null -> message.orEmpty()
                    settings.type == LockType.Pattern -> "Нарисуйте графический ключ"
                    else -> "Введите пин-код"
                }
                Surface(
                    shape = CircleShape,
                    color = if (isError) scheme.errorContainer else scheme.surfaceContainerHigh,
                    contentColor = if (isError) scheme.onErrorContainer else scheme.onSurfaceVariant,
                ) {
                    Text(
                        hint,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))

                if (settings.type == LockType.Pattern) {
                    PatternPad(
                        onComplete = { secret ->
                            if (secret.split(",").size < 4) {
                                message = "Слишком короткий ключ"
                                isError = true
                            } else {
                                submit(secret)
                            }
                        },
                        modifier = Modifier.size(280.dp),
                        enabled = !lockedOut,
                        isError = isError,
                        onStart = {
                            isError = false
                            message = null
                        },
                    )
                    if (settings.biometric) {
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { tryBiometric() }) { Text("Отпечаток или лицо") }
                    }
                } else {
                    PinEntry(
                        value = pin,
                        onValueChange = {
                            pin = it
                            isError = false
                            message = null
                        },
                        onSubmit = { submit(pin) },
                        enabled = !lockedOut,
                        isError = isError,
                        extraKey = if (settings.biometric) {
                            {
                                IconButton(onClick = { tryBiometric() }) {
                                    Icon(Icons.Filled.Fingerprint, contentDescription = "Отпечаток или лицо")
                                }
                            }
                        } else null,
                    )
                }
            }
        }
    }
}