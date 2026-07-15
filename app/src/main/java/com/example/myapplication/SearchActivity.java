package com.example.myapplication;

import android.os.AsyncTask;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import com.example.myapplication.adapter.SongAdapter;
import com.example.myapplication.model.Song;
import com.example.myapplication.model.Songinf;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
import com.example.myapplication.network.ServerConfig;
//搜索逻辑，注意搜索的xml中的输入框存在适配问题，需要修改，页面需要美化
//同时可以取消搜索按钮，改为输入框获取内容后直接向后端提出申请
public class SearchActivity extends AppCompatActivity {
    private ListView lvResults;
    private EditText etServer, etKeyword;
    private List<Songinf> songList = new ArrayList<>();
//    private List<Song> songList = new ArrayList<>();
    private String currentPlaylist;
    private SongAdapter adapter;

    private ImageView ivBgBlur;
    private View vBgTint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        ivBgBlur = findViewById(R.id.detail_bg_blur);
        vBgTint = findViewById(R.id.detail_bg_tint);
        applyPlaybackBackground();

        View contentRoot = findViewById(R.id.contentRoot);
        if (contentRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (v, insets) -> {
                int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), imeBottom);
                return insets;
            });
        }
        
        currentPlaylist = getIntent().getStringExtra("current_playlist");
        String fromActivity = getIntent().getStringExtra("from_activity");
        
        if (currentPlaylist == null) {
            currentPlaylist = "";
        }
        etServer = findViewById(R.id.et_server);
        etKeyword = findViewById(R.id.et_keyword);
        lvResults = findViewById(R.id.lv_results);
        View btnSearch = findViewById(R.id.btn_search);
        Button btnConfirm = findViewById(R.id.btn_confirm);
        adapter = new SongAdapter(this, songList);
        lvResults.setAdapter(adapter);

        // 默认展示当前后端地址（主要用于提示），实际请求已统一走 ServerConfig
        try {
            String hint = ServerConfig.appBaseUrl().replace("http://", "").replaceAll("/$", "");
            etServer.setText(hint);
        } catch (Exception ignored) {}
//        ImageButton btnBack = findViewById(R.id.btn_back);
//        btnBack.setOnClickListener(v -> finish());
        Toolbar toolbar = findViewById(R.id.toolbar_search);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> {
            // 根据来源页面决定返回行为
            if ("MainActivity".equals(fromActivity)) {
                // 从播放页面进入，返回播放页面
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
            finish();
        });
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

    @Override
    protected void onResume() {
        super.onResume();
        applyPlaybackBackground();
    }
    // SearchActivity.java
    private void addToLocalPlaylist(Songinf serverSong) throws UnsupportedEncodingException {
        // 后端返回的 songpath 形如 "/song/xxx.mp3"，需要带上应用 context 才能访问到资源
        String cleanPath = serverSong.getSongpath();
        if (cleanPath == null) cleanPath = "";
        if (cleanPath.startsWith("/")) cleanPath = cleanPath.substring(1);
        String fullPath = ServerConfig.appBaseUrl() + cleanPath;
    
        // 组合歌手和歌曲名，格式："歌手 - 歌曲名"
        String musician = serverSong.getMusician();
        String songName = serverSong.getSongname();
        String combinedName;
        
        if (musician != null && !musician.trim().isEmpty()) {
            combinedName = musician + " - " + songName;
        } else {
            // 如果没有歌手信息，只使用歌曲名
            combinedName = songName;
        }

        Log.d("SearchActivity", "addToLocalPlaylist: songId = " + serverSong.getSongid());
        Song localSong = new Song(
                serverSong.getSongduration(),
                combinedName,  // 使用组合后的名称
                fullPath,
                currentPlaylist
        );
        localSong.setOnlineSongId(serverSong.getSongid());
        localSong.setCoverUrl(serverSong.getCoverUrl());
        MainActivity.addSongToPlaylist(localSong);
        MusicLoader.appendMusic(getApplicationContext(), localSong);
    }
    class SearchTask extends AsyncTask<Void, Void, List<Songinf>> {
        // SearchActivity.java 的 SearchTask 类
        @Override
        protected List<Songinf> doInBackground(Void... voids) {
            try {
                String keyword = URLEncoder.encode(etKeyword.getText().toString().trim(), "UTF-8");
                String urlStr = ServerConfig.appBaseUrl()
                        + "jiekou?keyword="
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

    private void applyPlaybackBackground() {
        if (ivBgBlur == null || vBgTint == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
        String playbackBgPath = prefs.getString("playback_background_path", null);
        int transparency = 180;
        int blurRadius = 0;
        if (playbackBgPath == null || !new File(playbackBgPath).exists()) {
            ivBgBlur.setImageDrawable(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ivBgBlur.setRenderEffect(null);
            }
            return;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        AtomicBoolean cancelled = new AtomicBoolean(false);
        new Thread(() -> {
            Bitmap out = null;
            try {
                Bitmap original = decodeSampledBitmapFromFile(playbackBgPath, screenWidth, screenHeight);
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
                if (isFinishing() || isDestroyed() || cancelled.get()) {
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
                    } else {
                        if (blurRadius > 0) {
                            Bitmap blurred = blurBitmap(finalOut, Math.min(25, blurRadius));
                            if (blurred != finalOut) {
                                ivBgBlur.setImageBitmap(blurred);
                                try {
                                    finalOut.recycle();
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                } else {
                    ivBgBlur.setImageDrawable(null);
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

    private Bitmap blurBitmap(Bitmap bitmap, int radius) {
        if (radius <= 0) {
            return bitmap;
        }
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        android.renderscript.RenderScript rs = android.renderscript.RenderScript.create(this);
        android.renderscript.ScriptIntrinsicBlur script = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs));
        android.renderscript.Allocation input = android.renderscript.Allocation.createFromBitmap(rs, bitmap);
        android.renderscript.Allocation outputAlloc = android.renderscript.Allocation.createFromBitmap(rs, output);
        script.setRadius(radius);
        script.setInput(input);
        script.forEach(outputAlloc);
        outputAlloc.copyTo(output);
        rs.destroy();
        return output;
    }
}
