package com.refill.auto;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;

/** Sends the configured refill SMS. */
public final class SmsSender {
    private static int requestCode = 0;

    private SmsSender() {}

    /** @param reason short label for the log, e.g. "carrier SMS" or "timer". */
    public static void sendRefill(Context context, String reason) {
        Context c = context.getApplicationContext();
        String number = Prefs.number(c);
        String message = Prefs.message(c);

        if (number.isEmpty() || message.isEmpty()) {
            Prefs.log(c, "Not sent (" + reason + "): number or message is empty");
            return;
        }
        if (c.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Prefs.log(c, "Not sent (" + reason + "): SMS permission missing");
            return;
        }

        try {
            SmsManager sms = Build.VERSION.SDK_INT >= 31
                    ? c.getSystemService(SmsManager.class)
                    : SmsManager.getDefault();

            Intent sentIntent = new Intent(c, SmsSentReceiver.class)
                    .putExtra(SmsSentReceiver.EXTRA_DESC, "\"" + message + "\" to " + number + " (" + reason + ")");
            PendingIntent sentPi = PendingIntent.getBroadcast(c, nextRequestCode(), sentIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

            sms.sendTextMessage(number, null, message, sentPi, null);
        } catch (Exception e) {
            Prefs.log(c, "Send failed (" + reason + "): " + e.getMessage());
        }
    }

    private static synchronized int nextRequestCode() {
        requestCode = (requestCode + 1) % 100_000;
        return requestCode;
    }
}
