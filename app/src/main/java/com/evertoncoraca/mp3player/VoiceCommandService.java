package com.evertoncoraca.mp3player;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

public final class VoiceCommandService extends Service implements VoiceCommandExecutor.Host {
    public static final String ACTION_START = "com.evertoncoraca.mp3player.voice.START";
    public static final String ACTION_STOP = "com.evertoncoraca.mp3player.voice.STOP";
    private static final String CHANNEL_ID = "voice_commands";
    private static final int NOTIFICATION_ID = 2002;
    private static final long RESTART_DELAY_MS = 250L;
    private static final long ERROR_RESTART_DELAY_MS = 800L;

    private final Handler main = new Handler(Looper.getMainLooper());

    private LibraryPrefs prefs;
    private AndroidCommandRecognizer commandRecognizer;
    private VoiceCommandParser parser;
    private RecognizedCommandSelector selector;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private VoiceCommandExecutor executor;
    private String localeTag = "pt-BR";
    private boolean started;
    private boolean commandInProgress;

    @Override public void onCreate() {
        super.onCreate();
        prefs = new LibraryPrefs(this);
        parser = new VoiceCommandParser();
        selector = new RecognizedCommandSelector(parser);
        commandRecognizer = new AndroidCommandRecognizer(this);

        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                executor = new VoiceCommandExecutor(this, controller, this);
                if (started) scheduleListening(0L);
            } catch (Exception e) {
                disableBecauseUnavailable();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            disableVoice();
            return START_NOT_STICKY;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            disableBecauseUnavailable();
            return START_NOT_STICKY;
        }
        startVoiceForeground();
        started = true;
        prefs.setVoiceEnabled(true);
        VoiceState.broadcast(this, true);
        if (controller != null) scheduleListening(0L);
        return START_NOT_STICKY;
    }

    private void startVoiceForeground() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Comando de voz", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Mantém o comando de voz do Everton MP3 Player ativo");
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_placeholder)
                .setContentTitle("Everton MP3 Player")
                .setContentText("Comando de voz ativo — diga “Player” + o comando")
                .setOngoing(true)
                .setContentIntent(pending)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void scheduleListening(long delayMs) {
        main.removeCallbacks(this::beginListening);
        main.postDelayed(this::beginListening, Math.max(0L, delayMs));
    }

    private void beginListening() {
        if (!started || commandInProgress || executor == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            disableBecauseUnavailable();
            return;
        }

        commandInProgress = true;
        commandRecognizer.listen(localeTag, new AndroidCommandRecognizer.Callback() {
            @Override public void onResults(List<String> texts) {
                VoiceCommand command = selector.select(texts);
                if (command == null || executor == null) {
                    finishRecognition(RESTART_DELAY_MS);
                    return;
                }
                executor.execute(command);
            }

            @Override public void onError(String message, boolean fatal) {
                if (fatal) disableBecauseUnavailable();
                else finishRecognition(ERROR_RESTART_DELAY_MS);
            }
        });
    }

    private void finishRecognition(long delayMs) {
        commandInProgress = false;
        if (started) scheduleListening(delayMs);
    }

    @Override public void setLanguage(String localeTag) {
        if ("en-US".equalsIgnoreCase(localeTag)) this.localeTag = "en-US";
        else this.localeTag = "pt-BR";
    }

    @Override public void disableVoice() {
        started = false;
        commandInProgress = false;
        main.removeCallbacksAndMessages(null);
        prefs.setVoiceEnabled(false);
        if (commandRecognizer != null) commandRecognizer.cancel();
        VoiceState.broadcast(this, false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void disableBecauseUnavailable() {
        started = false;
        commandInProgress = false;
        main.removeCallbacksAndMessages(null);
        prefs.setVoiceEnabled(false);
        if (commandRecognizer != null) commandRecognizer.cancel();
        VoiceState.broadcast(this, false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void commandFinished() {
        finishRecognition(RESTART_DELAY_MS);
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        started = false;
        commandInProgress = false;
        main.removeCallbacksAndMessages(null);
        if (commandRecognizer != null) commandRecognizer.release();
        if (executor != null) executor.release();
        if (controller != null) controller.release();
        else if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        super.onDestroy();
    }
}
