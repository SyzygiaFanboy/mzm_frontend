package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.PlaylistItemTouchHelperCallback;
import com.example.myapplication.adapter.PlaylistRecyclerAdapter;
import com.example.myapplication.model.Playlist;
import com.example.myapplication.model.Song;
import com.example.myapplication.utils.BiliFavService;
import com.example.myapplication.utils.BiliPlaylistSyncManager;
import com.example.myapplication.utils.BiliSeasonsSeriesService;
import com.example.myapplication.utils.SongDeletionUtils;
import com.example.myapplication.utils.UiAutoRefreshHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import okhttp3.OkHttpClient;

public class PlaylistListActivity extends AppCompatActivity implements PlaylistRecyclerAdapter.OnStartDragListener {
    private static final int BACKGROUND_PLAYLIST = 0;
    private static final int BACKGROUND_PLAYBACK = 1;
    private List<Playlist> playlists = new ArrayList<>();
    private PlaylistRecyclerAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    public static final String PREFS = "playlist_prefs";
    public static final String KEY_PLAYLISTS = "playlists";
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private static final int REQUEST_CODE_SEARCH = 1001;
    private static final int REQUEST_CODE_NOTIFICATIONS = 2001;

    // 新增的变量
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private int currentBackgroundType = BACKGROUND_PLAYLIST;
    
    private MusicPlayer musicPlayer;
    private final OkHttpClient biliHttpClient = new OkHttpClient();
    private String lastPlaylistStoreSnapshot = null;
    private final UiAutoRefreshHelper playlistUiAutoRefresh = new UiAutoRefreshHelper(1200L, this::refreshPlaylistUiIfNeeded);

    private static final class BiliBindTarget {
        final String type;
        final String id;
        final String title;
        final int mediaCount;

        BiliBindTarget(String type, String id, String title, int mediaCount) {
            this.type = type;
            this.id = id;
            this.title = title;
            this.mediaCount = mediaCount;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_list);
        ensureNotificationPermission();

        // 初始化图片选择器
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            saveBackgroundImage(imageUri, currentBackgroundType);
                        }
                    }
                }
        );

        RecyclerView recyclerView = findViewById(R.id.rvPlaylists);
        ImageButton btnNew = findViewById(R.id.btnNewPlaylist);
        ImageButton btnDelete = findViewById(R.id.btnDeletePlaylist);
        ImageButton btnUpload = findViewById(R.id.btnUpload);
        ImageButton btnExport = findViewById(R.id.btnExport);
        btnUpload.setOnClickListener(v -> {
            Intent intent = new Intent(PlaylistListActivity.this, UploadActivity.class);
            startActivity(intent);
        });
        if (btnExport != null) {
            btnExport.setOnClickListener(v -> {
                Intent intent = new Intent(PlaylistListActivity.this, ExportSongsActivity.class);
                startActivity(intent);
            });
        }
        ImageButton btnMore = findViewById(R.id.btnMore);
        ConstraintLayout manageBar = findViewById(R.id.manageBar);
        CheckBox cbSelectAll = findViewById(R.id.cbSelectAll);

        // 加载已有歌单
        loadPlaylists();

        // 设置RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PlaylistRecyclerAdapter(this, playlists, this);
        recyclerView.setAdapter(adapter);

        // 设置ItemTouchHelper
        PlaylistItemTouchHelperCallback callback = new PlaylistItemTouchHelperCallback(adapter, this::savePlaylist);
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // 设置点击监听
        adapter.setOnItemClickListener(position -> {
            String selected = playlists.get(position).getName();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("playlist", selected);
            // 添加标志以重用现有实例
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                adapter.selectAll();
            } else {
                adapter.clearSelection();
            }
        });

        adapter.setOnSelectionChanged(() -> {
            boolean allSelected = adapter.getSelectedIndices().size() == adapter.getItemCount();
            cbSelectAll.setChecked(allSelected);
        });

        // 进入管理模式
        btnMore.setOnClickListener(v -> {
            if (adapter.isManageMode()) {
                // 当前是管理模式，退出管理模式
                adapter.setManageMode(false);
                adapter.clearSelection();
                manageBar.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);
            } else {
                // 当前不是管理模式，进入管理模式
                adapter.setManageMode(true);
                manageBar.setVisibility(View.VISIBLE);
                btnDelete.setVisibility(View.VISIBLE);
            }
        });

        // 删除所选
        btnDelete.setOnClickListener(v -> {
            List<Integer> selected = adapter.getSelectedIndices();
            if (selected.isEmpty()) {
                Toast.makeText(PlaylistListActivity.this, "请先选择要删除的歌单", Toast.LENGTH_SHORT).show();
                return;
            }

            LinearLayout layout = new LinearLayout(PlaylistListActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 20, 50, 10);

            android.widget.TextView message = new android.widget.TextView(PlaylistListActivity.this);
            message.setText("确定要删除所选歌单吗？");
            message.setTextSize(16);
            layout.addView(message);

            CheckBox cbDeleteFiles = new CheckBox(PlaylistListActivity.this);
            cbDeleteFiles.setText("同时删除歌单内本App下载/导入的音频文件");
            cbDeleteFiles.setChecked(true);
            layout.addView(cbDeleteFiles);

            new AlertDialog.Builder(PlaylistListActivity.this)
                    .setTitle("确认删除")
                    .setView(layout)
                    .setPositiveButton("确定", (dialog, which) -> {
                        boolean shouldDeleteFiles = cbDeleteFiles.isChecked();

                        selected.sort(Collections.reverseOrder());
                        for (int index : selected) {
                            Playlist p = playlists.get(index);
                            List<Map<String, Object>> songs = MusicLoader.loadSongs(PlaylistListActivity.this, p.getName());
                            java.util.HashMap<String, Integer> deleteCounts = new java.util.HashMap<>();
                            for (Map<String, Object> songMap : songs) {
                                String fp = (String) songMap.get("filePath");
                                if (fp != null) {
                                    deleteCounts.put(fp, deleteCounts.getOrDefault(fp, 0) + 1);
                                }
                            }
                            java.util.HashSet<String> processedPaths = new java.util.HashSet<>();
                            for (Map<String, Object> songMap : songs) {
                                Song s = Song.fromMap(songMap);
                                SongDeletionUtils.deleteCoverCaches(PlaylistListActivity.this, s.getName(), s.getCoverUrl());
                                if (shouldDeleteFiles) {
                                    String fp = s.getFilePath();
                                    if (!processedPaths.contains(fp)) {
                                        processedPaths.add(fp);
                                        int refs = MusicLoader.countFilePathReferences(PlaylistListActivity.this, fp);
                                        int delCount = deleteCounts.getOrDefault(fp, 1);
                                        if (refs <= delCount) {
                                            SongDeletionUtils.deleteAppOwnedAudioFile(PlaylistListActivity.this, fp);
                                        }
                                    }
                                }
                            }

                            try {
                                MusicLoader.removePlaylistEntries(PlaylistListActivity.this, p.getName());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            adapter.removeAt(index);
                        }

                        savePlaylist();
                        updateAllSongCounts();
                        dialog.dismiss();
                    })
                    .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        btnNew.setOnClickListener(v -> showCreateDialog());

        drawerLayout = findViewById(R.id.drawer_layout);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        Drawable navIcon = toolbar.getNavigationIcon();
        if (navIcon != null) {
            navIcon = DrawableCompat.wrap(navIcon.mutate());
            DrawableCompat.setTint(navIcon, Color.parseColor("#c69bc5"));
            toolbar.setNavigationIcon(navIcon);
        }

        /*
        findViewById(R.id.menu_search).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivityForResult(intent, REQUEST_CODE_SEARCH);
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        */
        findViewById(R.id.menu_logout).setOnClickListener(v -> {
            SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
            preferences.edit()
                    .putBoolean("is_logged_in", false)
                    .putBoolean("is_admin", false)
                    .remove("username")
                    .apply();

            MusicPlayer player = ((MyApp) getApplication()).getMusicPlayer();
            if (player.isPlaying() || player.isPaused()) {
                player.stop();
            }

            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        View adminUsersBtn = findViewById(R.id.menu_admin_users);
        if (adminUsersBtn != null) {
            SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
            boolean isAdmin = preferences.getBoolean("is_admin", false);
            adminUsersBtn.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
            adminUsersBtn.setOnClickListener(v -> {
                startActivity(new Intent(this, AdminUsersActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        // 添加设置按钮点击事件
        findViewById(R.id.menu_item1).setOnClickListener(v -> {
            showSettingsDialog();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        findViewById(R.id.menu_item2).setOnClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        View btnServer = findViewById(R.id.btnServer);
        if (btnServer != null) {
            btnServer.setOnClickListener(v -> {
                startActivity(new Intent(this, OnlineMusicActivity.class));
            });
        }

        // 应用保存的背景图片
        applyBackgroundImage();
        musicPlayer = ((MyApp) getApplication()).getMusicPlayer();
        // 初始化底部播放栏
        initBottomPlayerBar();
    }
    private void initBottomPlayerBar() {
        // 使用全局管理器
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.attachToActivity(this);
        
        // 添加播放状态监听器
        musicPlayer.setOnPlaybackStateChangeListener(new MusicPlayer.OnPlaybackStateChangeListener() {
            @Override
            public void onPlaybackStateChanged() {
                // 播放状态改变时更新高亮
                runOnUiThread(() -> updateCurrentPlayingPlaylistHighlight());
            }

            @Override
            public void onSongChanged() {
                // 歌曲改变时更新高亮
                runOnUiThread(() -> updateCurrentPlayingPlaylistHighlight());
            }
        });
        
        globalManager.setOnBottomPlayerClickListener(this, () -> {
            // 检查是否有当前歌曲
            Song currentSong = musicPlayer.getCurrentSong();
            if (currentSong == null) {
                // 没有歌曲时，提示用户或跳转到有歌曲的歌单
                Toast.makeText(PlaylistListActivity.this, "请先选择一首歌曲播放", Toast.LENGTH_SHORT).show();
                return;
            }

            // 有歌曲时正常处理播放/暂停
            if (musicPlayer.isPlaying()) {
                musicPlayer.pause();
            } else {
                musicPlayer.play();
            }
        });
    }
    private void showSettingsDialog() {
        String[] options = {"绑定B站UID/收藏夹/合集系列", "设置歌单页面背景", "设置播放页面背景", "恢复默认背景"};

        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showBindBiliUidDialog();
                        return;
                    }
                    if (which == 3) {
                        // 恢复默认背景
                        showRestoreDefaultDialog();
                    } else {
                        currentBackgroundType = which == 1 ? BACKGROUND_PLAYLIST : BACKGROUND_PLAYBACK;
                        openImagePicker();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBindBiliUidDialog() {
        EditText etUid = new EditText(this);
        etUid.setHint("输入B站UID");

        String prefill = null;
        for (Playlist p : playlists) {
            if (p != null && p.isBiliBound() && p.getBiliUid() != null && !p.getBiliUid().isEmpty()) {
                prefill = p.getBiliUid();
                break;
            }
        }
        if (prefill != null) {
            etUid.setText(prefill);
        }

        new AlertDialog.Builder(this)
                .setTitle("绑定B站UID")
                .setView(etUid)
                .setPositiveButton("下一步", (d, w) -> {
                    String uid = etUid.getText() != null ? etUid.getText().toString().trim() : "";
                    uid = uid.replace("UID:", "").trim();
                    if (!uid.matches("\\d+")) {
                        Toast.makeText(this, "请输入正确的UID", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startBindFlow(uid);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startBindFlow(String uid) {
        Toast.makeText(this, "正在读取公开收藏夹与合集/系列…", Toast.LENGTH_SHORT).show();

        final BiliFavService.UserInfo[] userInfoRef = {new BiliFavService.UserInfo(uid, uid, null)};
        final List<BiliFavService.FavFolder>[] folderRef = new List[]{new ArrayList<>()};
        final List<BiliSeasonsSeriesService.CollectionItem>[] collectionRef = new List[]{new ArrayList<>()};
        final int[] remaining = {3};

        Runnable tryContinue = () -> {
            remaining[0]--;
            if (remaining[0] > 0) {
                return;
            }
            List<BiliFavService.FavFolder> folders = folderRef[0] != null ? folderRef[0] : new ArrayList<>();
            List<BiliSeasonsSeriesService.CollectionItem> collections = collectionRef[0] != null ? collectionRef[0] : new ArrayList<>();
            if (folders.isEmpty() && collections.isEmpty()) {
                Toast.makeText(this, "未获取到公开收藏夹或合集/系列", Toast.LENGTH_SHORT).show();
                return;
            }
            showBiliBindingPickDialog(userInfoRef[0], folders, collections);
        };

        BiliFavService.fetchUserInfo(biliHttpClient, uid, userInfo -> {
            if (userInfo != null) {
                userInfoRef[0] = userInfo;
            }
            tryContinue.run();
        });

        BiliFavService.fetchFolders(biliHttpClient, uid, folders -> {
            folderRef[0] = folders != null ? folders : new ArrayList<>();
            tryContinue.run();
        });

        BiliSeasonsSeriesService.fetchCollections(biliHttpClient, uid, collections -> {
            collectionRef[0] = collections != null ? collections : new ArrayList<>();
            tryContinue.run();
        });
    }

    private void showBiliBindingPickDialog(BiliFavService.UserInfo userInfo,
                                           List<BiliFavService.FavFolder> folders,
                                           List<BiliSeasonsSeriesService.CollectionItem> collections) {
        List<BiliBindTarget> targets = new ArrayList<>();
        if (folders != null) {
            for (BiliFavService.FavFolder folder : folders) {
                targets.add(new BiliBindTarget(Playlist.BILI_BIND_TYPE_FAV, folder.mediaId, folder.title, folder.mediaCount));
            }
        }
        if (collections != null) {
            for (BiliSeasonsSeriesService.CollectionItem item : collections) {
                targets.add(new BiliBindTarget(item.type, item.id, item.title, item.mediaCount));
            }
        }
        int n = targets.size();
        String[] items = new String[n];
        boolean[] checked = new boolean[n];
        for (int i = 0; i < n; i++) {
            BiliBindTarget target = targets.get(i);
            items[i] = getBiliBindTypeLabel(target.type) + target.title + " (" + target.mediaCount + ")";
            checked[i] = false;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择要追加绑定的收藏夹/合集/系列")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("绑定并同步", (dialog, which) -> {
                    List<BiliBindTarget> selected = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        if (checked[i]) {
                            selected.add(targets.get(i));
                        }
                    }
                    if (selected.isEmpty()) {
                        Toast.makeText(this, "请至少选择一个收藏夹、合集或系列", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    applyBiliBinding(userInfo, selected);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyBiliBinding(BiliFavService.UserInfo userInfo, List<BiliBindTarget> selectedTargets) {
        List<String> playlistNamesToSync = new ArrayList<>();
        final int[] createdCount = {0};
        final int[] reusedCount = {0};

        for (BiliBindTarget target : selectedTargets) {
            Playlist existing = findExistingBiliBoundPlaylist(userInfo.uid, target);
            if (existing != null) {
                fillBiliPlaylistMeta(existing, userInfo, target);
                playlistNamesToSync.add(existing.getName());
                reusedCount[0]++;
                continue;
            }

            String plName = ensureUniquePlaylistName(buildBiliPlaylistName(userInfo, target));
            Playlist p = new Playlist(plName, 0, R.drawable.default_playlist_cover);
            fillBiliPlaylistMeta(p, userInfo, target);
            playlists.add(p);
            playlistNamesToSync.add(plName);
            createdCount[0]++;
        }

        savePlaylist();
        adapter.notifyDataSetChanged();

        AlertDialog progressDialog = createSyncProgressDialog();
        progressDialog.show();

        final int total = selectedTargets.size();
        final int[] done = {0};
        final int[] failedCount = {0};
        for (int i = 0; i < selectedTargets.size(); i++) {
            BiliBindTarget target = selectedTargets.get(i);
            String plName = playlistNamesToSync.get(i);
            updateSyncProgressDialog(progressDialog, done[0], total, "正在同步: " + plName);
            syncBiliBindTarget(userInfo.uid, target, plName, songCount -> runOnUiThread(() -> {
                done[0]++;
                if (songCount < 0) {
                    failedCount[0]++;
                    updateSyncProgressDialog(progressDialog, done[0], total, "同步失败，已保留原内容: " + plName);
                } else {
                    updateSyncProgressDialog(progressDialog, done[0], total, "已同步: " + plName + " (" + songCount + "首)");
                }
                if (done[0] >= total) {
                    progressDialog.dismiss();
                    loadPlaylists();
                    updateAllSongCounts();
                    String message = failedCount[0] > 0
                            ? "部分绑定同步失败，已保留原有内容"
                            : createdCount[0] > 0 && reusedCount[0] > 0
                            ? "已追加绑定并同步，已有绑定也已刷新"
                            : createdCount[0] > 0
                            ? "已追加绑定并同步"
                            : "所选内容已重新同步";
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            }));
        }
    }

    private void syncBiliBindTarget(String uid, BiliBindTarget target, String playlistName, BiliPlaylistSyncManager.SyncCallback callback) {
        if (Playlist.BILI_BIND_TYPE_SEASON.equals(target.type)) {
            BiliPlaylistSyncManager.syncBoundSeasonToPlaylist(this, biliHttpClient, uid, target.id, playlistName, callback);
            return;
        }
        if (Playlist.BILI_BIND_TYPE_SERIES.equals(target.type)) {
            BiliPlaylistSyncManager.syncBoundSeriesToPlaylist(this, biliHttpClient, uid, target.id, playlistName, callback);
            return;
        }
        BiliPlaylistSyncManager.syncBoundFolderToPlaylist(this, biliHttpClient, uid, target.id, playlistName, callback);
    }

    private void fillBiliPlaylistMeta(Playlist playlist, BiliFavService.UserInfo userInfo, BiliBindTarget target) {
        if (playlist == null || userInfo == null || target == null) {
            return;
        }
        String uid = userInfo.uid != null && !userInfo.uid.trim().isEmpty()
                ? userInfo.uid.trim()
                : playlist.getBiliUid();
        String displayName = chooseBetterDisplayName(playlist.getBiliUserName(), userInfo.name, uid);
        String avatarUrl = chooseBetterAvatarUrl(playlist.getBiliAvatarUrl(), userInfo.avatarUrl);
        playlist.setBiliBound(true);
        playlist.setBiliUid(uid);
        playlist.setBiliUserName(displayName);
        playlist.setBiliAvatarUrl(avatarUrl);
        playlist.setBiliBindingType(target.type);
        playlist.setBiliFolderId(null);
        playlist.setBiliFolderTitle(null);
        playlist.setBiliSeasonId(null);
        playlist.setBiliSeasonTitle(null);
        playlist.setBiliSeriesId(null);
        playlist.setBiliSeriesTitle(null);
        if (Playlist.BILI_BIND_TYPE_FAV.equals(target.type)) {
            playlist.setBiliFolderId(target.id);
            playlist.setBiliFolderTitle(target.title);
        } else if (Playlist.BILI_BIND_TYPE_SEASON.equals(target.type)) {
            playlist.setBiliSeasonId(target.id);
            playlist.setBiliSeasonTitle(target.title);
        } else if (Playlist.BILI_BIND_TYPE_SERIES.equals(target.type)) {
            playlist.setBiliSeriesId(target.id);
            playlist.setBiliSeriesTitle(target.title);
        }
    }

    private Playlist findExistingBiliBoundPlaylist(String uid, BiliBindTarget target) {
        if (uid == null || uid.isEmpty() || target == null) {
            return null;
        }
        for (Playlist playlist : playlists) {
            if (playlist == null || !playlist.isBiliBound()) {
                continue;
            }
            if (!uid.equals(playlist.getBiliUid())) {
                continue;
            }
            String bindingType = playlist.getEffectiveBiliBindingType();
            if (!target.type.equals(bindingType)) {
                continue;
            }
            String boundId = null;
            if (Playlist.BILI_BIND_TYPE_FAV.equals(target.type)) {
                boundId = playlist.getBiliFolderId();
            } else if (Playlist.BILI_BIND_TYPE_SEASON.equals(target.type)) {
                boundId = playlist.getBiliSeasonId();
            } else if (Playlist.BILI_BIND_TYPE_SERIES.equals(target.type)) {
                boundId = playlist.getBiliSeriesId();
            }
            if (target.id.equals(boundId)) {
                return playlist;
            }
        }
        return null;
    }

    private String ensureUniquePlaylistName(String baseName) {
        if (!playlistNameExists(baseName)) {
            return baseName;
        }
        int suffix = 2;
        while (playlistNameExists(baseName + "-" + suffix)) {
            suffix++;
        }
        return baseName + "-" + suffix;
    }

    private boolean playlistNameExists(String playlistName) {
        if (playlistName == null || playlistName.isEmpty()) {
            return false;
        }
        for (Playlist playlist : playlists) {
            if (playlist != null && playlistName.equals(playlist.getName())) {
                return true;
            }
        }
        return false;
    }

    private String buildBiliPlaylistName(BiliFavService.UserInfo userInfo, BiliBindTarget target) {
        if (target == null) {
            return "B站歌单";
        }
        String userLabel = userInfo != null && userInfo.name != null && !userInfo.name.trim().isEmpty()
                ? userInfo.name.trim()
                : userInfo != null && userInfo.uid != null && !userInfo.uid.trim().isEmpty()
                ? "UID" + userInfo.uid.trim()
                : "用户";
        if (Playlist.BILI_BIND_TYPE_SEASON.equals(target.type)) {
            return "B站合集-" + userLabel + "-" + target.title;
        }
        if (Playlist.BILI_BIND_TYPE_SERIES.equals(target.type)) {
            return "B站系列-" + userLabel + "-" + target.title;
        }
        return "B站收藏夹-" + userLabel + "-" + target.title;
    }

    private String getBiliBindTypeLabel(String type) {
        if (Playlist.BILI_BIND_TYPE_SEASON.equals(type)) {
            return "[合集] ";
        }
        if (Playlist.BILI_BIND_TYPE_SERIES.equals(type)) {
            return "[系列] ";
        }
        return "[收藏夹] ";
    }

    private String chooseBetterDisplayName(String preferred, String fallback, String uid) {
        if (isMeaningfulDisplayName(preferred, uid)) {
            return preferred.trim();
        }
        if (isMeaningfulDisplayName(fallback, uid)) {
            return fallback.trim();
        }
        return uid != null && !uid.trim().isEmpty() ? uid.trim() : "B站用户";
    }

    private boolean isMeaningfulDisplayName(String value, String uid) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        return uid == null || !value.trim().equals(uid.trim());
    }

    private String chooseBetterAvatarUrl(String preferred, String fallback) {
        if (isMeaningfulAvatarUrl(preferred)) {
            return preferred.trim();
        }
        if (isMeaningfulAvatarUrl(fallback)) {
            return fallback.trim();
        }
        return null;
    }

    private boolean isMeaningfulAvatarUrl(String value) {
        return value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim());
    }

    private AlertDialog createSyncProgressDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 10);

        android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setId(android.R.id.progress);
        bar.setMax(100);
        bar.setProgress(0);
        layout.addView(bar);

        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setId(android.R.id.text1);
        tv.setPadding(0, 20, 0, 0);
        layout.addView(tv);

        return new AlertDialog.Builder(this)
                .setTitle("同步中")
                .setView(layout)
                .setCancelable(false)
                .create();
    }

    private void updateSyncProgressDialog(AlertDialog dialog, int done, int total, String message) {
        if (dialog == null) {
            return;
        }
        android.widget.ProgressBar bar = dialog.findViewById(android.R.id.progress);
        android.widget.TextView tv = dialog.findViewById(android.R.id.text1);
        int progress = total <= 0 ? 0 : (done * 100 / total);
        if (bar != null) {
            bar.setProgress(progress);
        }
        if (tv != null) {
            tv.setText(message);
        }
    }

    private void showRestoreDefaultDialog() {
        String[] options = {"恢复歌单页面默认背景", "恢复播放页面默认背景", "恢复所有默认背景"};

        new AlertDialog.Builder(this)
                .setTitle("恢复默认背景")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            restoreDefaultBackground(0); // 歌单页面
                            break;
                        case 1:
                            restoreDefaultBackground(1); // 播放页面
                            break;
                        case 2:
                            restoreDefaultBackground(-1); // 所有页面
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void restoreDefaultBackground(int backgroundType) {
        SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (backgroundType == 0 || backgroundType == -1) {
            // 清除歌单页面背景设置
            String playlistBgPath = prefs.getString("playlist_background_path", null);
            if (playlistBgPath != null) {
                // 删除保存的背景图片文件
                File bgFile = new File(playlistBgPath);
                if (bgFile.exists()) {
                    bgFile.delete();
                }
            }
            editor.remove("playlist_background_path");

            // 清除DrawerLayout的背景
            androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
            if (drawerLayout != null) {
                drawerLayout.setBackgroundResource(0);
            }
        }

        if (backgroundType == 1 || backgroundType == -1) {
            // 清除播放页面背景设置
            String playbackBgPath = prefs.getString("playback_background_path", null);
            if (playbackBgPath != null) {
                // 删除保存的背景图片文件
                File bgFile = new File(playbackBgPath);
                if (bgFile.exists()) {
                    bgFile.delete();
                }
            }
            editor.remove("playback_background_path");
        }

        editor.apply();

        // 立即应用默认背景
        applyBackgroundImage();

        String message;
        if (backgroundType == -1) {
            message = "已恢复所有默认背景";
        } else if (backgroundType == 0) {
            message = "已恢复歌单页面默认背景";
        } else {
            message = "已恢复播放页面默认背景";
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void saveBackgroundImage(Uri imageUri, int backgroundType) {
        try {
            // 获取图片并显示预览对话框
            showBackgroundPreviewDialog(imageUri, backgroundType);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBackgroundPreviewDialog(Uri imageUri, int backgroundType) {
        // 创建对话框布局
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_bg_preview, null);
        SeekBar transparencySeekBar = dialogView.findViewById(R.id.transparencySeekBar);
        SeekBar blurSeekBar = dialogView.findViewById(R.id.blurSeekBar);

        // 记住原始背景
        Drawable originalBackground = drawerLayout.getBackground();

        // 异步加载和处理图片
        new Thread(() -> {
            try {
                // 获取屏幕尺寸
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;

                // 使用采样率减小图片大小
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();

                int sampleSize = calculateInSampleSize(options, screenWidth, screenHeight);
                options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;

                // 重新加载图片
                inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();

                // 预先缩放到屏幕尺寸
                final Bitmap preScaledBitmap = createScaledBitmap(originalBitmap, screenWidth, screenHeight);
                if (originalBitmap != preScaledBitmap) {
                    originalBitmap.recycle();
                }

                // 在UI线程中设置对话框
                runOnUiThread(() -> {
                    // 初始透明度和模糊值
                    final int[] transparency = {130};
                    final int[] blurRadius = {0};

                    // 设置滑块初始值
                    transparencySeekBar.setMax(255);
                    transparencySeekBar.setProgress(transparency[0]);
                    blurSeekBar.setMax(25);
                    blurSeekBar.setProgress(blurRadius[0]);

                    // 更新预览的帮助方法
                    Runnable updatePreview = () -> {
                        // 应用模糊效果
                        Bitmap processedBitmap;
                        if (blurRadius[0] > 0) {
                            processedBitmap = blurBitmap(preScaledBitmap, blurRadius[0]);
                        } else {
                            processedBitmap = preScaledBitmap;
                        }

                        // 创建背景drawable并设置透明度
                        BitmapDrawable backgroundDrawable = new BitmapDrawable(getResources(), processedBitmap);
                        backgroundDrawable.setAlpha(transparency[0]);

                        // 更新实时预览
                        drawerLayout.setBackground(backgroundDrawable.getConstantState().newDrawable());
                    };
                    updatePreview.run(); // 马上更新一次

                    // 设置滑块监听器
                    transparencySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            transparency[0] = progress;
                            updatePreview.run();
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });

                    blurSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            blurRadius[0] = progress;
                            updatePreview.run();
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });

                    // 创建对话框
                    AlertDialog dialog = new AlertDialog.Builder(PlaylistListActivity.this)
                            .setTitle("预览中")
                            .setView(dialogView)
                            .setPositiveButton("确认", (dialogInterface, i) -> {
                                saveProcessedBackground(imageUri, backgroundType, transparency[0], blurRadius[0]);
                                if (backgroundType == BACKGROUND_PLAYBACK) {
                                    // 如果修改播放页面背景，还原回去
                                    drawerLayout.setBackground(originalBackground);
                                }
                            })
                            .setNegativeButton("取消", (dialogInterface, i) -> {
                                drawerLayout.setBackground(originalBackground);
                            })
                            .create();

                    // 设置对话框属性
                    Window window = dialog.getWindow();
                    if (window != null) {
                        window.setGravity(android.view.Gravity.BOTTOM);
                        window.setDimAmount(0f); // 背景不变暗
                    }
                    // 设置点击外部不关闭
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 计算合适的采样率以减小图片大小，免得拖滑条会卡
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private void saveProcessedBackground(Uri imageUri, int backgroundType, int transparency, int blurRadius) {
        try {
            // 创建保存目录
            File backgroundDir = new File(getExternalFilesDir("backgrounds"), "");
            if (!backgroundDir.exists()) {
                backgroundDir.mkdirs();
            }

            // 确定文件名
            String fileName = (backgroundType == BACKGROUND_PLAYLIST) ? "playlist_background.jpg" : "playback_background.jpg";
            File backgroundFile = new File(backgroundDir, fileName);

            // 直接获取当前显示的背景图
            Drawable currentBackground = drawerLayout.getBackground();
            if (!(currentBackground instanceof BitmapDrawable)) {
                Toast.makeText(this, "背景设置失败：无法保存当前背景", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap currentBitmap = ((BitmapDrawable) currentBackground).getBitmap();

            // 保存当前显示的图片 (已经应用了透明度和模糊)
            FileOutputStream outputStream = new FileOutputStream(backgroundFile);
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.close();

            // 保存设置到SharedPreferences
            SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
            String key = (backgroundType == BACKGROUND_PLAYLIST) ? "playlist_background_path" : "playback_background_path";
            prefs.edit()
                    .putString(key, backgroundFile.getAbsolutePath())
                    .putInt(key + "_transparency", transparency)
                    .putInt(key + "_blur", blurRadius)
                    .apply();

            Toast.makeText(this, "背景设置成功", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "背景设置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 添加模糊效果的方法
    private Bitmap blurBitmap(Bitmap bitmap, int radius) {
        if (radius == 0) return bitmap;

        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 对于Android 12及以上，使用RenderEffect
            try {
                // 使用ScriptIntrinsicBlur通过Canvas应用模糊
                RenderScript rs = RenderScript.create(this);
                Allocation input = Allocation.createFromBitmap(rs, bitmap);
                Allocation outputAlloc = Allocation.createFromBitmap(rs, output);

                // 将模糊半径限制在0.0f到25.0f之间
                float blurRadius = Math.min(Math.max(radius, 0), 25);

                ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
                blurScript.setInput(input);
                blurScript.setRadius(blurRadius);
                blurScript.forEach(outputAlloc);

                outputAlloc.copyTo(output);

                // 清理资源
                input.destroy();
                outputAlloc.destroy();
                blurScript.destroy();
                rs.destroy();
            } catch (Exception e) {
                e.printStackTrace();
                return bitmap; // 如果失败就返回原图
            }
        } else {
            // 较老版本使用RenderScript
            RenderScript rs = RenderScript.create(this);
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation input = Allocation.createFromBitmap(rs, bitmap);
            Allocation outputAlloc = Allocation.createFromBitmap(rs, output);
            script.setRadius(radius);
            script.setInput(input);
            script.forEach(outputAlloc);
            outputAlloc.copyTo(output);
            rs.destroy();
        }

        return output;
    }

    // 修改applyBackgroundImage方法，应用保存的模糊效果
    private void applyBackgroundImage() {
        SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
        String playlistBgPath = prefs.getString("playlist_background_path", null);
        int transparency = prefs.getInt("playlist_background_path_transparency", 130);
        int blurRadius = prefs.getInt("playlist_background_path_blur", 0);

        // 使用DrawerLayout作为背景容器，而不是有边距的LinearLayout
        androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        if (drawerLayout != null) {
            if (playlistBgPath != null && new File(playlistBgPath).exists()) {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;

                new Thread(() -> {
                    Bitmap outputBitmap = null;
                    try {
                        Bitmap originalBitmap = decodeSampledBitmapFromFile(playlistBgPath, screenWidth, screenHeight);
                        if (originalBitmap != null) {
                            Bitmap scaledBitmap = createScaledBitmap(originalBitmap, screenWidth, screenHeight);
                            Bitmap processedBitmap = scaledBitmap;
                            if (blurRadius > 0) {
                                processedBitmap = blurBitmap(scaledBitmap, blurRadius);
                                if (processedBitmap != scaledBitmap) {
                                    scaledBitmap.recycle();
                                }
                            }
                            if (originalBitmap != processedBitmap) {
                                originalBitmap.recycle();
                            }
                            outputBitmap = processedBitmap;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    Bitmap finalBitmap = outputBitmap;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            if (finalBitmap != null) {
                                finalBitmap.recycle();
                            }
                            return;
                        }
                        if (finalBitmap != null) {
                            BitmapDrawable backgroundDrawable = new BitmapDrawable(getResources(), finalBitmap);
                            backgroundDrawable.setAlpha(transparency);
                            drawerLayout.setBackground(backgroundDrawable);
                        } else {
                            drawerLayout.setBackgroundResource(0);
                        }
                    });
                }).start();
            } else {
                // 恢复默认背景
                drawerLayout.setBackgroundResource(0);
            }
        }
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

    // 保持原有的图片缩放方法不变
    private Bitmap createScaledBitmap(Bitmap originalBitmap, int targetWidth, int targetHeight) {
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();

        // 计算缩放比例，使用CENTER_CROP效果（保持宽高比，填满目标区域）
        float scaleX = (float) targetWidth / originalWidth;
        float scaleY = (float) targetHeight / originalHeight;
        float scale = Math.max(scaleX, scaleY); // 选择较大的缩放比例

        // 计算缩放后的尺寸
        int scaledWidth = Math.round(originalWidth * scale);
        int scaledHeight = Math.round(originalHeight * scale);

        // 缩放图片
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true);

        // 如果缩放后的图片大于目标尺寸，进行裁剪（居中裁剪）
        if (scaledWidth > targetWidth || scaledHeight > targetHeight) {
            int x = Math.max(0, (scaledWidth - targetWidth) / 2);
            int y = Math.max(0, (scaledHeight - targetHeight) / 2);

            Bitmap croppedBitmap = Bitmap.createBitmap(scaledBitmap, x, y,
                    Math.min(targetWidth, scaledWidth),
                    Math.min(targetHeight, scaledHeight));

            // 回收缩放后的位图
            if (scaledBitmap != croppedBitmap) {
                scaledBitmap.recycle();
            }

            return croppedBitmap;
        }

        return scaledBitmap;
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_NOTIFICATIONS);
    }

    @Override
    public void onStartDrag(PlaylistRecyclerAdapter.ViewHolder holder) {
        itemTouchHelper.startDrag(holder);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            updateAllSongCounts(); // 歌单内容变更后刷新
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlaylists();
        updateAllSongCounts();
        for (Playlist playlist : playlists) {
            String coverPath = MusicLoader.getFirstCoverForPlaylist(this, playlist.getName());
            playlist.setLatestCoverPath(coverPath);
        }
        applyBackgroundImage();
        if (adapter != null) {
            // 添加检测当前播放歌单的逻辑
            updateCurrentPlayingPlaylistHighlight();
            adapter.notifyDataSetChanged();
        }
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.attachToActivity(this);
        globalManager.forceRefresh();
        playlistUiAutoRefresh.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        playlistUiAutoRefresh.stop();
    }

    // 添加更新当前播放歌单高亮的方法
    private void updateCurrentPlayingPlaylistHighlight() {
        Song currentSong = musicPlayer.getCurrentSong();
        if (currentSong != null && adapter != null) {
            String currentPlaylist = currentSong.getPlaylist();
            adapter.setCurrentPlayingPlaylist(currentPlaylist);
        } else if (adapter != null) {
            // 如果没有正在播放的歌曲，清除高亮
            adapter.setCurrentPlayingPlaylist(null);
        }
    }

    private void confirmAndDeletePlaylist(String name, int pos) {
        new AlertDialog.Builder(this)
                .setTitle("删除歌单")
                .setMessage("确定要删除歌单 “" + name + "” 及其所有歌曲吗？")
                .setPositiveButton("删除", (d, w) -> {
                    // 从 prefs 删除歌单名
                    SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
                    Set<String> set = new HashSet<>(prefs.getStringSet(KEY_PLAYLISTS, Collections.emptySet()));
                    set.remove(name);
                    prefs.edit().putStringSet(KEY_PLAYLISTS, set).apply();

                    // 从本地 txt 中删除该歌单歌曲
                    try {
                        MusicLoader.removePlaylistEntries(this, name);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // 更新 UI 列表
                    playlists.remove(pos);
                    adapter.notifyDataSetChanged();

                    // 停止并初始化播放器状态
                    MusicPlayer player = ((MyApp) getApplication()).getMusicPlayer();
                    // 如果正在播放，立即停止
                    if (player.isPlaying() || player.isPaused()) {
                        player.stop();              // 停止并 release mediaPlayer
                        player.resetProgress();     // 重置进度显示为 0
                    }
//                    // 可以额外重置底部“正在播放”UI，例如：
//                    TextView tvNow = findViewById(R.id.tvNowTitle);
//                    Button btnPP = findViewById(R.id.btnNowPlayPause);
//                    tvNow.setText("暂无播放");
//                    btnPP.setText("播放");
//                    btnPP.setEnabled(false);

                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private void showCreateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        EditText input = new EditText(this);
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.VERTICAL);
        inputLayout.setPadding(50, 20, 50, 10);
        inputLayout.addView(input);

        builder.setTitle("新建歌单")
                .setView(inputLayout)
                .setPositiveButton("确定", null) // 暂时设为null，在下面手动处理点击事件
                .setNegativeButton("取消", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // 实现判空和判重
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(PlaylistListActivity.this, "歌单名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 判重
            boolean isDuplicate = false;
            for (Playlist playlist : playlists) {
                if (playlist.getName().equals(name)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                Toast.makeText(PlaylistListActivity.this, "歌单名称已存在", Toast.LENGTH_SHORT).show();
            } else {
                Playlist newPlaylist = new Playlist(name, 0, R.drawable.default_playlist_cover);
                adapter.addPlaylist(newPlaylist);
                savePlaylist(); // 保存到文件或数据库
                updateAllSongCounts();
                dialog.dismiss(); // 关闭对话框
            }
        });
    }

    private void savePlaylist() {
        for (Playlist playlist : playlists) {
            String coverPath = MusicLoader.getFirstCoverForPlaylist(this, playlist.getName());
            playlist.setLatestCoverPath(coverPath);
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String playlistsJson = Playlist.toJson(playlists);
        lastPlaylistStoreSnapshot = playlistsJson;
        prefs.edit().putString(KEY_PLAYLISTS, playlistsJson).apply();
        Log.d("PlaylistSave", "Saved playlists: " + playlistsJson);
    }


    private void loadPlaylists() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String playlistsJson = prefs.getString(KEY_PLAYLISTS, null);
        lastPlaylistStoreSnapshot = playlistsJson;
        playlists.clear();
        if (playlistsJson != null && !playlistsJson.trim().isEmpty()) {
            playlists.addAll(Playlist.fromJson(playlistsJson));
        }

        boolean removedLegacyFav = false;
        for (int i = playlists.size() - 1; i >= 0; i--) {
            Playlist p = playlists.get(i);
            if (p != null && "我的收藏".equals(p.getName())) {
                playlists.remove(i);
                removedLegacyFav = true;
            }
        }
        boolean removedDefault = false;
        for (int i = playlists.size() - 1; i >= 0; i--) {
            Playlist p = playlists.get(i);
            if (p != null && "默认歌单".equals(p.getName())) {
                playlists.remove(i);
                removedDefault = true;
            }
        }
        if (removedLegacyFav) {
            savePlaylist();
        }
        if (removedDefault) {
            savePlaylist();
        }
        for (Playlist playlist : playlists) {
            String coverPath = MusicLoader.getFirstCoverForPlaylist(this, playlist.getName());
            playlist.setLatestCoverPath(coverPath);
        }
    }

    private void refreshPlaylistUiIfNeeded() {
        if (adapter == null) {
            return;
        }

        boolean changed = false;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String latestSnapshot = prefs.getString(KEY_PLAYLISTS, null);
        if (!Objects.equals(lastPlaylistStoreSnapshot, latestSnapshot)) {
            loadPlaylists();
            changed = true;
        }

        for (Playlist playlist : playlists) {
            if (playlist == null) {
                continue;
            }
            String latestCover = MusicLoader.getFirstCoverForPlaylist(this, playlist.getName());
            if (!Objects.equals(playlist.getLatestCoverPath(), latestCover)) {
                playlist.setLatestCoverPath(latestCover);
                changed = true;
            }
            try {
                int latestCount = MusicLoader.getSongCountFromPlaylist(this, playlist.getName());
                if (playlist.getSongCount() != latestCount) {
                    playlist.setSongCount(latestCount);
                    changed = true;
                }
            } catch (IOException e) {
                if (playlist.getSongCount() != 0) {
                    playlist.setSongCount(0);
                    changed = true;
                }
            }
        }

        Song currentSong = musicPlayer != null ? musicPlayer.getCurrentSong() : null;
        String currentPlaylist = currentSong != null ? currentSong.getPlaylist() : null;
        if (!Objects.equals(adapter.getCurrentPlayingPlaylist(), currentPlaylist)) {
            adapter.setCurrentPlayingPlaylist(currentPlaylist);
        } else if (changed) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateAllSongCounts() {
        for (Playlist p : playlists) {
            try {
                int count = MusicLoader.getSongCountFromPlaylist(this, p.getName());
                p.setSongCount(count);
            } catch (IOException e) {
                p.setSongCount(0); // 出错时置为 0
            }
        }
        adapter.notifyDataSetChanged(); // 更新 UI
    }

    @Override
    protected void onDestroy() {
        playlistUiAutoRefresh.stop();
        super.onDestroy();
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.detachFromActivity(this);
    }
}
