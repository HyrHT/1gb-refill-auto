package com.refill.auto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Foreground service that sends the refill SMS every N minutes while the
 * "send every minute" switch is on. A foreground service with a wake lock is
 * used because Android's alarm APIs won't reliably fire every 60 seconds.
 */
public class RefillService extends Service {
    private static final String CHANNEL_ID = "refill_timer";
    private static final int NOTIFICATION_ID = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!Prefs.timerEnabled(RefillService.this)) {
                stopSelf();
                return;
            }
            SmsSender.sendRefill(RefillService.this, "timer");
            handler.postDelayed(this, Prefs.intervalMinutes(RefillService.this) * 60_000L);
        }
    };

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, RefillService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, RefillService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }

        if (!Prefs.timerEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        handler.removeCallbacks(tick);
        if (wakeLock == null) {
            // Fresh start: send right away, then every interval.
            PowerManager pm = getSystemService(PowerManager.class);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RefillAuto:timer");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(); // held until the timer is switched off (intentional)
            Prefs.log(this, "Timer started (every " + Prefs.intervalMinutes(this) + " min)");
            handler.post(tick);
        } else {
            // Already running (settings changed): pick up the new interval without an extra SMS.
            Prefs.log(this, "Timer updated (every " + Prefs.intervalMinutes(this) + " min)");
            handler.postDelayed(tick, Prefs.intervalMinutes(this) * 60_000L);
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        int mins = Prefs.intervalMinutes(this);
        String text = "Texting \"" + Prefs.message(this) + "\" to " + Prefs.number(this)
                + (mins == 1 ? " every minute" : " every " + mins + " minutes");
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(getString(R.string.app_name) + " timer on")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        Prefs.log(this, "Timer stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
