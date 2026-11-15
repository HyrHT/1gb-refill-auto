package com.refill.auto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;
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

        if (!matches(Prefs.senderFilter(context), Prefs.keywordFilter(context), sender, body.toString())) return;

        long now = System.currentTimeMillis();
        if (now - Prefs.lastAutoReply(context) < Prefs.AUTO_REPLY_COOLDOWN_MS) {
            Prefs.log(context, "Carrier SMS from " + sender + " ignored (replied less than 30 s ago)");
            return;
        }
        Prefs.setLastAutoReply(context, now);

        // Reply from the SIM the message arrived on (matters on dual-SIM phones), and
        // remember it so the timer uses the same SIM.
        int subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                intent.getIntExtra("subscription", -1));
        if (subId < 0) subId = -1;
        if (subId != -1) Prefs.setCarrierSubId(context, subId);

        // "Antworte einfach auf diese SMS": reply to the sender if it is a real number.
        String to = Prefs.replyToSender(context) && isPhoneNumber(sender) ? sender : null;
        Prefs.log(context, "Carrier SMS from " + sender + " received, replying");
        SmsSender.sendRefill(context, "carrier SMS", to, subId);
    }

    /** True for numbers like +4915112345678 or 80808; false for names like "Lidl Connect". */
    static boolean isPhoneNumber(String s) {
        return s != null && s.replaceAll("[\\s\\-()]", "").matches("\\+?\\d{3,15}");
    }

    static boolean matches(String senderFilter, String keywordFilter, String sender, String body) {
        senderFilter = senderFilter == null ? "" : senderFilter.trim();
        keywordFilter = keywordFilter == null ? "" : keywordFilter.trim();
        if (body == null) body = "";

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
