package com.evertoncoraca.mp3player;

final class PlaybackUriResolver {
    private PlaybackUriResolver() {}

    static String resolve(String requestMetadataUri, String mediaId) {
        if (requestMetadataUri != null && !requestMetadataUri.isBlank()) return requestMetadataUri;
        if (mediaId != null && !mediaId.isBlank()) return mediaId;
        return null;
    }
}
