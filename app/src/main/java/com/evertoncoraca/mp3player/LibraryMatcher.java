package com.evertoncoraca.mp3player;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

public final class LibraryMatcher {
    private final List<Track> orderedTracks;
    private final Set<Track> activeTracks;

    public LibraryMatcher(List<Track> orderedTracks, List<Track> activeRootTracks) {
        this.orderedTracks = orderedTracks == null ? List.of() : List.copyOf(orderedTracks);
        this.activeTracks = new HashSet<>(activeRootTracks == null ? List.of() : activeRootTracks);
    }

    public LibrarySelection findSong(String query) {
        LibrarySelection exact = exactSong(query);
        return exact != null ? exact : partialSong(query);
    }

    public LibrarySelection findAlbum(String query) {
        return groupedSelection(query, track -> track.album);
    }

    public LibrarySelection findArtist(String query) {
        return groupedSelection(query, track -> track.artist);
    }

    public LibrarySelection resolveGeneric(String query) {
        LibrarySelection result = exactSong(query);
        if (result != null) return result;
        result = exactGroup(query, track -> track.artist);
        if (result != null) return result;
        result = exactGroup(query, track -> track.album);
        if (result != null) return result;
        result = partialSong(query);
        if (result != null) return result;
        result = partialGroup(query, track -> track.artist);
        if (result != null) return result;
        return partialGroup(query, track -> track.album);
    }

    public LibrarySelection shuffleAll(Random random) {
        if (orderedTracks.isEmpty()) return null;
        List<Track> shuffled = new ArrayList<>(orderedTracks);
        Collections.shuffle(shuffled, random == null ? new Random() : random);
        return new LibrarySelection(shuffled, shuffled.get(0));
    }

    private LibrarySelection exactSong(String query) {
        String needle = normalize(query);
        if (needle.isBlank()) return null;
        Track track = firstPreferred(t -> normalize(t.displayName).equals(needle));
        return track == null ? null : new LibrarySelection(List.of(track), track);
    }

    private LibrarySelection partialSong(String query) {
        String needle = normalize(query);
        if (needle.isBlank()) return null;
        Track track = firstPreferred(t -> normalize(t.displayName).contains(needle));
        return track == null ? null : new LibrarySelection(List.of(track), track);
    }

    private LibrarySelection groupedSelection(String query, Function<Track, String> field) {
        LibrarySelection exact = exactGroup(query, field);
        return exact != null ? exact : partialGroup(query, field);
    }

    private LibrarySelection exactGroup(String query, Function<Track, String> field) {
        String needle = normalize(query);
        if (needle.isBlank()) return null;
        Track match = firstPreferred(t -> normalize(field.apply(t)).equals(needle));
        if (match == null) return null;
        return groupByValue(field, field.apply(match));
    }

    private LibrarySelection partialGroup(String query, Function<Track, String> field) {
        String needle = normalize(query);
        if (needle.isBlank()) return null;
        Track match = firstPreferred(t -> normalize(field.apply(t)).contains(needle));
        if (match == null) return null;
        return groupByValue(field, field.apply(match));
    }

    private LibrarySelection groupByValue(Function<Track, String> field, String selectedValue) {
        String value = normalize(selectedValue);
        List<Track> queue = new ArrayList<>();
        for (Track track : orderedTracks) {
            if (normalize(field.apply(track)).equals(value)) queue.add(track);
        }
        return queue.isEmpty() ? null : new LibrarySelection(queue, queue.get(0));
    }

    private Track firstPreferred(TrackPredicate predicate) {
        for (Track track : orderedTracks) if (activeTracks.contains(track) && predicate.test(track)) return track;
        for (Track track : orderedTracks) if (!activeTracks.contains(track) && predicate.test(track)) return track;
        return null;
    }

    static String normalize(String value) {
        if (value == null) return "";
        String n = value.trim().replaceFirst("(?i)\\.(mp3|wav|wave)$", "");
        n = Normalizer.normalize(n, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return n.replaceAll("\\s+", " ");
    }

    private interface TrackPredicate { boolean test(Track track); }
}
