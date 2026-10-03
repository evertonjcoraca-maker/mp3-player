package com.evertoncoraca.mp3player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackUriResolverTest {
    @Test
    public void fallsBackToMediaIdWhenControllerStripsLocalUri() {
        String uri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2F01%20Track.mp3";
        assertEquals(uri, PlaybackUriResolver.resolve(null, uri));
    }

    @Test
    public void prefersRequestMetadataUriWhenAvailable() {
        String requestUri = "content://provider/document/requested.mp3";
        String mediaId = "content://provider/document/id.mp3";
        assertEquals(requestUri, PlaybackUriResolver.resolve(requestUri, mediaId));
    }
}
