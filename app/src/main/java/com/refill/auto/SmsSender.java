package com.refill.auto;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Sends the configured refill SMS. */
public final class SmsSender {
    /** Seeded from the clock so request codes don't repeat after the app restarts. */
    private static final AtomicInteger requestCode =
            new AtomicInteger((int) (System.currentTimeMillis() & 0x3FFFFFFF));

    private SmsSender() {}

    /** @param reason short label for the log, e.g. "timer". */
    public static void sendRefill(Context context, String reason) {
        sendRefill(context, reason, null, Prefs.carrierSubId(context));
    }

    /**
     * @param to    number to text; null means the number from the settings.
     * @param subId SIM to send from; -1 means the phone's default SMS SIM.
     */
    public static void sendRefill(Context context, String reason, String to, int subId) {
        Context c = context.getApplicationContext();
        String number = to != null ? to : Prefs.number(c);
        String message = Prefs.message(c);

        if (number.isEmpty() || message.isEmpty()) {
            Prefs.log(c, "Not sent (" + reason + "): number or message is empty");
            return;
        }
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Prefs.log(c, "Not sent (" + reason + "): SMS permission missing");
            return;
        }

        String desc = "\"" + message + "\" to " + number + " (" + reason + ")";
        try {
            send(managerFor(c, subId), c, number, message, desc);
        } catch (Exception e) {
            if (subId == -1) {
                Prefs.log(c, "Send failed (" + reason + "): " + e);
                return;
            }
            // The remembered SIM may have been removed: try the default SIM once.
            try {
                send(managerFor(c, -1), c, number, message, desc + " [default SIM]");
            } catch (Exception e2) {
                Prefs.log(c, "Send failed (" + reason + "): " + e2);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static SmsManager managerFor(Context c, int subId) {
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager m = c.getSystemService(SmsManager.class);
            return subId == -1 ? m : m.createForSubscriptionId(subId);
        }
        return subId == -1 ? SmsManager.getDefault() : SmsManager.getSmsManagerForSubscriptionId(subId);
    }

    private static void send(SmsManager sms, Context c, String number, String message, String desc) {
        ArrayList<String> parts = sms.divideMessage(message);
        ArrayList<PendingIntent> sent = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            // Only log the last part, so a multi-part SMS gives one log line.
            sent.add(i == parts.size() - 1 ? sentIntent(c, desc) : null);
        }
        if (parts.size() == 1) {
            sms.sendTextMessage(number, null, message, sent.get(0), null);
        } else {
            sms.sendMultipartTextMessage(number, null, parts, sent, null);
        }
    }

    private static PendingIntent sentIntent(Context c, String desc) {
        Intent i = new Intent(c, SmsSentReceiver.class).putExtra(SmsSentReceiver.EXTRA_DESC, desc);
        return PendingIntent.getBroadcast(c, requestCode.incrementAndGet(), i,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
    }
}
