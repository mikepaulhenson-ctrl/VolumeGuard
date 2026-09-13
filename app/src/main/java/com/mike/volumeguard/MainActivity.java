package com.mike.volumeguard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String PREFS = "volume_guard";
    private static final String KEY_TARGET = "target_percent";
    private static final String KEY_MINIMUM = "minimum_percent";
    private static final String KEY_ACK = "hearing_ack";

    private SharedPreferences prefs;
    private AudioManager audioManager;
    private TextView targetLabel;
    private TextView minimumLabel;
    private TextView currentLabel;
    private TextView statusLabel;
    private SeekBar targetSeek;
    private SeekBar minimumSeek;
    private CheckBox acknowledgement;
    private Button startButton;
    private Button stopButton;
    private Button restoreButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        requestNotificationsIfNeeded();
        setContentView(buildUi());
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private View buildUi() {
        int pad = dp(22);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(24), pad, dp(24));
        root.setBackgroundColor(Color.rgb(16, 20, 24));

        TextView title = text("Volume Guard", 30, true);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView subtitle = text("Protect against big unwanted volume drops without fighting normal manual adjustments.", 16, false);
        subtitle.setTextColor(Color.rgb(190, 196, 202));
        subtitle.setPadding(0, dp(8), 0, dp(22));
        root.addView(subtitle);

        statusLabel = text("Status: Off", 18, true);
        statusLabel.setTextColor(Color.WHITE);
        root.addView(statusLabel);

        currentLabel = text("Current media volume: —", 15, false);
        currentLabel.setTextColor(Color.rgb(190, 196, 202));
        currentLabel.setPadding(0, dp(7), 0, dp(24));
        root.addView(currentLabel);

        targetLabel = text("Preferred restore volume: 80%", 19, true);
        targetLabel.setTextColor(Color.WHITE);
        root.addView(targetLabel);

        targetSeek = new SeekBar(this);
        targetSeek.setMax(100);
        targetSeek.setProgress(prefs.getInt(KEY_TARGET, 80));
        targetSeek.setPadding(0, dp(6), 0, dp(4));
        targetSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int target = Math.max(1, progress);
                targetLabel.setText("Preferred restore volume: " + target + "%");
                if (fromUser) {
                    if (minimumSeek != null && minimumSeek.getProgress() > target) {
                        minimumSeek.setProgress(target);
                        prefs.edit().putInt(KEY_MINIMUM, target).apply();
                    }
                    prefs.edit().putInt(KEY_TARGET, target).apply();
                    sendSettingsUpdate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        root.addView(targetSeek);

        TextView targetHelp = text("When Guard intervenes, it restores the volume to this level.", 13, false);
        targetHelp.setTextColor(Color.rgb(160, 168, 176));
        targetHelp.setPadding(0, 0, 0, dp(18));
        root.addView(targetHelp);

        minimumLabel = text("Minimum protected volume: 50%", 19, true);
        minimumLabel.setTextColor(Color.WHITE);
        root.addView(minimumLabel);

        minimumSeek = new SeekBar(this);
        minimumSeek.setMax(100);
        int initialTarget = Math.max(1, prefs.getInt(KEY_TARGET, 80));
        int initialMinimum = Math.max(1, Math.min(initialTarget, prefs.getInt(KEY_MINIMUM, 50)));
        minimumSeek.setProgress(initialMinimum);
        minimumSeek.setPadding(0, dp(6), 0, dp(4));
        minimumSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int minimum = Math.max(1, progress);
                minimumLabel.setText("Minimum protected volume: " + minimum + "%");
                if (fromUser) {
                    if (targetSeek != null && minimum > targetSeek.getProgress()) {
                        targetSeek.setProgress(minimum);
                        prefs.edit().putInt(KEY_TARGET, minimum).apply();
                    }
                    prefs.edit().putInt(KEY_MINIMUM, minimum).apply();
                    sendSettingsUpdate();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        root.addView(minimumSeek);

        TextView minimumHelp = text(
                "Guard ignores volume changes at or above this level. If volume falls below it, Guard restores your preferred volume.",
                13,
                false
        );
        minimumHelp.setTextColor(Color.rgb(160, 168, 176));
        minimumHelp.setPadding(0, 0, 0, dp(16));
        root.addView(minimumHelp);

        TextView warning = text(
                "Hearing safety: sustained high volume can permanently damage hearing. Volume Guard does not disable Android's sound-dose tracking or warnings.",
                14,
                false
        );
        warning.setTextColor(Color.rgb(255, 205, 120));
        warning.setPadding(0, dp(8), 0, dp(12));
        root.addView(warning);

        acknowledgement = new CheckBox(this);
        acknowledgement.setText("I understand the hearing-risk warning");
        acknowledgement.setTextColor(Color.WHITE);
        acknowledgement.setChecked(prefs.getBoolean(KEY_ACK, false));
        acknowledgement.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_ACK, isChecked).apply();
            updateButtons();
        });
        root.addView(acknowledgement);

        startButton = button("START GUARD");
        startButton.setOnClickListener(v -> startGuard());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        buttonParams.setMargins(0, dp(18), 0, dp(10));
        root.addView(startButton, buttonParams);

        stopButton = button("STOP GUARD");
        stopButton.setOnClickListener(v -> stopGuard());
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        stopParams.setMargins(0, 0, 0, dp(10));
        root.addView(stopButton, stopParams);

        restoreButton = button("RESTORE NOW");
        restoreButton.setOnClickListener(v -> restoreNow());
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        root.addView(restoreButton, restoreParams);

        TextView note = text(
                "Example: Preferred 85%, Minimum 55%. You can manually lower the volume to 70% and Guard leaves it alone. If it drops below 55%, Guard returns it to 85%.",
                13,
                false
        );
        note.setTextColor(Color.rgb(160, 168, 176));
        note.setPadding(0, dp(20), 0, 0);
        root.addView(note);

        return root;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private void startGuard() {
        if (!acknowledgement.isChecked()) {
            Toast.makeText(this, "Please acknowledge the hearing-risk warning first.", Toast.LENGTH_SHORT).show();
            return;
        }

        int target = Math.max(1, targetSeek.getProgress());
        int minimum = Math.max(1, Math.min(target, minimumSeek.getProgress()));
        prefs.edit().putInt(KEY_TARGET, target).putInt(KEY_MINIMUM, minimum).apply();

        Intent intent = new Intent(this, VolumeGuardService.class);
        intent.setAction(VolumeGuardService.ACTION_START);
        intent.putExtra(VolumeGuardService.EXTRA_TARGET_PERCENT, target);
        intent.putExtra(VolumeGuardService.EXTRA_MINIMUM_PERCENT, minimum);
        startForegroundService(intent);

        statusLabel.setText("Status: Starting…");
        statusLabel.postDelayed(this::refreshUi, 500);
    }

    private void stopGuard() {
        Intent intent = new Intent(this, VolumeGuardService.class);
        intent.setAction(VolumeGuardService.ACTION_STOP);
        startService(intent);
        statusLabel.postDelayed(this::refreshUi, 300);
    }

    private void restoreNow() {
        if (!acknowledgement.isChecked()) {
            Toast.makeText(this, "Please acknowledge the hearing-risk warning first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (audioManager.isVolumeFixed()) {
            Toast.makeText(this, "Android reports this device as fixed-volume.", Toast.LENGTH_LONG).show();
            return;
        }

        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int target = Math.max(1, targetSeek.getProgress());
        int targetIndex = Math.max(1, Math.round(max * (target / 100f)));
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI);
            Toast.makeText(this, "Media volume restored to about " + target + "%.", Toast.LENGTH_SHORT).show();
            refreshUi();
        } catch (SecurityException e) {
            Toast.makeText(this, "Android blocked the volume change.", Toast.LENGTH_LONG).show();
        }
    }

    private void sendSettingsUpdate() {
        if (!VolumeGuardService.isRunning()) return;
        int target = Math.max(1, targetSeek.getProgress());
        int minimum = Math.max(1, Math.min(target, minimumSeek.getProgress()));

        Intent intent = new Intent(this, VolumeGuardService.class);
        intent.setAction(VolumeGuardService.ACTION_UPDATE);
        intent.putExtra(VolumeGuardService.EXTRA_TARGET_PERCENT, target);
        intent.putExtra(VolumeGuardService.EXTRA_MINIMUM_PERCENT, minimum);
        startService(intent);
    }

    private void refreshUi() {
        int target = Math.max(1, prefs.getInt(KEY_TARGET, 80));
        int minimum = Math.max(1, Math.min(target, prefs.getInt(KEY_MINIMUM, 50)));

        if (targetSeek != null && targetSeek.getProgress() != target) targetSeek.setProgress(target);
        if (minimumSeek != null && minimumSeek.getProgress() != minimum) minimumSeek.setProgress(minimum);
        if (targetLabel != null) targetLabel.setText("Preferred restore volume: " + target + "%");
        if (minimumLabel != null) minimumLabel.setText("Minimum protected volume: " + minimum + "%");

        if (audioManager != null) {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int pct = max > 0 ? Math.round(100f * current / max) : 0;
            currentLabel.setText("Current media volume: " + pct + "%");
        }

        boolean running = VolumeGuardService.isRunning();
        statusLabel.setText(running ? "Status: Guard active" : "Status: Off");
        updateButtons();
    }

    private void updateButtons() {
        boolean running = VolumeGuardService.isRunning();
        boolean ack = acknowledgement != null && acknowledgement.isChecked();
        if (startButton != null) startButton.setEnabled(!running && ack);
        if (stopButton != null) stopButton.setEnabled(running);
        if (restoreButton != null) restoreButton.setEnabled(ack);
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
