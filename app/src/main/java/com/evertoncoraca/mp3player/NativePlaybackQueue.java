package com.evertoncoraca.mp3player;

import java.util.ArrayList;
import java.util.List;

final class NativePlaybackQueue {
    private final List<Track> tracks = new ArrayList<>();
    private int index = -1;
    private boolean repeatAll;

    void setQueue(List<Track> items, Track start, boolean repeatAll) {
        tracks.clear();
        if (items != null) {
            for (Track item : items) if (item != null) tracks.add(item);
        }
        this.repeatAll = repeatAll;
        index = tracks.indexOf(start);
        if (index < 0 && !tracks.isEmpty()) index = 0;
    }

    Track current() {
        return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
    }

    Track next() {
        if (tracks.isEmpty() || index < 0) return null;
        if (index + 1 < tracks.size()) {
            index++;
            return tracks.get(index);
        }
        if (repeatAll) {
            index = 0;
            return tracks.get(index);
        }
        return null;
    }

    Track previous() {
        if (tracks.isEmpty() || index < 0) return null;
        if (index - 1 >= 0) {
            index--;
            return tracks.get(index);
        }
        if (repeatAll) {
            index = tracks.size() - 1;
            return tracks.get(index);
        }
        return null;
    }

    boolean hasCurrent() {
        return current() != null;
    }
}
