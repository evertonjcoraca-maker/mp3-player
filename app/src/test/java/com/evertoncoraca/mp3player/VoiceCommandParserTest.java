package com.evertoncoraca.mp3player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VoiceCommandParserTest {
    private final VoiceCommandParser parser = new VoiceCommandParser();

    @Test public void parsesTransportAliasesInPortugueseAndEnglish() {
        assertEquals(VoiceCommand.Type.PLAY, parser.parse("Player, tocar música").type);
        assertEquals(VoiceCommand.Type.PLAY, parser.parse("player play").type);
        assertEquals(VoiceCommand.Type.NEXT, parser.parse("PLAYER, próxima!").type);
        assertEquals(VoiceCommand.Type.PREVIOUS, parser.parse("Player previous song").type);
        assertEquals(VoiceCommand.Type.PAUSE, parser.parse("Player, pausa").type);
        assertEquals(VoiceCommand.Type.STOP, parser.parse("Player stop music").type);
    }

    @Test public void parsesNamedMediaBeforeGenericPlay() {
        VoiceCommand song = parser.parse("Player, tocar música Hotel California");
        assertEquals(VoiceCommand.Type.PLAY_SONG, song.type);
        assertEquals("Hotel California", song.query);

        VoiceCommand album = parser.parse("Player play album The Wall");
        assertEquals(VoiceCommand.Type.PLAY_ALBUM, album.type);
        assertEquals("The Wall", album.query);

        VoiceCommand artist = parser.parse("Player, tocar artista Queen");
        assertEquals(VoiceCommand.Type.PLAY_ARTIST, artist.type);
        assertEquals("Queen", artist.query);

        VoiceCommand generic = parser.parse("Player, toca Queen");
        assertEquals(VoiceCommand.Type.PLAY_GENERIC, generic.type);
        assertEquals("Queen", generic.query);
    }

    @Test public void parsesSearchShuffleAndVoiceShutdown() {
        VoiceCommand search = parser.parse("Player, buscar música The Sun");
        assertEquals(VoiceCommand.Type.SEARCH_SONG, search.type);
        assertEquals("The Sun", search.query);
        assertEquals(VoiceCommand.Type.SHUFFLE_ALL, parser.parse("Player tocar todas as músicas aleatórias").type);
        assertEquals(VoiceCommand.Type.SHUFFLE_ALL, parser.parse("Player shuffle all songs").type);
        assertEquals(VoiceCommand.Type.DISABLE_VOICE, parser.parse("Player, encerrar comando de voz").type);
    }

    @Test public void parsesVolumeCommandsWithExactValues() {
        assertEquals(10, parser.parse("Player, aumentar um pouco o volume").value);
        assertEquals(-10, parser.parse("Player volume down").value);
        assertEquals(100, parser.parse("Player, aumente todo o volume").value);
        assertEquals(0, parser.parse("Player, diminua todo o volume").value);
        assertEquals(VoiceCommand.Type.MUTE, parser.parse("Player, mutar volume").type);
        assertEquals(VoiceCommand.Type.UNMUTE, parser.parse("Player unmute").type);
    }

    @Test public void parsesLanguageSwitchesRegardlessOfCurrentLanguage() {
        VoiceCommand portuguese = parser.parse("Player, linguagem português");
        assertEquals(VoiceCommand.Type.SET_LANGUAGE, portuguese.type);
        assertEquals("pt-BR", portuguese.localeTag);

        VoiceCommand english = parser.parse("Player language English");
        assertEquals(VoiceCommand.Type.SET_LANGUAGE, english.type);
        assertEquals("en-US", english.localeTag);
    }

    @Test public void normalizesCaseAccentsAndPunctuationButPreservesQueryText() {
        assertEquals(VoiceCommand.Type.NEXT, parser.parse("  PLAYER... PRÓXIMA MÚSICA!!! ").type);
        VoiceCommand command = parser.parse("Player, tocar álbum Águas de Março");
        assertEquals(VoiceCommand.Type.PLAY_ALBUM, command.type);
        assertEquals("Águas de Março", command.query);
    }

    @Test public void returnsNullForUnknownOrWakeWordOnly() {
        assertNull(parser.parse("Player"));
        assertNull(parser.parse("Player faça café"));
        assertNull(parser.parse("próxima música"));
        assertNull(parser.parse(null));
    }
}
