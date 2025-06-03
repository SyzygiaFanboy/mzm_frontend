package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.myapplication.MusicPlayer;
import com.example.myapplication.MyApp;
import com.example.myapplication.adapter.PlaylistAdapter;
import com.example.myapplication.model.Playlist;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.*;

public class PlaylistListActivity extends AppCompatActivity {
    private List<Playlist> playlists = new ArrayList<>();
    private PlaylistAdapter adapter;
    private static final String PREFS = "playlist_prefs";
    private static final String KEY_PLAYLISTS = "playlists";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_list);

        ListView listView = findViewById(R.id.lvPlaylists);
        ImageButton btnNew = findViewById(R.id.btnNewPlaylist);
        ImageButton btnDelete = findViewById(R.id.btnDeletePlaylist);
        ImageButton btnMore = findViewById(R.id.btnMore);
        ConstraintLayout manageBar = findViewById(R.id.manageBar);
        CheckBox cbSelectAll = findViewById(R.id.cbSelectAll);
        // 加载已有歌单
        loadPlaylists();
        adapter = new PlaylistAdapter(this, playlists);
        listView.setAdapter(adapter);
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                adapter.selectAll();
            } else {
                adapter.clearSelection();
            }
        });

        adapter.setOnSelectionChanged(() -> {
            boolean allSelected = adapter.getSelectedIndices().size() == adapter.getCount();
            cbSelectAll.setChecked(allSelected);
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            listView.clearChoices();
            adapter.notifyDataSetChanged();  // 刷新 UI，隐藏任何打勾


            String selected = playlists.get(position).getName();
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("playlist", selected);
            startActivity(intent);
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

            savePlaylist(); // 更新 SharedPreferences
            updateAllSongCounts();
        });

        // 新建歌单
        btnNew.setOnClickListener(v -> showCreateDialog());
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
        updateAllSongCounts(); // 每次进入页面时刷新歌曲数量
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
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String playlistsJson = Playlist.toJson(playlists);
        prefs.edit().putString(KEY_PLAYLISTS, playlistsJson).apply();
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