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
    private static volatile boolean running = false;

    /** True while the timer service is alive in this process. */
    public static boolean isRunning() { return running; }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!Prefs.timerEnabled(RefillService.this)) {
                stopSelf();
                return;
            }
            Prefs.setLastTimerSend(RefillService.this, System.currentTimeMillis());
            SmsSender.sendRefill(RefillService.this, "timer");
            handler.postDelayed(this, intervalMs());
        }
    };

    public static void start(Context c) {
        c.startForegroundService(new Intent(c, RefillService.class));
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, RefillService.class));
    }

    private long intervalMs() { return Prefs.intervalMinutes(this) * 60_000L; }

    /** Time until the next SMS is due, counted from the last one the timer sent. */
    private long delayUntilNext() {
        long since = System.currentTimeMillis() - Prefs.lastTimerSend(this);
        if (since < 0) return 0; // clock was changed
        return Math.max(0, intervalMs() - since);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
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
        boolean fresh = wakeLock == null;
        if (fresh) {
            PowerManager pm = getSystemService(PowerManager.class);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RefillAuto:timer");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(); // held until the timer is switched off (intentional)
        }
        // Send when due: right away if the last timer SMS is older than the interval,
        // otherwise wait out the rest. So restarts (reboot, app killed, settings changed)
        // never send early or twice.
        long delay = delayUntilNext();
        Prefs.log(this, (fresh ? "Timer started" : "Timer updated")
                + " (every " + Prefs.intervalMinutes(this) + " min"
                + (delay > 0 ? ", next in " + (delay + 999) / 1000 + " s)" : ")"));
        handler.postDelayed(tick, delay);
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
        running = false;
        handler.removeCallbacks(tick);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        Prefs.log(this, "Timer stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
