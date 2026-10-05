package com.evertoncoraca.mp3player;

import java.util.ArrayList;
import java.util.List;

public final class LibrarySearch {
    private LibrarySearch() {}

    public static final class Result {
        public final List<LibraryNode> folders = new ArrayList<>();
        public final List<Track> tracks = new ArrayList<>();

        public boolean isEmpty() { return folders.isEmpty() && tracks.isEmpty(); }
    }

    public static Result find(List<LibraryNode> roots, String query) {
        return new Result();
    }
}
