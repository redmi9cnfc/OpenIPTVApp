package com.kraftplay.openiptv.auto;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.OnBackPressedDispatcherOwner;
import androidx.activity.ViewTreeOnBackPressedDispatcherOwner;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.ActivityResultRegistryOwner;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.R;
import androidx.compose.ui.platform.ComposeView;
import androidx.compose.ui.platform.ViewCompositionStrategy;
import androidx.core.app.ActivityOptionsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

import com.google.android.apps.auto.sdk.CarActivity;
import com.kraftplay.openiptv.viewmodel.MainViewModel;

public class OpenIptvCarActivity extends CarActivity implements
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner,
        OnBackPressedDispatcherOwner,
        ActivityResultRegistryOwner {

    private final LifecycleRegistry lifecycleRegistry = new LifecycleRegistry(this);
    private final SavedStateRegistryController savedStateRegistryController = SavedStateRegistryController.create(this);
    private final ViewModelStore appViewModelStore = new ViewModelStore();
    private final OnBackPressedDispatcher backDispatcher = new OnBackPressedDispatcher();

    public Window getWindow() {
        return c();
    }

    @NonNull
    @Override
    public OnBackPressedDispatcher getOnBackPressedDispatcher() {
        return backDispatcher;
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    @NonNull
    @Override
    public SavedStateRegistry getSavedStateRegistry() {
        return savedStateRegistryController.getSavedStateRegistry();
    }

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        return appViewModelStore;
    }

    private final ActivityResultRegistry dummyActivityRegistry = new ActivityResultRegistry() {
        @Override
        public <I, O> void onLaunch(
                int requestCode,
                @NonNull ActivityResultContract<I, O> contract,
                I input,
                @Nullable ActivityOptionsCompat options
        ) {}
    };

    @NonNull
    @Override
    public ActivityResultRegistry getActivityResultRegistry() {
        return dummyActivityRegistry;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_AppCompat_NoActionBar);
        super.onCreate(savedInstanceState);
        savedStateRegistryController.performRestore(savedInstanceState);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);

        SharedPreferences prefs = getSharedPreferences("com.kraftplay.openiptv_preferences", MODE_PRIVATE);
        boolean isFullscreen = prefs == null || prefs.getBoolean("car_fullscreen_mode", true);

        try {
            setIgnoreConfigChanges(0xFFFFFFFF);
            if (getCarUiController() != null) {
                if (getCarUiController().getStatusBarController() != null) {
                    getCarUiController().getStatusBarController().hideAppHeader();
                }
                if (getCarUiController().getMenuController() != null) {
                    getCarUiController().getMenuController().hideMenuButton();
                }
            }
        } catch (Throwable ignored) {}

        if (getWindow() != null && getWindow().getDecorView() != null) {
            if (isFullscreen) {
                int fullScreenFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                getWindow().getDecorView().setSystemUiVisibility(fullScreenFlags);
            }

            ViewTreeLifecycleOwner.set(getWindow().getDecorView(), this);
            ViewTreeViewModelStoreOwner.set(getWindow().getDecorView(), this);
            ViewTreeSavedStateRegistryOwner.set(getWindow().getDecorView(), this);
            ViewTreeOnBackPressedDispatcherOwner.set(getWindow().getDecorView(), this);
        }

        ContextThemeWrapper themedContext = new ContextThemeWrapper(this, R.style.Theme_AppCompat_NoActionBar);
        ComposeView composeView = new ComposeView(themedContext);
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow.INSTANCE);
        ViewTreeLifecycleOwner.set(composeView, this);
        ViewTreeViewModelStoreOwner.set(composeView, this);
        ViewTreeSavedStateRegistryOwner.set(composeView, this);
        ViewTreeOnBackPressedDispatcherOwner.set(composeView, this);

        MainViewModel viewModel = new MainViewModel(resolveApplication());

        CarVideoPresentationKt.launchCarAppRoot(composeView, this, viewModel);
        setContentView(composeView);

        if (composeView.getParent() instanceof View parent) {
            ViewTreeLifecycleOwner.set(parent, this);
            ViewTreeViewModelStoreOwner.set(parent, this);
            ViewTreeSavedStateRegistryOwner.set(parent, this);
            ViewTreeOnBackPressedDispatcherOwner.set(parent, this);
        }
    }

    private Application resolveApplication() {
        try {
            Application app = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
            if (app != null) return app;
        } catch (Throwable ignored) {}
        Context ctx = getApplicationContext();
        if (ctx instanceof Application) return (Application) ctx;
        return (Application) ctx.getApplicationContext();
    }

    @Override
    public void onStart() {
        super.onStart();
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
    }

    @Override
    public void onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        appViewModelStore.clear();
        super.onDestroy();
    }

    private boolean handleSteeringWheelKey(int keyCode) {
        SharedPreferences prefs = getSharedPreferences("com.kraftplay.openiptv_preferences", MODE_PRIVATE);
        if (prefs == null) prefs = getSharedPreferences("iptv_prefs", MODE_PRIVATE);

        androidx.media3.exoplayer.ExoPlayer exo = com.kraftplay.openiptv.service.SharedPlayerManager.INSTANCE.getOrCreatePlayer(this, "Standard");

        String nextAction = prefs.getString("wheel_next_action", "NextChannel");
        String prevAction = prefs.getString("wheel_prev_action", "PrevChannel");
        String chUpAction = prefs.getString("wheel_channel_up_action", "NextChannel");
        String chDownAction = prefs.getString("wheel_channel_down_action", "PrevChannel");

        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                executeAction(exo, nextAction);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                executeAction(exo, prevAction);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                executeAction(exo, chUpAction);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                executeAction(exo, chDownAction);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                if (exo.isPlaying()) exo.pause(); else exo.play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                exo.play();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                exo.pause();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                executeAction(exo, "Skip30");
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                executeAction(exo, "Rewind30");
                return true;
        }
        return false;
    }

    private void executeAction(androidx.media3.exoplayer.ExoPlayer exo, String action) {
        if (exo == null) return;
        if ("NextChannel".equals(action)) {
            if (exo.hasNextMediaItem()) exo.seekToNextMediaItem(); else exo.seekTo(0, 0L);
        } else if ("PrevChannel".equals(action)) {
            if (exo.hasPreviousMediaItem()) exo.seekToPreviousMediaItem(); else exo.seekTo(Math.max(0, exo.getMediaItemCount() - 1), 0L);
        } else if ("Skip10".equals(action)) {
            exo.seekTo(Math.min(exo.getDuration() > 0 ? exo.getDuration() : Long.MAX_VALUE, exo.getCurrentPosition() + 10000L));
        } else if ("Skip30".equals(action)) {
            exo.seekTo(Math.min(exo.getDuration() > 0 ? exo.getDuration() : Long.MAX_VALUE, exo.getCurrentPosition() + 30000L));
        } else if ("Skip60".equals(action)) {
            exo.seekTo(Math.min(exo.getDuration() > 0 ? exo.getDuration() : Long.MAX_VALUE, exo.getCurrentPosition() + 60000L));
        } else if ("Rewind10".equals(action)) {
            exo.seekTo(Math.max(0L, exo.getCurrentPosition() - 10000L));
        } else if ("Rewind30".equals(action)) {
            exo.seekTo(Math.max(0L, exo.getCurrentPosition() - 30000L));
        } else if ("Rewind60".equals(action)) {
            exo.seekTo(Math.max(0L, exo.getCurrentPosition() - 60000L));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backDispatcher.onBackPressed();
            return true;
        }
        if (handleSteeringWheelKey(keyCode)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
