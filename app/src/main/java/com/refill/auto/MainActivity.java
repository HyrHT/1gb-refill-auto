package com.refill.auto;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_PERMS = 1;

    private EditText number, message, senderFilter, keywordFilter, interval;
    private Switch autoReply, replyToSender, timer;
    private TextView log, warning;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (sp, key) -> runOnUiThread(this::refreshLog);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        number = findViewById(R.id.number);
        message = findViewById(R.id.message);
        senderFilter = findViewById(R.id.sender_filter);
        keywordFilter = findViewById(R.id.keyword_filter);
        interval = findViewById(R.id.interval);
        autoReply = findViewById(R.id.auto_reply);
        replyToSender = findViewById(R.id.reply_to_sender);
        timer = findViewById(R.id.timer);
        log = findViewById(R.id.log);
        warning = findViewById(R.id.warning);

        number.setText(Prefs.number(this));
        message.setText(Prefs.message(this));
        senderFilter.setText(Prefs.senderFilter(this));
        keywordFilter.setText(Prefs.keywordFilter(this));
        interval.setText(String.valueOf(Prefs.intervalMinutes(this)));
        autoReply.setChecked(Prefs.autoReply(this));
        replyToSender.setChecked(Prefs.replyToSender(this));
        replyToSender.setOnCheckedChangeListener((b, on) -> Prefs.setReplyToSender(this, on));
        timer.setChecked(Prefs.timerEnabled(this));

        autoReply.setOnCheckedChangeListener((b, on) -> {
            save();
            Prefs.setAutoReply(this, on);
            Prefs.log(this, on ? "Auto-reply on" : "Auto-reply off");
        });

        timer.setOnCheckedChangeListener((b, on) -> {
            save();
            Prefs.setTimerEnabled(this, on);
            if (on) {
                if (!hasSmsPermission()) {
                    Toast.makeText(this, "Allow SMS permission first", Toast.LENGTH_LONG).show();
                    requestPermissions();
                }
                RefillService.start(this);
            } else {
                RefillService.stop(this);
            }
        });

        ((Button) findViewById(R.id.send_now)).setOnClickListener(v -> {
            save();
            SmsSender.sendRefill(this, "manual");
        });

        ((Button) findViewById(R.id.battery)).setOnClickListener(v -> askBatteryExemption());
        ((Button) findViewById(R.id.clear_log)).setOnClickListener(v -> Prefs.clearLog(this));

        requestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs.registerListener(this, prefsListener);
        refreshLog();
        refreshWarning();
    }

    @Override
    protected void onPause() {
        save();
        // If the interval or text changed while the timer runs, restart it with the new values.
        if (Prefs.timerEnabled(this) && settingsChangedSinceStart) {
            RefillService.start(this);
        }
        settingsChangedSinceStart = false;
        Prefs.unregisterListener(this, prefsListener);
        super.onPause();
    }

    private boolean settingsChangedSinceStart = false;

    private void save() {
        int mins;
        try {
            mins = Integer.parseInt(interval.getText().toString().trim());
        } catch (NumberFormatException e) {
            mins = 1;
        }
        boolean changed = !number.getText().toString().trim().equals(Prefs.number(this))
                || !message.getText().toString().equals(Prefs.message(this))
                || Math.max(1, mins) != Prefs.intervalMinutes(this);
        if (changed) settingsChangedSinceStart = true;

        Prefs.saveText(this,
                number.getText().toString(),
                message.getText().toString(),
                senderFilter.getText().toString(),
                keywordFilter.getText().toString(),
                mins);
    }

    private void refreshLog() {
        String text = Prefs.log(this);
        log.setText(text.isEmpty() ? "Nothing yet." : text);
    }

    private void refreshWarning() {
        List<String> problems = new ArrayList<>();
        if (!hasSmsPermission()) {
            problems.add("SMS permission is not granted, so nothing can be sent or detected. "
                    + "If Android blocks it, open App info → ⋮ → \"Allow restricted settings\", then try again.");
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            problems.add("Battery optimization is on: the timer may pause while the screen is off. "
                    + "Tap \"Let the timer run in the background\".");
        }
        warning.setVisibility(problems.isEmpty() ? View.GONE : View.VISIBLE);
        warning.setText(String.join("\n\n", problems));
    }

    private boolean hasSmsPermission() {
        return checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        needed.add(Manifest.permission.SEND_SMS);
        needed.add(Manifest.permission.RECEIVE_SMS);
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS);
        List<String> missing = new ArrayList<>();
        for (String p : needed) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshWarning();
    }

    private void askBatteryExemption() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }
}
