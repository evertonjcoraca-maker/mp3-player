package com.evertoncoraca.mp3player;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class LibraryNodeTest {
    private Track t(String uri, String name) { return new Track(uri, "p", name, "Artist", "Album"); }

    @Test public void flattenedQueueContinuesIntoNextAlbum() {
        LibraryNode root = LibraryNode.folder("Rock", "root");
        LibraryNode a = LibraryNode.folder("A", "a");
        LibraryNode b = LibraryNode.folder("B", "b");
        a.children.add(LibraryNode.track(t("1", "02 - Second.mp3")));
        a.children.add(LibraryNode.track(t("0", "01 - First.mp3")));
        b.children.add(LibraryNode.track(t("2", "01 - Third.mp3")));
        root.children.add(b);
        root.children.add(a);
        root.sortRecursively();
        List<Track> tracks = root.flattenTracks();
        assertEquals("01 - First", tracks.get(0).displayName);
        assertEquals("02 - Second", tracks.get(1).displayName);
        assertEquals("01 - Third", tracks.get(2).displayName);
    }

    @Test public void rootWithoutSubfoldersActsAsPlaylist() {
        LibraryNode root = LibraryNode.folder("Playlist", "root");
        root.children.add(LibraryNode.track(t("1", "01 - A.mp3")));
        root.children.add(LibraryNode.track(t("2", "02 - B.mp3")));
        assertTrue(root.rootActsAsPlaylist());
        root.children.add(LibraryNode.folder("Album", "album"));
        assertFalse(root.rootActsAsPlaylist());
    }
}
