package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;


import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlaylistListActivity extends AppCompatActivity implements PlaylistRecyclerAdapter.OnStartDragListener {
    private List<Playlist> playlists = new ArrayList<>();
    private PlaylistRecyclerAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    public static final String PREFS = "playlist_prefs";
    public static final String KEY_PLAYLISTS = "playlists";
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle toggle;
    private static final int REQUEST_CODE_SEARCH = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_list);

        RecyclerView recyclerView = findViewById(R.id.rvPlaylists);
        ImageButton btnNew = findViewById(R.id.btnNewPlaylist);
        ImageButton btnDelete = findViewById(R.id.btnDeletePlaylist);
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
            Collections.sort(selected, Collections.reverseOrder()); // 先删除后面的，防止下标错乱

            for (int index : selected) {
                Playlist p = playlists.get(index);
                try {
                    MusicLoader.removePlaylistEntries(this, p.getName());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                adapter.removeAt(index);
            }

            savePlaylist();
            updateAllSongCounts();
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

        findViewById(R.id.menu_search).setOnClickListener(v -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivityForResult(intent, REQUEST_CODE_SEARCH);
            drawerLayout.closeDrawer(GravityCompat.START);
        });
        findViewById(R.id.menu_logout).setOnClickListener(v -> {
            SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
            preferences.edit().putBoolean("is_logged_in", false).apply();

            MusicPlayer player = ((MyApp) getApplication()).getMusicPlayer();
            if (player.isPlaying() || player.isPaused()) {
                player.stop();
            }

            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        findViewById(R.id.menu_back_to_playlists).setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
        });
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
        updateAllSongCounts(); // 每次进入页面时刷新歌曲数量
        for (Playlist playlist : playlists) {
            String coverPath = MusicLoader.getLatestCoverForPlaylist(this, playlist.getName());
            playlist.setLatestCoverPath(coverPath);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
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
        final EditText input = new EditText(this);
        builder.setTitle("新建歌单")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String name = input.getText().toString();
                    Playlist newPlaylist = new Playlist(name, 0, R.drawable.default_playlist_cover);
//                    playlists.add(newPlaylist);
//                    adapter.notifyDataSetChanged();
                    adapter.addPlaylist(newPlaylist);
                    savePlaylist(); // 保存到文件或数据库
                    updateAllSongCounts();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void savePlaylist() {
        for (Playlist playlist : playlists) {
            String coverPath = MusicLoader.getLatestCoverForPlaylist(this, playlist.getName());
            playlist.setLatestCoverPath(coverPath);
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String playlistsJson = Playlist.toJson(playlists);
        prefs.edit().putString(KEY_PLAYLISTS, playlistsJson).apply();
        Log.d("PlaylistSave", "Saved playlists: " + playlistsJson);
    }


    private void loadPlaylists() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String playlistsJson = prefs.getString(KEY_PLAYLISTS, null);
        playlists.clear();
        if (playlistsJson != null) {
            playlists.addAll(Playlist.fromJson(playlistsJson));
        } else {
            playlists.add(new Playlist("默认歌单", 0, R.drawable.default_playlist_cover));
            playlists.add(new Playlist("我的收藏", 0, R.drawable.default_playlist_cover));
            savePlaylist();
        }
        for (Playlist playlist : playlists) {
            String coverPath = MusicLoader.getLatestCoverForPlaylist(this, playlist.getName());
            playlist.setLatestCoverPath(coverPath);
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


}