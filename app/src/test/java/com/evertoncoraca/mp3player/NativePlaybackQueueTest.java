package com.evertoncoraca.mp3player;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NativePlaybackQueueTest {
    private Track track(String id) {
        return new Track("content://music/" + id, "content://music", id + ".mp3", "Artist", "Album");
    }

    @Test public void startsAtSelectedTrackAndMovesForward() {
        Track a = track("a");
        Track b = track("b");
        Track c = track("c");
        NativePlaybackQueue queue = new NativePlaybackQueue();

        queue.setQueue(List.of(a, b, c), b, false);

        assertEquals(b, queue.current());
        assertEquals(c, queue.next());
        assertNull(queue.next());
    }

    @Test public void repeatAllWrapsAtBothEnds() {
        Track a = track("a");
        Track b = track("b");
        NativePlaybackQueue queue = new NativePlaybackQueue();

        queue.setQueue(List.of(a, b), b, true);

        assertEquals(a, queue.next());
        assertEquals(b, queue.previous());
    }
}
