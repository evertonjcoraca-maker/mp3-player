package com.evertoncoraca.mp3player;

public interface WakeWordRecognizer {
    interface Listener {
        void onWakeWord();
        void onError(String message);
    }

    void start();
    void stop();
    void release();
}
