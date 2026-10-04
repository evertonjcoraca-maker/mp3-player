package com.evertoncoraca.mp3player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

public final class VoskWakeWordRecognizer implements WakeWordRecognizer {
    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private boolean requested;
    private boolean loading;
    private boolean triggered;

    public VoskWakeWordRecognizer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    @Override public void start() {
        requested = true;
        triggered = false;
        if (speechService != null) return;
        if (model != null) {
            startListening();
            return;
        }
        if (loading) return;
        loading = true;
        StorageService.unpack(context, "model-en-us", "model",
                unpacked -> main.post(() -> {
                    loading = false;
                    model = unpacked;
                    if (requested) startListening();
                }),
                exception -> main.post(() -> {
                    loading = false;
                    requested = false;
                    listener.onError("Não foi possível carregar o reconhecimento local: " + exception.getMessage());
                }));
    }

    private void startListening() {
        if (!requested || model == null || speechService != null) return;
        try {
            recognizer = new Recognizer(model, 16000.0f, "[\"player\", \"[unk]\"]");
            speechService = new SpeechService(recognizer, 16000.0f);
            speechService.startListening(new RecognitionListener() {
                @Override public void onPartialResult(String hypothesis) { inspect(hypothesis); }
                @Override public void onResult(String hypothesis) { inspect(hypothesis); }
                @Override public void onFinalResult(String hypothesis) { inspect(hypothesis); }
                @Override public void onError(Exception exception) {
                    if (requested) listener.onError("Falha ao ouvir a palavra Player: " + exception.getMessage());
                }
                @Override public void onTimeout() {
                    if (requested) restart();
                }
            });
        } catch (Exception e) {
            cleanupSession();
            listener.onError("Não foi possível iniciar o microfone local: " + e.getMessage());
        }
    }

    private void inspect(String json) {
        if (triggered || json == null) return;
        try {
            JSONObject object = new JSONObject(json);
            String text = object.optString("partial", object.optString("text", ""));
            if (text.toLowerCase().contains("player")) {
                triggered = true;
                stop();
                main.post(listener::onWakeWord);
            }
        } catch (Exception ignored) {}
    }

    private void restart() {
        cleanupSession();
        if (requested) startListening();
    }

    @Override public void stop() {
        requested = false;
        cleanupSession();
    }

    private void cleanupSession() {
        SpeechService service = speechService;
        speechService = null;
        if (service != null) {
            try { service.stop(); } catch (RuntimeException ignored) {}
            try { service.shutdown(); } catch (RuntimeException ignored) {}
        }
        Recognizer old = recognizer;
        recognizer = null;
        if (old != null) {
            try { old.close(); } catch (RuntimeException ignored) {}
        }
    }

    @Override public void release() {
        requested = false;
        cleanupSession();
        Model oldModel = model;
        model = null;
        if (oldModel != null) {
            try { oldModel.close(); } catch (RuntimeException ignored) {}
        }
    }
}
