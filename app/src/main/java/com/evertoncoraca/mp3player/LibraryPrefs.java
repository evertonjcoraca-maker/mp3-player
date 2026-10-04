package com.evertoncoraca.mp3player;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LibraryPrefs {
    private static final String PREF = "library_prefs";
    private static final String ROOTS = "roots";
    private static final String ACTIVE = "active";
    private static final String VOLUME = "volume";
    private static final String LUFFY = "luffy";
    private static final String VOICE_CONFIGURED = "voice_configured";
    private static final String VOICE_ENABLED = "voice_enabled";
    private static final String PENDING_SEARCH = "pending_search";

    private final SharedPreferences prefs;

    public LibraryPrefs(Context context) { prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE); }

    public List<String> roots() {
        Set<String> set = prefs.getStringSet(ROOTS, new LinkedHashSet<>());
        return new ArrayList<>(set == null ? new LinkedHashSet<>() : set);
    }

    public void addRoot(String uri) {
        LinkedHashSet<String> roots = new LinkedHashSet<>(roots());
        roots.add(uri);
        prefs.edit().putStringSet(ROOTS, roots).apply();
        if (activeRoot() == null) setActiveRoot(uri);
    }

    public void removeRoot(String uri) {
        LinkedHashSet<String> roots = new LinkedHashSet<>(roots());
        roots.remove(uri);
        SharedPreferences.Editor edit = prefs.edit().putStringSet(ROOTS, roots);
        if (uri != null && uri.equals(activeRoot())) edit.putString(ACTIVE, roots.isEmpty() ? null : roots.iterator().next());
        edit.apply();
    }

    public String activeRoot() { return prefs.getString(ACTIVE, null); }
    public void setActiveRoot(String uri) { prefs.edit().putString(ACTIVE, uri).apply(); }
    public int volume() { return Math.max(0, Math.min(100, prefs.getInt(VOLUME, 70))); }
    public void setVolume(int value) { prefs.edit().putInt(VOLUME, Math.max(0, Math.min(100, value))).apply(); }
    public boolean luffy() { return prefs.getBoolean(LUFFY, false); }
    public void setLuffy(boolean enabled) { prefs.edit().putBoolean(LUFFY, enabled).apply(); }

    public boolean voiceConfigured() { return prefs.getBoolean(VOICE_CONFIGURED, false); }
    public boolean voiceEnabled() { return prefs.getBoolean(VOICE_ENABLED, false); }
    public void setVoiceEnabled(boolean enabled) {
        prefs.edit().putBoolean(VOICE_CONFIGURED, true).putBoolean(VOICE_ENABLED, enabled).apply();
    }

    public void setPendingSearch(String query) {
        prefs.edit().putString(PENDING_SEARCH, query == null ? "" : query).apply();
    }

    public String consumePendingSearch() {
        String query = prefs.getString(PENDING_SEARCH, "");
        prefs.edit().remove(PENDING_SEARCH).apply();
        return query == null ? "" : query;
    }
}
