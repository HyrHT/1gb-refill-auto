package com.refill.auto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.Locale;

/**
 * Fires on every incoming SMS. If it looks like the carrier's message
 * (sender and keyword filters match), it replies with the refill SMS.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        if (!Prefs.autoReply(context)) return;

        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;

        String sender = parts[0].getDisplayOriginatingAddress();
        StringBuilder body = new StringBuilder();
        for (SmsMessage p : parts) {
            if (p.getDisplayMessageBody() != null) body.append(p.getDisplayMessageBody());
        }

        if (!matches(context, sender, body.toString())) return;

        long now = System.currentTimeMillis();
        if (now - Prefs.lastAutoReply(context) < Prefs.AUTO_REPLY_COOLDOWN_MS) {
            Prefs.log(context, "Carrier SMS from " + sender + " ignored (replied less than 30 s ago)");
            return;
        }
        Prefs.setLastAutoReply(context, now);
        Prefs.log(context, "Carrier SMS from " + sender + " received, replying");
        SmsSender.sendRefill(context, "carrier SMS");
    }

    static boolean matches(Context c, String sender, String body) {
        String senderFilter = Prefs.senderFilter(c);
        String keywordFilter = Prefs.keywordFilter(c);

        // Safety: with both filters blank we would answer every SMS, so do nothing.
        if (senderFilter.isEmpty() && keywordFilter.isEmpty()) return false;

        if (!senderFilter.isEmpty()) {
            if (sender == null) return false;
            if (!normalize(sender).contains(normalize(senderFilter))) return false;
        }
        if (!keywordFilter.isEmpty()) {
            String b = body.toLowerCase(Locale.ROOT);
            boolean any = false;
            // Comma-separated: any one keyword is enough.
            for (String k : keywordFilter.split(",")) {
                String kw = k.trim().toLowerCase(Locale.ROOT);
                if (!kw.isEmpty() && b.contains(kw)) { any = true; break; }
            }
            if (!any) return false;
        }
        return true;
    }

    /** Compares sender names/numbers ignoring case, spaces, dashes and the leading "+". */
    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\-+()]", "");
    }
}
