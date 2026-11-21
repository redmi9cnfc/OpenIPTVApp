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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayCircle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun YouTubeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showKeyboardDialog by remember { mutableStateOf(false) }

    var customView: View? by remember { mutableStateOf(null) }
    var customViewCallback: WebChromeClient.CustomViewCallback? by remember { mutableStateOf(null) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            ).build()
        audioManager?.requestAudioFocus(req)
        onDispose { audioManager?.abandonAudioFocusRequest(req) }
    }

    BackHandler {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
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
            // Полноэкранный видеоплеер YouTube
            AndroidView(
                factory = { customView!! },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Панель управления YouTube
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = "YouTube",
                                tint = Color(0xFFFF0000),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "YouTube",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                showKeyboardDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Клавиатура поиска YouTube",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = {
                                com.kraftplay.openiptv.util.VoiceInputHelper.startSpeechRecognition(
                                    context = context,
                                    onResult = { query ->
                                        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
                                        webView?.loadUrl("https://m.youtube.com/results?search_query=$encoded")
                                    }
                                )
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Голосовой поиск YouTube",
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
                                webView?.loadUrl("https://m.youtube.com")
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Home,
                                    contentDescription = "Главная YouTube",
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
                        }
                    }
                }

                // Встроенный WebView плеера YouTube
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
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                            addJavascriptInterface(
                                object {
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
                                                "if (e && (e.tagName === 'INPUT' || e.tagName === 'TEXTAREA' || e.isContentEditable || e.getAttribute('contenteditable') === 'true')) {" +
                                                "  var val = e.value || e.innerText || '';" +
                                                "  window.CarBridge.onInputFocused(val);" +
                                                "}" +
                                                "})();")
                                    }, 200)
                                }
                                false
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
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
                            loadUrl("https://m.youtube.com")
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (showKeyboardDialog) {
            CarOnScreenKeyboardDialog(
                initialText = "",
                onDismiss = { showKeyboardDialog = false },
                onConfirm = { query ->
                    val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
                    webView?.loadUrl("https://m.youtube.com/results?search_query=$encoded")
                }
            )
        }
    }
}
