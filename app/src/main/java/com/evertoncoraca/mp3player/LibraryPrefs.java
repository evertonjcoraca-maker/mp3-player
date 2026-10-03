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
    public int volume() { return prefs.getInt(VOLUME, 70); }
    public void setVolume(int value) { prefs.edit().putInt(VOLUME, value).apply(); }
    public boolean luffy() { return prefs.getBoolean(LUFFY, false); }
    public void setLuffy(boolean enabled) { prefs.edit().putBoolean(LUFFY, enabled).apply(); }
}
