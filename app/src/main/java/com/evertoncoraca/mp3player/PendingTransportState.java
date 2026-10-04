package com.evertoncoraca.mp3player;

/** Pure state holder for UI commands issued before MediaController is connected. */
final class PendingTransportState {
    static final long NO_SEEK = -1L;

    private Boolean playRequested;
    private int navigationDelta;
    private long seekPosition = NO_SEEK;

    void requestPlay() { playRequested = Boolean.TRUE; }
    void requestPause() { playRequested = Boolean.FALSE; }
    void requestNext() { navigationDelta++; }
    void requestPrevious() { navigationDelta--; }
    void requestSeek(long positionMs) { seekPosition = Math.max(0L, positionMs); }

    boolean consumePlayRequested() {
        if (!Boolean.TRUE.equals(playRequested)) return false;
        playRequested = null;
        return true;
    }

    boolean consumePauseRequested() {
        if (!Boolean.FALSE.equals(playRequested)) return false;
        playRequested = null;
        return true;
    }

    int consumeNavigationDelta() {
        int value = navigationDelta;
        navigationDelta = 0;
        return value;
    }

    long consumeSeekPosition() {
        long value = seekPosition;
        seekPosition = NO_SEEK;
        return value;
    }
}
