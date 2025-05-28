package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.MusicPlayer;
import com.example.myapplication.MyApp;
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
    private List<String> playlists = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private static final String PREFS = "playlist_prefs";
    private static final String KEY_PLAYLISTS = "playlists";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_list);
        ListView listView = findViewById(R.id.lvPlaylists);
        Button btnNew = findViewById(R.id.btnNewPlaylist);
        Button btnDelete = findViewById(R.id.btnDeletePlaylist);
        // 加载已有歌单（可从文件或数据库）
        loadPlaylists();

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_single_choice,
                playlists);
        listView.setAdapter(adapter);
//关于短按长按可能需要修改
        // 短按：进入歌单
        listView.setOnItemClickListener((parent, view, position, id) -> {
            listView.clearChoices();
            adapter.notifyDataSetChanged();  // 刷新 UI，隐藏任何打勾

            String selected = playlists.get(position);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("playlist", selected);
            startActivity(intent);
        });

        // 长按：切换选中状态（用于删除）
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            boolean currently = listView.isItemChecked(position);
            listView.setItemChecked(position, !currently);
            return true;
        });

        btnDelete.setOnClickListener(v -> {
            int pos = listView.getCheckedItemPosition();
            if (pos >= 0 && pos < playlists.size()) {
                String name = playlists.get(pos);
                confirmAndDeletePlaylist(name, pos);
            }
        });

        // 新建歌单
        btnNew.setOnClickListener(v -> showCreateDialog());
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
                    playlists.add(name);
                    adapter.notifyDataSetChanged();
                    savePlaylist(name); // 保存到文件或数据库
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void loadPlaylists() {
        // 从 SharedPreferences 读取已有歌单
        Set<String> set = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_PLAYLISTS, null);
        if (set != null && !set.isEmpty()) {
            playlists.addAll(set);
        } else {
            // 第一次安装，初始化两个默认歌单
            playlists.add("默认歌单");
            playlists.add("我的收藏");
            // 并保存到 prefs
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putStringSet(KEY_PLAYLISTS, new HashSet<>(playlists))
                    .apply();
        }
    }

    private void savePlaylist(String name) {
        // 先把当前 prefs 里已有的取出来
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> set = new HashSet<>(
                prefs.getStringSet(KEY_PLAYLISTS, Collections.emptySet())
        );
        // 加入新歌单
        set.add(name);
        // 写回 prefs
        prefs.edit().putStringSet(KEY_PLAYLISTS, set).apply();
    }

}