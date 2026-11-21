package com.kraftplay.openiptv.auto

import android.app.Application
import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kraftplay.openiptv.service.SharedPlayerManager
import com.kraftplay.openiptv.ui.MainScreen
import com.kraftplay.openiptv.ui.PlayerScreen
import com.kraftplay.openiptv.ui.SeasonalEffects
import com.kraftplay.openiptv.ui.SettingsScreen
import com.kraftplay.openiptv.ui.StyledBackground
import com.kraftplay.openiptv.viewmodel.MainViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import android.graphics.Color as AndroidColor

class CarVideoPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner,
    ActivityResultRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val appViewModelStore = ViewModelStore()
    private val backDispatcher = OnBackPressedDispatcher()

    override val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = backDispatcher
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    private val dummyActivityRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {}
    }
    override val activityResultRegistry: ActivityResultRegistry
        get() = dummyActivityRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        window?.let { win ->
            win.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            win.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))

            win.addFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )

            win.clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )

            win.decorView.fitsSystemWindows = true
            win.decorView.keepScreenOn = true

            win.decorView.setViewTreeLifecycleOwner(this)
            win.decorView.setViewTreeViewModelStoreOwner(this)
            win.decorView.setViewTreeSavedStateRegistryOwner(this)
            win.decorView.setViewTreeOnBackPressedDispatcherOwner(this)
        }

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val app = runCatching {
                    Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication")
                        .invoke(null) as? Application
                }.getOrNull() ?: (context.applicationContext as Application)
                val viewModel: MainViewModel = remember { MainViewModel(app) }
                CarAppRoot(
                    registryOwner = this@CarVideoPresentation,
                    viewModel = viewModel
                )
            }
        }
        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        appViewModelStore.clear()
        super.onDetachedFromWindow()
    }

    override fun dismiss() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        appViewModelStore.clear()
        super.dismiss()
    }

    private fun handleSteeringWheelKey(keyCode: Int): Boolean {
        val prefs = context.getSharedPreferences("com.kraftplay.openiptv_preferences", Context.MODE_PRIVATE)
            ?: context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE)
        val exo = SharedPlayerManager.getOrCreatePlayer(context)

        val nextAction = prefs.getString("wheel_next_action", "NextChannel") ?: "NextChannel"
        val prevAction = prefs.getString("wheel_prev_action", "PrevChannel") ?: "PrevChannel"
        val chUpAction = prefs.getString("wheel_channel_up_action", "NextChannel") ?: "NextChannel"
        val chDownAction = prefs.getString("wheel_channel_down_action", "PrevChannel") ?: "PrevChannel"

        fun executeAction(actionStr: String) {
            when (actionStr) {
                "NextChannel" -> {
                    if (exo.hasNextMediaItem()) exo.seekToNextMediaItem() else exo.seekTo(0, 0L)
                }
                "PrevChannel" -> {
                    if (exo.hasPreviousMediaItem()) exo.seekToPreviousMediaItem() else exo.seekTo(exo.mediaItemCount.coerceAtLeast(1) - 1, 0L)
                }
                "Skip10" -> exo.seekTo((exo.currentPosition + 10_000L).coerceAtMost(exo.duration.coerceAtLeast(0L)))
                "Skip30" -> exo.seekTo((exo.currentPosition + 30_000L).coerceAtMost(exo.duration.coerceAtLeast(0L)))
                "Skip60" -> exo.seekTo((exo.currentPosition + 60_000L).coerceAtMost(exo.duration.coerceAtLeast(0L)))
                "Rewind10" -> exo.seekTo((exo.currentPosition - 10_000L).coerceAtLeast(0L))
                "Rewind30" -> exo.seekTo((exo.currentPosition - 30_000L).coerceAtLeast(0L))
                "Rewind60" -> exo.seekTo((exo.currentPosition - 60_000L).coerceAtLeast(0L))
            }
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> { executeAction(nextAction); true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { executeAction(prevAction); true }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> { executeAction(chUpAction); true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> { executeAction(chDownAction); true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                if (exo.isPlaying) exo.pause() else exo.play()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { exo.play(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { exo.pause(); true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { executeAction("Skip30"); true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { executeAction("Rewind30"); true }
            else -> false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backDispatcher.onBackPressed()
            return true
        }
        if (handleSteeringWheelKey(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun CarAppRoot(
    registryOwner: ActivityResultRegistryOwner,
    viewModel: MainViewModel
) {
    com.kraftplay.openiptv.OpenIptvRootContent(
        viewModel = viewModel,
        resultRegistryOwner = registryOwner,
        isAuto = true
    )
}

private fun parseHexColor(hex: String, defaultColor: Color): Color {
    return runCatching {
        val clean = hex.trim().replace("#", "")
        when (clean.length) {
            6 -> Color(AndroidColor.parseColor("#FF$clean"))
            8 -> Color(AndroidColor.parseColor("#$clean"))
            else -> defaultColor
        }
    }.getOrDefault(defaultColor)
}

fun launchCarAppRoot(composeView: ComposeView, owner: ActivityResultRegistryOwner, viewModel: MainViewModel) {
    composeView.setContent {
        CarAppRoot(registryOwner = owner, viewModel = viewModel)
    }
}

