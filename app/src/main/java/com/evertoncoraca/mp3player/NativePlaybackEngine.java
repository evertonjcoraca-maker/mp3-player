package com.evertoncoraca.mp3player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;

import java.io.IOException;
import java.util.List;

final class NativePlaybackEngine {
    interface Listener {
        void onTrackChanged(Track track);
        void onPlaybackChanged(boolean isPlaying);
        void onError(String message);
    }

    private static final int LUFFY_GAIN_MB = 600;

    private final Context context;
    private final Listener listener;
    private final NativePlaybackQueue queue = new NativePlaybackQueue();

    private MediaPlayer player;
    private LoudnessEnhancer loudnessEnhancer;
    private float volume = 0.7f;
    private boolean luffyEnabled;
    private boolean prepared;
    private boolean preparing;
    private boolean playWhenReady;

    NativePlaybackEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void playQueue(List<Track> tracks, Track start, boolean repeatAll) {
        queue.setQueue(tracks, start, repeatAll);
        if (!queue.hasCurrent()) {
            listener.onError("A fila de reprodução está vazia.");
            return;
        }
        openCurrent(true);
    }

    void play() {
        if (preparing) {
            playWhenReady = true;
            return;
        }
        if (player != null && prepared) {
            try {
                player.start();
                listener.onPlaybackChanged(true);
            } catch (IllegalStateException e) {
                listener.onError("Não foi possível iniciar a reprodução.");
            }
            return;
        }
        if (queue.hasCurrent()) openCurrent(true);
    }

    void pause() {
        if (player == null || !prepared) return;
        try {
            if (player.isPlaying()) player.pause();
            playWhenReady = false;
            listener.onPlaybackChanged(false);
        } catch (IllegalStateException ignored) {
        }
    }

    void next() {
        Track next = queue.next();
        if (next != null) openCurrent(true);
    }

    void previous() {
        if (player != null && prepared) {
            try {
                if (player.getCurrentPosition() > 3000) {
                    player.seekTo(0);
                    return;
                }
            } catch (IllegalStateException ignored) {
            }
        }
        Track previous = queue.previous();
        if (previous != null) openCurrent(true);
    }

    void seekTo(long positionMs) {
        if (player == null || !prepared) return;
        try {
            player.seekTo((int) Math.max(0, Math.min(Integer.MAX_VALUE, positionMs)));
        } catch (IllegalStateException ignored) {
        }
    }

    long getDuration() {
        if (player == null || !prepared) return 0;
        try { return Math.max(0, player.getDuration()); }
        catch (IllegalStateException e) { return 0; }
    }

    long getCurrentPosition() {
        if (player == null || !prepared) return 0;
        try { return Math.max(0, player.getCurrentPosition()); }
        catch (IllegalStateException e) { return 0; }
    }

    boolean isPlaying() {
        if (player == null || !prepared) return false;
        try { return player.isPlaying(); }
        catch (IllegalStateException e) { return false; }
    }

    boolean hasCurrent() {
        return queue.hasCurrent();
    }

    void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        if (player != null) {
            try { player.setVolume(volume, volume); }
            catch (IllegalStateException ignored) {}
        }
    }

    void setLuffy(boolean enabled) {
        luffyEnabled = enabled;
        if (loudnessEnhancer != null) {
            try {
                loudnessEnhancer.setTargetGain(LUFFY_GAIN_MB);
                loudnessEnhancer.setEnabled(enabled);
            } catch (RuntimeException ignored) {
            }
        }
    }

    void release() {
        releasePlayer();
    }

    private void openCurrent(boolean autoplay) {
        Track track = queue.current();
        if (track == null) return;

        releasePlayer();
        playWhenReady = autoplay;
        prepared = false;
        preparing = true;

        MediaPlayer newPlayer = new MediaPlayer();
        player = newPlayer;
        try {
            newPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            newPlayer.setOnPreparedListener(mp -> {
                if (mp != player) return;
                preparing = false;
                prepared = true;
                attachLoudness(mp.getAudioSessionId());
                mp.setVolume(volume, volume);
                listener.onPlaybackChanged(false);
                if (playWhenReady) {
                    try {
                        mp.start();
                        listener.onPlaybackChanged(true);
                    } catch (IllegalStateException e) {
                        listener.onError("O Android preparou o arquivo, mas não conseguiu iniciar o áudio.");
                    }
                }
            });
            newPlayer.setOnCompletionListener(mp -> {
                if (mp != player) return;
                Track next = queue.next();
                if (next != null) openCurrent(true);
                else listener.onPlaybackChanged(false);
            });
            newPlayer.setOnErrorListener((mp, what, extra) -> {
                if (mp == player) {
                    listener.onError("Falha do reprodutor nativo (código " + what + "/" + extra + ").");
                    releasePlayer();
                    listener.onPlaybackChanged(false);
                }
                return true;
            });
            newPlayer.setDataSource(context, Uri.parse(track.uri));
            newPlayer.setVolume(volume, volume);
            listener.onTrackChanged(track);
            newPlayer.prepareAsync();
        } catch (SecurityException e) {
            releasePlayer();
            listener.onError("O Android negou acesso a este arquivo. Escolha novamente a pasta de músicas.");
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            releasePlayer();
            listener.onError("Não foi possível abrir este áudio: " + e.getClass().getSimpleName());
        }
    }

    private void attachLoudness(int audioSessionId) {
        releaseLoudness();
        if (audioSessionId <= 0) return;
        try {
            loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
            loudnessEnhancer.setTargetGain(LUFFY_GAIN_MB);
            loudnessEnhancer.setEnabled(luffyEnabled);
        } catch (RuntimeException e) {
            loudnessEnhancer = null;
        }
    }

    private void releasePlayer() {
        releaseLoudness();
        MediaPlayer old = player;
        player = null;
        prepared = false;
        preparing = false;
        playWhenReady = false;
        if (old != null) {
            try { old.setOnPreparedListener(null); } catch (RuntimeException ignored) {}
            try { old.setOnCompletionListener(null); } catch (RuntimeException ignored) {}
            try { old.setOnErrorListener(null); } catch (RuntimeException ignored) {}
            try { old.release(); } catch (RuntimeException ignored) {}
        }
    }

    private void releaseLoudness() {
        if (loudnessEnhancer != null) {
            try { loudnessEnhancer.release(); } catch (RuntimeException ignored) {}
            loudnessEnhancer = null;
        }
    }
}
