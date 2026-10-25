package com.kraftplay.openiptv

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kraftplay.openiptv.model.CustomThemeConfig
import com.kraftplay.openiptv.ui.MainScreen
import com.kraftplay.openiptv.ui.PlayerScreen
import com.kraftplay.openiptv.ui.SettingsScreen
import com.kraftplay.openiptv.viewmodel.MainViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import android.content.pm.ActivityInfo

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
            val language by viewModel.language.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            val accentColorName by viewModel.accentColor.collectAsState()
            val backBehavior by viewModel.backBehavior.collectAsState()
            val uiStyle by viewModel.uiStyle.collectAsState()
            val customTheme by viewModel.parsedCustomTheme.collectAsState()
            val panelColorName by viewModel.panelColor.collectAsState()
            
            val accentColor = when(accentColorName) {
                "Green" -> Color(0xFF4CAF50)
                "Red" -> Color(0xFFF44336)
                "Purple" -> Color(0xFF9C27B0)
                "Orange" -> Color(0xFFFF9800)
                "Pink" -> Color(0xFFE91E63)
                "Cyan" -> Color(0xFF00BCD4)
                else -> Color(0xFF2196F3)
            }

            val (customSurface, customSurfaceVariant) = when(panelColorName) {
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
                when(themeMode) {
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

            LanguageContextWrapper(language, this@MainActivity) {
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
                            when(backBehavior) {
                                "Exit" -> (this@MainActivity as Activity).finish()
                                "Confirm", "Double press to exit" -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastBackPressTime < 2000) {
                                        (this@MainActivity as Activity).finish()
                                    } else {
                                        lastBackPressTime = now
                                        Toast.makeText(this@MainActivity, "Press again to exit", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                "Back to list" -> { }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }

                    Surface(color = MaterialTheme.colorScheme.background) {
                        com.kraftplay.openiptv.ui.StyledBackground(uiStyle = uiStyle) {
                            Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                                OpenIptvApp(viewModel, navController, this@MainActivity)
                                com.kraftplay.openiptv.ui.SeasonalEffects(themeMode = themeMode)
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
}

@Composable
fun LanguageContextWrapper(
    language: String,
    activity: ComponentActivity,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val locale = Locale(language)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    val localizedContext = context.createConfigurationContext(config)
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalActivityResultRegistryOwner provides activity
    ) {
        content()
    }
}

@Composable
fun OpenIptvApp(
    viewModel: MainViewModel,
    navController: androidx.navigation.NavHostController,
    activity: MainActivity
) {
    val autoStartLast by viewModel.autoStartLast.collectAsState()
    val lastUrl by viewModel.lastChannelUrl.collectAsState()
    
    LaunchedEffect(Unit) {
        if (autoStartLast && lastUrl.isNotEmpty() && activity.intent.getStringExtra("auto_play_url").isNullOrEmpty()) {
            val encodedUrl = URLEncoder.encode(lastUrl, StandardCharsets.UTF_8.toString())
            navController.navigate("player/$encodedUrl")
        }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            activity.setPlayingVideoState(false)
            MainScreen(
                viewModel = viewModel,
                onChannelClick = { channel ->
                    val encodedUrl = URLEncoder.encode(channel.url, StandardCharsets.UTF_8.toString())
                    navController.navigate("player/$encodedUrl")
                },
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            activity.setPlayingVideoState(false)
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(
            route = "player/{url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            activity.setPlayingVideoState(true)
            val url = backStackEntry.arguments?.getString("url") ?: ""
            PlayerScreen(
                viewModel = viewModel,
                url = url,
                onTriggerPip = { activity.triggerPipMode() },
                onExitChannel = { navController.popBackStack() }
            )
        }
    }
}
