package com.evertoncoraca.mp3player;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements LibraryAdapter.Listener {
    private enum ViewMode { MUSIC, ALBUMS, ARTISTS, FOLDERS }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, LibraryNode> cache = new HashMap<>();
    private final Map<String, String> rootNames = new HashMap<>();

    private LibraryPrefs prefs;
    private LibraryAdapter adapter;
    private LibraryNode activeLibrary;
    private ViewMode viewMode = ViewMode.MUSIC;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private Track currentTrack;
    private Track pendingTrack;
    private boolean pendingPlayFirst;
    private boolean userSeeking;

    private TextView folderText, nowTitle, nowArtist, currentTime, totalTime, volumeValue, luffyStatus;
    private Button tabMusic, tabAlbums, tabArtists, tabFolders, playPauseButton;
    private EditText searchInput;
    private ProgressBar loading;
    private SeekBar progressSeek, volumeSeek;
    private Switch luffySwitch;
    private ImageView coverImage;
    private View emptyPanel;

    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                boolean persisted = false;
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    persisted = hasPersistedReadPermission(uri);
                } catch (SecurityException e) {
                    Toast.makeText(this,
                            "O Android não permitiu manter o acesso à pasta. Se as músicas pararem de abrir, escolha a pasta novamente.",
                            Toast.LENGTH_LONG).show();
                }
                if (!persisted) {
                    Toast.makeText(this,
                            "A pasta foi aberta, mas o acesso permanente não foi confirmado pelo Android.",
                            Toast.LENGTH_LONG).show();
                }
                String value = uri.toString();
                prefs.addRoot(value);
                prefs.setActiveRoot(value);
                cache.remove(value);
                loadActiveRoot();
            });

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new LibraryPrefs(this);
        bindViews();
        setupUi();
        connectController();
        requestNotificationPermission();
        if (prefs.roots().isEmpty()) showEmpty(true); else loadActiveRoot();
        scheduleProgressTick();
    }

    private void bindViews() {
        folderText = findViewById(R.id.folderText);
        nowTitle = findViewById(R.id.nowTitle);
        nowArtist = findViewById(R.id.nowArtist);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        volumeValue = findViewById(R.id.volumeValue);
        luffyStatus = findViewById(R.id.luffyStatus);
        tabMusic = findViewById(R.id.tabMusic);
        tabAlbums = findViewById(R.id.tabAlbums);
        tabArtists = findViewById(R.id.tabArtists);
        tabFolders = findViewById(R.id.tabFolders);
        playPauseButton = findViewById(R.id.playPauseButton);
        searchInput = findViewById(R.id.searchInput);
        loading = findViewById(R.id.loading);
        progressSeek = findViewById(R.id.progressSeek);
        volumeSeek = findViewById(R.id.volumeSeek);
        luffySwitch = findViewById(R.id.luffySwitch);
        coverImage = findViewById(R.id.coverImage);
        emptyPanel = findViewById(R.id.emptyPanel);

        RecyclerView list = findViewById(R.id.libraryList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LibraryAdapter(this);
        list.setAdapter(adapter);
    }

    private void setupUi() {
        findViewById(R.id.chooseFolderButton).setOnClickListener(v -> folderPicker.launch(null));
        findViewById(R.id.menuButton).setOnClickListener(v -> showRootsDialog());
        findViewById(R.id.settingsButton).setOnClickListener(v -> showSettingsDialog());
        findViewById(R.id.searchButton).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.backButton).setOnClickListener(v -> navigateBack());

        tabMusic.setOnClickListener(v -> setViewMode(ViewMode.MUSIC));
        tabAlbums.setOnClickListener(v -> setViewMode(ViewMode.ALBUMS));
        tabArtists.setOnClickListener(v -> setViewMode(ViewMode.ARTISTS));
        tabFolders.setOnClickListener(v -> setViewMode(ViewMode.FOLDERS));

        findViewById(R.id.previousButton).setOnClickListener(v -> {
            if (controller != null) controller.seekToPreviousMediaItem();
        });
        playPauseButton.setOnClickListener(v -> handlePlayPause());
        findViewById(R.id.nextButton).setOnClickListener(v -> {
            if (controller != null) controller.seekToNextMediaItem();
        });

        volumeSeek.setProgress(prefs.volume());
        volumeValue.setText(String.valueOf(prefs.volume()));
        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeValue.setText(String.valueOf(progress));
                if (fromUser && controller != null) controller.setVolume(progress / 100f);
                if (fromUser) prefs.setVolume(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        boolean luffy = prefs.luffy();
        luffySwitch.setChecked(luffy);
        updateLuffyLabel(luffy);
        luffySwitch.setOnCheckedChangeListener((button, enabled) -> {
            prefs.setLuffy(enabled);
            updateLuffyLabel(enabled);
            sendLuffy(enabled);
        });

        progressSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (controller != null && controller.getDuration() > 0) {
                    controller.seekTo(controller.getDuration() * seekBar.getProgress() / 1000L);
                }
                userSeeking = false;
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                mainHandler.postDelayed(() -> runSearch(q), 300);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void connectController() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> mainHandler.post(() -> {
            try {
                controller = controllerFuture.get();
                controller.setVolume(prefs.volume() / 100f);
                controller.addListener(new Player.Listener() {
                    @Override public void onIsPlayingChanged(boolean isPlaying) { refreshPlayerUi(); }

                    @Override public void onPlaybackStateChanged(int playbackState) { refreshPlayerUi(); }

                    @Override public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                        syncCurrentTrackFromController();
                        refreshPlayerUi();
                    }

                    @Override public void onPlayerError(PlaybackException error) {
                        String message = "Erro ao tocar: " + error.getErrorCodeName();
                        if (error.getMessage() != null && !error.getMessage().isBlank()) {
                            message += "\n" + error.getMessage();
                        }
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
                sendLuffy(prefs.luffy());
                syncCurrentTrackFromController();
                refreshPlayerUi();

                if (pendingTrack != null) {
                    Track track = pendingTrack;
                    pendingTrack = null;
                    playTrack(track);
                } else if (pendingPlayFirst) {
                    pendingPlayFirst = false;
                    playFirstTrack();
                }
            } catch (Exception e) {
                Toast.makeText(this,
                        "Não foi possível iniciar o reprodutor: " + e.getClass().getSimpleName(),
                        Toast.LENGTH_LONG).show();
            }
        }), MoreExecutors.directExecutor());
    }

    private void handlePlayPause() {
        if (controller == null) {
            pendingPlayFirst = true;
            Toast.makeText(this, "Preparando o reprodutor…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (controller.isPlaying()) {
            controller.pause();
            return;
        }
        if (controller.getMediaItemCount() > 0) {
            controller.play();
            return;
        }
        playFirstTrack();
    }

    private void playFirstTrack() {
        if (activeLibrary == null) {
            pendingPlayFirst = true;
            Toast.makeText(this, "A biblioteca ainda está carregando.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Track> tracks = activeLibrary.flattenTracks();
        if (tracks.isEmpty()) {
            Toast.makeText(this, "Não há MP3 disponível nesta pasta.", Toast.LENGTH_LONG).show();
            return;
        }
        playTrack(tracks.get(0));
    }

    private void loadActiveRoot() {
        String rootUri = prefs.activeRoot();
        if (rootUri == null) { showEmpty(true); return; }
        showEmpty(false);
        setLoading(true);
        executor.execute(() -> {
            LibraryNode node = cache.get(rootUri);
            if (node == null) {
                node = new LibraryScanner(this).scan(rootUri);
                cache.put(rootUri, node);
            }
            LibraryNode finalNode = node;
            rootNames.put(rootUri, node.name);
            mainHandler.post(() -> {
                activeLibrary = finalNode;
                folderText.setText("Pasta: " + finalNode.name);
                setLoading(false);
                setViewMode(viewMode == ViewMode.FOLDERS ? ViewMode.MUSIC : viewMode);
                if (pendingPlayFirst && controller != null) {
                    pendingPlayFirst = false;
                    playFirstTrack();
                }
            });
        });
    }

    private void setViewMode(ViewMode mode) {
        viewMode = mode;
        setTabColor(tabMusic, mode == ViewMode.MUSIC);
        setTabColor(tabAlbums, mode == ViewMode.ALBUMS);
        setTabColor(tabArtists, mode == ViewMode.ARTISTS);
        setTabColor(tabFolders, mode == ViewMode.FOLDERS);
        searchInput.setVisibility(View.GONE);
        if (mode == ViewMode.FOLDERS) { adapter.setRoot(buildRootsView()); return; }
        if (activeLibrary == null) return;
        if (mode == ViewMode.MUSIC) adapter.setRoot(activeLibrary);
        else if (mode == ViewMode.ALBUMS) adapter.setRoot(activeLibrary.albumsView());
        else adapter.setRoot(activeLibrary.artistsView());
    }

    private LibraryNode buildRootsView() {
        LibraryNode root = LibraryNode.folder("Pastas principais", "virtual:roots");
        for (String uri : prefs.roots()) {
            String name = rootNames.getOrDefault(uri, uri.equals(prefs.activeRoot()) ? "Pasta ativa" : "Pasta cadastrada");
            root.children.add(LibraryNode.folder(name, "root:" + uri));
        }
        return root;
    }

    private void setTabColor(Button button, boolean active) {
        button.setTextColor(getColor(active ? R.color.accent : R.color.muted));
    }

    private void showRootsDialog() {
        List<String> roots = prefs.roots();
        List<String> labels = new ArrayList<>();
        for (String uri : roots) labels.add((uri.equals(prefs.activeRoot()) ? "✓ " : "") + rootNames.getOrDefault(uri, uri));
        labels.add("＋ Adicionar pasta");
        if (!roots.isEmpty()) labels.add("Remover pasta ativa");
        new AlertDialog.Builder(this).setTitle("Pastas principais")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which < roots.size()) {
                        prefs.setActiveRoot(roots.get(which));
                        loadActiveRoot();
                    } else if (which == roots.size()) {
                        folderPicker.launch(null);
                    } else {
                        String active = prefs.activeRoot();
                        if (active != null) {
                            prefs.removeRoot(active);
                            cache.remove(active);
                            loadActiveRoot();
                        }
                    }
                }).show();
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Configurações")
                .setMessage("Luffy reforça o volume percebido em +6 dB. O aplicativo não usa equalizador.\n\nAs pastas são acessadas somente pelas permissões que você escolheu no Android.")
                .setPositiveButton("OK", null).show();
    }

    private void toggleSearch() {
        boolean show = searchInput.getVisibility() != View.VISIBLE;
        searchInput.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            searchInput.requestFocus();
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        } else {
            searchInput.setText("");
            setViewMode(viewMode);
        }
    }

    private void runSearch(String query) {
        if (searchInput.getVisibility() != View.VISIBLE) return;
        if (query.isBlank()) { setViewMode(viewMode); return; }
        setLoading(true);
        executor.execute(() -> {
            String needle = query.toLowerCase(Locale.ROOT);
            List<Track> matches = new ArrayList<>();
            for (String rootUri : prefs.roots()) {
                LibraryNode node = cache.get(rootUri);
                if (node == null) {
                    node = new LibraryScanner(this).scan(rootUri);
                    cache.put(rootUri, node);
                    rootNames.put(rootUri, node.name);
                }
                for (Track track : node.flattenTracks()) {
                    if (track.displayName.toLowerCase(Locale.ROOT).contains(needle)
                            || track.artist.toLowerCase(Locale.ROOT).contains(needle)
                            || track.album.toLowerCase(Locale.ROOT).contains(needle)) matches.add(track);
                }
            }
            mainHandler.post(() -> { adapter.setFlatTracks(matches); setLoading(false); });
        });
    }

    private void navigateBack() {
        if (searchInput.getVisibility() == View.VISIBLE) {
            searchInput.setVisibility(View.GONE);
            searchInput.setText("");
            setViewMode(viewMode);
        } else if (adapter.canGoBack()) adapter.goBack();
        else getOnBackPressedDispatcher().onBackPressed();
    }

    @Override public void onFolderClicked(LibraryNode node) {
        if (viewMode == ViewMode.FOLDERS && node.uri.startsWith("root:")) {
            prefs.setActiveRoot(node.uri.substring("root:".length()));
            viewMode = ViewMode.MUSIC;
            loadActiveRoot();
            return;
        }
        adapter.toggleFolder(node);
    }

    @Override public void onTrackClicked(Track track) { playTrack(track); }
    @Override public boolean isCurrentTrack(Track track) { return currentTrack != null && currentTrack.equals(track); }

    private void playTrack(Track track) {
        if (activeLibrary == null) {
            Toast.makeText(this, "A biblioteca ainda está carregando.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (controller == null) {
            pendingTrack = track;
            Toast.makeText(this, "Preparando o reprodutor…", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!canOpenTrack(track)) {
            Toast.makeText(this,
                    "O Android não permitiu abrir este MP3. Escolha novamente a pasta de músicas.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        List<Track> queue = activeLibrary.flattenTracks();
        int startIndex = queue.indexOf(track);
        if (startIndex < 0) { queue = List.of(track); startIndex = 0; }
        List<MediaItem> items = new ArrayList<>();
        for (Track item : queue) {
            MediaTransportData transport = MediaTransportData.fromTrack(item);
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(item.displayName)
                    .setArtist(item.artist)
                    .setAlbumTitle(item.album)
                    .build();
            MediaItem.RequestMetadata requestMetadata = new MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse(transport.requestUri))
                    .build();
            items.add(new MediaItem.Builder()
                    .setMediaId(transport.mediaId)
                    .setRequestMetadata(requestMetadata)
                    .setMediaMetadata(metadata)
                    .build());
        }
        controller.setMediaItems(items, startIndex, 0);
        controller.setRepeatMode(activeLibrary.rootActsAsPlaylist() ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
        controller.prepare();
        controller.play();
        currentTrack = track;
        refreshPlayerUi();
        loadCover(track);
        adapter.notifyDataSetChanged();
    }

    private boolean canOpenTrack(Track track) {
        try (ParcelFileDescriptor ignored = getContentResolver().openFileDescriptor(Uri.parse(track.uri), "r")) {
            return ignored != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasPersistedReadPermission(Uri uri) {
        for (UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (permission.isReadPermission() && permission.getUri().equals(uri)) return true;
        }
        return false;
    }

    private void syncCurrentTrackFromController() {
        if (controller == null || controller.getCurrentMediaItem() == null) return;
        currentTrack = findTrackByUri(controller.getCurrentMediaItem().mediaId);
        if (currentTrack != null) loadCover(currentTrack);
        adapter.notifyDataSetChanged();
    }

    private Track findTrackByUri(String uri) {
        for (LibraryNode node : cache.values()) for (Track t : node.flattenTracks()) if (t.uri.equals(uri)) return t;
        return null;
    }

    private void loadCover(Track track) {
        executor.execute(() -> {
            Bitmap bitmap = CoverLoader.load(this, track);
            mainHandler.post(() -> {
                if (currentTrack != null && currentTrack.equals(track)) {
                    if (bitmap != null) coverImage.setImageBitmap(bitmap);
                    else coverImage.setImageResource(R.drawable.ic_music_placeholder);
                }
            });
        });
    }

    private void sendLuffy(boolean enabled) {
        if (controller == null) return;
        Bundle args = new Bundle();
        args.putBoolean(PlaybackService.ARG_ENABLED, enabled);
        SessionCommand command = new SessionCommand(PlaybackService.COMMAND_SET_LUFFY, Bundle.EMPTY);
        ListenableFuture<SessionResult> future = controller.sendCustomCommand(command, args);
        future.addListener(() -> {}, MoreExecutors.directExecutor());
    }

    private void refreshPlayerUi() {
        if (controller == null) return;
        MediaItem item = controller.getCurrentMediaItem();
        if (item != null) {
            CharSequence title = item.mediaMetadata.title;
            CharSequence artist = item.mediaMetadata.artist;
            nowTitle.setText(title == null ? "Música" : title);
            nowArtist.setText(artist == null ? "" : artist);
        }
        playPauseButton.setText(controller.isPlaying() ? "Ⅱ" : "▶");
    }

    private void scheduleProgressTick() {
        mainHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (controller != null) {
                    long duration = controller.getDuration();
                    long position = controller.getCurrentPosition();
                    if (duration > 0) {
                        if (!userSeeking) progressSeek.setProgress((int) Math.min(1000, position * 1000L / duration));
                        totalTime.setText(formatTime(duration));
                    }
                    currentTime.setText(formatTime(Math.max(0, position)));
                }
                mainHandler.postDelayed(this, 500);
            }
        }, 500);
    }

    private String formatTime(long ms) {
        long total = ms / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60);
    }

    private void updateLuffyLabel(boolean enabled) {
        luffyStatus.setText(enabled ? getString(R.string.on) : getString(R.string.off));
        luffyStatus.setTextColor(getColor(enabled ? R.color.accent : R.color.muted));
    }

    private void setLoading(boolean value) { loading.setVisibility(value ? View.VISIBLE : View.GONE); }

    private void showEmpty(boolean empty) {
        emptyPanel.setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.libraryList).setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
        }
    }

    @Override protected void onDestroy() {
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        executor.shutdownNow();
        super.onDestroy();
    }
}
