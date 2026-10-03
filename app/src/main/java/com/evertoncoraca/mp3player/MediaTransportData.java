package com.evertoncoraca.mp3player;

/**
 * Data that must survive the MediaController -> MediaSession IPC boundary.
 * Media3 intentionally strips MediaItem localConfiguration, so the playable URI
 * is carried both as mediaId and requestMetadata.mediaUri.
 */
public final class MediaTransportData {
    public final String mediaId;
    public final String requestUri;

    private MediaTransportData(String mediaId, String requestUri) {
        this.mediaId = mediaId;
        this.requestUri = requestUri;
    }

    public static MediaTransportData fromTrack(Track track) {
        if (track == null || track.uri == null || track.uri.isBlank()) {
            throw new IllegalArgumentException("Track URI is required");
        }
        return new MediaTransportData(track.uri, track.uri);
    }
}
