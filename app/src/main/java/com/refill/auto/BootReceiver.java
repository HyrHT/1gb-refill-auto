package com.refill.auto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Turns the timer back on after the phone restarts, if it was on before. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && Prefs.timerEnabled(context)) {
            try {
                RefillService.start(context);
            } catch (Exception e) {
                Prefs.log(context, "Could not restart timer after reboot: " + e.getMessage());
            }
        }
    }
}
