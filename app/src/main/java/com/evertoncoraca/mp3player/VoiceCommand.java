package com.evertoncoraca.mp3player;

public final class VoiceCommand {
    public enum Type {
        PLAY,
        NEXT,
        PREVIOUS,
        PAUSE,
        STOP,
        VOLUME_DELTA,
        VOLUME_SET,
        MUTE,
        UNMUTE,
        PLAY_SONG,
        PLAY_ALBUM,
        PLAY_ARTIST,
        SEARCH_SONG,
        PLAY_GENERIC,
        SHUFFLE_ALL,
        SET_LANGUAGE,
        DISABLE_VOICE
    }

    public final Type type;
    public final String query;
    public final int value;
    public final String localeTag;

    private VoiceCommand(Type type, String query, int value, String localeTag) {
        this.type = type;
        this.query = query;
        this.value = value;
        this.localeTag = localeTag;
    }

    public static VoiceCommand simple(Type type) {
        return new VoiceCommand(type, null, 0, null);
    }

    public static VoiceCommand query(Type type, String query) {
        return new VoiceCommand(type, query, 0, null);
    }

    public static VoiceCommand value(Type type, int value) {
        return new VoiceCommand(type, null, value, null);
    }

    public static VoiceCommand language(String localeTag) {
        return new VoiceCommand(Type.SET_LANGUAGE, null, 0, localeTag);
    }
}
