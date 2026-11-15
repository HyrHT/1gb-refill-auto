package com.refill.auto;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

/** Logs whether the phone actually managed to send each refill SMS. */
public class SmsSentReceiver extends BroadcastReceiver {
    public static final String EXTRA_DESC = "desc";

    @Override
    public void onReceive(Context context, Intent intent) {
        String desc = intent.getStringExtra(EXTRA_DESC);
        int code = getResultCode();
        String result;
        if (code == Activity.RESULT_OK) {
            result = "Sent ";
        } else if (code == SmsManager.RESULT_ERROR_NO_SERVICE) {
            result = "FAILED (no service): ";
        } else if (code == SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED
                || code == SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED) {
            result = "FAILED (Android blocked texting a short number. Allow it: tap Send with "
                    + "\"Remember my choice\" on the prompt, or Settings → Apps → Special app "
                    + "access → Premium SMS access → Refill Auto → Always allow): ";
        } else if (code == SmsManager.RESULT_RIL_NETWORK_ERR
                || code == SmsManager.RESULT_RIL_NETWORK_REJECT
                || code == SmsManager.RESULT_NETWORK_REJECT) {
            result = "FAILED (network refused it, error " + code + ". Check the number: "
                    + "use 80808, not +4980808): ";
        } else if (code == SmsManager.RESULT_ERROR_RADIO_OFF) {
            result = "FAILED (radio off / airplane mode): ";
        } else {
            result = "FAILED (error " + code + "): ";
        }
        Prefs.log(context, result + desc);
    }
}
