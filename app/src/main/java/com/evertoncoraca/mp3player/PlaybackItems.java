package com.evertoncoraca.mp3player;

import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;

import java.util.ArrayList;
import java.util.List;

public final class PlaybackItems {
    private PlaybackItems() {}

    public static MediaItem fromTrack(Track track) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(track.displayName)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .build();
        Uri uri = Uri.parse(track.uri);
        return new MediaItem.Builder()
                .setMediaId(track.uri)
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
                .setMediaMetadata(metadata)
                .build();
    }

    public static List<MediaItem> fromTracks(List<Track> tracks) {
        List<MediaItem> items = new ArrayList<>(tracks.size());
        for (Track track : tracks) items.add(fromTrack(track));
        return items;
    }
}
