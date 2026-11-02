@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.miniapp.ui

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import app.yougram.core.ui.component.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.ui.component.Button
import app.yougram.core.ui.component.LinearWavyProgressIndicator
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.core.ui.component.TextButton
import app.yougram.core.ui.component.openExternally
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.chat.data.MiniAppLaunch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONTokener

/** Состояние главной / второй кнопки мини-приложения (MainButton / SecondaryButton). */
private data class BottomBtn(
    val visible: Boolean = false,
    val active: Boolean = true,
    val progress: Boolean = false,
    val text: String = "",
    val color: Color? = null,
    val textColor: Color? = null,
    val position: String = "left",
)

private data class PopupButton(val id: String, val type: String, val text: String?)

private data class PopupSpec(val title: String?, val message: String, val buttons: List<PopupButton>)

private fun parseHex(s: String?): Color? {
    if (s.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(s)) }.getOrNull()
}

private fun JSONObject.toBtn() = BottomBtn(
    visible = optBoolean("is_visible"),
    active = optBoolean("is_active", true),
    progress = optBoolean("is_progress_visible"),
    text = optString("text"),
    color = parseHex(optString("color")),
    textColor = parseHex(optString("text_color")),
    position = optString("position", "left"),
)

/**
 * Мини-приложение в стиле Material 3: панель со скруглёнными верхними углами, TopAppBar, кнопки приложения
 * как M3-кнопки «connected group», диалоги — M3 AlertDialog. Тема передаётся из текущей схемы цветов.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniAppScreen(
    request: MiniAppRequest,
    repository: ChatRepository,
    openLink: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val sysInsets = WindowInsets.systemBars
    val scope = rememberCoroutineScope()

    var botName by remember { mutableStateOf(request.botName) }
    var launch by remember { mutableStateOf<MiniAppLaunch?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }

    var mainBtn by remember { mutableStateOf(BottomBtn()) }
    var secBtn by remember { mutableStateOf(BottomBtn()) }
    var backVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var headerColor by remember { mutableStateOf<Color?>(null) }
    var bgColor by remember { mutableStateOf<Color?>(null) }
    var bottomColor by remember { mutableStateOf<Color?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var closeAsk by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf<PopupSpec?>(null) }
    var writeAsk by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var viewportDp by remember { mutableFloatStateOf(0f) }

    val headerBg = headerColor ?: scheme.surfaceContainer
    val bodyBg = bgColor ?: scheme.surface
    val bottomBg = bottomColor ?: scheme.surfaceContainer

    LaunchedEffect(request) {
        if (botName.isBlank()) botName = runCatching { repository.userName(request.botUserId) }.getOrDefault("")
    }

    // Получаем адрес у TDLib; при «Обновить» (reloadKey) открываем приложение заново.
    LaunchedEffect(request, reloadKey) {
        launch = null
        error = null
        pageError = null
        loading = true
        val theme = scheme.toThemeParameters()
        val result = if (request.fromLink) {
            repository.miniAppFromLink(request.chatId, request.botUserId, request.shortName, request.startParameter, theme)
        } else {
            repository.openMiniApp(request.chatId, request.botUserId, request.url, theme)
        }
        result
            .onSuccess { launch = it }
            .onFailure { error = it.message ?: tr("Не удалось открыть мини-приложение") }
    }
    DisposableEffect(launch) {
        val id = launch?.launchId ?: 0L
        onDispose { repository.closeMiniApp(id) }
    }
    LaunchedEffect(launch, web) {
        val l = launch ?: return@LaunchedEffect
        web?.loadUrl(l.url)
        // Запасной выход, если приложение так и не прислало web_app_ready.
        delay(8_000)
        loading = false
    }
    LaunchedEffect(scheme, web) {
        web?.sendEvent("theme_changed", JSONObject().put("theme_params", scheme.toThemeJson()))
    }

    fun userClose() {
        if (confirmClose) closeAsk = true else onClose()
    }

    fun sendViewport() {
        web?.sendEvent(
            "viewport_changed",
            JSONObject().put("height", viewportDp.toDouble()).put("is_state_stable", true).put("is_expanded", true),
        )
    }

    fun sendSafeArea() {
        val insets = sysInsets
        fun dp(px: Int) = with(density) { px.toDp().value.toDouble() }
        val safe = if (fullscreen) {
            JSONObject()
                .put("top", dp(insets.getTop(density)))
                .put("bottom", dp(insets.getBottom(density)))
                .put("left", dp(insets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)))
                .put("right", dp(insets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)))
        } else {
            JSONObject().put("top", 0).put("bottom", 0).put("left", 0).put("right", 0)
        }
        // В полноэкранном режиме сверху лежит плавающая кнопка закрытия.
        val content = JSONObject().put("top", if (fullscreen) 48 else 0).put("bottom", 0).put("left", 0).put("right", 0)
        web?.sendEvent("safe_area_changed", safe)
        web?.sendEvent("content_safe_area_changed", content)
    }

    fun haptic(d: JSONObject) {
        val c = when (d.optString("type")) {
            "impact" -> when (d.optString("impact_style")) {
                "light", "soft" -> HapticFeedbackConstants.CLOCK_TICK
                "heavy", "rigid" -> HapticFeedbackConstants.LONG_PRESS
                else -> HapticFeedbackConstants.KEYBOARD_TAP
            }
            "notification" -> when (d.optString("notification_type")) {
                "success" -> HapticFeedbackConstants.CONFIRM
                "error" -> HapticFeedbackConstants.REJECT
                else -> HapticFeedbackConstants.LONG_PRESS
            }
            "selection_change" -> HapticFeedbackConstants.CLOCK_TICK
            else -> return
        }
        view.performHapticFeedback(c)
    }

    val handle: (String, JSONObject) -> Unit = { type, d ->
        when (type) {
            "web_app_ready" -> loading = false
            "web_app_close" -> onClose()
            "web_app_setup_main_button" -> mainBtn = d.toBtn()
            "web_app_setup_secondary_button" -> secBtn = d.toBtn()
            "web_app_setup_back_button" -> backVisible = d.optBoolean("is_visible")
            "web_app_setup_settings_button" -> settingsVisible = d.optBoolean("is_visible")
            "web_app_request_theme" ->
                web?.sendEvent("theme_changed", JSONObject().put("theme_params", scheme.toThemeJson()))
            "web_app_request_viewport" -> sendViewport()
            "web_app_request_safe_area", "web_app_request_content_safe_area" -> sendSafeArea()
            "web_app_set_header_color" -> headerColor = when (d.optString("color_key")) {
                "bg_color" -> scheme.surface
                "secondary_bg_color" -> scheme.surfaceContainer
                else -> parseHex(d.optString("color"))
            }
            "web_app_set_background_color" -> bgColor = parseHex(d.optString("color"))
            "web_app_set_bottom_bar_color" -> bottomColor = parseHex(d.optString("color"))
            "web_app_setup_closing_behavior" -> confirmClose = d.optBoolean("need_confirmation")
            "web_app_trigger_haptic_feedback" -> haptic(d)
            "web_app_open_popup" -> {
                val arr = d.optJSONArray("buttons")
                val buttons = buildList {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val b = arr.optJSONObject(i) ?: continue
                            add(PopupButton(b.optString("id"), b.optString("type", "default"), b.optString("text").takeIf { it.isNotEmpty() }))
                        }
                    }
                }.ifEmpty { listOf(PopupButton("", "close", null)) }
                popup = PopupSpec(d.optString("title").takeIf { it.isNotEmpty() }, d.optString("message"), buttons)
            }
            "web_app_open_link" -> {
                val url = d.optString("url")
                if (url.startsWith("https://t.me") || url.startsWith("tg:")) {
                    openLink(url)
                    onClose()
                } else if (url.isNotBlank()) {
                    openExternally(context, url)
                }
            }
            "web_app_open_tg_link" -> {
                openLink("https://t.me" + d.optString("path_full"))
                onClose()
            }
            "web_app_data_send" -> {
                val text = request.buttonText
                if (text != null) {
                    scope.launch {
                        repository.sendMiniAppData(request.botUserId, text, d.optString("data"))
                        onClose()
                    }
                }
            }
            "web_app_read_text_from_clipboard" -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                web?.sendEvent(
                    "clipboard_text_received",
                    JSONObject().put("req_id", d.optString("req_id")).put("data", text ?: JSONObject.NULL),
                )
            }
            "web_app_request_write_access" -> writeAsk = true
            "web_app_request_phone" ->
                web?.sendEvent("phone_requested", JSONObject().put("status", "cancelled"))
            // Платежи в клиенте не поддерживаются: честно сообщаем, что счёт не оплачен.
            "web_app_open_invoice" ->
                web?.sendEvent("invoice_closed", JSONObject().put("slug", d.optString("slug")).put("status", "cancelled"))
            "web_app_request_fullscreen" -> {
                fullscreen = true
                web?.sendEvent("fullscreen_changed", JSONObject().put("is_fullscreen", true))
                sendSafeArea()
            }
            "web_app_exit_fullscreen" -> {
                fullscreen = false
                web?.sendEvent("fullscreen_changed", JSONObject().put("is_fullscreen", false))
                sendSafeArea()
            }
            "web_app_invoke_custom_method" -> {
                val reqId = d.optString("req_id")
                val params = d.opt("params")?.toString() ?: "{}"
                scope.launch {
                    repository.miniAppCustomMethod(request.botUserId, d.optString("method"), params)
                        .onSuccess { r ->
                            val value = runCatching { JSONTokener(r).nextValue() }.getOrDefault(r)
                            web?.sendEvent("custom_method_invoked", JSONObject().put("req_id", reqId).put("result", value))
                        }
                        .onFailure { e ->
                            web?.sendEvent(
                                "custom_method_invoked",
                                JSONObject().put("req_id", reqId).put("error", e.message ?: "METHOD_INVALID"),
                            )
                        }
                }
            }
            else -> Unit
        }
        Unit
    }
    val latest by rememberUpdatedState(handle)
    val latestOpenLink by rememberUpdatedState(openLink)
    val latestClose by rememberUpdatedState(onClose)

    BackHandler {
        if (backVisible) web?.sendEvent("back_button_pressed") else userClose()
    }

    val bottomVisible = mainBtn.visible || secBtn.visible
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .then(if (fullscreen) Modifier else Modifier.statusBarsPadding().padding(top = 12.dp)),
        shape = if (fullscreen) RectangleShape else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = bodyBg,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().imePadding()) {
                if (!fullscreen) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    botName.ifBlank { "Мини-приложение" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Мини-приложение",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { if (backVisible) web?.sendEvent("back_button_pressed") else userClose() }) {
                                Icon(
                                    if (backVisible) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close,
                                    contentDescription = if (backVisible) tr("Назад") else tr("Закрыть"),
                                )
                            }
                        },
                        actions = {
                            if (settingsVisible) {
                                IconButton(onClick = { web?.sendEvent("settings_button_pressed") }) {
                                    Icon(Icons.Filled.Settings, contentDescription = tr("Настройки"))
                                }
                            }
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = tr("Ещё"))
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Обновить") },
                                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            reloadKey++
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Открыть в браузере") },
                                        leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            launch?.url?.let { openExternally(context, it.substringBefore('#')) }
                                        },
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = headerBg),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    )
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (!bottomVisible && !fullscreen) Modifier.navigationBarsPadding() else Modifier)
                        .onSizeChanged { size ->
                            val h = with(density) { size.height.toDp().value }
                            if (h != viewportDp) {
                                viewportDp = h
                                sendViewport()
                            }
                        },
                ) {
                    if (launch != null && error == null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setBackgroundColor(bodyBg.toArgb())
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        allowFileAccess = false
                                        allowContentAccess = false
                                        javaScriptCanOpenWindowsAutomatically = false
                                        setSupportMultipleWindows(false)
                                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    }
                                    addJavascriptInterface(MiniAppBridge { t, d -> latest(t, d) }, "TelegramWebviewProxy")
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                            val u = r.url
                                            val scheme0 = u.scheme?.lowercase()
                                            val host = u.host?.lowercase().orEmpty()
                                            return when {
                                                (scheme0 == "http" || scheme0 == "https") &&
                                                    (host == "t.me" || host == "telegram.me") -> {
                                                    latestOpenLink(u.toString())
                                                    latestClose()
                                                    true
                                                }
                                                scheme0 == "http" || scheme0 == "https" -> false
                                                scheme0 == "tg" -> {
                                                    latestOpenLink(u.toString())
                                                    latestClose()
                                                    true
                                                }
                                                else -> {
                                                    runCatching {
                                                        v.context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(u.toString()))
                                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                        )
                                                    }
                                                    true
                                                }
                                            }
                                        }

                                        override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                                            if (r.isForMainFrame) pageError = e.description?.toString() ?: "Ошибка загрузки"
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(v: WebView, newProgress: Int) {
                                            progress = newProgress
                                        }

                                        // Камера и микрофон — только если разрешение уже выдано приложению.
                                        override fun onPermissionRequest(req: PermissionRequest) {
                                            val granted = req.resources.filter { res ->
                                                val perm = when (res) {
                                                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> android.Manifest.permission.CAMERA
                                                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> android.Manifest.permission.RECORD_AUDIO
                                                    else -> null
                                                }
                                                perm != null && androidx.core.content.ContextCompat.checkSelfPermission(ctx, perm) ==
                                                    android.content.pm.PackageManager.PERMISSION_GRANTED
                                            }
                                            if (granted.isEmpty()) req.deny() else req.grant(granted.toTypedArray())
                                        }
                                    }
                                    web = this
                                }
                            },
                            update = { it.setBackgroundColor(bodyBg.toArgb()) },
                            // Уничтожаем именно тот WebView, который уходит из композиции
                            // (раньше DisposableEffect читал уже НОВОЕ значение web и убивал свежесозданный WebView).
                            onRelease = { w ->
                                if (web === w) web = null
                                w.stopLoading()
                                w.destroy()
                            },
                        )
                    }

                    if (loading && error == null && pageError == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(56.dp), contained = true)
                        }
                        if (launch != null) {
                            LinearWavyProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            )
                        }
                    }

                    val failure = error ?: pageError
                    if (failure != null) {
                        Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = scheme.error,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                failure,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { reloadKey++ }) { Text("Повторить") }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = bottomVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    BottomButtons(
                        main = mainBtn,
                        sec = secBtn,
                        background = bottomBg,
                        onMain = { web?.sendEvent("main_button_pressed") },
                        onSecondary = { web?.sendEvent("secondary_button_pressed") },
                    )
                }
            }

            if (fullscreen) {
                FilledTonalIconButton(
                    onClick = { if (backVisible) web?.sendEvent("back_button_pressed") else userClose() },
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                ) {
                    Icon(
                        if (backVisible) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close,
                        contentDescription = tr("Закрыть"),
                    )
                }
            }
        }
    }

    if (closeAsk) {
        AlertDialog(
            onDismissRequest = { closeAsk = false },
            title = { Text("Закрыть приложение?") },
            text = { Text("Несохранённые изменения могут пропасть.") },
            confirmButton = {
                TextButton(onClick = {
                    closeAsk = false
                    onClose()
                }) { Text("Закрыть") }
            },
            dismissButton = { TextButton(onClick = { closeAsk = false }) { Text("Отмена") } },
        )
    }

    if (writeAsk) {
        AlertDialog(
            onDismissRequest = {
                writeAsk = false
                web?.sendEvent("write_access_requested", JSONObject().put("status", "cancelled"))
            },
            title = { Text("Разрешить писать вам?") },
            text = { Text(tr("{0} сможет отправлять вам сообщения.", botName.ifBlank { tr("Бот") })) },
            confirmButton = {
                TextButton(onClick = {
                    writeAsk = false
                    scope.launch {
                        val ok = repository.allowMiniAppWrite(request.botUserId).isSuccess
                        web?.sendEvent(
                            "write_access_requested",
                            JSONObject().put("status", if (ok) "allowed" else "cancelled"),
                        )
                    }
                }) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    writeAsk = false
                    web?.sendEvent("write_access_requested", JSONObject().put("status", "cancelled"))
                }) { Text("Отмена") }
            },
        )
    }

    popup?.let { p ->
        BasicAlertDialog(onDismissRequest = {
            popup = null
            web?.sendEvent("popup_closed")
        }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.padding(24.dp)) {
                    p.title?.let {
                        Text(it, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(p.message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        p.buttons.forEach { b ->
                            val label = b.text ?: when (b.type) {
                                "close" -> "Закрыть"
                                "cancel" -> "Отмена"
                                else -> "OK"
                            }
                            TextButton(onClick = {
                                popup = null
                                web?.sendEvent("popup_closed", JSONObject().put("button_id", b.id))
                            }) {
                                Text(label, color = if (b.type == "destructive") scheme.error else Color.Unspecified)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Кнопки приложения. Две кнопки стоят вплотную (connected group M3): внешние углы крупные, внутренние мелкие,
 * как у клавиатуры бота и экрана входа.
 */
@Composable
private fun BottomButtons(
    main: BottomBtn,
    sec: BottomBtn,
    background: Color,
    onMain: () -> Unit,
    onSecondary: () -> Unit,
) {
    val outer = 28.dp
    val inner = 6.dp
    val both = main.visible && sec.visible
    val row = both && (sec.position == "left" || sec.position == "right")
    val secFirst = both && (sec.position == "top" || sec.position == "left")

    fun shapeFor(first: Boolean): Shape = when {
        !both -> RoundedCornerShape(50)
        row && first -> RoundedCornerShape(outer, inner, inner, outer)
        row -> RoundedCornerShape(inner, outer, outer, inner)
        first -> RoundedCornerShape(outer, outer, inner, inner)
        else -> RoundedCornerShape(inner, inner, outer, outer)
    }

    Surface(color = background) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val mainContent: @Composable (Modifier) -> Unit = { m ->
                MainButton(main, shapeFor(!secFirst), m, onMain)
            }
            val secContent: @Composable (Modifier) -> Unit = { m ->
                SecondaryButton(sec, shapeFor(secFirst), m, onSecondary)
            }
            if (row) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (secFirst) {
                        secContent(Modifier.weight(1f))
                        mainContent(Modifier.weight(1f))
                    } else {
                        mainContent(Modifier.weight(1f))
                        secContent(Modifier.weight(1f))
                    }
                }
            } else {
                if (secFirst) secContent(Modifier.fillMaxWidth())
                if (main.visible) mainContent(Modifier.fillMaxWidth())
                if (both && !secFirst) secContent(Modifier.fillMaxWidth())
                if (!both && sec.visible) secContent(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MainButton(btn: BottomBtn, shape: Shape, modifier: Modifier, onClick: () -> Unit) {
    if (!btn.visible) return
    val scheme = MaterialTheme.colorScheme
    val container = btn.color ?: scheme.primary
    val content = btn.textColor ?: scheme.onPrimary
    Button(
        onClick = onClick,
        enabled = btn.active && !btn.progress,
        shape = shape,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (btn.progress) container else scheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = if (btn.progress) content else scheme.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        if (btn.progress) {
            LoadingIndicator(Modifier.size(24.dp), color = content)
        } else {
            Text(btn.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SecondaryButton(btn: BottomBtn, shape: Shape, modifier: Modifier, onClick: () -> Unit) {
    if (!btn.visible) return
    val scheme = MaterialTheme.colorScheme
    val container = btn.color ?: scheme.secondaryContainer
    val content = btn.textColor ?: scheme.onSecondaryContainer
    FilledTonalButton(
        onClick = onClick,
        enabled = btn.active && !btn.progress,
        shape = shape,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (btn.progress) container else scheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = if (btn.progress) content else scheme.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        if (btn.progress) {
            LoadingIndicator(Modifier.size(24.dp), color = content)
        } else {
            Text(btn.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
