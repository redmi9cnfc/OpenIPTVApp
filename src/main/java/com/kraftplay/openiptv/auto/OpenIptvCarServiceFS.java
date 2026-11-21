package com.kraftplay.openiptv.auto;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.R;
import androidx.car.app.AppManager;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.SessionInfo;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.validation.HostValidator;

public class OpenIptvCarServiceFS extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    public int getMinCarApiLevel() {
        return 1;
    }

    @NonNull
    @Override
    public Session onCreateSession(@NonNull SessionInfo sessionInfo) {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                return new OpenIptvCarNavScreen(getCarContext());
            }
        };
    }

    public static class OpenIptvCarNavScreen extends Screen implements SurfaceCallback {

        private VirtualDisplay virtualDisplay;
        private CarVideoPresentation presentation;
        private Surface surface;
        private int surfaceWidth = 1280;
        private int surfaceHeight = 720;
        private int surfaceDensity = 160;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        private boolean isDragging = false;
        private float currentDragX = 0f;
        private float currentDragY = 0f;
        private long dragStartTime = 0;

        private Runnable pendingClickRunnable = null;
        private final Runnable endDragRunnable = this::endDrag;

        public OpenIptvCarNavScreen(@NonNull CarContext carContext) {
            super(carContext);
            try {
                carContext.getCarService(AppManager.class).setSurfaceCallback(this);
            } catch (Throwable ignored) {}

            try {
                carContext.getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        exitApp();
                    }
                });
            } catch (Throwable ignored) {}
        }

        private void exitApp() {
            mainHandler.post(() -> {
                releaseVirtualDisplay();
                try {
                    getCarContext().finishCarApp();
                } catch (Throwable ignored) {}
            });
        }

        @Override
        public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer) {
            surface = surfaceContainer.getSurface();
            if (surface == null || !surface.isValid()) return;

            surfaceWidth = surfaceContainer.getWidth() > 0 ? surfaceContainer.getWidth() : 1280;
            surfaceHeight = surfaceContainer.getHeight() > 0 ? surfaceContainer.getHeight() : 720;
            surfaceDensity = surfaceContainer.getDpi() > 0 ? surfaceContainer.getDpi() : 160;

            mainHandler.post(this::createVirtualDisplayAndPresentation);
        }

        private void createVirtualDisplayAndPresentation() {
            if (surface == null || !surface.isValid()) return;
            releaseVirtualDisplay();

            try {
                ContextThemeWrapper themedContext = new ContextThemeWrapper(getCarContext(), R.style.Theme_AppCompat_NoActionBar);
                DisplayManager dm = (DisplayManager) themedContext.getSystemService(Context.DISPLAY_SERVICE);
                if (dm == null) return;

                virtualDisplay = dm.createVirtualDisplay(
                        "AABrowserWideDisplay",
                        surfaceWidth,
                        surfaceHeight,
                        surfaceDensity,
                        surface,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                );

                if (virtualDisplay == null || virtualDisplay.getDisplay() == null) return;

                presentation = new CarVideoPresentation(themedContext, virtualDisplay.getDisplay());
                presentation.show();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        private void releaseVirtualDisplay() {
            cancelPendingClick();
            endDrag();
            if (presentation != null) {
                try {
                    presentation.dismiss();
                } catch (Throwable ignored) {}
                presentation = null;
            }
            if (virtualDisplay != null) {
                try {
                    virtualDisplay.release();
                } catch (Throwable ignored) {}
                virtualDisplay = null;
            }
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer) {
            surface = null;
            mainHandler.post(this::releaseVirtualDisplay);
        }

        private void cancelPendingClick() {
            if (pendingClickRunnable != null) {
                mainHandler.removeCallbacks(pendingClickRunnable);
                pendingClickRunnable = null;
            }
        }

        private void endDrag() {
            if (isDragging) {
                isDragging = false;
                mainHandler.removeCallbacks(endDragRunnable);
                if (presentation != null && presentation.getWindow() != null) {
                    long now = SystemClock.uptimeMillis();
                    MotionEvent up = MotionEvent.obtain(dragStartTime, now, MotionEvent.ACTION_UP, currentDragX, currentDragY, 0);
                    View decor = presentation.getWindow().getDecorView();
                    if (decor != null) {
                        decor.dispatchTouchEvent(up);
                    }
                    up.recycle();
                }
            }
        }

        @Override
        public void onClick(float x, float y) {
            mainHandler.post(() -> {
                if (isDragging) return;
                cancelPendingClick();

                pendingClickRunnable = () -> {
                    pendingClickRunnable = null;
                    if (isDragging) return;
                    if (presentation != null && presentation.getWindow() != null) {
                        long now = SystemClock.uptimeMillis();
                        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0);
                        View decor = presentation.getWindow().getDecorView();
                        if (decor != null) {
                            decor.dispatchTouchEvent(down);
                            decor.dispatchTouchEvent(up);
                        }
                        down.recycle();
                        up.recycle();
                    }
                };
                mainHandler.postDelayed(pendingClickRunnable, 75);
            });
        }

        @Override
        public void onScroll(float distanceX, float distanceY) {
            mainHandler.post(() -> {
                cancelPendingClick();
                if (presentation == null || presentation.getWindow() == null) return;

                mainHandler.removeCallbacks(endDragRunnable);
                long now = SystemClock.uptimeMillis();

                float scale = 0.8f;
                float stepX = distanceX * scale;
                float stepY = distanceY * scale;

                if (!isDragging) {
                    isDragging = true;
                    dragStartTime = now;
                    currentDragX = surfaceWidth / 2f;
                    currentDragY = surfaceHeight / 2f;

                    MotionEvent down = MotionEvent.obtain(dragStartTime, now, MotionEvent.ACTION_DOWN, currentDragX, currentDragY, 0);
                    View decor = presentation.getWindow().getDecorView();
                    if (decor != null) {
                        decor.dispatchTouchEvent(down);
                    }
                    down.recycle();
                }

                currentDragX = Math.max(10f, Math.min(surfaceWidth - 10f, currentDragX - stepX));
                currentDragY = Math.max(10f, Math.min(surfaceHeight - 10f, currentDragY - stepY));

                MotionEvent move = MotionEvent.obtain(dragStartTime, now, MotionEvent.ACTION_MOVE, currentDragX, currentDragY, 0);
                View decor = presentation.getWindow().getDecorView();
                if (decor != null) {
                    decor.dispatchTouchEvent(move);
                }
                move.recycle();

                mainHandler.postDelayed(endDragRunnable, 120);
            });
        }

        @Override
        public void onFling(float velocityX, float velocityY) {
            mainHandler.post(() -> {
                cancelPendingClick();
                if (presentation == null || presentation.getWindow() == null) return;
                onScroll(-velocityX / 18f, -velocityY / 18f);
            });
        }

        @NonNull
        @Override
        public Template onGetTemplate() {
            Action backAction = new Action.Builder()
                    .setIcon(CarIcon.BACK)
                    .setOnClickListener(this::exitApp)
                    .build();

            ActionStrip strip = new ActionStrip.Builder()
                    .addAction(backAction)
                    .build();

            ActionStrip mapStrip = new ActionStrip.Builder()
                    .addAction(new Action.Builder(Action.PAN).build())
                    .build();

            return new NavigationTemplate.Builder()
                    .setActionStrip(strip)
                    .setMapActionStrip(mapStrip)
                    .build();
        }
    }
}
