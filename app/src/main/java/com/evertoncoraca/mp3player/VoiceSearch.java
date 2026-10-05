package com.evertoncoraca.mp3player;

import java.util.List;

public final class VoiceSearch {
    private VoiceSearch() {}

    public static String firstResult(List<String> results) {
        if (results == null || results.isEmpty() || results.get(0) == null) return "";
        return results.get(0).trim();
    }
}
