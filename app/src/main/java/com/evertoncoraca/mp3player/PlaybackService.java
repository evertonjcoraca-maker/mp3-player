package com.evertoncoraca.mp3player;

import android.media.audiofx.LoudnessEnhancer;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class PlaybackService extends MediaSessionService {
    public static final String COMMAND_SET_LUFFY = "com.evertoncoraca.mp3player.SET_LUFFY";
    public static final String ARG_ENABLED = "enabled";
    public static final int LUFFY_GAIN_MB = 600;

    private ExoPlayer player;
    private MediaSession mediaSession;
    private LoudnessEnhancer loudnessEnhancer;
    private boolean luffyEnabled;
    private LibraryPrefs prefs;

    @Override public void onCreate() {
        super.onCreate();
        prefs = new LibraryPrefs(this);
        luffyEnabled = prefs.luffy();
        player = new ExoPlayer.Builder(this).build();
        player.setVolume(prefs.volume() / 100f);
        player.addListener(new Player.Listener() {
            @Override public void onAudioSessionIdChanged(int audioSessionId) { attachLoudness(audioSessionId); }
            @Override public void onVolumeChanged(float volume) { prefs.setVolume(Math.round(volume * 100f)); }
        });

        SessionCommand luffyCommand = new SessionCommand(COMMAND_SET_LUFFY, Bundle.EMPTY);
        MediaSession.Callback callback = new MediaSession.Callback() {
            @Override
            public MediaSession.ConnectionResult onConnect(MediaSession session, MediaSession.ControllerInfo controller) {
                MediaSession.ConnectionResult accepted = MediaSession.Callback.super.onConnect(session, controller);
                return new MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                        .setAvailableSessionCommands(accepted.availableSessionCommands.buildUpon().add(luffyCommand).build())
                        .setAvailablePlayerCommands(accepted.availablePlayerCommands)
                        .build();
            }

            @Override
            public ListenableFuture<List<MediaItem>> onAddMediaItems(MediaSession session,
                                                                      MediaSession.ControllerInfo controller,
                                                                      List<MediaItem> mediaItems) {
                List<MediaItem> playableItems = new ArrayList<>(mediaItems.size());
                for (MediaItem item : mediaItems) {
                    String requestUri = item.requestMetadata.mediaUri == null ? null : item.requestMetadata.mediaUri.toString();
                    String playableUri = PlaybackUriResolver.resolve(requestUri, item.mediaId);
                    if (playableUri == null) playableItems.add(item);
                    else playableItems.add(item.buildUpon().setUri(playableUri).build());
                }
                return Futures.immediateFuture(playableItems);
            }

            @Override
            public ListenableFuture<SessionResult> onCustomCommand(MediaSession session, MediaSession.ControllerInfo controller,
                                                                    SessionCommand customCommand, Bundle args) {
                if (COMMAND_SET_LUFFY.equals(customCommand.customAction)) {
                    setLuffy(args.getBoolean(ARG_ENABLED, false));
                    return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                }
                return MediaSession.Callback.super.onCustomCommand(session, controller, customCommand, args);
            }
        };
        mediaSession = new MediaSession.Builder(this, player).setCallback(callback).build();
    }

    private void setLuffy(boolean enabled) {
        luffyEnabled = enabled;
        prefs.setLuffy(enabled);
        if (loudnessEnhancer != null) {
            try {
                loudnessEnhancer.setTargetGain(LUFFY_GAIN_MB);
                loudnessEnhancer.setEnabled(enabled);
            } catch (RuntimeException ignored) {}
        }
    }

    private void attachLoudness(int audioSessionId) {
        if (loudnessEnhancer != null) {
            try { loudnessEnhancer.release(); } catch (RuntimeException ignored) {}
            loudnessEnhancer = null;
        }
        if (audioSessionId == 0) return;
        try {
            loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
            loudnessEnhancer.setTargetGain(LUFFY_GAIN_MB);
            loudnessEnhancer.setEnabled(luffyEnabled);
        } catch (RuntimeException ignored) { loudnessEnhancer = null; }
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) { return mediaSession; }

    @Override public void onDestroy() {
        if (loudnessEnhancer != null) {
            try { loudnessEnhancer.release(); } catch (RuntimeException ignored) {}
        }
        if (mediaSession != null) mediaSession.release();
        if (player != null) player.release();
        super.onDestroy();
    }
}
