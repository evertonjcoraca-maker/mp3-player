package com.evertoncoraca.mp3player;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.function.Consumer;

final class VoiceUiController {
    private final AppCompatActivity activity;
    private final LibraryPrefs prefs;
    private final Button button;
    private final Consumer<String> searchConsumer;
    private final ActivityResultLauncher<String> microphonePermission;
    private boolean registered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (VoiceState.ACTION_CHANGED.equals(intent.getAction())) {
                updateButton(intent.getBooleanExtra(VoiceState.EXTRA_ENABLED, false));
            } else if (VoiceState.ACTION_SEARCH.equals(intent.getAction())) {
                String query = intent.getStringExtra(VoiceState.EXTRA_QUERY);
                if (query != null && !query.isBlank()) searchConsumer.accept(query);
            }
        }
    };

    VoiceUiController(AppCompatActivity activity, LibraryPrefs prefs, Consumer<String> searchConsumer) {
        this.activity = activity;
        this.prefs = prefs;
        this.searchConsumer = searchConsumer;
        button = activity.findViewById(R.id.voiceButton);
        microphonePermission = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) enableVoice();
                    else {
                        prefs.setVoiceEnabled(false);
                        updateButton(false);
                        Toast.makeText(activity, "Comando de voz desligado: permissão do microfone não concedida.", Toast.LENGTH_LONG).show();
                    }
                });
        button.setOnClickListener(v -> {
            if (prefs.voiceEnabled()) disableVoice();
            else requestOrEnable();
        });
        updateButton(prefs.voiceEnabled());
    }

    void onStart() {
        if (!registered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(VoiceState.ACTION_CHANGED);
            filter.addAction(VoiceState.ACTION_SEARCH);
            ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            registered = true;
        }

        if (!prefs.voiceConfigured()) requestOrEnable();
        else if (prefs.voiceEnabled()) startVoiceService();
        updateButton(prefs.voiceEnabled());
        consumePendingSearch();
    }

    void onStop() {
        if (registered) {
            try { activity.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) {}
            registered = false;
        }
    }

    void consumePendingSearch() {
        String query = prefs.consumePendingSearch();
        if (!query.isBlank()) searchConsumer.accept(query);
    }

    private void requestOrEnable() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            enableVoice();
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void enableVoice() {
        prefs.setVoiceEnabled(true);
        updateButton(true);
        startVoiceService();
    }

    private void disableVoice() {
        prefs.setVoiceEnabled(false);
        Intent intent = new Intent(activity, VoiceCommandService.class).setAction(VoiceCommandService.ACTION_STOP);
        activity.startService(intent);
        updateButton(false);
    }

    private void startVoiceService() {
        Intent intent = new Intent(activity, VoiceCommandService.class).setAction(VoiceCommandService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) activity.startForegroundService(intent);
        else activity.startService(intent);
    }

    private void updateButton(boolean enabled) {
        button.setText(enabled ? "🎙 ON" : "🎙 OFF");
        button.setTextColor(activity.getColor(enabled ? R.color.accent : R.color.muted));
    }
}
