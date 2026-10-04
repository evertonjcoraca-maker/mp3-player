package com.evertoncoraca.mp3player;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LibraryRepository {
    private final Context context;
    private final LibraryPrefs prefs;
    private final Map<String, LibraryNode> cache = new LinkedHashMap<>();

    public LibraryRepository(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = new LibraryPrefs(this.context);
    }

    public synchronized Snapshot snapshot() {
        List<Track> all = new ArrayList<>();
        List<Track> active = new ArrayList<>();
        String activeRoot = prefs.activeRoot();
        List<String> roots = prefs.roots();
        cache.keySet().retainAll(roots);
        for (String root : roots) {
            LibraryNode node = cache.get(root);
            if (node == null) {
                node = new LibraryScanner(context).scan(root);
                cache.put(root, node);
            }
            List<Track> tracks = node.flattenTracks();
            all.addAll(tracks);
            if (root.equals(activeRoot)) active.addAll(tracks);
        }
        return new Snapshot(all, active);
    }

    public synchronized void invalidate() { cache.clear(); }

    public static final class Snapshot {
        public final List<Track> allTracks;
        public final List<Track> activeTracks;

        Snapshot(List<Track> allTracks, List<Track> activeTracks) {
            this.allTracks = List.copyOf(allTracks);
            this.activeTracks = List.copyOf(activeTracks);
        }

        public LibraryMatcher matcher() { return new LibraryMatcher(allTracks, activeTracks); }
    }
}
