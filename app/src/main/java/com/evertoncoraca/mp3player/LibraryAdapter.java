package com.evertoncoraca.mp3player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LibraryAdapter extends RecyclerView.Adapter<LibraryAdapter.Holder> {
    public interface Listener {
        void onFolderClicked(LibraryNode node);
        void onTrackClicked(Track track);
        boolean isCurrentTrack(Track track);
    }

    private static final class Row {
        final LibraryNode node;
        final int depth;
        Row(LibraryNode node, int depth) { this.node = node; this.depth = depth; }
    }

    private final Listener listener;
    private LibraryNode root;
    private final List<Row> rows = new ArrayList<>();
    private final List<String> expandedPath = new ArrayList<>();

    public LibraryAdapter(Listener listener) { this.listener = listener; }

    public void setRoot(LibraryNode root) {
        this.root = root;
        expandedPath.clear();
        rebuild();
    }

    public void setFlatTracks(List<Track> tracks) {
        LibraryNode virtual = LibraryNode.folder("Busca", "virtual:search");
        for (Track track : tracks) virtual.children.add(LibraryNode.track(track));
        root = virtual;
        expandedPath.clear();
        rebuild();
    }

    public boolean canGoBack() { return !expandedPath.isEmpty(); }

    public void goBack() {
        if (!expandedPath.isEmpty()) {
            expandedPath.remove(expandedPath.size() - 1);
            rebuild();
        }
    }

    public void toggleFolder(LibraryNode target) {
        List<String> path = new ArrayList<>();
        if (!findPath(root, target.uri, path)) return;
        if (!expandedPath.isEmpty() && expandedPath.get(expandedPath.size() - 1).equals(target.uri)) {
            expandedPath.remove(expandedPath.size() - 1);
        } else {
            expandedPath.clear();
            expandedPath.addAll(path);
        }
        rebuild();
    }

    private boolean findPath(LibraryNode node, String targetUri, List<String> path) {
        for (LibraryNode child : node.children) {
            if (child.type != LibraryNode.Type.FOLDER) continue;
            path.add(child.uri);
            if (child.uri.equals(targetUri)) return true;
            if (findPath(child, targetUri, path)) return true;
            path.remove(path.size() - 1);
        }
        return false;
    }

    private void rebuild() {
        rows.clear();
        if (root != null) appendChildren(root, 0, new HashSet<>(expandedPath));
        notifyDataSetChanged();
    }

    private void appendChildren(LibraryNode parent, int depth, Set<String> expanded) {
        for (LibraryNode child : parent.children) {
            rows.add(new Row(child, depth));
            if (child.type == LibraryNode.Type.FOLDER && expanded.contains(child.uri)) appendChildren(child, depth + 1, expanded);
        }
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_library_node, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        Row row = rows.get(position);
        LibraryNode node = row.node;
        h.indent.getLayoutParams().width = dp(h.itemView, row.depth * 28);
        h.indent.requestLayout();
        boolean folder = node.type == LibraryNode.Type.FOLDER;
        h.arrow.setText(folder ? (expandedPath.contains(node.uri) ? "⌄" : "›") : "");
        h.icon.setText(folder ? "■" : "♫");
        h.icon.setTextColor(h.itemView.getResources().getColor(folder ? R.color.folder : R.color.accent, null));
        h.name.setText(node.name);
        h.subtitle.setText(folder ? folderSubtitle(node) : node.track.artist);
        h.menu.setVisibility(folder ? View.VISIBLE : View.INVISIBLE);
        boolean selected = !folder && listener.isCurrentTrack(node.track);
        h.rowRoot.setBackgroundResource(selected ? R.drawable.bg_selected : android.R.color.transparent);
        h.itemView.setOnClickListener(v -> { if (folder) listener.onFolderClicked(node); else listener.onTrackClicked(node.track); });
    }

    private String folderSubtitle(LibraryNode node) {
        int tracks = node.flattenTracks().size();
        return tracks == 1 ? "1 faixa" : tracks + " faixas";
    }

    @Override public int getItemCount() { return rows.size(); }
    private int dp(View view, int value) { return Math.round(value * view.getResources().getDisplayMetrics().density); }

    static final class Holder extends RecyclerView.ViewHolder {
        final LinearLayout rowRoot;
        final TextView indent, arrow, icon, name, subtitle, menu;
        Holder(View itemView) {
            super(itemView);
            rowRoot = itemView.findViewById(R.id.rowRoot);
            indent = itemView.findViewById(R.id.indent);
            arrow = itemView.findViewById(R.id.arrow);
            icon = itemView.findViewById(R.id.icon);
            name = itemView.findViewById(R.id.name);
            subtitle = itemView.findViewById(R.id.subtitle);
            menu = itemView.findViewById(R.id.menu);
        }
    }
}
