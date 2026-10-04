package com.evertoncoraca.mp3player;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RecognizedCommandSelectorTest {
    private final RecognizedCommandSelector selector = new RecognizedCommandSelector(new VoiceCommandParser());

    @Test public void picksFirstHypothesisThatParses() {
        VoiceCommand command = selector.select(List.of(
                "player proxima musica estranha",
                "Player, próxima música",
                "player next song"));
        assertEquals(VoiceCommand.Type.NEXT, command.type);
    }

    @Test public void returnsNullWhenNoHypothesisContainsACommand() {
        assertNull(selector.select(List.of("olá mundo", "player faça café")));
    }
}
