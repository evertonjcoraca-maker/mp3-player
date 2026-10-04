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
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

public final class VoiceCommandService extends Service implements WakeWordRecognizer.Listener,
        VoiceCommandExecutor.Host {
    public static final String ACTION_START = "com.evertoncoraca.mp3player.voice.START";
    public static final String ACTION_STOP = "com.evertoncoraca.mp3player.voice.STOP";
    private static final String CHANNEL_ID = "voice_commands";
    private static final int NOTIFICATION_ID = 2002;

    private LibraryPrefs prefs;
    private WakeWordRecognizer wakeWord;
    private AndroidCommandRecognizer commandRecognizer;
    private VoiceCommandParser parser;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController controller;
    private VoiceCommandExecutor executor;
    private String localeTag = "en-US";
    private boolean started;
    private boolean commandInProgress;

    @Override public void onCreate() {
        super.onCreate();
        prefs = new LibraryPrefs(this);
        parser = new VoiceCommandParser();
        wakeWord = new VoskWakeWordRecognizer(this, this);
        commandRecognizer = new AndroidCommandRecognizer(this);

        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                executor = new VoiceCommandExecutor(this, controller, this);
                if (started) wakeWord.start();
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
        if (controller != null) wakeWord.start();
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
                .setContentText("Comando de voz ativo — diga “Player”")
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

    @Override public void onWakeWord() {
        if (!started || commandInProgress) return;
        commandInProgress = true;
        wakeWord.stop();
        commandRecognizer.listen(localeTag, new AndroidCommandRecognizer.Callback() {
            @Override public void onResult(String text) {
                VoiceCommand command = parser.parse("Player, " + (text == null ? "" : text));
                if (command == null || executor == null) {
                    commandFinished();
                    return;
                }
                executor.execute(command);
            }

            @Override public void onError(String message) {
                commandFinished();
            }
        });
    }

    @Override public void onError(String message) {
        if (!started) return;
        disableBecauseUnavailable();
    }

    @Override public void setLanguage(String localeTag) {
        if ("pt-BR".equalsIgnoreCase(localeTag)) this.localeTag = "pt-BR";
        else this.localeTag = "en-US";
    }

    @Override public void disableVoice() {
        started = false;
        commandInProgress = false;
        prefs.setVoiceEnabled(false);
        if (wakeWord != null) wakeWord.stop();
        if (commandRecognizer != null) commandRecognizer.cancel();
        VoiceState.broadcast(this, false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void disableBecauseUnavailable() {
        started = false;
        commandInProgress = false;
        prefs.setVoiceEnabled(false);
        VoiceState.broadcast(this, false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void commandFinished() {
        commandInProgress = false;
        if (started && wakeWord != null) wakeWord.start();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        started = false;
        commandInProgress = false;
        if (commandRecognizer != null) commandRecognizer.release();
        if (wakeWord != null) wakeWord.release();
        if (executor != null) executor.release();
        if (controller != null) controller.release();
        else if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        super.onDestroy();
    }
}
