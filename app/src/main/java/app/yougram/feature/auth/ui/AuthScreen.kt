package app.yougram.feature.auth.ui

import app.yougram.core.i18n.tr
import androidx.compose.ui.graphics.Color
import app.yougram.feature.splash.ui.HalloweenEvent
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VpnKey
import app.yougram.core.ui.component.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Warning
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import app.yougram.core.i18n.Text
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.feature.auth.data.AuthStep
import app.yougram.feature.auth.data.CodeVia
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    /** Есть другой аккаунт, в который уже входили: показываем «Назад» (null — возвращаться некуда). */
    onBack: (() -> Unit)? = null,
) {
    val realStep by viewModel.step.collectAsState()
    val editPhone by viewModel.editPhone.collectAsState()
    // «Изменить номер» / «Войти по номеру»: поле телефона показываем поверх шага кода или QR.
    // Пришли на экран номера из QR: показываем обычный экран входа (с «Прокси» и «QR-код»), а не режим «изменить номер».
    val fromQr = realStep is AuthStep.QrLogin
    val step = if (editPhone && (realStep is AuthStep.EnterCode || realStep is AuthStep.QrLogin)) AuthStep.EnterPhone else realStep
    val ui by viewModel.ui.collectAsState()
    val proxies by viewModel.proxies.collectAsState()
    val activeProxyId by viewModel.activeProxyId.collectAsState()
    val activeProxy = proxies.firstOrNull { it.id == activeProxyId }
    var showProxy by remember { mutableStateOf(false) }
    var showFeatures by remember { mutableStateOf(false) }
    val backdrop = rememberBackdropState()
    val glassSettings = LocalGlass.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Пружины Expressive: пространственные для движения, «эффектные» для прозрачности.
    val fx = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val moveSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val sizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    // Последний текст ошибки остаётся на экране, пока плашка плавно скрывается.
    var lastError by remember { mutableStateOf("") }
    LaunchedEffect(ui.error) { ui.error?.let { lastError = it } }

    // Системная «назад» на экране номера, открытом из шага кода или QR, возвращает на этот шаг.
    androidx.activity.compose.BackHandler(enabled = editPhone) { viewModel.cancelEditPhone() }

    Box(Modifier.fillMaxSize()) {
        AuthBackground(Modifier.backdropSource(backdrop))

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
                val halloween = HalloweenEvent.isActive()
                Text(
                    "Yougram",
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    color = if (halloween) Color(0xFFFF8A1F) else Color.Unspecified,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (halloween) "🎃 Happy Halloween! Telegram, настроенный под вас"
                    else "Telegram, настроенный под вас",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(32.dp))

                AuthGlassCard(backdrop) {
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
                                ) { LoadingIndicator(contained = true) }

                                AuthStep.MissingCredentials -> AuthNotice(
                                    icon = Icons.Filled.Warning,
                                    title = "Не заданы ключи API",
                                    text = tr("1. Получите TG_API_ID и TG_API_HASH на my.telegram.org (API development tools).\n") +
                                            tr("2. Впишите их в local.properties в корне проекта.\n") +
                                            tr("3. Пересоберите и запустите приложение."),
                                )

                                AuthStep.EnterPhone -> {
                                    val inner = 6.dp
                                    val outer = 28.dp
                                    AuthInputStep(
                                        kind = AuthFieldKind.Phone,
                                        title = "Вход",
                                        subtitle = "Введите номер телефона",
                                        label = "Номер телефона",
                                        hint = "В международном формате, например +380…",
                                        busy = ui.busy,
                                        onEdit = viewModel::clearError,
                                        onSubmit = viewModel::submitPhone,
                                        bottomContent = if (editPhone && !fromQr) null else ({
                                            // Три кнопки вплотную (connected group M3): «Далее» сверху, «Прокси» и «QR-код» под ней.
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                FilledTonalButton(
                                                    onClick = { showProxy = true },
                                                    modifier = Modifier.weight(1f).height(52.dp),
                                                    shape = RoundedCornerShape(inner, inner, inner, outer),
                                                ) {
                                                    androidx.compose.material3.Icon(
                                                        Icons.Filled.VpnKey,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(if (activeProxy == null) "Прокси" else "Прокси •")
                                                }
                                                FilledTonalButton(
                                                    onClick = viewModel::startQr,
                                                    enabled = !ui.busy,
                                                    modifier = Modifier.weight(1f).height(52.dp),
                                                    shape = RoundedCornerShape(inner, inner, outer, inner),
                                                ) {
                                                    androidx.compose.material3.Icon(
                                                        Icons.Filled.QrCodeScanner,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text("QR-код")
                                                }
                                            }
                                        }),
                                    )
                                    if (editPhone && !fromQr) {
                                        Spacer(Modifier.height(8.dp))
                                        TextButton(onClick = viewModel::cancelEditPhone, enabled = !ui.busy) { Text("Отмена") }
                                    }
                                }

                                is AuthStep.QrLogin -> {
                                    AuthQrStep(link = s.link)
                                    Spacer(Modifier.height(4.dp))
                                    TextButton(onClick = viewModel::startEditPhone, enabled = !ui.busy) {
                                        Text("Войти по номеру телефона")
                                    }
                                }

                                is AuthStep.EnterCode -> {
                                    AuthInputStep(
                                        kind = AuthFieldKind.Code,
                                        title = "Код подтверждения",
                                        subtitle = (when (s.via) {
                                            CodeVia.Telegram -> tr("Мы отправили его в приложение Telegram на другом вашем устройстве. Нет другого устройства — запросите SMS ниже")
                                            CodeVia.Sms -> tr("Мы отправили его SMS-сообщением")
                                            CodeVia.Call -> tr("Вам позвонят и продиктуют код")
                                            CodeVia.MissedCall -> tr("Вам поступит пропущенный звонок — введите последние цифры номера звонившего")
                                            CodeVia.Fragment -> tr("Код отправлен через Fragment (раздел «Мои номера» на fragment.com)")
                                            CodeVia.Firebase -> tr("Telegram отправил код через Google — неофициальные клиенты его получить не могут. Запросите SMS ниже или войдите по QR-коду")
                                            CodeVia.Unknown -> tr("Мы отправили код на ваш номер")
                                        }) + if (s.phoneHint.isNotBlank()) "\n" + tr("Номер: {0}", s.phoneHint) else "",
                                        label = "Код",
                                        busy = ui.busy,
                                        onEdit = viewModel::clearError,
                                        onSubmit = viewModel::submitCode,
                                        bottomContent = {
                                            // Как на шаге номера: «Далее» сверху, под ней две кнопки вплотную (connected group M3).
                                            val inner = 6.dp
                                            val outer = 28.dp
                                            val pad = PaddingValues(horizontal = 12.dp)
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                FilledTonalButton(
                                                    onClick = viewModel::startEditPhone,
                                                    enabled = !ui.busy,
                                                    modifier = Modifier.weight(1f).height(52.dp),
                                                    shape = RoundedCornerShape(inner, inner, inner, outer),
                                                    contentPadding = pad,
                                                ) {
                                                    androidx.compose.material3.Icon(
                                                        Icons.Filled.Edit,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Изменить номер")
                                                }
                                                FilledTonalButton(
                                                    onClick = viewModel::startQr,
                                                    enabled = !ui.busy,
                                                    modifier = Modifier.weight(1f).height(52.dp),
                                                    shape = RoundedCornerShape(inner, inner, outer, inner),
                                                    contentPadding = pad,
                                                ) {
                                                    androidx.compose.material3.Icon(
                                                        Icons.Filled.QrCodeScanner,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("QR-код")
                                                }
                                            }
                                        },
                                    )
                                    // Код приходит не всегда в Telegram: даём запросить его повторно (SMS / звонок)
                                    // после таймаута, который назвал сервер.
                                    var left by remember(s.timeoutSec, s.canResend) { mutableIntStateOf(s.timeoutSec) }
                                    LaunchedEffect(s.timeoutSec, s.canResend) {
                                        while (left > 0) {
                                            delay(1_000)
                                            left--
                                        }
                                    }
                                    if (s.canResend) {
                                        TextButton(onClick = viewModel::resendCode, enabled = left <= 0 && !ui.busy) {
                                            Text(
                                                if (left > 0) tr("Запросить код ещё раз через {0} с", left)
                                                else "Не пришёл код? Отправить ещё раз",
                                            )
                                        }
                                    }
                                    // Опечатка в номере — главная причина «код не приходит»: «Изменить номер» и «QR-код» — в группе кнопок выше.
                                }

                                is AuthStep.EnterPassword -> AuthInputStep(
                                    kind = AuthFieldKind.Password,
                                    title = "Облачный пароль",
                                    subtitle = "Включена двухэтапная проверка",
                                    label = "Пароль",
                                    hint = s.hint.takeIf { it.isNotEmpty() }?.let { tr("Подсказка: {0}", it) },
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
                TextButton(onClick = { showFeatures = true }) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Что умеет Yougram")
                }
                if (!(step == AuthStep.EnterPhone && !editPhone)) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { showProxy = true }) {
                        Text(
                            if (activeProxy == null) "Прокси" else tr("Прокси: {0}", activeProxy.title),
                        )
                    }
                }
            }
        }

        // Размытие фона под системными барами, как на остальных экранах.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topInset)
                .glass(backdrop, glassSettings, RectangleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(bottomInset)
                .glass(backdrop, glassSettings, RectangleShape),
        )

        if (onBack != null) {
            // Системная кнопка «назад» тоже отменяет добавление аккаунта.
            androidx.activity.compose.BackHandler(enabled = !editPhone, onBack = onBack)
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp),
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                )
            }
        }

        if (showFeatures) {
            AuthFeaturesSheet(onDismiss = { showFeatures = false })
        }

        if (showProxy) {
            ProxyManagerDialog(
                proxies = proxies,
                activeId = activeProxyId,
                onSelect = viewModel::selectProxy,
                onSave = viewModel::saveProxy,
                onDelete = viewModel::deleteProxy,
                onDismiss = { showProxy = false },
            )
        }
    }
}