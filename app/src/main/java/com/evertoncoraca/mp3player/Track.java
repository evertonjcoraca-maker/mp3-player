package com.evertoncoraca.mp3player;

import java.util.Objects;

public final class Track {
    public final String uri;
    public final String parentUri;
    public final String fileName;
    public final String displayName;
    public final String artist;
    public final String album;

    public Track(String uri, String parentUri, String fileName, String artist, String album) {
        this.uri = uri;
        this.parentUri = parentUri;
        this.fileName = fileName == null ? "" : fileName;
        this.displayName = TrackNameParser.withoutExtension(this.fileName);
        this.artist = artist == null || artist.isBlank() ? "Artista desconhecido" : artist;
        this.album = album == null || album.isBlank() ? "Álbum desconhecido" : album;
    }

    @Override public boolean equals(Object o) {
        return o instanceof Track && Objects.equals(uri, ((Track) o).uri);
    }

    @Override public int hashCode() { return Objects.hash(uri); }
}
