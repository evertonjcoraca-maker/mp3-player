package com.evertoncoraca.mp3player;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VoiceCommandParser {
    private static final Pattern WAKE_PREFIX = Pattern.compile("(?iu)^\\s*player\\b[\\s,.:;!?-]*");

    private static final Pattern PT_SEARCH_SONG = Pattern.compile("(?iu)^(?:buscar|procura(?:r)?)\\s+m[uú]sica\\s+(.+)$");
    private static final Pattern EN_SEARCH_SONG = Pattern.compile("(?iu)^search\\s+song\\s+(.+)$");
    private static final Pattern PT_PLAY_SONG = Pattern.compile("(?iu)^(?:tocar|toca)\\s+m[uú]sica\\s+(.+)$");
    private static final Pattern EN_PLAY_SONG = Pattern.compile("(?iu)^play\\s+song\\s+(.+)$");
    private static final Pattern PT_PLAY_ALBUM = Pattern.compile("(?iu)^(?:tocar|toca)\\s+[aá]lbum\\s+(.+)$");
    private static final Pattern EN_PLAY_ALBUM = Pattern.compile("(?iu)^play\\s+album\\s+(.+)$");
    private static final Pattern PT_PLAY_ARTIST = Pattern.compile("(?iu)^(?:tocar|toca)\\s+artista\\s+(.+)$");
    private static final Pattern EN_PLAY_ARTIST = Pattern.compile("(?iu)^play\\s+artist\\s+(.+)$");
    private static final Pattern PT_GENERIC = Pattern.compile("(?iu)^(?:tocar|toca)\\s+(.+)$");
    private static final Pattern EN_GENERIC = Pattern.compile("(?iu)^play\\s+(.+)$");

    public VoiceCommand parse(String text) {
        if (text == null) return null;
        Matcher wake = WAKE_PREFIX.matcher(text);
        if (!wake.find()) return null;

        String body = trimOuterPunctuation(text.substring(wake.end()).trim());
        if (body.isBlank()) return null;
        String n = normalize(body);

        VoiceCommand fixed = parseFixedCommand(n);
        if (fixed != null) return fixed;

        return parseQueryCommand(body);
    }

    private VoiceCommand parseFixedCommand(String n) {
        if (equalsAny(n, "encerrar comando de voz", "desligar comando de voz", "disable voice command", "stop voice commands"))
            return VoiceCommand.simple(VoiceCommand.Type.DISABLE_VOICE);

        if (equalsAny(n, "linguagem portugues", "idioma portugues", "language portuguese"))
            return VoiceCommand.language("pt-BR");
        if (equalsAny(n, "linguagem ingles", "idioma ingles", "language english"))
            return VoiceCommand.language("en-US");

        if (equalsAny(n, "tocar todas as musicas aleatorias", "tocar musicas aleatorias", "shuffle all songs", "shuffle all music"))
            return VoiceCommand.simple(VoiceCommand.Type.SHUFFLE_ALL);

        if (equalsAny(n, "aumentar um pouco o volume", "aumenta um pouco o volume", "aumentar volume", "aumenta volume", "volume up", "increase volume"))
            return VoiceCommand.value(VoiceCommand.Type.VOLUME_DELTA, 10);
        if (equalsAny(n, "diminuir um pouco o volume", "diminui um pouco o volume", "diminuir volume", "diminui volume", "volume down", "decrease volume"))
            return VoiceCommand.value(VoiceCommand.Type.VOLUME_DELTA, -10);
        if (equalsAny(n, "aumente todo o volume", "aumentar todo o volume", "volume maximo", "volume no maximo", "full volume", "maximum volume"))
            return VoiceCommand.value(VoiceCommand.Type.VOLUME_SET, 100);
        if (equalsAny(n, "diminua todo o volume", "diminuir todo o volume", "volume zero", "zero volume"))
            return VoiceCommand.value(VoiceCommand.Type.VOLUME_SET, 0);
        if (equalsAny(n, "mutar volume", "mudo", "silenciar volume", "mute", "mute volume"))
            return VoiceCommand.simple(VoiceCommand.Type.MUTE);
        if (equalsAny(n, "restaurar volume", "desmutar volume", "unmute", "restore volume"))
            return VoiceCommand.simple(VoiceCommand.Type.UNMUTE);

        if (equalsAny(n, "proxima", "proxima musica", "next", "next song"))
            return VoiceCommand.simple(VoiceCommand.Type.NEXT);
        if (equalsAny(n, "anterior", "musica anterior", "previous", "previous song"))
            return VoiceCommand.simple(VoiceCommand.Type.PREVIOUS);
        if (equalsAny(n, "pausa", "pausar", "pausar musica", "pause", "pause music"))
            return VoiceCommand.simple(VoiceCommand.Type.PAUSE);
        if (equalsAny(n, "parar", "parar musica", "stop", "stop music"))
            return VoiceCommand.simple(VoiceCommand.Type.STOP);
        if (equalsAny(n, "tocar", "tocar musica", "toca", "continuar", "continuar musica", "play", "play music", "resume", "resume music"))
            return VoiceCommand.simple(VoiceCommand.Type.PLAY);

        return null;
    }

    private VoiceCommand parseQueryCommand(String body) {
        VoiceCommand command;
        command = query(PT_SEARCH_SONG, body, VoiceCommand.Type.SEARCH_SONG); if (command != null) return command;
        command = query(EN_SEARCH_SONG, body, VoiceCommand.Type.SEARCH_SONG); if (command != null) return command;
        command = query(PT_PLAY_SONG, body, VoiceCommand.Type.PLAY_SONG); if (command != null) return command;
        command = query(EN_PLAY_SONG, body, VoiceCommand.Type.PLAY_SONG); if (command != null) return command;
        command = query(PT_PLAY_ALBUM, body, VoiceCommand.Type.PLAY_ALBUM); if (command != null) return command;
        command = query(EN_PLAY_ALBUM, body, VoiceCommand.Type.PLAY_ALBUM); if (command != null) return command;
        command = query(PT_PLAY_ARTIST, body, VoiceCommand.Type.PLAY_ARTIST); if (command != null) return command;
        command = query(EN_PLAY_ARTIST, body, VoiceCommand.Type.PLAY_ARTIST); if (command != null) return command;
        command = query(PT_GENERIC, body, VoiceCommand.Type.PLAY_GENERIC); if (command != null) return command;
        return query(EN_GENERIC, body, VoiceCommand.Type.PLAY_GENERIC);
    }

    private VoiceCommand query(Pattern pattern, String body, VoiceCommand.Type type) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.matches()) return null;
        String value = trimOuterPunctuation(matcher.group(1).trim());
        if (value.isBlank()) return null;
        return VoiceCommand.query(type, value);
    }

    static String normalize(String value) {
        String n = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return n.replaceAll("\\s+", " ");
    }

    private static String trimOuterPunctuation(String value) {
        return value.replaceAll("^[\\s,.:;!?-]+", "")
                .replaceAll("[\\s,.:;!?-]+$", "")
                .trim();
    }

    private static boolean equalsAny(String value, String... options) {
        for (String option : options) if (value.equals(option)) return true;
        return false;
    }
}
