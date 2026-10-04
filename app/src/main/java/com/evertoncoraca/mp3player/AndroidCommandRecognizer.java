package com.evertoncoraca.mp3player;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

public final class AndroidCommandRecognizer {
    public interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    private final Context context;
    private SpeechRecognizer recognizer;
    private boolean listening;

    public AndroidCommandRecognizer(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean isAvailable() { return SpeechRecognizer.isRecognitionAvailable(context); }

    public void listen(String localeTag, Callback callback) {
        if (listening) return;
        if (!isAvailable()) {
            callback.onError("O reconhecimento de voz do Android não está disponível.");
            return;
        }
        releaseRecognizer();
        listening = true;
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
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
                callback.onError("Comando de voz não reconhecido (código " + error + ").");
                releaseRecognizer();
            }

            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> values = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = values == null || values.isEmpty() ? "" : values.get(0);
                callback.onResult(text);
                releaseRecognizer();
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag == null ? "en-US" : localeTag);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L);
        recognizer.startListening(intent);
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
