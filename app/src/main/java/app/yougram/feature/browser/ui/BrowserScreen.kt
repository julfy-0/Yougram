@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.browser.ui

import app.yougram.core.i18n.tr
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiOff
import app.yougram.core.ui.component.Button
import app.yougram.core.ui.component.LinearWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.yougram.core.ui.component.LocalOpenLink
import app.yougram.core.ui.component.httpsOnly
import app.yougram.core.ui.component.isTelegramUrl
import app.yougram.core.ui.component.openExternally
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.rememberBackdropState

/**
 * Встроенный браузер на WebView. Страница рисуется на весь экран, а верхняя и нижняя панели —
 * стеклянные: под ними содержимое страницы размывается (как в списке чатов и в чате).
 * Доступ к файлам, камере, микрофону и геолокации страницам закрыт, загрузки уходят во внешнее приложение.
 * Ссылки Telegram (t.me, tg://) уходят в клиент, остальные схемы (tel, mailto, intent, market и т.п.) — системе.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(initialUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    // Высоты панелей (px): страница получает такие же отступы сверху и снизу, чтобы её начало и конец не прятались под стеклом.
    var topBarPx by remember { mutableIntStateOf(0) }
    var bottomBarPx by remember { mutableIntStateOf(0) }
    val topBarDp = with(density) { topBarPx.toDp() }
    val bottomBarDp = with(density) { bottomBarPx.toDp() }
    val insetTopState = rememberUpdatedState(topBarDp.value.toInt())
    val insetBottomState = rememberUpdatedState(bottomBarDp.value.toInt())

    // Ссылки Telegram внутри страницы открываем средствами приложения (разбор через TDLib), а не в самом WebView.
    val openLinkInApp by rememberUpdatedState(LocalOpenLink.current)

    val web = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                safeBrowsingEnabled = true
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            setDownloadListener { link, _, _, _, _ -> openExternally(context, link) }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    val raw = uri.toString()
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    return when {
                        // Ссылка в клиент Telegram: t.me/..., telegram.me/..., tg://...
                        scheme == "tg" || ((scheme == "https" || scheme == "http") && isTelegramUrl(raw)) -> {
                            openLinkInApp(raw)
                            true
                        }
                        scheme == "https" -> false
                        scheme == "http" -> {
                            view.loadUrl(httpsOnly(raw))
                            true
                        }
                        scheme == "intent" -> {
                            if (request.hasGesture()) openIntentUri(context, view, raw)
                            true
                        }
                        scheme in BlockedSchemes || scheme.isEmpty() -> true
                        // tel:, mailto:, sms:, geo:, market:, whatsapp: и прочие схемы приложений — системе, но только по тапу пользователя.
                        else -> {
                            if (request.hasGesture()) openExternally(context, raw)
                            true
                        }
                    }
                }

                override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                    error = null
                    if (pageUrl != null) url = pageUrl
                    canBack = view.canGoBack()
                    canForward = view.canGoForward()
                }

                override fun onPageCommitVisible(view: WebView, pageUrl: String?) {
                    applyPageInsets(view, insetTopState.value, insetBottomState.value)
                }

                override fun onPageFinished(view: WebView, pageUrl: String?) {
                    if (pageUrl != null) url = pageUrl
                    canBack = view.canGoBack()
                    canForward = view.canGoForward()
                    applyPageInsets(view, insetTopState.value, insetBottomState.value)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                    if (request.isForMainFrame) error = err.description?.toString() ?: "Не удалось загрузить страницу"
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, e: SslError) {
                    handler.cancel()
                    error = "Сертификат сайта недействителен"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView, newTitle: String?) {
                    title = newTitle.orEmpty()
                }

                override fun onPermissionRequest(request: PermissionRequest) = request.deny()

                override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
                    callback.invoke(origin, false, false)
            }
        }
    }

    LaunchedEffect(web) { web.loadUrl(httpsOnly(url)) }
    // Панели поменяли размер (поворот, жесты навигации) — обновляем отступы уже открытой страницы.
    LaunchedEffect(web, topBarPx, bottomBarPx) { applyPageInsets(web, insetTopState.value, insetBottomState.value) }
    DisposableEffect(web) {
        onDispose {
            web.stopLoading()
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
    }
    BackHandler(enabled = canBack) { web.goBack() }

    val host = remember(url) { runCatching { Uri.parse(url).host }.getOrNull().orEmpty() }
    val secure = url.startsWith("https://", ignoreCase = true)
    val loading = progress in 1..99
    val scheme = MaterialTheme.colorScheme

    val backdrop = rememberBackdropState()
    val glass = LocalGlass.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Box(Modifier.fillMaxSize()) {
            // Страница и экран ошибки — «источник» для размытия: именно их копия просвечивает под панелями.
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
                AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                error?.let { message ->
                    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(top = topBarDp, bottom = bottomBarDp)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(96.dp)
                                    .clip(MaterialShapes.Cookie9Sided.toShape())
                                    .background(scheme.errorContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.WifiOff, contentDescription = null, tint = scheme.onErrorContainer, modifier = Modifier.size(44.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Страница не открылась", style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                            )
                            Button(onClick = { error = null; web.reload() }) { Text("Повторить") }
                            // Сайт мог не открыться только во встроенном браузере (например, http без https) — даём запасной путь.
                            TextButton(onClick = { openExternally(context, url) }) { Text("Открыть во внешнем браузере") }
                        }
                    }
                }
            }

            // Верхняя панель: стеклянный контейнер с прямыми углами; доходит и под строку состояния.
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { topBarPx = it.height }
                        .glass(
                            backdrop,
                            glass,
                            RectangleShape,
                            tint = scheme.surfaceContainer,
                        ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = tr("Закрыть")) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                title.ifEmpty { host },
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (secure) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = tr("Защищённое соединение"),
                                        modifier = Modifier.size(12.dp).padding(end = 2.dp),
                                        tint = scheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    host,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = tr("Ещё")) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                                DropdownMenuItem(
                                    text = { Text("Открыть во внешнем браузере") },
                                    leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) },
                                    onClick = { menu = false; openExternally(context, url) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Скопировать ссылку") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                                    onClick = { menu = false; copyLink(context, url) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Поделиться") },
                                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                                    onClick = { menu = false; shareLink(context, url) },
                                )
                            }
                        }
                    }
                }
                if (loading) {
                    LinearWavyProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }

            // Нижняя панель: единое стекло — кнопки и системная навигация составляют одно целое, без отдельной «таблетки».
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarPx = it.height }
                    .glass(backdrop, glass, RectangleShape, tint = scheme.surfaceContainer),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = navInset + 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { web.goBack() }, enabled = canBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад"))
                    }
                    IconButton(onClick = { web.goForward() }, enabled = canForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = tr("Вперёд"))
                    }
                    IconButton(onClick = { if (loading) web.stopLoading() else web.reload() }) {
                        Icon(
                            if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = if (loading) "Остановить" else "Обновить",
                        )
                    }
                    IconButton(onClick = { shareLink(context, url) }) {
                        Icon(Icons.Filled.Share, contentDescription = tr("Поделиться"))
                    }
                }
            }
        }
    }
}

/** Схемы, которые страница не вправе открывать ни у нас, ни в других приложениях. */
private val BlockedSchemes = setOf(
    "file", "content", "javascript", "data", "blob", "about", "view-source", "filesystem", "chrome", "chrome-extension",
)

/**
 * Страница сдвигает свой контент на высоту панелей: тогда начало и конец страницы видны целиком,
 * а при прокрутке содержимое уходит под размытые панели. Единицы — CSS-пиксели (с поправкой на масштаб страницы).
 */
private fun applyPageInsets(view: WebView, topDp: Int, bottomDp: Int) {
    if (topDp <= 0 && bottomDp <= 0) return
    val js = """
        (function() {
            var k = (screen.width > 0 && window.innerWidth > 0) ? window.innerWidth / screen.width : 1;
            var s = document.getElementById('yg-insets');
            if (!s) {
                s = document.createElement('style');
                s.id = 'yg-insets';
                (document.head || document.documentElement).appendChild(s);
            }
            s.textContent = 'html{box-sizing:border-box !important;padding-top:' + ($topDp * k) + 'px !important;padding-bottom:' + ($bottomDp * k) + 'px !important;}';
        })();
    """.trimIndent()
    view.evaluateJavascript(js, null)
}

/** intent://... со страницы: открываем подходящее приложение, а если его нет — запасную https-ссылку из самой intent-ссылки. */
private fun openIntentUri(context: Context, view: WebView, raw: String) {
    val intent = runCatching { Intent.parseUri(raw, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return
    // Страница не должна выбирать конкретный компонент или подставлять «селектор»: только обычное открытие по ссылке.
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    intent.component = null
    intent.selector = null
    val fallback = intent.getStringExtra("browser_fallback_url")
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        if (fallback != null && fallback.startsWith("https://", ignoreCase = true)) view.loadUrl(fallback)
        else Toast.makeText(context, tr("Нет приложения для открытия ссылки"), Toast.LENGTH_SHORT).show()
    }
}

private fun copyLink(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", url))
    Toast.makeText(context, tr("Скопировано"), Toast.LENGTH_SHORT).show()
}

private fun shareLink(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
