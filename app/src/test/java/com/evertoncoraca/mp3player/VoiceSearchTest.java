package com.evertoncoraca.mp3player;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class VoiceSearchTest {
    @Test public void usesFirstRecognizedPhraseAndTrimsIt() {
        assertEquals("Appetite for Destruction",
                VoiceSearch.firstResult(List.of("  Appetite for Destruction  ", "Appetite")));
    }

    @Test public void nullOrEmptyResultsBecomeBlankSearch() {
        assertEquals("", VoiceSearch.firstResult(null));
        assertEquals("", VoiceSearch.firstResult(List.of()));
    }
}
