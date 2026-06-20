package com.kraftplay.openiptv

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kraftplay.openiptv.ui.MainScreen
import com.kraftplay.openiptv.ui.PlayerScreen
import com.kraftplay.openiptv.ui.SettingsScreen
import com.kraftplay.openiptv.viewmodel.MainViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val language by viewModel.language.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()
            val accentColorName by viewModel.accentColor.collectAsState()
            val backBehavior by viewModel.backBehavior.collectAsState()
            
            val accentColor = when(accentColorName) {
                "Green" -> Color(0xFF4CAF50)
                "Red" -> Color(0xFFF44336)
                else -> Color(0xFF2196F3) // Blue
            }

            val colorScheme = when(themeMode) {
                "Light" -> lightColorScheme(
                    primary = accentColor,
                    background = Color.White,
                    surface = Color.White
                )
                "AMOLED" -> darkColorScheme(
                    primary = accentColor,
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color.Black
                )
                else -> darkColorScheme(
                    primary = accentColor,
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                ) // Dark
            }

            LanguageContextWrapper(language) {
                MaterialTheme(colorScheme = colorScheme) {
                    val navController = rememberNavController()
                    
                    var lastBackPressTime by remember { mutableStateOf(0L) }
                    
                    BackHandler(enabled = true) {
                        val currentRoute = navController.currentDestination?.route
                        if (currentRoute == "main") {
                            when(backBehavior) {
                                "Exit" -> (this@MainActivity as Activity).finish()
                                "Confirm" -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastBackPressTime < 2000) {
                                        (this@MainActivity as Activity).finish()
                                    } else {
                                        lastBackPressTime = now
                                        Toast.makeText(this@MainActivity, "Press again to exit", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                "Back to list" -> {
                                    // Already on main
                                }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }

                    Surface(color = MaterialTheme.colorScheme.background) {
                        OpenIptvApp(viewModel, navController)
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageContextWrapper(language: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val locale = Locale(language)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    val localizedContext = context.createConfigurationContext(config)
    CompositionLocalProvider(LocalContext provides localizedContext) {
        content()
    }
}

@Composable
fun OpenIptvApp(viewModel: MainViewModel, navController: androidx.navigation.NavHostController) {
    val autoStartLast by viewModel.autoStartLast.collectAsState()
    val lastUrl by viewModel.lastChannelUrl.collectAsState()
    
    LaunchedEffect(Unit) {
        if (autoStartLast && lastUrl.isNotEmpty()) {
            val encodedUrl = URLEncoder.encode(lastUrl, StandardCharsets.UTF_8.toString())
            navController.navigate("player/$encodedUrl")
        }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
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
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(
            route = "player/{url}",
            arguments = listOf(navArgument("url") { type = NavType.StringType })
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            PlayerScreen(viewModel = viewModel, url = url)
        }
    }
}
