package com.example.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.SelectedSongAdapter;
import com.example.myapplication.model.SelectedSong;
import com.example.myapplication.network.UploadTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class UploadActivity extends AppCompatActivity {
    private static final String TAG = "UploadActivity";
    private static final int REQUEST_PERMISSION = 1001;
    private static final int REQUEST_PICK_AUDIO = 1002;
    
    private RecyclerView rvSelectedSongs;
    private TextView tvEmptyHint;
    private Button btnUploadSongs;
    private SelectedSongAdapter adapter;
    private List<SelectedSong> selectedSongs;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);
        
        initViews();
        initData();
        setupListeners();
    }
    
    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        rvSelectedSongs = findViewById(R.id.rvSelectedSongs);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        btnUploadSongs = findViewById(R.id.btnUploadSongs);
        
        btnBack.setOnClickListener(v -> finish());
    }
    
    private void initData() {
        selectedSongs = new ArrayList<>();
        adapter = new SelectedSongAdapter(this, selectedSongs, this::removeSong);
        rvSelectedSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSelectedSongs.setAdapter(adapter);
        
        updateUI();
    }
    
    private void setupListeners() {
        findViewById(R.id.uploadCard).setOnClickListener(v -> {
            Log.d(TAG, "上传卡片被点击");
            checkPermissionAndPickAudio();
        });
        btnUploadSongs.setOnClickListener(v -> uploadSongs());
    }
    
    private void checkPermissionAndPickAudio() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用新的媒体权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                        REQUEST_PERMISSION);
                } else {
                    pickAudioFile();
                }
            } else {
                // Android 12 及以下使用传统权限
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            REQUEST_PERMISSION);
                    } else {
                        pickAudioFile();
                    }
                }
            }
    
    private void pickAudioFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        
        // 添加多种音频格式支持
        String[] mimeTypes = {"audio/mpeg", "audio/mp3", "audio/wav", "audio/flac", "audio/aac", "audio/ogg"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        try {
            startActivityForResult(Intent.createChooser(intent, "选择音乐文件"), REQUEST_PICK_AUDIO);
        } catch (android.content.ActivityNotFoundException ex) {
            // 如果没有文件管理器，尝试使用 ACTION_GET_CONTENT
            Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
            fallbackIntent.setType("audio/*");
            fallbackIntent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(fallbackIntent, "选择音乐文件"), REQUEST_PICK_AUDIO);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickAudioFile();
            } else {
                Toast.makeText(this, "需要存储权限才能选择音乐文件", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_AUDIO && resultCode == RESULT_OK && data != null) {
            Uri audioUri = data.getData();
            if (audioUri != null) {
                addSelectedSong(audioUri);
            }
        }
    }
    
    private void addSelectedSong(Uri audioUri) {
        try {
            SelectedSong song = extractSongInfo(audioUri);
            if (song != null) {
                // 检查是否已经添加过这首歌
                boolean alreadyExists = selectedSongs.stream()
                        .anyMatch(s -> s.getFilePath().equals(song.getFilePath()));
                
                if (!alreadyExists) {
                    selectedSongs.add(song);
                    adapter.notifyDataSetChanged();
                    updateUI();
                    Toast.makeText(this, "已添加: " + song.getSongName(), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "该歌曲已经添加过了", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "添加歌曲失败", e);
            Toast.makeText(this, "添加歌曲失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private SelectedSong extractSongInfo(Uri audioUri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, audioUri);
            
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            
            // 获取文件路径
            String filePath = getRealPathFromURI(audioUri);
            
            if (title == null || title.trim().isEmpty()) {
                title = "未知歌曲";
            }
            if (artist == null || artist.trim().isEmpty()) {
                artist = "未知艺术家";
            }
            
            int duration = 0;
            if (durationStr != null) {
                try {
                    duration = Integer.parseInt(durationStr);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "无法解析歌曲时长", e);
                }
            }
            
            retriever.release();
            
            return new SelectedSong(title, artist, duration, filePath, audioUri);
        } catch (Exception e) {
            Log.e(TAG, "提取歌曲信息失败", e);
            return null;
        }
    }
    
    private String getRealPathFromURI(Uri contentUri) {
        String[] projection = {MediaStore.Audio.Media.DATA};
        try (Cursor cursor = getContentResolver().query(contentUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                String path = cursor.getString(columnIndex);
                // 检查路径是否有效
                if (path != null && new File(path).exists()) {
                    return path;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取文件路径失败", e);
        }
        
        // 如果无法获取真实路径，返回null，让UploadTask使用URI
        Log.w(TAG, "无法获取文件真实路径，将使用URI: " + contentUri.toString());
        return null;
    }
    
    private void removeSong(int position) {
        if (position >= 0 && position < selectedSongs.size()) {
            SelectedSong removedSong = selectedSongs.remove(position);
            adapter.notifyItemRemoved(position);
            updateUI();
            Toast.makeText(this, "已移除: " + removedSong.getSongName(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateUI() {
        boolean hasSongs = !selectedSongs.isEmpty();
        tvEmptyHint.setVisibility(hasSongs ? View.GONE : View.VISIBLE);
        rvSelectedSongs.setVisibility(hasSongs ? View.VISIBLE : View.GONE);
        btnUploadSongs.setEnabled(hasSongs);
        btnUploadSongs.setText(hasSongs ? "上传 " + selectedSongs.size() + " 首歌曲" : "上传歌曲");
    }
    
    private void uploadSongs() {
        if (selectedSongs.isEmpty()) {
            Toast.makeText(this, "请先选择要上传的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        
        btnUploadSongs.setEnabled(false);
        btnUploadSongs.setText("上传中...");
        
        // 这里只上传第一首歌曲，因为要求只支持同时上传一首
        SelectedSong song = selectedSongs.get(0);
        
        UploadTask uploadTask = new UploadTask(this, new UploadTask.UploadCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(UploadActivity.this, "上传成功！", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(UploadActivity.this, "上传失败: " + error, Toast.LENGTH_SHORT).show();
                    btnUploadSongs.setEnabled(true);
                    btnUploadSongs.setText("上传 " + selectedSongs.size() + " 首歌曲");
                });
            }
            
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> {
                    btnUploadSongs.setText("上传中... " + progress + "%");
                });
            }
        });
        
        uploadTask.execute(song);
    }
}