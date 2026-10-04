package com.evertoncoraca.mp3player;

public final class VoiceVolumeState {
    private int current;
    private Integer mutedFrom;

    public VoiceVolumeState(int initial) {
        current = clamp(initial);
    }

    public int current() { return current; }

    public int changeBy(int delta) {
        return set(current + delta);
    }

    public int set(int value) {
        current = clamp(value);
        mutedFrom = null;
        return current;
    }

    public int mute() {
        if (mutedFrom == null) mutedFrom = current;
        current = 0;
        return current;
    }

    public int unmute() {
        if (mutedFrom != null) {
            current = mutedFrom;
            mutedFrom = null;
        }
        return current;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
