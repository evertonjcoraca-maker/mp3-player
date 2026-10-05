package com.evertoncoraca.mp3player;

import java.util.List;

public final class VoiceSearch {
    public static final String RECOGNITION_LANGUAGE = "en-US";

    private VoiceSearch() {}

    public static String firstResult(List<String> results) {
        if (results == null || results.isEmpty() || results.get(0) == null) return "";
        return results.get(0).trim();
    }
}
