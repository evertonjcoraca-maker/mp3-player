package com.evertoncoraca.mp3player;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.UriPermission;
import android.graphics.Bitmap;
import android.net.Uri;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private NativePlaybackEngine playback;
    private VoiceUiController voiceUi;
    private Track currentTrack;
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
        playback = new NativePlaybackEngine(this, new NativePlaybackEngine.Listener() {
            @Override public void onTrackChanged(Track track) {
                currentTrack = track;
                refreshPlayerUi();
                loadCover(track);
                adapter.notifyDataSetChanged();
            }

            @Override public void onPlaybackChanged(boolean isPlaying) {
                refreshPlayerUi();
            }

            @Override public void onError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                refreshPlayerUi();
            }

            @Override public void onVolumeChanged(int volume) {
                if (!userSeeking) {
                    volumeSeek.setProgress(volume);
                    volumeValue.setText(String.valueOf(volume));
                }
                prefs.setVolume(volume);
            }
        });
        playback.setVolume(prefs.volume() / 100f);
        playback.setLuffy(prefs.luffy());
        setupUi();
        voiceUi = new VoiceUiController(this, prefs, this::applyVoiceSearch);
        if (prefs.roots().isEmpty()) showEmpty(true); else loadActiveRoot();
        scheduleProgressTick();
    }

    @Override protected void onStart() {
        super.onStart();
        if (voiceUi != null) voiceUi.onStart();
    }

    @Override protected void onStop() {
        if (voiceUi != null) voiceUi.onStop();
        super.onStop();
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

        findViewById(R.id.previousButton).setOnClickListener(v -> playback.previous());
        playPauseButton.setOnClickListener(v -> handlePlayPause());
        findViewById(R.id.nextButton).setOnClickListener(v -> playback.next());

        volumeSeek.setProgress(prefs.volume());
        volumeValue.setText(String.valueOf(prefs.volume()));
        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volumeValue.setText(String.valueOf(progress));
                if (fromUser) {
                    playback.setVolume(progress / 100f);
                    prefs.setVolume(progress);
                }
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
            playback.setLuffy(enabled);
        });

        progressSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                long duration = playback.getDuration();
                if (duration > 0) playback.seekTo(duration * seekBar.getProgress() / 1000L);
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

    private void handlePlayPause() {
        if (playback.isPlaying()) {
            playback.pause();
            return;
        }
        if (playback.hasCurrent()) {
            playback.play();
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
            Toast.makeText(this, "Não há MP3 ou WAV disponível nesta pasta.", Toast.LENGTH_LONG).show();
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
                if (pendingPlayFirst) {
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
                .setMessage("Motor de áudio: Media3 em serviço de reprodução.\n\nLuffy reforça o volume percebido em +6 dB. O aplicativo não usa equalizador.\n\nComando de voz: diga ‘Player’ e em seguida o comando.\n\nAs pastas são acessadas somente pelas permissões que você escolheu no Android.")
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

    private void applyVoiceSearch(String query) {
        if (query == null || query.isBlank()) return;
        searchInput.setVisibility(View.VISIBLE);
        searchInput.setText(query);
        searchInput.setSelection(searchInput.length());
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
        if (!canOpenTrack(track)) {
            Toast.makeText(this,
                    "O Android não permitiu abrir este áudio. Escolha novamente a pasta de músicas.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        List<Track> queue = activeLibrary.flattenTracks();
        boolean repeatAll = activeLibrary.rootActsAsPlaylist();
        if (!queue.contains(track)) {
            queue = List.of(track);
            repeatAll = false;
        }
        playback.playQueue(queue, track, repeatAll);
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

    private void refreshPlayerUi() {
        if (currentTrack != null) {
            nowTitle.setText(currentTrack.displayName);
            nowArtist.setText(currentTrack.artist);
        }
        playPauseButton.setText(playback != null && playback.isPlaying() ? "Ⅱ" : "▶");
    }

    private void scheduleProgressTick() {
        mainHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (playback != null) {
                    long duration = playback.getDuration();
                    long position = playback.getCurrentPosition();
                    if (duration > 0) {
                        if (!userSeeking) progressSeek.setProgress((int) Math.min(1000, position * 1000L / duration));
                        totalTime.setText(formatTime(duration));
                    } else {
                        totalTime.setText("00:00");
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

    @Override protected void onDestroy() {
        if (playback != null) playback.release();
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
