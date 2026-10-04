package com.evertoncoraca.mp3player;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.List;

public final class AndroidCommandRecognizer {
    public interface Callback {
        void onResults(List<String> texts);
        void onError(String message, boolean fatal);
    }

    private final Context context;
    private SpeechRecognizer recognizer;
    private boolean listening;

    public AndroidCommandRecognizer(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return true;
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public void listen(String localeTag, Callback callback) {
        if (listening) return;
        if (!isAvailable()) {
            callback.onError("O reconhecimento de voz do Android não está disponível.", true);
            return;
        }

        releaseRecognizer();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            }
        } catch (RuntimeException e) {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            } catch (RuntimeException second) {
                callback.onError("Não foi possível iniciar o reconhecimento de voz.", true);
                return;
            }
        }

        listening = true;
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}
            @Override public void onPartialResults(Bundle partialResults) {}

            @Override public void onError(int error) {
                listening = false;
                boolean fatal = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS;
                String message = switch (error) {
                    case SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio no reconhecimento de voz.";
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão do microfone indisponível.";
                    case SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Reconhecimento de voz temporariamente sem rede.";
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecimento de voz ocupado.";
                    case SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhum comando reconhecido.";
                    default -> "Comando de voz não reconhecido (código " + error + ").";
                };
                releaseRecognizer();
                callback.onError(message, fatal);
            }

            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                List<String> texts = values == null ? List.of() : List.copyOf(values);
                releaseRecognizer();
                callback.onResults(texts);
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag == null ? "pt-BR" : localeTag);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L);

        if (Build.VERSION.SDK_INT >= 34) {
            ArrayList<String> languages = new ArrayList<>();
            languages.add("pt-BR");
            languages.add("en-US");
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                    RecognizerIntent.LANGUAGE_SWITCH_BALANCED);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, languages);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, languages);
        }

        try {
            recognizer.startListening(intent);
        } catch (RuntimeException e) {
            listening = false;
            releaseRecognizer();
            callback.onError("Não foi possível abrir o microfone para reconhecer o comando.", false);
        }
    }

    public void cancel() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (RuntimeException ignored) {}
        }
        listening = false;
        releaseRecognizer();
    }

    private void releaseRecognizer() {
        SpeechRecognizer old = recognizer;
        recognizer = null;
        if (old != null) {
            try { old.destroy(); } catch (RuntimeException ignored) {}
        }
    }

    public void release() { cancel(); }
}
