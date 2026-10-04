package com.evertoncoraca.mp3player;

import java.util.List;

public final class LibrarySelection {
    public final List<Track> queue;
    public final Track startTrack;

    public LibrarySelection(List<Track> queue, Track startTrack) {
        this.queue = List.copyOf(queue);
        this.startTrack = startTrack;
    }
}
