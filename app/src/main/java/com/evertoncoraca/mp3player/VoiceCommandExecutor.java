package com.evertoncoraca.mp3player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.Player;
import androidx.media3.session.MediaController;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VoiceCommandExecutor {
    public interface Host {
        void setLanguage(String localeTag);
        void disableVoice();
        void commandFinished();
    }

    private final Context context;
    private final MediaController controller;
    private final Host host;
    private final LibraryPrefs prefs;
    private final LibraryRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Integer mutedFrom;

    public VoiceCommandExecutor(Context context, MediaController controller, Host host) {
        this.context = context.getApplicationContext();
        this.controller = controller;
        this.host = host;
        prefs = new LibraryPrefs(this.context);
        repository = new LibraryRepository(this.context);
    }

    public void execute(VoiceCommand command) {
        if (command == null) { host.commandFinished(); return; }
        switch (command.type) {
            case PLAY -> playOrLoadFirst();
            case NEXT -> { controller.seekToNextMediaItem(); host.commandFinished(); }
            case PREVIOUS -> { controller.seekToPrevious(); host.commandFinished(); }
            case PAUSE -> { controller.pause(); host.commandFinished(); }
            case STOP -> { controller.pause(); controller.seekTo(0); host.commandFinished(); }
            case VOLUME_DELTA -> { setVolumePercent(currentVolumePercent() + command.value, true); host.commandFinished(); }
            case VOLUME_SET -> { setVolumePercent(command.value, true); host.commandFinished(); }
            case MUTE -> { mute(); host.commandFinished(); }
            case UNMUTE -> { unmute(); host.commandFinished(); }
            case SET_LANGUAGE -> { host.setLanguage(command.localeTag); host.commandFinished(); }
            case DISABLE_VOICE -> host.disableVoice();
            case SEARCH_SONG -> { search(command.query); host.commandFinished(); }
            case PLAY_SONG, PLAY_ALBUM, PLAY_ARTIST, PLAY_GENERIC, SHUFFLE_ALL -> playSelection(command);
        }
    }

    private void playOrLoadFirst() {
        if (controller.getCurrentMediaItem() != null) {
            controller.play();
            host.commandFinished();
            return;
        }
        executor.execute(() -> {
            LibraryRepository.Snapshot snapshot = repository.snapshot();
            List<Track> queue = snapshot.activeTracks.isEmpty() ? snapshot.allTracks : snapshot.activeTracks;
            boolean repeat = !snapshot.activeTracks.isEmpty() && snapshot.activeRepeatAll;
            main.post(() -> {
                if (!queue.isEmpty()) playQueue(queue, queue.get(0), repeat);
                host.commandFinished();
            });
        });
    }

    private void playSelection(VoiceCommand command) {
        executor.execute(() -> {
            LibraryRepository.Snapshot snapshot = repository.snapshot();
            LibraryMatcher matcher = snapshot.matcher();
            LibrarySelection selection = switch (command.type) {
                case PLAY_SONG -> matcher.findSong(command.query);
                case PLAY_ALBUM -> matcher.findAlbum(command.query);
                case PLAY_ARTIST -> matcher.findArtist(command.query);
                case PLAY_GENERIC -> matcher.resolveGeneric(command.query);
                case SHUFFLE_ALL -> matcher.shuffleAll(new Random());
                default -> null;
            };
            main.post(() -> {
                if (selection != null) {
                    if (command.type == VoiceCommand.Type.PLAY_SONG ||
                            (command.type == VoiceCommand.Type.PLAY_GENERIC && selection.queue.size() == 1)) {
                        List<Track> queue = snapshot.allTracks.isEmpty() ? selection.queue : snapshot.allTracks;
                        playQueue(queue, selection.startTrack, false);
                    } else {
                        playQueue(selection.queue, selection.startTrack, false);
                    }
                }
                host.commandFinished();
            });
        });
    }

    private void playQueue(List<Track> queue, Track start, boolean repeatAll) {
        int index = queue.indexOf(start);
        if (index < 0) index = 0;
        controller.setMediaItems(PlaybackItems.fromTracks(queue), index, 0L);
        controller.setRepeatMode(repeatAll ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        controller.prepare();
        controller.play();
    }

    private void search(String query) {
        prefs.setPendingSearch(query);
        VoiceState.broadcastSearch(context, query);
    }

    private int currentVolumePercent() {
        return Math.max(0, Math.min(100, Math.round(controller.getVolume() * 100f)));
    }

    private void setVolumePercent(int value, boolean explicit) {
        int clamped = Math.max(0, Math.min(100, value));
        controller.setVolume(clamped / 100f);
        prefs.setVolume(clamped);
        if (explicit) mutedFrom = null;
    }

    private void mute() {
        if (mutedFrom == null) mutedFrom = currentVolumePercent();
        controller.setVolume(0f);
    }

    private void unmute() {
        if (mutedFrom == null) return;
        if (currentVolumePercent() > 0) {
            mutedFrom = null;
            return;
        }
        int restore = mutedFrom;
        mutedFrom = null;
        setVolumePercent(restore, false);
    }

    public void release() { executor.shutdownNow(); }
}
