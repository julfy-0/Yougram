@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.browser.ui

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.yougram.core.ui.component.httpsOnly
import app.yougram.core.ui.component.openExternally

/**
 * Встроенный браузер на WebView. Разрешены только http(s) (http поднимается до https),
 * доступ к файлам, камере, микрофону и геолокации страницам закрыт, загрузки уходят во внешнее приложение.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(initialUrl: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

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
                    return when (uri.scheme?.lowercase()) {
                        "https" -> false
                        "http" -> {
                            view.loadUrl(httpsOnly(uri.toString()))
                            true
                        }
                        "tel", "mailto", "sms", "geo" -> {
                            openExternally(context, uri.toString())
                            true
                        }
                        else -> true // intent:, file:, javascript: и прочее не открываем
                    }
                }

                override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                    error = null
                    if (pageUrl != null) url = pageUrl
                    canBack = view.canGoBack()
                    canForward = view.canGoForward()
                }

                override fun onPageFinished(view: WebView, pageUrl: String?) {
                    if (pageUrl != null) url = pageUrl
                    canBack = view.canGoBack()
                    canForward = view.canGoForward()
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

    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            // Верхняя панель: контейнер с крупными нижними скруглениями; цвет доходит и под строку состояния.
            Surface(
                Modifier.fillMaxWidth(),
                color = scheme.surfaceContainer,
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Закрыть") }
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
                                    contentDescription = "Защищённое соединение",
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
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Ещё") }
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
            Box(Modifier.fillMaxWidth().heightIn(min = 6.dp)) {
                if (loading) {
                    LinearWavyProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                error?.let { message ->
                    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
                        Column(
                            Modifier.padding(32.dp),
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
                        }
                    }
                }
            }

            // Нижняя панель: плавающая «таблетка» с тональными кнопками.
            Surface(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = scheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = { web.goBack() }, enabled = canBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    FilledTonalIconButton(onClick = { web.goForward() }, enabled = canForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Вперёд")
                    }
                    FilledTonalIconButton(onClick = { if (loading) web.stopLoading() else web.reload() }) {
                        Icon(
                            if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                            contentDescription = if (loading) "Остановить" else "Обновить",
                        )
                    }
                    FilledTonalIconButton(onClick = { shareLink(context, url) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Поделиться")
                    }
                }
            }
        }
    }
}

private fun copyLink(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", url))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

private fun shareLink(context: Context, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}