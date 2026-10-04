package com.evertoncoraca.mp3player;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class LibraryMatcherTest {
    private Track track(String id, String title, String artist, String album) {
        return new Track("content://music/" + id, "content://music/root/" + id, title + ".mp3", artist, album);
    }

    @Test public void normalizesCaseAccentsAndExtension() {
        Track aguas = track("1", "Águas de Março", "Tom Jobim", "Elis & Tom");
        LibraryMatcher matcher = new LibraryMatcher(List.of(aguas), List.of(aguas));
        assertSame(aguas, matcher.findSong("aguas de marco.mp3").startTrack);
    }

    @Test public void exactSongBeatsPartialSong() {
        Track partial = track("1", "The Sun Live", "A", "One");
        Track exact = track("2", "The Sun", "B", "Two");
        LibraryMatcher matcher = new LibraryMatcher(List.of(partial, exact), List.of());
        assertSame(exact, matcher.findSong("The Sun").startTrack);
    }

    @Test public void activeRootWinsExactTitleTie() {
        Track other = track("1", "Forever", "Band A", "One");
        Track active = track("2", "Forever", "Band B", "Two");
        LibraryMatcher matcher = new LibraryMatcher(List.of(other, active), List.of(active));
        assertSame(active, matcher.findSong("Forever").startTrack);
    }

    @Test public void genericResolutionPrioritizesSongThenArtistThenAlbum() {
        Track songNamedQueen = track("1", "Queen", "Someone", "Singles");
        Track queenArtist = track("2", "Radio Ga Ga", "Queen", "The Works");
        Track queenAlbum = track("3", "Track 1", "Other", "Queen");
        LibraryMatcher all = new LibraryMatcher(List.of(songNamedQueen, queenArtist, queenAlbum), List.of());
        assertSame(songNamedQueen, all.resolveGeneric("Queen").startTrack);

        LibraryMatcher noSong = new LibraryMatcher(List.of(queenArtist, queenAlbum), List.of());
        LibrarySelection artistSelection = noSong.resolveGeneric("Queen");
        assertEquals(List.of(queenArtist), artistSelection.queue);
        assertSame(queenArtist, artistSelection.startTrack);

        LibraryMatcher onlyAlbum = new LibraryMatcher(List.of(queenAlbum), List.of());
        assertEquals(List.of(queenAlbum), onlyAlbum.resolveGeneric("Queen").queue);
    }

    @Test public void albumAndArtistKeepStableLibraryOrder() {
        Track a = track("1", "02 Second", "Artist", "Album");
        Track b = track("2", "01 First", "Artist", "Album");
        Track c = track("3", "Other", "Artist", "Other Album");
        LibraryMatcher matcher = new LibraryMatcher(List.of(a, b, c), List.of());
        assertEquals(List.of(a, b), matcher.findAlbum("album").queue);
        assertEquals(List.of(a, b, c), matcher.findArtist("artist").queue);
    }

    @Test public void partialMatchesUseStableExistingOrder() {
        Track first = track("1", "Sweet Child O Mine", "GNR", "A");
        Track second = track("2", "Sweet Home Alabama", "Skynyrd", "B");
        LibraryMatcher matcher = new LibraryMatcher(List.of(first, second), List.of());
        assertSame(first, matcher.findSong("sweet").startTrack);
    }

    @Test public void missingMatchReturnsNull() {
        Track song = track("1", "One", "U2", "Achtung Baby");
        LibraryMatcher matcher = new LibraryMatcher(List.of(song), List.of());
        assertNull(matcher.findSong("nothing here"));
        assertNull(matcher.findAlbum("nothing here"));
        assertNull(matcher.findArtist("nothing here"));
        assertNull(matcher.resolveGeneric("nothing here"));
    }

    @Test public void shuffleContainsEveryTrackExactlyOnce() {
        Track a = track("1", "A", "X", "A");
        Track b = track("2", "B", "X", "A");
        Track c = track("3", "C", "Y", "B");
        LibraryMatcher matcher = new LibraryMatcher(List.of(a, b, c), List.of(a));
        LibrarySelection selection = matcher.shuffleAll(new Random(7));
        assertEquals(3, selection.queue.size());
        assertEquals(3, new HashSet<>(selection.queue).size());
        assertEquals(new HashSet<>(List.of(a, b, c)), new HashSet<>(selection.queue));
        assertSame(selection.queue.get(0), selection.startTrack);
    }
}
