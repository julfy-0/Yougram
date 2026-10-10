package app.yougram.feature.auth.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.yougram.core.ui.component.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.yougram.feature.auth.data.AuthStep

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    val step by viewModel.step.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val proxy by viewModel.proxy.collectAsState()
    var showProxy by remember { mutableStateOf(false) }

    // Пружины Expressive: пространственные для движения, «эффектные» для прозрачности.
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val moveSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val sizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    // Последний текст ошибки остаётся на экране, пока плашка плавно скрывается.
    var lastError by remember { mutableStateOf("") }
    LaunchedEffect(ui.error) { ui.error?.let { lastError = it } }

    Box(Modifier.fillMaxSize()) {
        AuthBackground()

        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val minHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minHeight)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AuthLogo()
                Spacer(Modifier.height(20.dp))
                Text(
                    "Yougram",
                    style = MaterialTheme.typography.displaySmallEmphasized,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Telegram, настроенный под вас",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))

                AuthGlassCard {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            (slideInHorizontally(moveSpec) { it / 3 } + fadeIn(fx))
                                .togetherWith(slideOutHorizontally(moveSpec) { -it / 3 } + fadeOut(fx))
                        },
                        label = "authStepTransition",
                    ) { s ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            when (s) {
                                AuthStep.Loading, AuthStep.Ready -> Box(
                                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) { LoadingIndicator() }

                                AuthStep.MissingCredentials -> AuthNotice(
                                    icon = Icons.Filled.Warning,
                                    title = "Не заданы ключи API",
                                    text = "1. Получите TG_API_ID и TG_API_HASH на my.telegram.org (API development tools).\n" +
                                            "2. Впишите их в local.properties в корне проекта.\n" +
                                            "3. Пересоберите и запустите приложение.",
                                )

                                AuthStep.EnterPhone -> AuthInputStep(
                                    kind = AuthFieldKind.Phone,
                                    title = "Вход",
                                    subtitle = "Введите номер телефона",
                                    label = "Номер телефона",
                                    hint = "В международном формате, например +380…",
                                    busy = ui.busy,
                                    onEdit = viewModel::clearError,
                                    onSubmit = viewModel::submitPhone,
                                )

                                is AuthStep.EnterCode -> AuthInputStep(
                                    kind = AuthFieldKind.Code,
                                    title = "Код подтверждения",
                                    subtitle = "Мы отправили его в Telegram",
                                    label = "Код",
                                    busy = ui.busy,
                                    onEdit = viewModel::clearError,
                                    onSubmit = viewModel::submitCode,
                                )

                                is AuthStep.EnterPassword -> AuthInputStep(
                                    kind = AuthFieldKind.Password,
                                    title = "Облачный пароль",
                                    subtitle = "Включена двухэтапная проверка",
                                    label = "Пароль",
                                    hint = s.hint.takeIf { it.isNotEmpty() }?.let { "Подсказка: $it" },
                                    busy = ui.busy,
                                    onEdit = viewModel::clearError,
                                    onSubmit = viewModel::submitPassword,
                                )

                                is AuthStep.Unsupported -> AuthNotice(
                                    icon = Icons.Filled.Info,
                                    title = "Способ входа пока не поддерживается",
                                    text = s.description,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = ui.error != null,
                        enter = fadeIn(fx) + expandVertically(sizeSpec),
                        exit = fadeOut(fx) + shrinkVertically(sizeSpec),
                    ) {
                        AuthErrorBanner(lastError)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Неофициальный клиент Telegram",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showProxy = true }) {
                    Text(
                        if (proxy.proxyServer.isBlank()) "Прокси" else "Прокси: ${proxy.proxyServer}:${proxy.proxyPort}",
                    )
                }
            }
        }

        if (showProxy) {
            ProxyDialog(
                initial = proxy,
                onConfirm = { type, server, port, user, pass ->
                    viewModel.saveProxy(type, server, port, user, pass)
                    showProxy = false
                },
                onDismiss = { showProxy = false },
            )
        }
    }
}