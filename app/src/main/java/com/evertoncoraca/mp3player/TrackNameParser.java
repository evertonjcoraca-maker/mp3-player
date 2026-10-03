package com.evertoncoraca.mp3player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TrackNameParser {
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d+)");

    private TrackNameParser() {}

    public static String withoutExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static int leadingNumber(String fileName) {
        Matcher matcher = LEADING_NUMBER.matcher(withoutExtension(fileName));
        if (!matcher.find()) return Integer.MAX_VALUE;
        try { return Integer.parseInt(matcher.group(1)); }
        catch (NumberFormatException ignored) { return Integer.MAX_VALUE; }
    }

    public static int compareFileNames(String left, String right) {
        int ln = leadingNumber(left);
        int rn = leadingNumber(right);
        if (ln != rn) return Integer.compare(ln, rn);
        return withoutExtension(left).toLowerCase(Locale.ROOT)
                .compareTo(withoutExtension(right).toLowerCase(Locale.ROOT));
    }
}
