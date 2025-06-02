package com.example.myapplication;

import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.example.myapplication.adapter.SongAdapter;
import com.example.myapplication.model.Song;
import com.example.myapplication.model.Songinf;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.util.Log;

import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
// MainActivity.java

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

import java.util.ArrayList;
//搜索逻辑，注意搜索的xml中的输入框存在适配问题，需要修改，页面需要美化
//同时可以取消搜索按钮，改为输入框获取内容后直接向后端提出申请
public class SearchActivity extends AppCompatActivity {
    private ListView lvResults;
    private EditText etServer, etKeyword;
    private List<Songinf> songList = new ArrayList<>();
//    private List<Song> songList = new ArrayList<>();
    private String currentPlaylist;
    private SongAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        currentPlaylist = getIntent().getStringExtra("current_playlist");
        if (currentPlaylist == null) {
            currentPlaylist = "默认歌单";
        }
        etServer = findViewById(R.id.et_server);
        etKeyword = findViewById(R.id.et_keyword);
        lvResults = findViewById(R.id.lv_results);
        Button btnSearch = findViewById(R.id.btn_search);
        Button btnConfirm = findViewById(R.id.btn_confirm);
        adapter = new SongAdapter(this, songList);
        lvResults.setAdapter(adapter);
//        ImageButton btnBack = findViewById(R.id.btn_back);
//        btnBack.setOnClickListener(v -> finish());
        Toolbar toolbar = findViewById(R.id.toolbar_search);
        toolbar.setNavigationOnClickListener(v -> finish());
        btnSearch.setOnClickListener(v -> new SearchTask().execute());
        btnConfirm.setOnClickListener(v -> {
            boolean anySelected = false;
            for (Songinf song : songList) {
                if (song.isSelected()) {
                    anySelected = true;
                    break;
                }
            }
            if (!anySelected) {
                // 没选中任何歌曲，提示后不退出页面
                Toast.makeText(this, "请先选择至少一首歌曲", Toast.LENGTH_SHORT).show();
                return;
            }

            // 有选中，添加到本地列表
            for (Songinf song : songList) {
                if (song.isSelected()) {
                    try {
                        addToLocalPlaylist(song);
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            // 通知 MainActivity 刷新，并关闭
            Intent resultIntent = new Intent();
            resultIntent.putExtra("should_refresh", true);
            setResult(RESULT_OK, resultIntent);
            finish();
            Toast.makeText(this, "已添加选中的歌曲", Toast.LENGTH_SHORT).show();
        });

    }
    // SearchActivity.java
    private void addToLocalPlaylist(Songinf serverSong) throws UnsupportedEncodingException {
        String serverAddress = etServer.getText().toString().trim();
        // 确保路径格式正确（避免双斜杠）
        String cleanPath = serverSong.getSongpath().startsWith("/") ?
                serverSong.getSongpath().substring(1) :
                serverSong.getSongpath();

        String fullPath = "http://" + serverAddress + "/" + cleanPath;


        Log.d("SearchActivity", "addToLocalPlaylist: songId = " + serverSong.getSongid());
        Song localSong = new Song(
                serverSong.getSongduration(),
                serverSong.getSongname(),
                fullPath,
                currentPlaylist
        );
        localSong.setOnlineSongId(serverSong.getSongid());
        MainActivity.addSongToPlaylist(localSong);
        MusicLoader.appendMusic(getApplicationContext(), localSong);
    }
    class SearchTask extends AsyncTask<Void, Void, List<Songinf>> {
        // SearchActivity.java 的 SearchTask 类
        @Override
        protected List<Songinf> doInBackground(Void... voids) {
            try {
                // 动态获取服务器地址，这里以后可能要修改
                String serverAddress = "http://" + etServer.getText().toString().trim();
                String keyword = URLEncoder.encode(etKeyword.getText().toString().trim(), "UTF-8");
                String urlStr = serverAddress
                        + "/jiekou?keyword="
                        + keyword
                        + "&page=1"
                        + "&pageSize=50";
                URL url = new URL(urlStr);
                Log.d("Network", "Request URL: " + url.toString()); // 打印URL

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                // 检查HTTP状态码
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("Network", "HTTP Error: " + responseCode);
                    return null;
                }

                // 读取响应
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                Log.d("Network", "Raw Response: " + response.toString());

                // 解析JSON
                return new Gson().fromJson(response.toString(), new TypeToken<List<Songinf>>(){}.getType());
            } catch (Exception e) {
                Log.e("Error", "Exception: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<Songinf> result) {
            if(result != null) {
                Collections.sort(result, (a, b) -> b.getPlayCount() - a.getPlayCount());
                Log.d("Data", "Received items: " + result.size());
                for (Songinf song : result) {
                    Log.d("Data", "Song: " + song.getSongname()
                            + ", Duration: " + song.getSongduration());
                }
                songList.clear();
                songList.addAll(result);
                adapter.notifyDataSetChanged();
            } else {
                Log.e("Error", "Result is null");
            }
        }
    }
}