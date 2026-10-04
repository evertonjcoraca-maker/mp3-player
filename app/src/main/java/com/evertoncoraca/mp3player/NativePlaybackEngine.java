package com.evertoncoraca.mp3player;

import android.content.Context;

import java.util.List;

/**
 * Compatibility facade kept so the existing Activity API stays small while
 * PlaybackService/MediaSession is the single authoritative playback owner.
 */
final class NativePlaybackEngine {
    interface Listener {
        void onTrackChanged(Track track);
        void onPlaybackChanged(boolean isPlaying);
        void onError(String message);
        default void onVolumeChanged(int volume) {}
    }

    private final ServicePlaybackController delegate;

    NativePlaybackEngine(Context context, Listener listener) {
        delegate = new ServicePlaybackController(context, new ServicePlaybackController.Listener() {
            @Override public void onTrackChanged(Track track) { listener.onTrackChanged(track); }
            @Override public void onPlaybackChanged(boolean isPlaying) { listener.onPlaybackChanged(isPlaying); }
            @Override public void onError(String message) { listener.onError(message); }
            @Override public void onVolumeChanged(int volume) { listener.onVolumeChanged(volume); }
        });
    }

    void playQueue(List<Track> tracks, Track start, boolean repeatAll) { delegate.playQueue(tracks, start, repeatAll); }
    void play() { delegate.play(); }
    void pause() { delegate.pause(); }
    void next() { delegate.next(); }
    void previous() { delegate.previous(); }
    void seekTo(long positionMs) { delegate.seekTo(positionMs); }
    long getDuration() { return delegate.getDuration(); }
    long getCurrentPosition() { return delegate.getCurrentPosition(); }
    boolean isPlaying() { return delegate.isPlaying(); }
    boolean hasCurrent() { return delegate.hasCurrent(); }
    void setVolume(float value) { delegate.setVolume(value); }
    void setLuffy(boolean enabled) { delegate.setLuffy(enabled); }
    void release() { delegate.release(); }
}
