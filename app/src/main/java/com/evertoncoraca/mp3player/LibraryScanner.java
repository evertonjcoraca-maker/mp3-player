package com.evertoncoraca.mp3player;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.Locale;

public final class LibraryScanner {
    private final Context context;

    public LibraryScanner(Context context) { this.context = context.getApplicationContext(); }

    public LibraryNode scan(String rootUri) {
        Uri uri = Uri.parse(rootUri);
        DocumentFile root = DocumentFile.fromTreeUri(context, uri);
        if (root == null || !root.exists() || !root.canRead()) return LibraryNode.folder("Pasta indisponível", rootUri);
        LibraryNode node = scanFolder(root);
        node.sortRecursively();
        return node;
    }

    private LibraryNode scanFolder(DocumentFile folder) {
        LibraryNode node = LibraryNode.folder(safeName(folder), folder.getUri().toString());
        DocumentFile[] files;
        try { files = folder.listFiles(); }
        catch (RuntimeException e) { return node; }
        for (DocumentFile file : files) {
            if (file.isDirectory()) node.children.add(scanFolder(file));
            else if (isSupportedAudio(file)) node.children.add(LibraryNode.track(readTrack(file, folder)));
        }
        return node;
    }

    private Track readTrack(DocumentFile file, DocumentFile parent) {
        String artist = null;
        String album = null;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, file.getUri());
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
        } catch (RuntimeException ignored) {
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        return new Track(file.getUri().toString(), parent.getUri().toString(), safeName(file), artist, album);
    }

    private boolean isSupportedAudio(DocumentFile file) {
        String name = safeName(file).toLowerCase(Locale.ROOT);
        String type = file.getType();
        if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".wave")) return true;
        if (type == null) return false;
        return "audio/mpeg".equalsIgnoreCase(type)
                || "audio/mp3".equalsIgnoreCase(type)
                || "audio/wav".equalsIgnoreCase(type)
                || "audio/x-wav".equalsIgnoreCase(type)
                || "audio/vnd.wave".equalsIgnoreCase(type);
    }

    private String safeName(DocumentFile file) {
        String name = file.getName();
        return name == null || name.isBlank() ? "Sem nome" : name;
    }
}
