package com.evertoncoraca.mp3player;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LibrarySearchTest {
    private LibraryNode library() {
        LibraryNode root = LibraryNode.folder("Music", "root");
        LibraryNode guns = LibraryNode.folder("Guns N' Roses", "guns");
        LibraryNode appetite = LibraryNode.folder("Appetite for Destruction", "appetite");
        appetite.children.add(LibraryNode.track(new Track(
                "track:1", "appetite", "09 - Sweet Child O' Mine.mp3", "Guns N' Roses", "Appetite for Destruction")));
        appetite.children.add(LibraryNode.track(new Track(
                "track:2", "appetite", "01 - Welcome to the Jungle.mp3", "Guns N' Roses", "Appetite for Destruction")));
        guns.children.add(appetite);
        root.children.add(guns);
        return root;
    }

    @Test public void findsMatchingFolderByPartialName() {
        LibrarySearch.Result result = LibrarySearch.find(List.of(library()), "Appetite");
        assertEquals(1, result.folders.size());
        assertEquals("Appetite for Destruction", result.folders.get(0).name);
    }

    @Test public void findsMatchingTrackByTitleArtistOrAlbum() {
        LibrarySearch.Result title = LibrarySearch.find(List.of(library()), "Sweet Child");
        assertEquals(1, title.tracks.size());
        assertEquals("09 - Sweet Child O' Mine", title.tracks.get(0).displayName);

        LibrarySearch.Result artist = LibrarySearch.find(List.of(library()), "guns n' roses");
        assertEquals(2, artist.tracks.size());

        LibrarySearch.Result album = LibrarySearch.find(List.of(library()), "appetite for destruction");
        assertEquals(2, album.tracks.size());
    }

    @Test public void searchIsCaseInsensitiveAndBlankReturnsNothing() {
        LibrarySearch.Result result = LibrarySearch.find(List.of(library()), "aPpEtItE");
        assertEquals(1, result.folders.size());
        assertTrue(LibrarySearch.find(List.of(library()), "   ").isEmpty());
    }
}
