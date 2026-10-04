package com.evertoncoraca.mp3player;

import android.content.Context;
import android.content.Intent;

public final class VoiceState {
    public static final String ACTION_CHANGED = "com.evertoncoraca.mp3player.VOICE_STATE_CHANGED";
    public static final String ACTION_SEARCH = "com.evertoncoraca.mp3player.VOICE_SEARCH";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_QUERY = "query";

    private VoiceState() {}

    public static void broadcast(Context context, boolean enabled) {
        Intent intent = new Intent(ACTION_CHANGED).setPackage(context.getPackageName());
        intent.putExtra(EXTRA_ENABLED, enabled);
        context.sendBroadcast(intent);
    }

    public static void broadcastSearch(Context context, String query) {
        Intent intent = new Intent(ACTION_SEARCH).setPackage(context.getPackageName());
        intent.putExtra(EXTRA_QUERY, query);
        context.sendBroadcast(intent);
    }
}
