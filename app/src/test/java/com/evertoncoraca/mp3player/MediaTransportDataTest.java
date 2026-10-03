package com.evertoncoraca.mp3player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MediaTransportDataTest {
    @Test
    public void keepsContentUriInBothMediaIdAndRequestUri() {
        Track track = new Track(
                "content://com.android.externalstorage.documents/document/primary%3AMusic%2F01%20Track.mp3",
                "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                "01 - Track.mp3",
                "Artist",
                "Album");

        MediaTransportData data = MediaTransportData.fromTrack(track);

        assertEquals(track.uri, data.mediaId);
        assertEquals(track.uri, data.requestUri);
    }
}
