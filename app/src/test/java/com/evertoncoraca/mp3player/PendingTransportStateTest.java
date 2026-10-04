package com.evertoncoraca.mp3player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PendingTransportStateTest {
    @Test public void keepsLatestPlayPauseIntentUntilControllerConnects() {
        PendingTransportState state = new PendingTransportState();
        state.requestPlay();
        assertTrue(state.consumePlayRequested());
        assertFalse(state.consumePauseRequested());

        state.requestPause();
        assertTrue(state.consumePauseRequested());
        assertFalse(state.consumePlayRequested());
    }

    @Test public void accumulatesRelativeNavigationAndLatestSeek() {
        PendingTransportState state = new PendingTransportState();
        state.requestNext();
        state.requestNext();
        state.requestPrevious();
        state.requestSeek(12500L);
        state.requestSeek(9000L);

        assertEquals(1, state.consumeNavigationDelta());
        assertEquals(9000L, state.consumeSeekPosition());
        assertEquals(0, state.consumeNavigationDelta());
        assertEquals(PendingTransportState.NO_SEEK, state.consumeSeekPosition());
    }
}