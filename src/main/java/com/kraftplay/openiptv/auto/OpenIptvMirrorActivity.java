package com.kraftplay.openiptv.auto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.kraftplay.openiptv.R;

import com.google.android.apps.auto.sdk.CarActivity;

public class OpenIptvMirrorActivity extends CarActivity implements SurfaceHolder.Callback {

    private Context getCtx() {
        return this;
    }

    private FrameLayout rootContainer;
    private SurfaceView surfaceView;
    private LinearLayout statusBanner;
    private TextView statusText;

    private VirtualDisplay virtualDisplay;
    private int containerWidth = 0;
    private int containerHeight = 0;
    private int phoneWidth = 1080;
    private int phoneHeight = 2400;

    private DisplayManager displayManager;

    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayRemoved(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                updatePhoneResolution();
                recalculateLayoutAndDisplay();
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat_NoActionBar);
        super.onCreate(savedInstanceState);

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

        if (c() != null && c().getDecorView() != null && isFullscreen) {
            int fullScreenFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            c().getDecorView().setSystemUiVisibility(fullScreenFlags);
        }

        displayManager = (DisplayManager) getCtx().getSystemService(Context.DISPLAY_SERVICE);
        updatePhoneResolution();

        rootContainer = new FrameLayout(getCtx());
        rootContainer.setBackgroundColor(Color.BLACK);
        rootContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int newW = right - left;
            int newH = bottom - top;
            if (newW > 0 && newH > 0 && (newW != (oldRight - oldLeft) || newH != (oldBottom - oldTop))) {
                containerWidth = newW;
                containerHeight = newH;
                recalculateLayoutAndDisplay();
            }
        });

        surfaceView = new SurfaceView(getCtx());
        surfaceView.getHolder().addCallback(this);
        surfaceView.setOnTouchListener((v, event) -> {
            if (v.getWidth() > 0 && v.getHeight() > 0 && TouchInjectionService.isEnabled()) {
                float scaleX = (float) phoneWidth / (float) v.getWidth();
                float scaleY = (float) phoneHeight / (float) v.getHeight();
                MotionEvent mapped = MotionEvent.obtain(event);
                mapped.setLocation(event.getX() * scaleX, event.getY() * scaleY);
                TouchInjectionService.dispatchMotionEvent(mapped);
                mapped.recycle();
            }
            return true;
        });

        rootContainer.addView(
                surfaceView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                )
        );

        statusText = new TextView(getCtx());
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15f);
        statusText.setText(getString(R.string.mirror_unlock_phone));

        Button actionBtn = new Button(getCtx());
        actionBtn.setText(getString(R.string.mirror_enable_control));
        actionBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getCtx().startActivity(intent);
        });

        statusBanner = new LinearLayout(getCtx());
        statusBanner.setOrientation(LinearLayout.HORIZONTAL);
        statusBanner.setGravity(Gravity.CENTER_VERTICAL);
        statusBanner.setBackgroundColor(0xCC1A1A24);
        statusBanner.setPadding(24, 14, 24, 14);
        statusBanner.addView(statusText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        statusBanner.addView(actionBtn);

        rootContainer.addView(
                statusBanner,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM
                )
        );

        setContentView(rootContainer);
        if (displayManager != null) {
            displayManager.registerDisplayListener(displayListener, new Handler(Looper.getMainLooper()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatusBanner();
    }

    private void updateStatusBanner() {
        boolean hasProjection = MirrorProjectionService.mediaProjection != null;
        boolean hasTouch = TouchInjectionService.isEnabled();

        if (!hasProjection) {
            statusBanner.setVisibility(View.VISIBLE);
            statusText.setText(getString(R.string.mirror_confirm_projection));
        } else if (!hasTouch) {
            statusBanner.setVisibility(View.VISIBLE);
            statusText.setText(getString(R.string.mirror_enable_accessibility));
        } else {
            statusBanner.setVisibility(View.GONE);
        }
    }

    @SuppressWarnings("deprecation")
    private void updatePhoneResolution() {
        WindowManager wm = (WindowManager) getCtx().getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Point size = new Point();
            wm.getDefaultDisplay().getRealSize(size);
            if (size.x > 0 && size.y > 0) {
                phoneWidth = size.x;
                phoneHeight = size.y;
            }
        }
    }

    private void recalculateLayoutAndDisplay() {
        if (containerWidth <= 0 || containerHeight <= 0 || phoneWidth <= 0 || phoneHeight <= 0) return;

        float scale = Math.min(
                (float) containerWidth / (float) phoneWidth,
                (float) containerHeight / (float) phoneHeight
        );
        int targetW = Math.max(1, Math.round(phoneWidth * scale));
        int targetH = Math.max(1, Math.round(phoneHeight * scale));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) surfaceView.getLayoutParams();
        if (lp.width != targetW || lp.height != targetH) {
            lp.width = targetW;
            lp.height = targetH;
            lp.gravity = Gravity.CENTER;
            surfaceView.setLayoutParams(lp);
        }

        if (virtualDisplay != null && surfaceView.getHolder().getSurface() != null && surfaceView.getHolder().getSurface().isValid()) {
            int dpi = getCtx().getResources().getDisplayMetrics().densityDpi;
            virtualDisplay.resize(targetW, targetH, dpi);
            virtualDisplay.setSurface(surfaceView.getHolder().getSurface());
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        MirrorProjectionService.requestProjection(getCtx(), mp -> {
            updateStatusBanner();
            if (mp == null || !holder.getSurface().isValid()) return;

            int w = surfaceView.getWidth() > 0 ? surfaceView.getWidth() : 1280;
            int h = surfaceView.getHeight() > 0 ? surfaceView.getHeight() : 720;
            int dpi = getCtx().getResources().getDisplayMetrics().densityDpi;

            if (virtualDisplay != null) {
                virtualDisplay.release();
            }
            virtualDisplay = mp.createVirtualDisplay(
                    "OpenIPTV-Mirror",
                    w,
                    h,
                    dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    holder.getSurface(),
                    null,
                    null
            );
            recalculateLayoutAndDisplay();
        });
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (width > 0 && height > 0 && virtualDisplay != null) {
            virtualDisplay.resize(width, height, getCtx().getResources().getDisplayMetrics().densityDpi);
            virtualDisplay.setSurface(holder.getSurface());
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (TouchInjectionService.dispatchBack()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onDestroy() {
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        MirrorProjectionService.stopAll(getCtx());
        super.onDestroy();
    }
}
