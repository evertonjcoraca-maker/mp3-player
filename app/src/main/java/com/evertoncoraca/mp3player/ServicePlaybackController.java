package com.evertoncoraca.mp3player;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

final class ServicePlaybackController {
    interface Listener {
        void onTrackChanged(Track track);
        void onPlaybackChanged(boolean isPlaying);
        void onError(String message);
        default void onVolumeChanged(int volume) {}
    }

    private final Context context;
    private final Listener listener;
    private final ListenableFuture<MediaController> controllerFuture;
    private final PendingTransportState pendingTransport = new PendingTransportState();
    private MediaController controller;

    private List<Track> pendingQueue;
    private Track pendingStart;
    private boolean pendingRepeatAll;
    private Float pendingVolume;
    private Boolean pendingLuffy;

    ServicePlaybackController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        SessionToken token = new SessionToken(this.context,
                new ComponentName(this.context, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this.context, token).buildAsync();
        controllerFuture.addListener(this::finishConnect, ContextCompat.getMainExecutor(this.context));
    }

    private void finishConnect() {
        try {
            controller = controllerFuture.get();
            controller.addListener(new Player.Listener() {
                @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                    if (mediaItem != null) listener.onTrackChanged(trackFromItem(mediaItem));
                }

                @Override public void onIsPlayingChanged(boolean isPlaying) {
                    listener.onPlaybackChanged(isPlaying);
                }

                @Override public void onPlayerError(PlaybackException error) {
                    listener.onError("Não foi possível reproduzir este áudio: " + error.getErrorCodeName());
                }

                @Override public void onVolumeChanged(float volume) {
                    listener.onVolumeChanged(Math.max(0, Math.min(100, Math.round(volume * 100f))));
                }
            });

            MediaItem current = controller.getCurrentMediaItem();
            if (current != null) listener.onTrackChanged(trackFromItem(current));
            listener.onPlaybackChanged(controller.isPlaying());
            listener.onVolumeChanged(Math.round(controller.getVolume() * 100f));

            if (pendingVolume != null) setVolume(pendingVolume);
            if (pendingLuffy != null) setLuffy(pendingLuffy);
            if (pendingQueue != null) {
                List<Track> queue = pendingQueue;
                Track start = pendingStart;
                boolean repeat = pendingRepeatAll;
                pendingQueue = null;
                pendingStart = null;
                playQueue(queue, start, repeat);
            }
            applyPendingTransport();
        } catch (Exception e) {
            listener.onError("Não foi possível conectar ao serviço de reprodução.");
        }
    }

    private void applyPendingTransport() {
        if (controller == null) return;

        int navigation = pendingTransport.consumeNavigationDelta();
        while (navigation > 0) {
            if (controller.hasNextMediaItem()) controller.seekToNextMediaItem();
            navigation--;
        }
        while (navigation < 0) {
            if (controller.getCurrentPosition() > 3000) controller.seekTo(0);
            else if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem();
            else controller.seekTo(0);
            navigation++;
        }

        long seek = pendingTransport.consumeSeekPosition();
        if (seek != PendingTransportState.NO_SEEK) controller.seekTo(seek);

        if (pendingTransport.consumePauseRequested()) controller.pause();
        else if (pendingTransport.consumePlayRequested()) controller.play();
    }

    void playQueue(List<Track> tracks, Track start, boolean repeatAll) {
        if (tracks == null || tracks.isEmpty()) {
            listener.onError("A fila de reprodução está vazia.");
            return;
        }
        if (controller == null) {
            pendingQueue = List.copyOf(tracks);
            pendingStart = start;
            pendingRepeatAll = repeatAll;
            pendingTransport.requestPlay();
            return;
        }
        int startIndex = tracks.indexOf(start);
        if (startIndex < 0) startIndex = 0;
        controller.setMediaItems(PlaybackItems.fromTracks(tracks), startIndex, 0L);
        controller.setRepeatMode(repeatAll ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        controller.prepare();
        controller.play();
    }

    void play() {
        if (controller == null) pendingTransport.requestPlay();
        else controller.play();
    }

    void pause() {
        if (controller == null) pendingTransport.requestPause();
        else controller.pause();
    }

    void next() {
        if (controller == null) pendingTransport.requestNext();
        else if (controller.hasNextMediaItem()) controller.seekToNextMediaItem();
    }

    void previous() {
        if (controller == null) {
            pendingTransport.requestPrevious();
            return;
        }
        if (controller.getCurrentPosition() > 3000) controller.seekTo(0);
        else if (controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem();
        else controller.seekTo(0);
    }

    void seekTo(long positionMs) {
        if (controller == null) pendingTransport.requestSeek(positionMs);
        else controller.seekTo(Math.max(0L, positionMs));
    }

    long getDuration() {
        if (controller == null) return 0L;
        long duration = controller.getDuration();
        return duration == C.TIME_UNSET || duration < 0 ? 0L : duration;
    }

    long getCurrentPosition() {
        return controller == null ? 0L : Math.max(0L, controller.getCurrentPosition());
    }

    boolean isPlaying() { return controller != null && controller.isPlaying(); }
    boolean hasCurrent() { return controller != null && controller.getCurrentMediaItem() != null; }

    void setVolume(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (controller == null) {
            pendingVolume = clamped;
            return;
        }
        pendingVolume = null;
        controller.setVolume(clamped);
    }

    void setLuffy(boolean enabled) {
        if (controller == null) {
            pendingLuffy = enabled;
            return;
        }
        pendingLuffy = null;
        Bundle args = new Bundle();
        args.putBoolean(PlaybackService.ARG_ENABLED, enabled);
        controller.sendCustomCommand(new SessionCommand(PlaybackService.COMMAND_SET_LUFFY, Bundle.EMPTY), args);
    }

    void release() {
        if (controller != null) {
            controller.release();
            controller = null;
        } else {
            MediaController.releaseFuture(controllerFuture);
        }
    }

    private Track trackFromItem(MediaItem item) {
        String uri = item.mediaId == null || item.mediaId.isBlank() ?
                (item.requestMetadata.mediaUri == null ? "" : item.requestMetadata.mediaUri.toString()) : item.mediaId;
        String title = item.mediaMetadata.title == null ? "Música" : item.mediaMetadata.title.toString();
        String artist = item.mediaMetadata.artist == null ? "Artista desconhecido" : item.mediaMetadata.artist.toString();
        String album = item.mediaMetadata.albumTitle == null ? "Álbum desconhecido" : item.mediaMetadata.albumTitle.toString();
        return new Track(uri, "", title, artist, album);
    }
}
