package com.kraftplay.openiptv

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kraftplay.openiptv.ui.MainScreen
import com.kraftplay.openiptv.ui.PlayerScreen
import com.kraftplay.openiptv.ui.SeasonalEffects
import com.kraftplay.openiptv.ui.SettingsScreen
import com.kraftplay.openiptv.ui.StyledBackground
import com.kraftplay.openiptv.viewmodel.MainViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var isPlayingVideo = false
    private var fullScreenEnabledForWindow = true

    private fun applyAppSystemBars() {
        val appWindow = this.window
        WindowCompat.setDecorFitsSystemWindows(appWindow, !fullScreenEnabledForWindow)
        val controller = WindowInsetsControllerCompat(appWindow, appWindow.decorView)
        if (isPlayingVideo && fullScreenEnabledForWindow) {
            appWindow.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        } else {
            appWindow.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onResume() {
        super.onResume()
        applyAppSystemBars()
    }

    fun setPlayingVideoState(playing: Boolean) {
        isPlayingVideo = playing
        applyAppSystemBars()
    }

    fun triggerPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder().build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayingVideo) {
            triggerPipMode()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoPlayUrl = intent.getStringExtra("auto_play_url")

        setContent {
            val viewModel: MainViewModel = viewModel()
            val fullScreenEnabled by viewModel.fullScreenByDefault.collectAsState()
            val forceLandscape by viewModel.forceLandscape.collectAsState()

            SideEffect {
                fullScreenEnabledForWindow = fullScreenEnabled
                if (forceLandscape) {
                    this@MainActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else if (this@MainActivity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                    this@MainActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                applyAppSystemBars()
            }

            OpenIptvRootContent(
                viewModel = viewModel,
                autoPlayUrl = autoPlayUrl,
                resultRegistryOwner = this@MainActivity,
                onPlayingStateChanged = { setPlayingVideoState(it) },
                onTriggerPip = { triggerPipMode() },
                onExitApp = { finish() }
            )
        }
    }
}

/** Общий корневой интерфейс OpenIPTV — используется 1-в-1 и на телефоне/ТВ, и в Android Auto */
@Composable
fun OpenIptvRootContent(
    viewModel: MainViewModel,
    autoPlayUrl: String? = null,
    resultRegistryOwner: ActivityResultRegistryOwner? = null,
    isAuto: Boolean = false,
    onPlayingStateChanged: (Boolean) -> Unit = {},
    onTriggerPip: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColorName by viewModel.accentColor.collectAsState()
    val backBehavior by viewModel.backBehavior.collectAsState()
    val uiStyle by viewModel.uiStyle.collectAsState()
    val customTheme by viewModel.parsedCustomTheme.collectAsState()
    val panelColorName by viewModel.panelColor.collectAsState()

    val accentColor = when (accentColorName) {
        "Green" -> Color(0xFF4CAF50)
        "Red" -> Color(0xFFF44336)
        "Purple" -> Color(0xFF9C27B0)
        "Orange" -> Color(0xFFFF9800)
        "Pink" -> Color(0xFFE91E63)
        "Cyan" -> Color(0xFF00BCD4)
        else -> Color(0xFF2196F3)
    }

    val (customSurface, customSurfaceVariant) = when (panelColorName) {
        "Dark Blue" -> Color(0xFF1F293D) to Color(0xFF2B3852)
        "Graphite" -> Color(0xFF282C34) to Color(0xFF323842)
        "Deep Purple" -> Color(0xFF271F33) to Color(0xFF352B45)
        "Midnight" -> Color(0xFF152238) to Color(0xFF1E3150)
        "Emerald" -> Color(0xFF122C1E) to Color(0xFF1B3D2B)
        else -> Color(0xFF242424) to Color(0xFF2D2D2D)
    }

    val colorScheme = if (customTheme.enabled) {
        darkColorScheme(
            primary = parseHexColor(customTheme.primaryColorHex, accentColor),
            background = parseHexColor(customTheme.backgroundColorHex, Color(0xFF121212)),
            surface = parseHexColor(customTheme.surfaceColorHex, Color(0xFF1E1E1E)),
            surfaceContainerHigh = parseHexColor(customTheme.surfaceColorHex, customSurface),
            surfaceVariant = customSurfaceVariant,
            onBackground = parseHexColor(customTheme.textColorHex, Color.White),
            onSurface = parseHexColor(customTheme.textColorHex, Color.White)
        )
    } else {
        when (themeMode) {
            "Light" -> lightColorScheme(
                primary = accentColor,
                background = Color.White,
                surface = Color.White
            )
            "AMOLED" -> darkColorScheme(
                primary = accentColor,
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color.Black,
                surfaceContainerHigh = Color(0xFF121212)
            )
            "Halloween" -> darkColorScheme(
                primary = Color(0xFFFF7518), secondary = Color(0xFF9B4DCA),
                background = Color(0xFF160D20), surface = Color(0xFF24152E),
                surfaceContainerHigh = Color(0xFF2F1C3C), surfaceVariant = Color(0xFF3B234A)
            )
            "Winter" -> darkColorScheme(
                primary = Color(0xFF8ED8FF), secondary = Color(0xFFB8E7FF),
                background = Color(0xFF071827), surface = Color(0xFF102B40),
                surfaceContainerHigh = Color(0xFF153752), surfaceVariant = Color(0xFF1D476B)
            )
            else -> darkColorScheme(
                primary = accentColor,
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E),
                surfaceContainerHigh = customSurface,
                surfaceVariant = customSurfaceVariant
            )
        }
    }

    LanguageContextWrapper(language = language, resultRegistryOwner = resultRegistryOwner) {
        val context = LocalContext.current
        androidx.compose.runtime.CompositionLocalProvider(com.kraftplay.openiptv.ui.LocalIsAuto provides isAuto) {
            MaterialTheme(colorScheme = colorScheme) {
                val navController = rememberNavController()
            var lastBackPressTime by remember { mutableStateOf(0L) }

            LaunchedEffect(autoPlayUrl) {
                if (!autoPlayUrl.isNullOrEmpty()) {
                    val encodedUrl = URLEncoder.encode(autoPlayUrl, StandardCharsets.UTF_8.toString())
                    navController.navigate("player/$encodedUrl")
                }
            }

            BackHandler(enabled = true) {
                val currentRoute = navController.currentDestination?.route
                if (currentRoute == "main") {
                    when (backBehavior) {
                        "Exit" -> onExitApp()
                        "Confirm", "Double press to exit" -> {
                            val now = System.currentTimeMillis()
                            if (now - lastBackPressTime < 2000) {
                                onExitApp()
                            } else {
                                lastBackPressTime = now
                                Toast.makeText(context, "Press again to exit", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "Back to list" -> { }
                    }
                } else {
                    navController.popBackStack()
                }
            }

            Surface(color = MaterialTheme.colorScheme.background) {
                StyledBackground(uiStyle = uiStyle) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OpenIptvApp(
                            viewModel = viewModel,
                            navController = navController,
                            hasIntentAutoPlay = !autoPlayUrl.isNullOrEmpty(),
                            isAuto = isAuto,
                            onPlayingStateChanged = onPlayingStateChanged,
                            onTriggerPip = onTriggerPip
                        )
                        SeasonalEffects(themeMode = themeMode)
                    }
                }
            }
        }
    }
}
}

private fun parseHexColor(hex: String, defaultColor: Color): Color {
    return try {
        val clean = hex.trim().replace("#", "")
        if (clean.length == 6) {
            Color(android.graphics.Color.parseColor("#FF$clean"))
        } else if (clean.length == 8) {
            Color(android.graphics.Color.parseColor("#$clean"))
        } else defaultColor
    } catch (e: Exception) {
        defaultColor
    }
}

@Composable
fun LanguageContextWrapper(
    language: String,
    resultRegistryOwner: ActivityResultRegistryOwner?,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val locale = Locale(language)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    val localizedContext = context.createConfigurationContext(config)
    if (resultRegistryOwner != null) {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalActivityResultRegistryOwner provides resultRegistryOwner
        ) {
            content()
        }
    } else {
        CompositionLocalProvider(
            LocalContext provides localizedContext
        ) {
            content()
        }
    }
}

@Composable
fun OpenIptvApp(
    viewModel: MainViewModel,
    navController: NavHostController,
    hasIntentAutoPlay: Boolean = false,
    isAuto: Boolean = false,
    onPlayingStateChanged: (Boolean) -> Unit = {},
    onTriggerPip: () -> Unit = {}
) {
    val autoStartLast by viewModel.autoStartLast.collectAsState()
    val lastUrl by viewModel.lastChannelUrl.collectAsState()

    LaunchedEffect(Unit) {
        if (autoStartLast && lastUrl.isNotEmpty() && !hasIntentAutoPlay) {
            val encodedUrl = URLEncoder.encode(lastUrl, StandardCharsets.UTF_8.toString())
            navController.navigate("player/$encodedUrl")
        }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            onPlayingStateChanged(false)
            MainScreen(
                viewModel = viewModel,
                onChannelClick = { channel ->
                    val encodedUrl = URLEncoder.encode(channel.url, StandardCharsets.UTF_8.toString())
                    navController.navigate("player/$encodedUrl")
                },
                onSettingsClick = { navController.navigate("settings") },
                isAuto = isAuto,
                onYouTubeClick = { navController.navigate("youtube") },
                onRuTubeClick = { navController.navigate("rutube") },
                onBrowserClick = { navController.navigate("browser") },
                onMsxClick = {
                    val rawUrl = viewModel.msxStartUrl.value.ifBlank { "http://msx.benzac.de" }
                    val finalUrl = if (rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)) rawUrl else "http://$rawUrl"
                    val encoded = runCatching { URLEncoder.encode(finalUrl, StandardCharsets.UTF_8.toString()) }.getOrDefault(finalUrl)
                    navController.navigate("browser_url/$encoded")
                }
            )
        }
        composable("youtube") {
            onPlayingStateChanged(true)
            com.kraftplay.openiptv.ui.YouTubeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("rutube") {
            onPlayingStateChanged(true)
            com.kraftplay.openiptv.ui.RuTubeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("browser") {
            onPlayingStateChanged(true)
            com.kraftplay.openiptv.ui.BrowserScreen(
                initialUrl = "https://google.com",
                onBack = { navController.popBackStack() }
            )
        }
        composable("msx") {
            onPlayingStateChanged(true)
            com.kraftplay.openiptv.ui.MsxScreen(
                viewModel = viewModel,
                onLaunchUrl = { targetUrl ->
                    val encoded = runCatching { URLEncoder.encode(targetUrl, StandardCharsets.UTF_8.toString()) }.getOrDefault(targetUrl)
                    navController.navigate("browser_url/$encoded")
                },
                onBack = { navController.popBackStack() },
                isAuto = isAuto
            )
        }
        composable(
            route = "browser_url/{url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            onPlayingStateChanged(true)
            val url = backStackEntry.arguments?.getString("url") ?: "https://google.com"
            com.kraftplay.openiptv.ui.BrowserScreen(
                initialUrl = url,
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            onPlayingStateChanged(false)
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(
            route = "player/{url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            onPlayingStateChanged(true)
            val url = backStackEntry.arguments?.getString("url") ?: ""
            PlayerScreen(
                viewModel = viewModel,
                url = url,
                onTriggerPip = onTriggerPip,
                onExitChannel = {
                    com.kraftplay.openiptv.service.SharedPlayerManager.stopPlayback()
                    navController.popBackStack()
                }
            )
        }
    }
}