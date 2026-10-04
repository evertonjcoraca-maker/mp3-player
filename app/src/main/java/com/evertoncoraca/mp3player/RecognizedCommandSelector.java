package com.evertoncoraca.mp3player;

import java.util.List;

final class RecognizedCommandSelector {
    private final VoiceCommandParser parser;

    RecognizedCommandSelector(VoiceCommandParser parser) {
        this.parser = parser;
    }

    VoiceCommand select(List<String> hypotheses) {
        if (hypotheses == null) return null;
        for (String text : hypotheses) {
            VoiceCommand command = parser.parse(text);
            if (command != null) return command;
        }
        return null;
    }
}
