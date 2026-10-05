package com.evertoncoraca.mp3player;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LibrarySearch {
    private LibrarySearch() {}

    public static final class Result {
        public final List<LibraryNode> folders = new ArrayList<>();
        public final List<Track> tracks = new ArrayList<>();

        public boolean isEmpty() { return folders.isEmpty() && tracks.isEmpty(); }
    }

    public static Result find(List<LibraryNode> roots, String query) {
        Result result = new Result();
        String needle = normalize(query);
        if (needle.isBlank() || roots == null) return result;

        Map<String, LibraryNode> folders = new LinkedHashMap<>();
        Map<String, Track> tracks = new LinkedHashMap<>();
        for (LibraryNode root : roots) {
            if (root != null) collect(root, needle, folders, tracks);
        }

        result.folders.addAll(folders.values());
        result.tracks.addAll(tracks.values());
        result.folders.sort(Comparator.comparing(node -> normalize(node.name)));
        result.tracks.sort((a, b) -> TrackNameParser.compareFileNames(a.fileName, b.fileName));
        return result;
    }

    private static void collect(LibraryNode node, String needle,
                                Map<String, LibraryNode> folders,
                                Map<String, Track> tracks) {
        for (LibraryNode child : node.children) {
            if (child.type == LibraryNode.Type.FOLDER) {
                if (normalize(child.name).contains(needle)) folders.putIfAbsent(child.uri, child);
                collect(child, needle, folders, tracks);
            } else if (child.track != null && trackMatches(child.track, needle)) {
                tracks.putIfAbsent(child.track.uri, child.track);
            }
        }
    }

    private static boolean trackMatches(Track track, String needle) {
        return normalize(track.displayName).contains(needle)
                || normalize(track.artist).contains(needle)
                || normalize(track.album).contains(needle);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
