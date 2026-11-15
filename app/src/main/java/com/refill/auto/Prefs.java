package com.refill.auto;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** All app settings plus a small activity log, stored in SharedPreferences. */
public final class Prefs {
    public static final String DEFAULT_NUMBER = "+4980808";
    public static final String DEFAULT_MESSAGE = "Refill";
    /** Carrier messages are matched when the sender contains this text (blank = any sender). */
    public static final String DEFAULT_SENDER_FILTER = "";
    /**
     * ...and the message body contains one of these (comma-separated, blank = any text).
     * Matches Lidl Connect's "Deine Unlimited-1GB Option ist fast verbraucht ... antworte
     * einfach auf diese SMS mit Refill" message.
     */
    public static final String DEFAULT_KEYWORD_FILTER = "Unlimited-1GB, mit Refill";
    /** Minimum gap between two automatic replies, so the app can never loop. */
    public static final long AUTO_REPLY_COOLDOWN_MS = 30_000;

    private static final String FILE = "refill_prefs";
    private static final int MAX_LOG_LINES = 100;

    private static final String K_NUMBER = "number";
    private static final String K_MESSAGE = "message";
    private static final String K_SENDER = "sender_filter";
    private static final String K_KEYWORD = "keyword_filter";
    private static final String K_AUTO_REPLY = "auto_reply";
    private static final String K_REPLY_TO_SENDER = "reply_to_sender";
    private static final String K_TIMER = "timer_enabled";
    private static final String K_INTERVAL = "interval_minutes";
    private static final String K_LAST_AUTO = "last_auto_reply";
    private static final String K_LOG = "log";

    private Prefs() {}

    private static final String K_SCHEMA = "schema";
    private static boolean migrated = false;

    private static SharedPreferences sp(Context c) {
        SharedPreferences sp = c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (!migrated) {
            migrated = true;
            // v1.0 defaulted to sender "80808" with no keyword; switch untouched v1.0 settings
            // to matching the Lidl Connect message text instead.
            if (sp.getInt(K_SCHEMA, 1) < 2) {
                SharedPreferences.Editor e = sp.edit().putInt(K_SCHEMA, 2);
                if ("80808".equals(sp.getString(K_SENDER, null)) && sp.getString(K_KEYWORD, "").isEmpty()) {
                    e.putString(K_SENDER, DEFAULT_SENDER_FILTER).putString(K_KEYWORD, DEFAULT_KEYWORD_FILTER);
                }
                e.apply();
            }
        }
        return sp;
    }

    public static String number(Context c) { return sp(c).getString(K_NUMBER, DEFAULT_NUMBER); }
    public static String message(Context c) { return sp(c).getString(K_MESSAGE, DEFAULT_MESSAGE); }
    public static String senderFilter(Context c) { return sp(c).getString(K_SENDER, DEFAULT_SENDER_FILTER); }
    public static String keywordFilter(Context c) { return sp(c).getString(K_KEYWORD, DEFAULT_KEYWORD_FILTER); }
    public static boolean autoReply(Context c) { return sp(c).getBoolean(K_AUTO_REPLY, true); }
    /** Reply to the number the carrier SMS came from, instead of the fixed number. */
    public static boolean replyToSender(Context c) { return sp(c).getBoolean(K_REPLY_TO_SENDER, true); }
    public static boolean timerEnabled(Context c) { return sp(c).getBoolean(K_TIMER, false); }
    public static int intervalMinutes(Context c) { return Math.max(1, sp(c).getInt(K_INTERVAL, 1)); }
    public static long lastAutoReply(Context c) { return sp(c).getLong(K_LAST_AUTO, 0); }

    public static void saveText(Context c, String number, String message, String sender, String keyword, int interval) {
        sp(c).edit()
                .putString(K_NUMBER, number.trim())
                .putString(K_MESSAGE, message)
                .putString(K_SENDER, sender.trim())
                .putString(K_KEYWORD, keyword.trim())
                .putInt(K_INTERVAL, Math.max(1, interval))
                .apply();
    }

    public static void setAutoReply(Context c, boolean on) { sp(c).edit().putBoolean(K_AUTO_REPLY, on).apply(); }
    public static void setReplyToSender(Context c, boolean on) { sp(c).edit().putBoolean(K_REPLY_TO_SENDER, on).apply(); }
    public static void setTimerEnabled(Context c, boolean on) { sp(c).edit().putBoolean(K_TIMER, on).apply(); }
    public static void setLastAutoReply(Context c, long t) { sp(c).edit().putLong(K_LAST_AUTO, t).apply(); }

    public static synchronized void log(Context c, String line) {
        String stamp = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(new Date());
        String old = sp(c).getString(K_LOG, "");
        StringBuilder sb = new StringBuilder(stamp).append("  ").append(line);
        if (!old.isEmpty()) {
            String[] lines = old.split("\n");
            for (int i = 0; i < Math.min(lines.length, MAX_LOG_LINES - 1); i++) {
                sb.append('\n').append(lines[i]);
            }
        }
        sp(c).edit().putString(K_LOG, sb.toString()).apply();
    }

    public static String log(Context c) { return sp(c).getString(K_LOG, ""); }
    public static void clearLog(Context c) { sp(c).edit().remove(K_LOG).apply(); }

    public static void registerListener(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) {
        sp(c).registerOnSharedPreferenceChangeListener(l);
    }

    public static void unregisterListener(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) {
        sp(c).unregisterOnSharedPreferenceChangeListener(l);
    }
}
