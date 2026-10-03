package com.evertoncoraca.mp3player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LibraryNode {
    public enum Type { FOLDER, TRACK }

    public final Type type;
    public final String name;
    public final String uri;
    public final Track track;
    public final List<LibraryNode> children = new ArrayList<>();

    private LibraryNode(Type type, String name, String uri, Track track) {
        this.type = type;
        this.name = name == null ? "" : name;
        this.uri = uri == null ? "" : uri;
        this.track = track;
    }

    public static LibraryNode folder(String name, String uri) { return new LibraryNode(Type.FOLDER, name, uri, null); }
    public static LibraryNode track(Track track) { return new LibraryNode(Type.TRACK, track.displayName, track.uri, track); }

    public void sortRecursively() {
        children.sort((a, b) -> {
            if (a.type != b.type) return a.type == Type.FOLDER ? -1 : 1;
            if (a.type == Type.TRACK) return TrackNameParser.compareFileNames(a.track.fileName, b.track.fileName);
            return a.name.compareToIgnoreCase(b.name);
        });
        for (LibraryNode child : children) if (child.type == Type.FOLDER) child.sortRecursively();
    }

    public List<Track> flattenTracks() {
        List<Track> out = new ArrayList<>();
        collectTracks(this, out);
        return out;
    }

    private static void collectTracks(LibraryNode node, List<Track> out) {
        for (LibraryNode child : node.children) {
            if (child.type == Type.TRACK) out.add(child.track);
            else collectTracks(child, out);
        }
    }

    public boolean hasFolderChildren() {
        for (LibraryNode child : children) if (child.type == Type.FOLDER) return true;
        return false;
    }

    public boolean rootActsAsPlaylist() { return !hasFolderChildren() && !flattenTracks().isEmpty(); }

    public List<LibraryNode> albumFolders() {
        List<LibraryNode> albums = new ArrayList<>();
        collectAlbums(this, albums);
        albums.sort(Comparator.comparing(n -> n.name.toLowerCase()));
        return albums;
    }

    private static void collectAlbums(LibraryNode node, List<LibraryNode> out) {
        boolean hasTrack = false;
        for (LibraryNode child : node.children) if (child.type == Type.TRACK) { hasTrack = true; break; }
        if (hasTrack && node.type == Type.FOLDER) out.add(node);
        for (LibraryNode child : node.children) if (child.type == Type.FOLDER) collectAlbums(child, out);
    }

    public LibraryNode artistsView() {
        LibraryNode root = folder("Artistas", "virtual:artists");
        Map<String, LibraryNode> groups = new LinkedHashMap<>();
        for (Track track : flattenTracks()) {
            LibraryNode artist = groups.computeIfAbsent(track.artist, key -> folder(key, "virtual:artist:" + key));
            artist.children.add(track(track));
        }
        List<String> names = new ArrayList<>(groups.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            LibraryNode artist = groups.get(name);
            artist.children.sort((a, b) -> TrackNameParser.compareFileNames(a.track.fileName, b.track.fileName));
            root.children.add(artist);
        }
        return root;
    }

    public LibraryNode albumsView() {
        LibraryNode root = folder("Álbuns", "virtual:albums");
        root.children.addAll(albumFolders());
        return root;
    }
}
