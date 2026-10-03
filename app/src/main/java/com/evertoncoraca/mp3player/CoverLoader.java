package com.evertoncoraca.mp3player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

public final class CoverLoader {
    private static final Set<String> COVER_NAMES = Set.of(
            "cover.jpg", "cover.jpeg", "cover.png",
            "folder.jpg", "folder.jpeg", "folder.png",
            "capa.jpg", "capa.jpeg", "capa.png"
    );

    private CoverLoader() {}

    public static Bitmap load(Context context, Track track) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, Uri.parse(track.uri));
            byte[] bytes = mmr.getEmbeddedPicture();
            if (bytes != null && bytes.length > 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (RuntimeException ignored) {
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }

        try {
            DocumentFile parent = DocumentFile.fromTreeUri(context, Uri.parse(track.parentUri));
            if (parent != null) {
                for (DocumentFile file : parent.listFiles()) {
                    String name = file.getName();
                    if (name != null && COVER_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                        try (InputStream in = context.getContentResolver().openInputStream(file.getUri())) {
                            if (in != null) return BitmapFactory.decodeStream(in);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
