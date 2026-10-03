package com.evertoncoraca.mp3player;

import org.junit.Test;
import static org.junit.Assert.*;

public class TrackNameParserTest {
    @Test public void removesOnlyFinalExtension() {
        assertEquals("01 - Highway Star", TrackNameParser.withoutExtension("01 - Highway Star.mp3"));
        assertEquals("mix.v2.03 - Burn", TrackNameParser.withoutExtension("mix.v2.03 - Burn.mp3"));
    }

    @Test public void numericPrefixSortsBeforeLexicalFallback() {
        assertTrue(TrackNameParser.compareFileNames("2 - Two.mp3", "10 - Ten.mp3") < 0);
        assertTrue(TrackNameParser.compareFileNames("10 - Ten.mp3", "Song.mp3") < 0);
    }
}
