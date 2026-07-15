package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.content.SharedPreferences;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.SelectedSongAdapter;
import com.example.myapplication.model.SelectedSong;
import com.example.myapplication.adapter.UploadLibrarySongAdapter;
import com.example.myapplication.model.Song;
import com.example.myapplication.network.BatchUploadTask;
import com.example.myapplication.utils.BiliAudioDownloadHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import java.util.Map;

public class UploadActivity extends AppCompatActivity {
    private static final String TAG = "UploadActivity";
    private static final int REQUEST_PERMISSION = 1001;
    private static final int REQUEST_PICK_AUDIO = 1002;

    private RecyclerView rvSelectedSongs;
    private ImageView ivBgBlur;
    private View vBgTint;

    private RecyclerView rvLibrarySongs;
    private TextView tvLibraryEmpty;
    private TextView tvLibraryCount;
    private TextView tvSelectedCount;
    private CheckBox cbSelectAllLibrary;
    private EditText etLibraryFilter;
    private UploadLibrarySongAdapter libraryAdapter;
    private final List<UploadLibrarySongAdapter.LibrarySong> libraryAllItems = new ArrayList<>();
    private final List<UploadLibrarySongAdapter.LibrarySong> libraryFilteredItems = new ArrayList<>();
    private boolean suppressSelectAllListener = false;

    private TextView tvEmptyHint;
    private Button btnUploadSongs;
    private SelectedSongAdapter adapter;
    private List<SelectedSong> selectedSongs;

    private final Map<String, SelectedSong> selectedByKey = new HashMap<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        initViews();
        initData();
        setupListeners();
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
        toolbar.setNavigationOnClickListener(v -> finish());

        rvLibrarySongs = findViewById(R.id.rvLibrarySongs);
        tvLibraryEmpty = findViewById(R.id.tvLibraryEmpty);
        tvLibraryCount = findViewById(R.id.tvLibraryCount);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        cbSelectAllLibrary = findViewById(R.id.cbSelectAllLibrary);
        etLibraryFilter = findViewById(R.id.etLibraryFilter);

        rvSelectedSongs = findViewById(R.id.rvSelectedSongs);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        btnUploadSongs = findViewById(R.id.btnUploadSongs);
    }
    private void initData() {
        selectedSongs = new ArrayList<>();
        adapter = new SelectedSongAdapter(this, selectedSongs, this::removeSong);
        rvSelectedSongs.setLayoutManager(new LinearLayoutManager(this));
        rvSelectedSongs.setAdapter(adapter);


        libraryAdapter = new UploadLibrarySongAdapter();
        rvLibrarySongs.setLayoutManager(new LinearLayoutManager(this));
        rvLibrarySongs.setAdapter(libraryAdapter);

        libraryAdapter.setOnSelectionChangedListener((item, selected) -> {
            if (selected) {
                addSelectedFromLibrary(item);
            } else {
                removeSelectedByKey(item.key);
            }
            updateSelectionBar();
            updateUI();
        });

        loadLibrarySongs();
        updateUI();
    }

    private void setupListeners() {
        findViewById(R.id.uploadCard).setOnClickListener(v -> {
            Log.d(TAG, "上传卡片被点击");
            checkPermissionAndPickAudio();
        });
        btnUploadSongs.setOnClickListener(v -> uploadSongs());

        cbSelectAllLibrary.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSelectAllListener) {
                return;
            }
            if (libraryFilteredItems.isEmpty()) {
                return;
            }
            for (UploadLibrarySongAdapter.LibrarySong it : libraryFilteredItems) {
                if (it.selected != isChecked) {
                    it.selected = isChecked;
                    if (isChecked) {
                        addSelectedFromLibrary(it);
                    } else {
                        removeSelectedByKey(it.key);
                    }
                }
            }
            libraryAdapter.notifyDataSetChanged();
            updateSelectionBar();
            updateUI();
        });

        etLibraryFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                applyLibraryFilter();
            }
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        applyPlaylistBackground();
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
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // 允许多选
    
        // 添加多种音频格式支持
        String[] mimeTypes = {"audio/mpeg", "audio/mp3", "audio/wav", "audio/flac", "audio/aac", "audio/ogg"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
    
        try {
            startActivityForResult(Intent.createChooser(intent, "选择音乐文件（可多选）"), REQUEST_PICK_AUDIO);
        } catch (android.content.ActivityNotFoundException ex) {
            // 如果没有文件管理器，尝试使用 ACTION_GET_CONTENT
            Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
            fallbackIntent.setType("audio/*");
            fallbackIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            fallbackIntent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(fallbackIntent, "选择音乐文件（可多选）"), REQUEST_PICK_AUDIO);
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
            if (data.getClipData() != null) {
                // 多文件选择
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri audioUri = data.getClipData().getItemAt(i).getUri();
                    addSelectedSong(audioUri);
                }
            } else if (data.getData() != null) {
                // 单文件选择
                Uri audioUri = data.getData();
                addSelectedSong(audioUri);
            }
        }
    }

    private void addSelectedSong(Uri audioUri) {
        try {
            SelectedSong song = extractSongInfo(audioUri);
            if (song != null) {
                String key = makeSelectedKey(song);
                if (key != null && selectedByKey.containsKey(key)) {
                    Toast.makeText(this, "该歌曲已经添加过了", Toast.LENGTH_SHORT).show();
                    return;
                }

                selectedSongs.add(song);
                if (key != null) {
                    selectedByKey.put(key, song);
                }
                adapter.notifyDataSetChanged();
                updateUI();
                Toast.makeText(this, "已添加: " + song.getSongName(), Toast.LENGTH_SHORT).show();
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

            String key = makeSelectedKey(removedSong);
            if (key != null) {
                selectedByKey.remove(key);
                boolean changed = false;
                for (UploadLibrarySongAdapter.LibrarySong it : libraryAllItems) {
                    if (key.equals(it.key) && it.selected) {
                        it.selected = false;
                        changed = true;
                        break;
                    }
                }
                if (changed) {
                    libraryAdapter.notifyDataSetChanged();
                    updateSelectionBar();
                }
            }
            
            // 如果删除后列表为空，使用完整刷新
            if (selectedSongs.isEmpty()) {
                adapter.notifyDataSetChanged();
            } else {
                adapter.notifyItemRemoved(position);
                // 如果删除的不是最后一项，需要通知后续项目位置变化
                if (position < selectedSongs.size()) {
                    adapter.notifyItemRangeChanged(position, selectedSongs.size() - position);
                }
            }
            
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

    private void updateSelectionBar() {
        int selectedCount = 0;
        for (UploadLibrarySongAdapter.LibrarySong it : libraryAllItems) {
            if (it.selected) {
                selectedCount++;
            }
        }
        tvSelectedCount.setText("已选 " + selectedCount);
        suppressSelectAllListener = true;
        cbSelectAllLibrary.setChecked(!libraryFilteredItems.isEmpty() && isAllFilteredSelected());
        suppressSelectAllListener = false;
    }

    private boolean isAllFilteredSelected() {
        for (UploadLibrarySongAdapter.LibrarySong it : libraryFilteredItems) {
            if (!it.selected) {
                return false;
            }
        }
        return true;
    }

    private void addSelectedFromLibrary(UploadLibrarySongAdapter.LibrarySong item) {
        if (item == null || item.key == null) {
            return;
        }
        if (selectedByKey.containsKey(item.key)) {
            return;
        }
        Uri uri = null;
        if (item.uriStringForUpload != null && !item.uriStringForUpload.isEmpty()) {
            try {
                uri = Uri.parse(item.uriStringForUpload);
            } catch (Exception ignored) {
            }
        }
        SelectedSong song = new SelectedSong(
                item.title,
                item.artistForUpload,
                item.durationMsForUpload,
                item.filePathForUpload,
                uri
        );
        if (item.coverUrlForPreview != null && !item.coverUrlForPreview.isEmpty()) {
            song.setCoverUrl(item.coverUrlForPreview);
        }
        selectedSongs.add(song);
        selectedByKey.put(item.key, song);
        adapter.notifyDataSetChanged();
    }

    private void removeSelectedByKey(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        SelectedSong existing = selectedByKey.remove(key);
        if (existing != null) {
            selectedSongs.remove(existing);
            adapter.notifyDataSetChanged();
        } else {
            for (int i = selectedSongs.size() - 1; i >= 0; i--) {
                SelectedSong s = selectedSongs.get(i);
                String k = makeSelectedKey(s);
                if (key.equals(k)) {
                    selectedSongs.remove(i);
                }
            }
            adapter.notifyDataSetChanged();
        }
    }

    private String makeSelectedKey(SelectedSong song) {
        if (song == null) {
            return null;
        }
        if (song.getFilePath() != null && !song.getFilePath().isEmpty()) {
            return normalizeFilePath(song.getFilePath());
        }
        if (song.getUri() != null) {
            return song.getUri().toString();
        }
        return null;
    }

    private void loadLibrarySongs() {
        libraryAllItems.clear();
        try {
            List<Song> localSongs = MusicLoader.loadAllLocalSongs(this);
            for (Song s : localSongs) {
                UploadLibrarySongAdapter.LibrarySong item = toLibraryItem(s);
                if (item != null) {
                    libraryAllItems.add(item);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "加载本地音乐库失败", e);
        }

        tvLibraryCount.setText("(" + libraryAllItems.size() + ")");
        applyLibraryFilter();
    }

    private void applyLibraryFilter() {
        String q = etLibraryFilter != null ? etLibraryFilter.getText().toString().trim().toLowerCase() : "";
        libraryFilteredItems.clear();
        if (q.isEmpty()) {
            libraryFilteredItems.addAll(libraryAllItems);
        } else {
            for (UploadLibrarySongAdapter.LibrarySong it : libraryAllItems) {
                String t = it.title != null ? it.title.toLowerCase() : "";
                String sub = it.subtitle != null ? it.subtitle.toLowerCase() : "";
                if (t.contains(q) || sub.contains(q)) {
                    libraryFilteredItems.add(it);
                }
            }
        }

        boolean empty = libraryFilteredItems.isEmpty();
        tvLibraryEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvLibrarySongs.setVisibility(empty ? View.GONE : View.VISIBLE);
        libraryAdapter.submitList(new ArrayList<>(libraryFilteredItems));
        updateSelectionBar();
    }

    private UploadLibrarySongAdapter.LibrarySong toLibraryItem(Song song) {
        if (song == null) {
            return null;
        }
        String raw = song.getFilePath();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        if (BiliAudioDownloadHelper.isBiliPath(raw)) {
            String name = song.getName() != null ? song.getName() : "未知歌曲";
            String artist = "未知艺术家";
            String title = name;
            int split = name.indexOf(" - ");
            if (split > 0 && split < name.length() - 3) {
                artist = name.substring(0, split).trim();
                title = name.substring(split + 3).trim();
            }
            int durationSec = Math.max(0, song.getRawDuration());
            String subtitle = song.getPlaylist() != null && !song.getPlaylist().isEmpty()
                    ? ("歌单: " + song.getPlaylist() + " | B站在线音频")
                    : "B站在线音频";
            return new UploadLibrarySongAdapter.LibrarySong(
                    raw,
                    title,
                    subtitle,
                    formatDuration(durationSec),
                    artist,
                    durationSec * 1000,
                    raw,
                    null,
                    song.getCoverUrl()
            );
        }

        String filePath = normalizeFilePath(raw);
        Uri uri;
        String lower = raw.toLowerCase();
        if (lower.startsWith("content://") || lower.startsWith("file://")) {
            uri = Uri.parse(raw);
        } else {
            File f = new File(filePath);
            if (!f.exists()) {
                return null;
            }
            uri = Uri.fromFile(f);
        }

        String name = song.getName() != null ? song.getName() : "未知歌曲";
        String artist = "未知艺术家";
        String title = name;
        int split = name.indexOf(" - ");
        if (split > 0 && split < name.length() - 3) {
            artist = name.substring(0, split).trim();
            title = name.substring(split + 3).trim();
        }

        int durationSec = Math.max(0, song.getRawDuration());
        String durationText = formatDuration(durationSec);
        int durationMs = durationSec * 1000;

        String subtitle = song.getPlaylist() != null && !song.getPlaylist().isEmpty() ? ("歌单: " + song.getPlaylist()) : "";
        String key = filePath != null && !filePath.isEmpty() ? filePath : uri.toString();
        String coverUrl = song.getCoverUrl();
        UploadLibrarySongAdapter.LibrarySong item = new UploadLibrarySongAdapter.LibrarySong(
                key,
                title,
                subtitle,
                durationText,
                artist,
                durationMs,
                filePath,
                uri.toString(),
                coverUrl
        );
        return item;
    }

    private String normalizeFilePath(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            Uri u = Uri.parse(raw);
            if ("file".equalsIgnoreCase(u.getScheme())) {
                String p = u.getPath();
                if (p != null && !p.isEmpty()) {
                    return p;
                }
            }
        } catch (Exception ignored) {
        }
        return raw;
    }

    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
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

    private void uploadSongs() {
        if (selectedSongs.isEmpty()) {
            Toast.makeText(this, "请先选择要上传的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }

        btnUploadSongs.setEnabled(false);
        btnUploadSongs.setText("准备上传...");

        BatchUploadTask batchUploadTask = new BatchUploadTask(this, selectedSongs, new BatchUploadTask.BatchUploadCallback() {
            @Override
            public void onSuccess(int successCount, int totalCount) {
                String message = String.format("上传完成！成功: %d/%d", successCount, totalCount);
                Toast.makeText(UploadActivity.this, message, Toast.LENGTH_LONG).show();
                if (successCount == totalCount) {
                    finish(); // 全部成功才关闭页面
                } else {
                    btnUploadSongs.setEnabled(true);
                    btnUploadSongs.setText("重新上传失败的歌曲");
                }
            }

            @Override
            public void onError(String error, int failedIndex, int successCount) {
                String message = String.format("上传失败: %s (已成功: %d首)", error, successCount);
                Toast.makeText(UploadActivity.this, message, Toast.LENGTH_LONG).show();
                btnUploadSongs.setEnabled(true);
                btnUploadSongs.setText("重新上传");
            }

            @Override
            public void onProgress(BatchUploadTask.UploadProgress progress) {
                @SuppressLint("DefaultLocale") String text = String.format("上传中 (%d/%d): %s - %d%%",
                    progress.currentIndex + 1, 
                    progress.totalCount, 
                    progress.currentSongName, 
                    progress.currentProgress);
                btnUploadSongs.setText(text);
            }

            @Override
            public void onSongUploadComplete(int index, String songName, boolean success) {
                String status = success ? "✓" : "✗";
                Log.d(TAG, String.format("%s 第%d首: %s", status, index + 1, songName));
            }
        });

        batchUploadTask.execute();
    }
}
