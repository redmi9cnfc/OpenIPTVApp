package com.kraftplay.openiptv.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun BrowserScreen(
    initialUrl: String = "https://google.com",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Браузер") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showKeyboardDialog by remember { mutableStateOf(false) }
    var isFullscreenMode by remember { mutableStateOf(false) }

    var customView: View? by remember { mutableStateOf(null) }
    var customViewCallback: WebChromeClient.CustomViewCallback? by remember { mutableStateOf(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                ).build()
            audioManager?.requestAudioFocus(req)
            onDispose { audioManager?.abandonAudioFocusRequest(req) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN)
            onDispose {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        }
    }

    BackHandler {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
        } else if (isFullscreenMode) {
            isFullscreenMode = false
        } else if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            com.kraftplay.openiptv.service.SharedPlayerManager.stopPlayback()
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (customView != null) {
            // Полноэкранный видеоплеер (HTML5 video)
            AndroidView(
                factory = { customView!! },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (!isFullscreenMode) {
                    // Панель управления браузера
                    Surface(
                        color = Color(0xFF181818),
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(onClick = {
                                    com.kraftplay.openiptv.service.SharedPlayerManager.stopPlayback()
                                    onBack()
                                }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Назад к каналам",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Browser",
                                    tint = Color(0xFF4285F4),
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = pageTitle.ifEmpty { currentUrl },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    showKeyboardDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Поиск / Ввод адреса",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = {
                                    com.kraftplay.openiptv.util.VoiceInputHelper.startSpeechRecognition(
                                        context = context,
                                        onResult = { query ->
                                            val targetUrl = if (query.startsWith("http://") || query.startsWith("https://")) {
                                                query
                                            } else if (query.contains(".")) {
                                                "https://$query"
                                            } else {
                                                val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
                                                "https://www.google.com/search?q=$encoded"
                                            }
                                            webView?.loadUrl(targetUrl)
                                        }
                                    )
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Голосовой поиск",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = { webView?.goBack() },
                                    enabled = canGoBack
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronLeft,
                                        contentDescription = "Назад в браузере",
                                        tint = if (canGoBack) Color.White else Color.Gray
                                    )
                                }
                                IconButton(
                                    onClick = { webView?.goForward() },
                                    enabled = canGoForward
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = "Вперед в браузере",
                                        tint = if (canGoForward) Color.White else Color.Gray
                                    )
                                }
                                IconButton(onClick = {
                                    webView?.loadUrl(initialUrl)
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Home,
                                        contentDescription = "Главная страница",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = { webView?.reload() }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Обновить страницу",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = { isFullscreenMode = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fullscreen,
                                        contentDescription = "На весь экран",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = {
                                    com.kraftplay.openiptv.service.SharedPlayerManager.stopPlayback()
                                    onBack()
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Выйти",
                                        tint = Color(0xFFFF5252)
                                    )
                                }
                            }
                        }
                    }
                }

                // Встроенный WebView плеера браузера
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            isFocusable = true
                            isFocusableInTouchMode = true
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            @Suppress("DEPRECATION")
                            settings.allowFileAccessFromFileURLs = true
                            @Suppress("DEPRECATION")
                            settings.allowUniversalAccessFromFileURLs = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 MediaStationX/1.0.220"

                            addJavascriptInterface(
                                object {
                                    @android.webkit.JavascriptInterface
                                    fun getVersion(): String = "1.0.220"

                                    @android.webkit.JavascriptInterface
                                    fun getPlatform(): String = "android"

                                    @android.webkit.JavascriptInterface
                                    fun ready() {}

                                    @android.webkit.JavascriptInterface
                                    fun onInputFocused(initialValue: String) {
                                        post {
                                            showKeyboardDialog = true
                                        }
                                    }
                                },
                                "CarBridge"
                            )

                            setOnTouchListener { v, event ->
                                if (event.action == MotionEvent.ACTION_UP) {
                                    v.requestFocus()
                                    postDelayed({
                                        loadUrl("javascript:(function() {" +
                                                "var e = document.activeElement;" +
                                                "var isInput = e && (e.tagName === 'INPUT' || e.tagName === 'TEXTAREA' || e.isContentEditable || e.getAttribute('contenteditable') === 'true' || (e.className && e.className.indexOf('input') !== -1) || e.getAttribute('role') === 'textbox');" +
                                                "if (isInput) {" +
                                                "  var val = e.value || e.innerText || '';" +
                                                "  if (window.CarBridge) window.CarBridge.onInputFocused(val);" +
                                                "}" +
                                                "})();")
                                    }, 150)
                                }
                                false
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    view?.evaluateJavascript(
                                        "(function() {" +
                                        "  var msxObj = {" +
                                        "    version: '1.0.220'," +
                                        "    platform: 'android'," +
                                        "    ready: function() {}," +
                                        "    getStartParameter: function() { return ''; }," +
                                        "    hasStartParameter: function() { return false; }," +
                                        "    isPlugin: function() { return false; }" +
                                        "  };" +
                                        "  if (typeof window.MSX === 'undefined') window.MSX = msxObj;" +
                                        "  if (typeof window.msx === 'undefined') window.msx = msxObj;" +
                                        "})();",
                                        null
                                    )
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString() ?: ""
                                    if (targetUrl.contains("benzac.de/download") || targetUrl.contains("msx.benzac.de/#download") || targetUrl.contains("download_msx")) {
                                        return true
                                    }
                                    return super.shouldOverrideUrlLoading(view, request)
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: android.webkit.SslErrorHandler?,
                                    error: android.net.http.SslError?
                                ) {
                                    handler?.proceed()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    if (failingUrl?.startsWith("https://", ignoreCase = true) == true) {
                                        val httpFallback = failingUrl.replaceFirst("https://", "http://", ignoreCase = true)
                                        view?.loadUrl(httpFallback)
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    currentUrl = url ?: ""
                                    pageTitle = view?.title ?: "Браузер"
                                    view?.evaluateJavascript(
                                        "(function() {" +
                                        "  var msxObj = {" +
                                        "    version: '1.0.220'," +
                                        "    platform: 'android'," +
                                        "    ready: function() {}," +
                                        "    getStartParameter: function() { return ''; }," +
                                        "    hasStartParameter: function() { return false; }," +
                                        "    isPlugin: function() { return false; }" +
                                        "  };" +
                                        "  if (typeof window.MSX === 'undefined') window.MSX = msxObj;" +
                                        "  if (typeof window.msx === 'undefined') window.msx = msxObj;" +
                                        "})();",
                                        null
                                    )
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrEmpty()) {
                                        pageTitle = title
                                    }
                                }

                                override fun onShowCustomView(
                                    view: View?,
                                    callback: CustomViewCallback?
                                ) {
                                    customView = view
                                    customViewCallback = callback
                                }

                                override fun onHideCustomView() {
                                    customViewCallback?.onCustomViewHidden()
                                    customView = null
                                    customViewCallback = null
                                }
                            }
                            loadUrl(initialUrl)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isFullscreenMode || customView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.TopStart
            ) {
                Surface(
                    onClick = {
                        if (customView != null) {
                            customViewCallback?.onCustomViewHidden()
                            customView = null
                            customViewCallback = null
                        }
                        isFullscreenMode = false
                    },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.FullscreenExit,
                            contentDescription = "Выйти из полноэкранного режима",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (showKeyboardDialog) {
            CarOnScreenKeyboardDialog(
                initialText = currentUrl,
                onDismiss = { showKeyboardDialog = false },
                onConfirm = { query ->
                    val safeText = query.replace("'", "\\'").replace("\n", " ")
                    val targetSearchUrl = if (query.startsWith("http://") || query.startsWith("https://")) {
                        query
                    } else if (query.contains(".") && !query.contains(" ")) {
                        "https://$query"
                    } else {
                        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
                        "https://www.google.com/search?q=$encoded"
                    }
                    webView?.loadUrl("javascript:(function() {" +
                            "var e = document.activeElement;" +
                            "var isInput = e && (e.tagName === 'INPUT' || e.tagName === 'TEXTAREA' || e.isContentEditable || e.getAttribute('contenteditable') === 'true' || (e.className && e.className.indexOf('input') !== -1) || e.getAttribute('role') === 'textbox');" +
                            "if (isInput) {" +
                            "  if (e.isContentEditable) e.innerText = '$safeText';" +
                            "  else e.value = '$safeText';" +
                            "  e.dispatchEvent(new InputEvent('input', { bubbles: true, data: '$safeText', inputType: 'insertText' }));" +
                            "  e.dispatchEvent(new Event('change', { bubbles: true }));" +
                            "  e.dispatchEvent(new KeyboardEvent('keydown', { keyCode: 13, key: 'Enter', bubbles: true }));" +
                            "  e.dispatchEvent(new KeyboardEvent('keyup', { keyCode: 13, key: 'Enter', bubbles: true }));" +
                            "} else if (typeof window.MSX !== 'undefined' && window.MSX.executeAction) {" +
                            "  window.MSX.executeAction('search:' + '$safeText');" +
                            "} else {" +
                            "  window.location.href = '$targetSearchUrl';" +
                            "}" +
                            "})();")
                }
            )
        }
    }
}
