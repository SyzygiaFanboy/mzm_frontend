package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.myapplication.adapter.OnlineMusicAdapter;
import com.example.myapplication.model.Playlist;
import com.example.myapplication.model.Song;
import com.example.myapplication.model.Songinf;
import com.example.myapplication.network.ServerConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class OnlineMusicActivity extends AppCompatActivity {
    private static final String TAG = "OnlineMusicActivity";
    private static final int PAGE_SIZE = 30;

    private enum SortMode {
        LATEST,
        HOT,
        NAME
    }

    private ImageView ivBgBlur;
    private View vBgTint;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvSongs;
    private TextView tvEmpty;

    private EditText etKeyword;
    private ImageButton btnSearch;
    private TextView btnSortLatest;
    private TextView btnSortHot;
    private TextView btnSortName;

    private View selectionBar;
    private CheckBox cbSelectAll;
    private TextView tvSelectedCount;
    private TextView btnAddToPlaylist;
    private TextView btnDeleteServer;
    private TextView btnExitSelect;

    private OnlineMusicAdapter adapter;
    private final List<Songinf> items = new ArrayList<>();

    private String currentPlaylist;
    private String currentKeyword = "";
    private SortMode sortMode = SortMode.LATEST;
    private int currentPage = 1;
    private volatile boolean hasMore = true;
    private volatile boolean isLoading = false;
    private boolean didModifyPlaylist = false;
    private final MusicPlayer.OnPlaybackStateChangeListener playerStateListener = new MusicPlayer.OnPlaybackStateChangeListener() {
        @Override
        public void onPlaybackStateChanged() {
            runOnUiThread(() -> refreshPlayingHighlight());
        }

        @Override
        public void onSongChanged() {
            runOnUiThread(() -> refreshPlayingHighlight());
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_music);

        currentPlaylist = getIntent().getStringExtra("current_playlist");
        if (currentPlaylist == null) {
            currentPlaylist = "";
        }

        initViews();
        initBottomPlayer();
        initList();
        setupListeners();

        currentKeyword = "";
        if (etKeyword != null) {
            etKeyword.setText("");
        }
        swipeRefresh.setRefreshing(true);
        loadPage(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPlaylistBackground();
        ((MyApp) getApplication()).getMusicPlayer().addOnPlaybackStateChangeListener(playerStateListener);
        refreshPlayingHighlight();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ((MyApp) getApplication()).getMusicPlayer().removeOnPlaybackStateChangeListener(playerStateListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.detachFromActivity(this);
    }

    @Override
    public void onBackPressed() {
        if (adapter != null && adapter.isSelectionMode()) {
            exitSelectionMode();
            return;
        }
        if (didModifyPlaylist) {
            Intent result = new Intent();
            result.putExtra("should_refresh", true);
            setResult(RESULT_OK, result);
        }
        super.onBackPressed();
    }

    private void initViews() {
        ivBgBlur = findViewById(R.id.detail_bg_blur);
        vBgTint = findViewById(R.id.detail_bg_tint);
        applyPlaylistBackground();

        View contentRoot = findViewById(R.id.contentRoot);
        if (contentRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (v, insets) -> {
                int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), imeBottom);
                return insets;
            });
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvSongs = findViewById(R.id.rvSongs);
        tvEmpty = findViewById(R.id.tvEmpty);

        etKeyword = findViewById(R.id.etKeyword);
        btnSearch = findViewById(R.id.btnSearch);
        btnSortLatest = findViewById(R.id.btnSortLatest);
        btnSortHot = findViewById(R.id.btnSortHot);
        btnSortName = findViewById(R.id.btnSortName);

        selectionBar = findViewById(R.id.selectionBar);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnAddToPlaylist = findViewById(R.id.btnAddToPlaylist);
        btnDeleteServer = findViewById(R.id.btnDeleteServer);
        btnExitSelect = findViewById(R.id.btnExitSelect);

        updateSortUi();
    }

    private void applyPlaylistBackground() {
        if (ivBgBlur == null || vBgTint == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
        String playlistBgPath = prefs.getString("playlist_background_path", null);
        int transparency = prefs.getInt("playlist_background_path_transparency", 180);
        int blurRadius = prefs.getInt("playlist_background_path_blur", 0);
        if (playlistBgPath == null || !new File(playlistBgPath).exists()) {
            // 背景未设置时：跟随歌单页面背景，直接清空，不再兜底使用 background.jpg
            ivBgBlur.setImageDrawable(null);
            ivBgBlur.setAlpha(0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ivBgBlur.setRenderEffect(null);
            }
            return;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        new Thread(() -> {
            Bitmap out = null;
            try {
                Bitmap original = decodeSampledBitmapFromFile(playlistBgPath, screenWidth, screenHeight);
                if (original != null) {
                    Bitmap scaled = createScaledBitmap(original, screenWidth, screenHeight);
                    if (original != scaled) {
                        original.recycle();
                    }
                    out = scaled;
                }
            } catch (Exception ignored) {
            }
            Bitmap finalOut = out;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (finalOut != null) {
                        finalOut.recycle();
                    }
                    return;
                }
                if (finalOut != null) {
                    ivBgBlur.setImageBitmap(finalOut);
                    ivBgBlur.setAlpha(Math.min(1f, Math.max(0f, transparency / 255f)));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (blurRadius > 0) {
                            float r = Math.min(25f, Math.max(0f, blurRadius));
                            ivBgBlur.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
                        } else {
                            ivBgBlur.setRenderEffect(null);
                        }
                    }
                } else {
                    ivBgBlur.setImageDrawable(null);
                    ivBgBlur.setAlpha(0f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ivBgBlur.setRenderEffect(null);
                    }
                }
            });
        }).start();
    }

    private Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private Bitmap createScaledBitmap(Bitmap originalBitmap, int targetWidth, int targetHeight) {
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();
        float scaleX = (float) targetWidth / originalWidth;
        float scaleY = (float) targetHeight / originalHeight;
        float scale = Math.max(scaleX, scaleY);
        int scaledWidth = Math.round(originalWidth * scale);
        int scaledHeight = Math.round(originalHeight * scale);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true);
        if (scaledWidth > targetWidth || scaledHeight > targetHeight) {
            int x = Math.max(0, (scaledWidth - targetWidth) / 2);
            int y = Math.max(0, (scaledHeight - targetHeight) / 2);
            Bitmap cropped = Bitmap.createBitmap(scaledBitmap, x, y,
                    Math.min(targetWidth, scaledWidth),
                    Math.min(targetHeight, scaledHeight));
            if (scaledBitmap != cropped) {
                scaledBitmap.recycle();
            }
            return cropped;
        }
        return scaledBitmap;
    }

    private void initBottomPlayer() {
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.attachToActivity(this);
        MusicPlayer player = ((MyApp) getApplication()).getMusicPlayer();
        globalManager.setOnBottomPlayerClickListener(this, () -> {
            if (player.getCurrentSong() == null) {
                Toast.makeText(this, "请先选择一首歌曲播放", Toast.LENGTH_SHORT).show();
                return;
            }
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
        });
    }

    private void initList() {
        adapter = new OnlineMusicAdapter(this, new OnlineMusicAdapter.Listener() {
            @Override
            public void onClick(int position) {
                handleSongClick(position);
            }

            @Override
            public void onLongPress(int position) {
                if (!adapter.isSelectionMode()) {
                    adapter.setSelectionMode(true);
                    selectionBar.setVisibility(View.VISIBLE);
                }
                adapter.toggleSelected(position);
                updateSelectionUi();
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                updateSelectionUi();
            }
        });
        adapter.setSongUrlProvider(this::buildSongUrl);

        rvSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSongs.setAdapter(adapter);
        rvSongs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) {
                    return;
                }
                int visible = lm.getChildCount();
                int total = lm.getItemCount();
                int firstVisible = lm.findFirstVisibleItemPosition();
                if (!isLoading && hasMore && (firstVisible + visible) >= (total - 6)) {
                    loadPage(false);
                }
            }
        });
    }

    private void setupListeners() {
        swipeRefresh.setOnRefreshListener(() -> loadPage(true));

        btnSearch.setOnClickListener(v -> runSearch());
        etKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                runSearch();
                return true;
            }
            return false;
        });

        btnSortLatest.setOnClickListener(v -> {
            sortMode = SortMode.LATEST;
            updateSortUi();
            applySort();
            adapter.submitList(items);
        });
        btnSortHot.setOnClickListener(v -> {
            sortMode = SortMode.HOT;
            updateSortUi();
            applySort();
            adapter.submitList(items);
        });
        btnSortName.setOnClickListener(v -> {
            sortMode = SortMode.NAME;
            updateSortUi();
            applySort();
            adapter.submitList(items);
        });

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (adapter == null || !adapter.isSelectionMode()) {
                return;
            }
            adapter.selectAll(isChecked);
        });
        btnExitSelect.setOnClickListener(v -> exitSelectionMode());
        btnAddToPlaylist.setOnClickListener(v -> showAddToPlaylistDialog());
        if (btnDeleteServer != null) {
            btnDeleteServer.setOnClickListener(v -> confirmDeleteSelectedFromServer());
        }
    }

    private boolean isAdmin() {
        return getSharedPreferences("user_pref", MODE_PRIVATE).getBoolean("is_admin", false);
    }

    private void confirmDeleteSelectedFromServer() {
        if (!isAdmin()) {
            Toast.makeText(this, "无管理员权限", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Songinf> selected = adapter != null ? adapter.getSelectedItems() : new ArrayList<>();
        if (selected == null || selected.isEmpty()) {
            Toast.makeText(this, "请先选择歌曲", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("删除云端歌曲")
                .setMessage("确认删除已选 " + selected.size() + " 首云端歌曲？\n此操作会删除服务器数据库记录及对应音频文件。")
                .setPositiveButton("删除", (d, w) -> deleteSelectedFromServer(selected))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteSelectedFromServer(List<Songinf> selected) {
        List<String> ids = new ArrayList<>();
        for (Songinf s : selected) {
            if (s != null && s.getSongid() != null && !s.getSongid().trim().isEmpty()) {
                ids.add(s.getSongid().trim());
            }
        }
        if (ids.isEmpty()) {
            Toast.makeText(this, "无有效songId", Toast.LENGTH_SHORT).show();
            return;
        }

        swipeRefresh.setRefreshing(true);
        new Thread(() -> {
            String error = null;
            boolean ok = false;
            try {
                String urlStr = ServerConfig.appBaseUrl() + "admin/songs";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Authorization", basicAdminAuth());
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

                String joined = TextUtils.join(",", ids);
                String form = "action=delete&songIds=" + URLEncoder.encode(joined, "UTF-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(form.getBytes("UTF-8"));
                }

                int code = conn.getResponseCode();
                InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
                String body = readStreamSafely(is, 64 * 1024);
                conn.disconnect();

                if (code == HttpURLConnection.HTTP_OK) {
                    ok = body != null && body.contains("\"status\":\"SUCCESS\"");
                    if (!ok) {
                        error = body;
                    }
                } else {
                    error = "HTTP " + code + ": " + body;
                }
            } catch (Exception e) {
                error = e.getMessage();
            }

            boolean finalOk = ok;
            String finalError = error;
            runOnUiThread(() -> {
                swipeRefresh.setRefreshing(false);
                if (!finalOk) {
                    Toast.makeText(this, "删除失败: " + finalError, Toast.LENGTH_SHORT).show();
                    return;
                }

                java.util.Set<String> toRemove = new java.util.HashSet<>(ids);
                java.util.Iterator<Songinf> it = items.iterator();
                while (it.hasNext()) {
                    Songinf s = it.next();
                    if (s != null && toRemove.contains(s.getSongid())) {
                        it.remove();
                    }
                }
                applySort();
                adapter.submitList(items);
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                exitSelectionMode();
                try {
                    MusicPlayer player = ((MyApp) getApplication()).getMusicPlayer();
                    if ("在线音乐".equals(player.getQueuePlaylist())) {
                        Song current = player.getCurrentSong();
                        String currentOnlineId = current != null ? current.getOnlineSongId() : null;
                        if (currentOnlineId != null && toRemove.contains(currentOnlineId)) {
                            player.stop();
                        }

                        List<Song> queue = new ArrayList<>();
                        int currentIndex = -1;
                        for (int i = 0; i < items.size(); i++) {
                            Songinf si = items.get(i);
                            Song local = toLocalSongForQueue(si);
                            if (local != null) {
                                queue.add(local);
                                if (currentOnlineId != null && currentOnlineId.equals(si.getSongid())) {
                                    currentIndex = queue.size() - 1;
                                }
                            }
                        }
                        if (queue.isEmpty()) {
                            player.setPlayQueue("在线音乐", queue, -1);
                        } else if (currentIndex >= 0) {
                            player.setPlayQueue("在线音乐", queue, currentIndex);
                        } else {
                            int idx = player.getQueueIndex();
                            if (idx < 0 || idx >= queue.size()) {
                                idx = 0;
                            }
                            player.setPlayQueue("在线音乐", queue, idx);
                        }
                        refreshPlayingHighlight();
                    }
                } catch (Exception ignored) {
                }
                Toast.makeText(this, "已删除云端歌曲", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private String basicAdminAuth() {
        String raw = "admin:admin";
        String b64 = android.util.Base64.encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
        return "Basic " + b64;
    }

    private static String readStreamSafely(InputStream is, int maxBytes) {
        if (is == null) {
            return "";
        }
        int cap = Math.max(1024, maxBytes);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() + line.length() > cap) {
                    sb.append(line, 0, Math.max(0, cap - sb.length()));
                    break;
                }
                sb.append(line);
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private void runSearch() {
        String q = etKeyword.getText() != null ? etKeyword.getText().toString().trim() : "";
        currentKeyword = q;
        swipeRefresh.setRefreshing(true);
        loadPage(true);
    }

    private void loadPage(boolean reset) {
        if (isLoading) {
            return;
        }
        isLoading = true;
        if (reset) {
            currentPage = 1;
            hasMore = true;
            items.clear();
            adapter.submitList(items);
            tvEmpty.setVisibility(View.GONE);
        }

        String keyword = currentKeyword;
        int pageToLoad = currentPage;

        new Thread(() -> {
            List<Songinf> result = null;
            String error = null;
            try {
                String encoded = URLEncoder.encode(keyword != null ? keyword : "", "UTF-8");
                String safeKeyword = (keyword == null) ? "" : keyword.trim();
                if (safeKeyword.isEmpty()) {
                    safeKeyword = "%";
                }
                String encodedSafe = URLEncoder.encode(safeKeyword, "UTF-8");
                String urlStr = ServerConfig.appBaseUrl()
                        + "jiekou?keyword=" + encodedSafe
                        + "&page=" + pageToLoad
                        + "&pageSize=" + PAGE_SIZE;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    error = "HTTP " + responseCode;
                } else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    result = new Gson().fromJson(sb.toString(), new TypeToken<List<Songinf>>() {
                    }.getType());
                }
                conn.disconnect();
            } catch (Exception e) {
                error = e.getMessage();
                Log.e(TAG, "loadPage failed", e);
            }

            List<Songinf> finalResult = result;
            String finalError = error;
            runOnUiThread(() -> {
                isLoading = false;
                swipeRefresh.setRefreshing(false);

                if (finalResult == null) {
                    if (reset) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                    if (finalError != null) {
                        Toast.makeText(this, "加载失败: " + finalError, Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                if (finalResult.size() < PAGE_SIZE) {
                    hasMore = false;
                }
                if (reset) {
                    items.clear();
                }
                items.addAll(finalResult);
                applySort();
                adapter.submitList(items);
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (hasMore) {
                    currentPage++;
                }
            });
        }).start();
    }

    private void updateSortUi() {
        updateSortButtonStyle(btnSortLatest, sortMode == SortMode.LATEST);
        updateSortButtonStyle(btnSortHot, sortMode == SortMode.HOT);
        updateSortButtonStyle(btnSortName, sortMode == SortMode.NAME);
    }

    private void updateSortButtonStyle(TextView view, boolean selected) {
        if (view == null) {
            return;
        }
        view.setTextColor(selected ? 0xFFFFFFFF : 0xE6FFFFFF);
        view.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setBackgroundColor(selected ? 0x44c69bc5 : 0x00000000);
    }

    private void applySort() {
        if (items.isEmpty()) {
            return;
        }
        if (sortMode == SortMode.LATEST) {
            Collections.sort(items, (a, b) -> {
                long ai = safeParseLong(a != null ? a.getSongid() : null);
                long bi = safeParseLong(b != null ? b.getSongid() : null);
                if (ai == bi) return 0;
                return ai < bi ? 1 : -1;
            });
            return;
        }
        if (sortMode == SortMode.HOT) {
            Collections.sort(items, (a, b) -> b.getPlayCount() - a.getPlayCount());
            return;
        }
        if (sortMode == SortMode.NAME) {
            Collections.sort(items, Comparator.comparing(a -> (a.getSongname() != null ? a.getSongname() : ""), String::compareToIgnoreCase));
        }
    }

    private long safeParseLong(String s) {
        if (s == null) return 0L;
        try {
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String buildSongUrl(Songinf serverSong) {
        String cleanPath = serverSong != null ? serverSong.getSongpath() : "";
        if (cleanPath == null) {
            cleanPath = "";
        }
        if (cleanPath.startsWith("/")) {
            cleanPath = cleanPath.substring(1);
        }
        return ServerConfig.appBaseUrl() + cleanPath;
    }

    private void playFromPosition(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        MusicPlayer player = ((MyApp) getApplication()).getMusicPlayer();
        List<Song> queue = new ArrayList<>();
        for (Songinf s : items) {
            Song song = toLocalSongForQueue(s);
            if (song != null) {
                queue.add(song);
            }
        }
        if (queue.isEmpty()) {
            Toast.makeText(this, "暂无可播放歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        int startIndex = Math.max(0, Math.min(position, queue.size() - 1));
        player.setPlayQueue("在线音乐", queue, startIndex);
        try {
            player.playAtQueueIndex(startIndex);
        } catch (Exception e) {
            Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        refreshPlayingHighlight();
        startActivity(new Intent(this, MusicDetailActivity.class));
    }

    private void handleSongClick(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        Songinf clicked = items.get(position);
        MusicPlayer player = ((MyApp) getApplication()).getMusicPlayer();
        Song current = player.getCurrentSong();
        boolean inOnlineQueue = "在线音乐".equals(player.getQueuePlaylist());
        boolean isCurrent = false;
        if (inOnlineQueue && current != null && clicked != null) {
            String currentOnlineId = current.getOnlineSongId();
            if (!TextUtils.isEmpty(currentOnlineId) && currentOnlineId.equals(clicked.getSongid())) {
                isCurrent = true;
            } else {
                String currentPath = current.getFilePath();
                String clickedUrl = buildSongUrl(clicked);
                isCurrent = !TextUtils.isEmpty(currentPath) && currentPath.equals(clickedUrl);
            }
        }
        if (isCurrent) {
            startActivity(new Intent(this, MusicDetailActivity.class));
            return;
        }
        playFromPosition(position);
    }

    private void refreshPlayingHighlight() {
        if (adapter == null) {
            return;
        }
        MusicPlayer player = ((MyApp) getApplication()).getMusicPlayer();
        Song current = player.getCurrentSong();
        String queuePlaylist = player.getQueuePlaylist();
        String songId = current != null ? current.getOnlineSongId() : null;
        String songUrl = current != null ? current.getFilePath() : null;
        adapter.setCurrentPlaying(queuePlaylist, songId, songUrl);
    }

    private Song toLocalSongForQueue(Songinf serverSong) {
        if (serverSong == null) {
            return null;
        }
        String songName = serverSong.getSongname();
        String musician = serverSong.getMusician();
        String combined;
        if (!TextUtils.isEmpty(musician)) {
            combined = musician + " - " + songName;
        } else {
            combined = songName;
        }
        Song s = new Song(serverSong.getSongduration(), combined, buildSongUrl(serverSong), "在线音乐");
        s.setOnlineSongId(serverSong.getSongid());
        String coverUrl = serverSong.getCoverUrl();
        if (coverUrl != null && !coverUrl.trim().isEmpty() && !"null".equalsIgnoreCase(coverUrl.trim())) {
            s.setCoverUrl(coverUrl);
        }
        return s;
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        selectionBar.setVisibility(View.GONE);
        cbSelectAll.setChecked(false);
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        int selectedCount = adapter.getSelectedCount();
        tvSelectedCount.setText("已选 " + selectedCount);

        if (btnDeleteServer != null) {
            boolean show = isAdmin() && adapter.isSelectionMode();
            btnDeleteServer.setVisibility(show ? View.VISIBLE : View.GONE);
        }

        cbSelectAll.setOnCheckedChangeListener(null);
        cbSelectAll.setChecked(adapter.isAllSelected());
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!adapter.isSelectionMode()) {
                return;
            }
            adapter.selectAll(isChecked);
        });
    }

    private void showAddToPlaylistDialog() {
        List<Songinf> selected = adapter.getSelectedItems();
        if (selected.isEmpty()) {
            Toast.makeText(this, "请先选择歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> playlistNames = loadLocalPlaylistNames();
        if (playlistNames.isEmpty()) {
            Toast.makeText(this, "暂无可添加的本地歌单", Toast.LENGTH_SHORT).show();
            return;
        }
        int defaultIndex = 0;
        for (int i = 0; i < playlistNames.size(); i++) {
            if (TextUtils.equals(currentPlaylist, playlistNames.get(i))) {
                defaultIndex = i;
                break;
            }
        }
        String[] arr = playlistNames.toArray(new String[0]);
        final int[] checked = new int[]{defaultIndex};

        new AlertDialog.Builder(this)
                .setTitle("添加到歌单")
                .setSingleChoiceItems(arr, defaultIndex, (dialog, which) -> checked[0] = which)
                .setPositiveButton("确定", (dialog, which) -> {
                    String target = arr[checked[0]];
                    int added = addSelectedSongsToPlaylist(selected, target);
                    Toast.makeText(this, "已添加 " + added + " 首到: " + target, Toast.LENGTH_SHORT).show();
                    didModifyPlaylist = true;
                    Intent result = new Intent();
                    result.putExtra("should_refresh", true);
                    setResult(RESULT_OK, result);
                    exitSelectionMode();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int addSelectedSongsToPlaylist(List<Songinf> selected, String playlistName) {
        if (selected == null || selected.isEmpty() || TextUtils.isEmpty(playlistName)) {
            return 0;
        }
        if (isBiliBoundPlaylistName(playlistName)) {
            Toast.makeText(this, "B站绑定歌单不允许添加云端音乐", Toast.LENGTH_SHORT).show();
            return 0;
        }
        Set<String> existing = new HashSet<>();
        try {
            List<java.util.Map<String, Object>> current = MusicLoader.loadSongs(this, playlistName);
            for (java.util.Map<String, Object> m : current) {
                Object fp = m.get("filePath");
                if (fp instanceof String) {
                    existing.add((String) fp);
                }
            }
        } catch (Exception ignored) {
        }

        int added = 0;
        for (Songinf s : selected) {
            String url = buildSongUrl(s);
            if (existing.contains(url)) {
                continue;
            }
            Song local = toLocalSongForQueue(s);
            if (local == null) {
                continue;
            }
            local.setPlaylist(playlistName);
            MusicLoader.appendMusic(getApplicationContext(), local);
            existing.add(url);
            added++;
        }
        return added;
    }

    private boolean isBiliBoundPlaylistName(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        SharedPreferences prefs = getSharedPreferences("playlist_prefs", MODE_PRIVATE);
        String json = prefs.getString("playlists", null);
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        try {
            List<Playlist> lists = Playlist.fromJson(json);
            if (lists == null) {
                return false;
            }
            for (Playlist p : lists) {
                if (p == null || p.getName() == null) {
                    continue;
                }
                if (name.equals(p.getName())) {
                    return p.isBiliBound();
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private List<String> loadLocalPlaylistNames() {
        SharedPreferences prefs = getSharedPreferences("playlist_prefs", MODE_PRIVATE);
        String json = prefs.getString("playlists", null);
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<Playlist> lists = Playlist.fromJson(json);
            List<String> out = new ArrayList<>();
            if (lists != null) {
                for (Playlist p : lists) {
                    if (p == null || p.getName() == null || p.getName().trim().isEmpty()) {
                        continue;
                    }
                    if (!p.isBiliBound()) {
                        out.add(p.getName());
                    }
                }
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

}
