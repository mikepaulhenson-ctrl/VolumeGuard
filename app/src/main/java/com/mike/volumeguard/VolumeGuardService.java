package com.mike.volumeguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class VolumeGuardService extends Service {

    public static final String ACTION_START = "com.mike.volumeguard.action.START";
    public static final String ACTION_STOP = "com.mike.volumeguard.action.STOP";
    public static final String ACTION_UPDATE = "com.mike.volumeguard.action.UPDATE";
    public static final String EXTRA_TARGET_PERCENT = "target_percent";

    private static final String PREFS = "volume_guard";
    private static final String KEY_TARGET = "target_percent";
    private static final String CHANNEL_ID = "volume_guard_active";
    private static final int NOTIFICATION_ID = 4207;
    private static final long POLL_MS = 700L;

    private AudioManager audioManager;
    private Handler handler;
    private SharedPreferences prefs;
    private int targetPercent = 80;
    private boolean active;
    private static volatile boolean running;

    private final Runnable volumeWatcher = new Runnable() {
        @Override
        public void run() {
            if (!active) return;
            enforceTargetIfNeeded();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        targetPercent = clampPercent(prefs.getInt(KEY_TARGET, 80));
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopGuard();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra(EXTRA_TARGET_PERCENT)) {
            targetPercent = clampPercent(intent.getIntExtra(EXTRA_TARGET_PERCENT, targetPercent));
            prefs.edit().putInt(KEY_TARGET, targetPercent).apply();
        }

        if (ACTION_UPDATE.equals(action)) {
            if (active) {
                updateNotification();
                enforceTargetIfNeeded();
            }
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action) || action == null) {
            startGuard();
        }

        return START_NOT_STICKY;
    }

    private void startGuard() {
        if (active) {
            updateNotification();
            return;
        }

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        active = true;
        running = true;
        handler.removeCallbacks(volumeWatcher);
        handler.post(volumeWatcher);
    }

    private void stopGuard() {
        active = false;
        handler.removeCallbacks(volumeWatcher);
        running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void enforceTargetIfNeeded() {
        if (audioManager == null || audioManager.isVolumeFixed()) return;

        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (max <= 0) return;

        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int desired = Math.max(1, Math.round(max * (targetPercent / 100f)));

        if (current < desired) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, desired, 0);
            } catch (SecurityException ignored) {
            }
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, VolumeGuardService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Action stopAction = new Notification.Action.Builder(
                null,
                "Stop",
                stopPending
        ).build();

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(R.drawable.ic_volume_guard)
                .setContentTitle("Volume Guard is active")
                .setContentText("Restoring media volume below " + targetPercent + "%")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(stopAction)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Volume Guard",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Shows when Volume Guard is actively watching media volume.");
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setLightColor(Color.TRANSPARENT);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private int clampPercent(int value) {
        return Math.max(1, Math.min(100, value));
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onDestroy() {
        active = false;
        if (handler != null) handler.removeCallbacksAndMessages(null);
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
