package com.kraftplay.openiptv.auto;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.kraftplay.openiptv.R;

public class MirrorProjectionService extends Service {

    public interface ProjectionCallback {
        void onProjectionReady(MediaProjection mediaProjection);
    }

    private static final String CHANNEL_ID = "openiptv_mirror_channel";
    private static final int NOTIF_ID = 2024;

    public static MediaProjection mediaProjection = null;
    private static ProjectionCallback pendingCallback = null;

    private PowerManager.WakeLock wakeLock = null;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            super.onStop();
            mediaProjection = null;
            if (wakeLock != null && wakeLock.isHeld()) {
                try { wakeLock.release(); } catch (Throwable ignored) {}
            }
            wakeLock = null;
        }
    };

    public static void requestProjection(Context context, ProjectionCallback callback) {
        if (mediaProjection != null) {
            callback.onProjectionReady(mediaProjection);
        } else {
            pendingCallback = callback;
            Intent intent = new Intent(context, MirrorProjectionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void onPermissionResult(Context context, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Intent intent = new Intent(context, MirrorProjectionService.class);
            intent.putExtra("resultCode", resultCode);
            intent.putExtra("data", data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        }
    }

    public static void stopAll(Context context) {
        try {
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        } catch (Throwable ignored) {}
        try {
            Intent intent = new Intent(context, MirrorProjectionService.class);
            context.stopService(intent);
        } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", 0);
            Intent data = intent.getParcelableExtra("data");

            if (resultCode != 0 && data != null) {
                startForegroundNotification();
                acquireScreenWakeLock();

                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (mpm != null) {
                    try {
                        mediaProjection = mpm.getMediaProjection(resultCode, data);
                        if (mediaProjection != null) {
                            mediaProjection.registerCallback(projectionCallback, new Handler(Looper.getMainLooper()));
                            if (pendingCallback != null) {
                                ProjectionCallback cb = pendingCallback;
                                pendingCallback = null;
                                cb.onProjectionReady(mediaProjection);
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }
        }
        return START_STICKY;
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "OpenMirror",
                        NotificationManager.IMPORTANCE_LOW
                );
                nm.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenMirror")
            .setContentText("Трансляция экрана на магнитолу")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void acquireScreenWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "OpenIPTV:MirrorWakeLock"
            );
            wakeLock.acquire(10 * 60 * 1000L);
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (mediaProjection != null) {
                mediaProjection.unregisterCallback(projectionCallback);
                mediaProjection.stop();
            }
        } catch (Throwable ignored) {}
        mediaProjection = null;

        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Throwable ignored) {}
        }
        wakeLock = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
