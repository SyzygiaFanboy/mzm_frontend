package com.example.myapplication;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
// MainActivity.java
import com.example.myapplication.adapter.BatchModeAdapter;
import com.example.myapplication.model.MusicViewModel;
import com.example.myapplication.model.Playlist;
import com.example.myapplication.model.Song;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements MusicPlayer.OnSongCompletionListener {

    // 修改默认歌曲构造，避免硬编码路径，这块大概率以后要改
    private Song song = new Song(0, "暂无歌曲", "", "");
    private Song selectSong;
    private Thread progressSyncThread = null;
    private volatile boolean isProgressSyncRunning = false;
    private int selectedPosition = -1; // 初始值为 -1，表示未选中任何项
    private Button playBtn = null;
    private Button btnNext;
    private Button btnPrevious;
    private MusicPlayer musicPlayer;
    private TextView preogress = null;
    private ImageView albumArt;
    //private List<Map<String, Object>> musicList = new ArrayList<>(); // 确保非空//这里因为listitem是私有变量，得先创建个全局的先，，，
    private SeekBar progressBar = null;
    private boolean isTracking = false;
    private boolean stop = false;
    private boolean isSongChanging = false;
    private boolean isBatchMode = false; // 是否处于批量选择模式
    private List<Integer> selectedPositions = new ArrayList<>(); // 存储选中项的位置
    private CheckBox cbSelectAll;
    private boolean isAllSelected = false;
    static ListView listview = null;
    private long lastSongChangeTime = 0;
    private long lastClickTime = 0;
    private volatile boolean isResettingProgress = false;
    private volatile boolean shouldTerminate = false;
    private boolean isAutoNextTriggered = false;
    private volatile String currentCoverPath;
    private static final int PICK_MUSIC_REQUEST = 1;
    private static final int REQUEST_CODE_BILI = 2; // B站音乐请求码
    private String currentPlaylist;
    private boolean isAutoTriggeredByCompletion = false;


    //进度加载弹窗的成员变量
    private AlertDialog progressDialog;
    private ProgressBar dialogProgressBar;
    private TextView dialogMessage;

    private static final int REQUEST_CODE_STORAGE_PERMISSION = 100;

    // MainActivity.java
    private static final int REQUEST_CODE_SEARCH = 1001; // 请求码

    private AtomicBoolean isSyncActive = new AtomicBoolean(false);

    private void syncBar() {
        new Thread(new ProgressSync()).start();
    }

    private void startSyncThread() {
        if (!isSyncActive.get()) {
            new Thread(new ProgressSync()).start();
        }
    }

    // 声明 musicList 为 public static（临时方案，得改）
    public static List<Map<String, Object>> musicList = new ArrayList<>();

    // 添加公共方法用于添加歌曲
    public static void addSongToPlaylist(Song song) {
        // 通过文件路径判重
        for (Map<String, Object> item : musicList) {
            String existingPath = (String) item.get("filePath");
            if (existingPath != null && existingPath.equals(song.getFilePath())) {
                Log.d("Duplicate", "歌曲已存在: " + song.getName());
                return;
            }
        }
        musicList.add(song.toMap(musicList.size() + 1));
        Log.d("PathCheck", "当前路径: " + song.getFilePath());
        for (Map<String, Object> item : musicList) {
            Log.d("PathCheck", "已存在路径: " + item.get("filePath"));
        }
    }

    public interface myCallback<T> {
        void onResult(T result);
    }


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        Song song = new Song(0, "暂无歌曲", "", "");
        setContentView(R.layout.activity_main);
        //musicPlayer = new MusicPlayer(this);
        musicPlayer = ((MyApp) getApplication()).getMusicPlayer();
        musicPlayer.setOnSongCompletionListener(this);
        try {
            musicPlayer.loadMusic(song);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        playBtn = findViewById(R.id.playerbutton);
        preogress = findViewById(R.id.textpreogress);
        Button deletebtn = findViewById(R.id.removeMusic);
        Button btnMoveUp = findViewById(R.id.btnMoveUp);
        Button btnMoveDown = findViewById(R.id.btnMoveDown);
        ImageButton btnBatchSelect = findViewById(R.id.btnBatchSelect);
        btnNext = findViewById(R.id.next);
        btnPrevious = findViewById(R.id.previous);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setMax(song.getTimeDuration());
        listview = findViewById(R.id.playlist);
        albumArt = findViewById(R.id.albumArt);
        //listview = findViewById(R.id.playlist);
        listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listview.setOnItemClickListener(new MusicListItemClickListener());
        //MusicLoader.initMusicFile(this);
        //playBtn = findViewById(R.id.playerbutton);
        playBtn.setEnabled(false);
        playBtn.setText(musicPlayer.getPlayStatus() == PlayerStatus.PLAYING ? "暂停" : "播放");
        MusicViewModel viewModel = new ViewModelProvider(this).get(MusicViewModel.class);
        if (musicList.isEmpty()) {
            btnNext.setEnabled(false);
            btnPrevious.setEnabled(false);
        }
        currentPlaylist = getIntent().getStringExtra("playlist");
        if (currentPlaylist == null) currentPlaylist = "默认歌单";
        loadMusicList(listview, currentPlaylist);
        updateNavButtons();
//        // 绑定返回歌单按钮
//        Button btnBackToPlaylists = findViewById(R.id.menu_back_to_playlists);
//        btnBackToPlaylists.setOnClickListener(v -> {
//            // 跳转到歌单列表页面，可以看看怎么改
//            Intent intent = new Intent(MainActivity.this, PlaylistListActivity.class);
//            startActivity(intent);
//            finish();
//        });
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PlaylistListActivity.class);
            startActivity(intent);
            finish();
        });

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (musicPlayer.isPlaying()) {
                    // 如果当前正在播放，则切换到暂停状态
                    switchPlayStatus(PlayerStatus.PAUSED);
                    playBtn.setText("播放");
                } else {
                    // 如果是暂停或者停止状态，则播放
                    // 如果处于停止状态，重新加载音乐
                    if (musicPlayer.getPlayStatus() == PlayerStatus.STOPPED) {
                        try {
                            musicPlayer.loadMusic(song);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    musicPlayer.play();
                    playBtn.setText("暂停");
                    switchPlayStatus(PlayerStatus.PLAYING);
                    // 启动进度同步线程，保证只有一个线程在更新进度条
                    new Thread(new ProgressSync()).start();
                }
            }
        });

//        // 退出按钮监听
//        findViewById(R.id.menu_logout).setOnClickListener(v -> {
//            // 停止播放并释放播放器资源
//            if (musicPlayer != null) {
//                musicPlayer.stop();
//                musicPlayer.release();
//            }
//
//            // 终止进度，同时同步线程
//            if (progressSyncThread != null && progressSyncThread.isAlive()) {
//                progressSyncThread.interrupt();
//                isProgressSyncRunning = false;
//            }
//
//            SharedPreferences preferences = getSharedPreferences("user_pref", MODE_PRIVATE);
//            preferences.edit().putBoolean("is_logged_in", false).apply();
//            startActivity(new Intent(this, LoginActivity.class));
//            finish();
//        });
//        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
//        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
//                this,
//                drawerLayout,
//                toolbar,
//                R.string.navigation_drawer_open,
//                R.string.navigation_drawer_close
//        );
//        drawerLayout.addDrawerListener(toggle);
//        toggle.syncState();
//        // 隐藏 ActionBar 标题
//        if (getSupportActionBar() != null) {
//            getSupportActionBar().setDisplayShowTitleEnabled(false);
//        }

        // 请求存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 构造一个标准的权限管理 Intent，不增加多余的类别和类型
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                // 启动权限请求界面，只需要一个 requestCode
                startActivityForResult(intent, REQUEST_CODE_STORAGE_PERMISSION);
            }
        }
//        findViewById(R.id.menu_search).setOnClickListener(v -> {
//            Intent intent = new Intent(MainActivity.this, SearchActivity.class);
//            startActivityForResult(intent, REQUEST_CODE_SEARCH); // 使用 startActivityForResult
//            drawerLayout.closeDrawer(GravityCompat.START);
//        });

//        musicPlayer = new MusicPlayer(getApplicationContext());
//        musicPlayer.setOnSongCompletionListener(this);

        btnBatchSelect.setOnClickListener(v -> {
            isBatchMode = !isBatchMode;
            // 初始化适配器时设置监听
            BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
            if (adapter != null) {
                adapter.setBatchMode(isBatchMode);
            }
            //spqqxkz;
            adapter.setOnSelectAllListener(shouldSelectAll -> {
                isAllSelected = shouldSelectAll;
                cbSelectAll.setChecked(shouldSelectAll);
            });
            listview.setAdapter(adapter);
            // 全选复选框点击事件
            cbSelectAll = findViewById(R.id.cbSelectAll);
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isBatchMode) {
                    isAllSelected = isChecked;
                    selectAllSongs(isChecked);
                }
            });
            if (isBatchMode) {
                findViewById(R.id.moveButtons).setVisibility(View.GONE);
                findViewById(R.id.btnBatchDelete).setVisibility(View.VISIBLE); // 显示批量删除按钮
            } else {
                findViewById(R.id.moveButtons).setVisibility(View.VISIBLE);
                findViewById(R.id.btnBatchDelete).setVisibility(View.GONE);
                selectedPositions.clear();
            }
        });

        // 绑定批量删除按钮点击事件
        ImageButton btnBatchDelete = findViewById(R.id.btnBatchDelete);
        btnBatchDelete.setVisibility(View.GONE);
        btnBatchDelete.setOnClickListener(v -> {
            // 遍历 musicList，删除选中的项
            List<Map<String, Object>> itemsToDelete = new ArrayList<>();

            for (int i = 0; i < musicList.size(); i++) {

                if ((Boolean) musicList.get(i).get("isSelected")) {
                    itemsToDelete.add(musicList.get(i));
                }
            }

            boolean isCurrentSongDeleted = false;
            Song currentSong = musicPlayer.getCurrentSong();//这里用一个for循环而不是直接将已选择的currentsong传入将会增加运行时间，但是一时半会他也想不出更好的判读办法了
            String currentPath = (currentSong != null) ? currentSong.getFilePath() : "";
            for (Map<String, Object> item : itemsToDelete) {
                Song songItem = Song.fromMap(item);
                if (songItem.getFilePath().equals(currentPath)) {
                    isCurrentSongDeleted = true;
                    break;
                }
            }
            if (isCurrentSongDeleted) {
                musicPlayer.resetProgress();
                runOnUiThread(() -> {
                    if (musicPlayer.isPlaying()) {
                        musicPlayer.stop(); // 停止播放
                        musicPlayer.release(); // 释放资源（如果MusicPlayer有该方法）
                    }
                    progressBar.setProgress(0);
                    preogress.setText("00:00");
                    albumArt.setImageResource(R.drawable.default_cover);
                    TextView currentSongTV = findViewById(R.id.currentSong);
                    currentSongTV.setText("当前播放：暂无歌曲");
                    playBtn.setText("播放");
                    playBtn.setEnabled(false);
                    selectedPosition = -1;
                    listview.clearChoices(); // 清除所有选中状态
                    listview.requestLayout(); // 强制刷新
                });
            }

            // 直接遍历列表，记录选中项的位置
            List<Integer> positionsToDelete = new ArrayList<>();
            for (int i = 0; i < musicList.size(); i++) {
                if ((Boolean) musicList.get(i).get("isSelected")) {
                    positionsToDelete.add(i); // 直接记录索引
                }
            }
            // 逆序删除避免索引错乱
            Collections.sort(positionsToDelete, Collections.reverseOrder());
            for (int pos : positionsToDelete) {
                musicList.remove(pos);
            }
            // 统一更新索引
            updateSongIndices();
            updatePersistentStorage();
            // 刷新列表
            ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();

            // 退出批量模式
            isBatchMode = false;
            ((BatchModeAdapter) listview.getAdapter()).setBatchMode(false);
            cbSelectAll.setVisibility(View.GONE); // 隐藏全选复选框
            cbSelectAll.setChecked(false); // 取消全选状态
            findViewById(R.id.moveButtons).setVisibility(View.VISIBLE);
            findViewById(R.id.btnBatchDelete).setVisibility(View.GONE);
            if (musicList.isEmpty()) {
                runOnUiThread(() -> {
                    btnNext.setEnabled(false);
                    btnPrevious.setEnabled(false);
                    playBtn.setEnabled(false);
                });
            }
        });
        // 初始化全选复选框
        cbSelectAll = findViewById(R.id.cbSelectAll);
        cbSelectAll.setVisibility(View.GONE);
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAllSelected = isChecked;
            selectAllSongs(isChecked);
        });
        btnBatchSelect.setOnClickListener(v -> toggleBatchMode());

        // 设置进度监听
        musicPlayer.setProgressListener(new MusicPlayer.ProgressListener() {
            @Override
            public void onProgressUpdated(int currentPosition, int totalDuration) {
                runOnUiThread(() -> {
                    if (!isTracking) {
                        progressBar.setMax(totalDuration);
                        progressBar.setProgress(currentPosition);
                        preogress.setText(formatTime(currentPosition) + "/" + formatTime(totalDuration));
                    }
                });
            }
        });

        // 处理拖动进度条
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    //musicPlayer.seekTo(progress);
                    preogress.setText(formatTime(progress) + "/" + formatTime(seekBar.getMax()));
                }
            }


            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTracking = true;
            }

            private Runnable finishSeekRunnable;
            private Handler seekHandler = new Handler();

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTracking = false;
                int newPos = seekBar.getProgress();
                int target = seekBar.getProgress();
                musicPlayer.seekTo(newPos);
                int duration = seekBar.getMax();
                if (duration - newPos < 150) { // 离结尾太近，可能导致不触发 completion
                    runOnUiThread(() -> {
                        musicPlayer.pause();
                        musicPlayer.release();
                        onSongCompleted();//回调
                    });
                }


                // 重新启动进度同步线程
                if (!isProgressSyncRunning) {
                    new Thread(new ProgressSync()).start();
                }
            }

        });

        btnMoveUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 调用上移方法
                moveSongUp(selectedPosition);
            }
        });

        btnMoveDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 同理
                moveSongDown(selectedPosition);
            }
        });
        deletebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedPosition == -1) {
                    Toast.makeText(MainActivity.this, "请先选择要删除的歌曲", Toast.LENGTH_SHORT).show();
                    return;
                }
                showDeleteConfirmationDialog();
            }
        });

        // 添加按钮
        Button Addbtn = findViewById(R.id.addMusic);
        Addbtn.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("选择添加方式");

            // 设置选项
            String[] options = {"从本地添加", "从BV号 / 链接添加", "从B站收藏夹添加"};
            builder.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // 添加本地音乐
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("audio/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // 允许多选
                    startActivityForResult(intent, PICK_MUSIC_REQUEST);
                } else if (which == 1) {
                    // 添加B站音乐，用回调处理需要的值
                    // 先处理用户输入的数据（处理移动端短链、bv或av号等）
                    showBVInputDialog(bv -> {
                        // 按取消返回 null
                        if (bv == null) {
                            return;
                        }
                        try {
                            // 获取音频流函数
                            getBiliMusic(bv, this, result -> runOnUiThread(() -> {
                                if (result != null) {
                                    String title = (String) result.get("title");
                                    File f = (File) result.get("file");
                                    String path = f.getAbsolutePath();

                                    // 获取文件信息，用于转化成song
                                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                    retriever.setDataSource(path);
                                    String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                                    int duration = Integer.parseInt(durationStr) / 1000;

                                    // 封装、添加到歌单
                                    Song newSong = new Song(duration, title, path, currentPlaylist);
                                    addSongToPlaylist(newSong);
                                    ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
                                    Toast.makeText(MainActivity.this, "已添加到歌单", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "获取B站音乐失败", Toast.LENGTH_SHORT).show();
                                    Log.e("BiliMusic", "获取B站音乐失败，bv: " + bv);
                                }
                            }));
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "无效的BV号：" + bv, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

            builder.create().show();
        });

        // 确保progressBar不为null
        if (progressBar == null) {
            throw new RuntimeException("ProgressBar not found in layout");
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    //回调
    @Override
    public void onSongCompleted() {
        Log.d("MainActivity", "收到播放完成回调");
        runOnUiThread(() -> {
            try {
                if (!musicList.isEmpty()) {
                    playNextSong();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void loadMusicCover(String musicFilePath) {
        new Thread(() -> {
            Bitmap coverBitmap = MusicCoverUtils.getCoverFromFile(musicFilePath, getApplicationContext());
            runOnUiThread(() -> {
                if (coverBitmap != null) {
                    albumArt.setImageBitmap(coverBitmap);
                } else {
                    // 设置default曲绘
                    albumArt.setImageResource(R.drawable.default_cover);
                }
            });
        }).start();
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void deleteAllSongs() {
        if (musicPlayer.isPlaying()) {
            musicPlayer.stop();
            playBtn.setText("播放");
            progressBar.setProgress(0);
            preogress.setText("00:00");
        }
        musicList.clear();
        MusicLoader.clearAllMusic(this);
        File musicDir = new File(getFilesDir(), "music");
        if (musicDir.exists()) {
            File[] files = musicDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
        runOnUiThread(() -> {
            ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
            playBtn.setEnabled(false);
            btnNext.setEnabled(false);
            btnPrevious.setEnabled(false);
            TextView currentSongTV = findViewById(R.id.currentSong);
            currentSongTV.setText("当前播放：暂无歌曲");
            albumArt.setImageResource(R.drawable.default_cover);
            progressBar.setProgress(0);
            preogress.setText("00:00");
        });
        selectedPosition = -1;
        selectedPositions.clear();
        isAllSelected = false;
        if (isBatchMode) {
            toggleBatchMode();
        }
        Toast.makeText(this, "已删除全部歌曲", Toast.LENGTH_SHORT).show();
        if (musicList.isEmpty()) {
            TextView currentSongTV = findViewById(R.id.currentSong);
            playBtn.setEnabled(false);
            progressBar.setProgress(0);
            preogress.setText("0");
            musicPlayer.setCurrentPositiontozero();
            btnPrevious.setEnabled(false);
            btnNext.setEnabled(false);
            albumArt.setImageResource(R.drawable.default_cover);

            currentSongTV.setText("当前播放: 暂无歌曲");
        }
        runOnUiThread(() -> {
            listview.clearChoices();
            listview.requestLayout();
        });
    }

    //字面意思
    private void toggleBatchMode() {
        isBatchMode = !isBatchMode;
        BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
        adapter.setBatchMode(isBatchMode);
        cbSelectAll.setVisibility(isBatchMode ? View.VISIBLE : View.GONE);
        cbSelectAll.setChecked(false);
        findViewById(R.id.moveButtons).setVisibility(isBatchMode ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnBatchDelete).setVisibility(isBatchMode ? View.VISIBLE : View.GONE);
        if (!isBatchMode) {
            selectedPositions.clear();
            isAllSelected = false;
        }
    }

    private void selectAllSongs(boolean selectAll) {
        isAllSelected = selectAll;
        for (Map<String, Object> item : musicList) {
            item.put("isSelected", selectAll);
        }
        ((BatchModeAdapter) listview.getAdapter()).notifyDataSetChanged();

        // 更新选中项索引列表
        selectedPositions.clear();
        if (selectAll) {
            for (int i = 0; i < musicList.size(); i++) {
                selectedPositions.add(i);
            }
        }
    }

    private int getCurrentSongPosition() {
        if (musicPlayer == null || musicPlayer.getCurrentSong() == null) {
            return -1;
        }
        Song current = musicPlayer.getCurrentSong();
        for (int i = 0; i < musicList.size(); i++) {
            Song songInList = Song.fromMap(musicList.get(i));
            if (songInList.equals(current)) {
                return i;
            }
        }
        return -1;
    }

    public void playNextSong() throws IOException {
        Log.d("MainActivity", "尝试播放下一首歌曲");
        // 检查列表是否为空
        if (musicList.isEmpty()) {
            Log.e("MainActivity", "歌曲列表为空，无法播放下一首");
            Toast.makeText(this, "没有可播放的歌曲", Toast.LENGTH_SHORT).show();
            return;
        }
        // 终止旧的进度更新线程
        if (progressSyncThread != null && progressSyncThread.isAlive()) {
            progressSyncThread.interrupt();
            isProgressSyncRunning = false;
        }
        musicPlayer.setCompletionLegitimate(false);

        // 重置自动触发标志
        isAutoNextTriggered = false;

        // 计算下一首的位置
        RadioGroup radioGroup = findViewById(R.id.radiogroup);
        int checkedId = radioGroup.getCheckedRadioButtonId();
        int newPosition;

        if (checkedId == R.id.shuffled) {
            newPosition = new Random().nextInt(musicList.size());
        } else if (checkedId == R.id.singleendless) {
            newPosition = selectedPosition;
        } else {
            newPosition = (selectedPosition + 1) % musicList.size();
        }

        // 播放新歌曲
        selectedPosition = newPosition;
        playSongAt(selectedPosition);
    }

    private void moveSongUp(int position) {//字面意思
        if (musicList.isEmpty() || position <= 0 || position >= musicList.size()) {
            Toast.makeText(this, "无法上移", Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.swap(musicList, position, position - 1);
        updateSongIndices();
        ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
        selectedPosition = position - 1;
        listview.setItemChecked(selectedPosition, true);
        listview.smoothScrollToPosition(selectedPosition);
        updatePersistentStorage();
    }

    private void moveSongDown(int position) {
        if (musicList.isEmpty() || position < 0 || position >= musicList.size() - 1) {
            Toast.makeText(this, "无法下移", Toast.LENGTH_SHORT).show();
            return;
        }
        // 交换当前位置与后一项
        Collections.swap(musicList, position, position + 1);

        // 更新每一项的序号（"index"）
        updateSongIndices();

        // 通知适配器刷新列表
        ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();

        // 更新选中的位置
        selectedPosition = position + 1;
        listview.setItemChecked(selectedPosition, true);

        // 调用滚动方法，确保新的选中项在可见区域
        listview.smoothScrollToPosition(selectedPosition);

        // 如果需要更新持久化存储中的数据，可以在这里同步更新文件内容
        updatePersistentStorage();
    }

    // 遍历 musicList 更新每项的 index
    private void updateSongIndices() {
        for (int i = 0; i < musicList.size(); i++) {
            musicList.get(i).put("index", i + 1);
        }
    }

    private void updatePersistentStorage() {
        String currentPlaylist = this.currentPlaylist; // 确保你当前保存了选中的歌单名
        File file = MusicLoader.getMusicFile(this);
        List<String> allLines = new ArrayList<>();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                allLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            }
            List<String> kept = new ArrayList<>();
            for (String line : allLines) {
                if (!line.startsWith(currentPlaylist + ",")) {
                    kept.add(line);
                }
            }
            for (Map<String, Object> item : musicList) {
                String playlist = (String) item.get("playlist");
                String name = (String) item.get("name");
                String formattedTime = (String) item.get("TimeDuration");
                String filePath = (String) item.get("filePath");
                int duration = Song.parseTime(formattedTime);

                String line = playlist + "," + duration + "," + name + "," + filePath;
                kept.add(line);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.write(file.toPath(), kept, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Log.e(TAG, "更新存储失败", e);
        }
    }

    private void updateNavButtons() {
        boolean hasItems = !musicList.isEmpty();
        btnNext.setEnabled(hasItems);
        btnPrevious.setEnabled(hasItems);
    }

    //这个函数可能会用到，别删
    public void refreshPlaylist() {
        // 重新从文件加载最新数据
        musicList = MusicLoader.loadSongs(listview.getContext(), currentPlaylist);
        // 确保适配器更新
        if (listview != null) {
            BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
            listview.setAdapter(adapter);
            if (adapter != null) {
                adapter.setData(musicList); // 更新适配器数据源
                adapter.notifyDataSetChanged(); // 直接通知适配器更新
            } else {
                // 重新初始化适配器
                adapter = new BatchModeAdapter(
                        listview.getContext(),
                        musicList,
                        R.layout.playlist_layout,
                        new String[]{"index", "name", "TimeDuration", "isSelected"},
                        new int[]{R.id.seq, R.id.musicname, R.id.musiclength, R.id.cbSelect}
                );
                listview.setAdapter(adapter);
            }
        }

        // 滚动到最新项
        if (!musicList.isEmpty()) {
            listview.smoothScrollToPosition(musicList.size() - 1);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 处理“搜索并添加在线音乐”返回
        if (requestCode == REQUEST_CODE_SEARCH && resultCode == RESULT_OK) {
            if (data != null && data.getBooleanExtra("should_refresh", false)) {
                // 先把最新的 musicList 同步给 adapter 了
                BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
                if (adapter != null) {
                    adapter.setData(musicList);
                    adapter.notifyDataSetChanged();
                }
                // 更新“上一曲/下一曲”按钮状态
                boolean hasItems = !musicList.isEmpty();
                btnNext.setEnabled(hasItems);
                btnPrevious.setEnabled(hasItems);
                // 滚动到最新添加的那一项
                if (hasItems) {
                    listview.smoothScrollToPosition(musicList.size() - 1);
                }
            }
        }

        // 处理系统文件选择（单选或多选）返回
        if (requestCode == PICK_MUSIC_REQUEST && resultCode == RESULT_OK && data != null) {
            List<Uri> uriList = new ArrayList<>();

            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    uriList.add(uri);
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                uriList.add(uri);
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
            }

            // 调用已有的方法批量复制添加，并在内部完成
            if (!uriList.isEmpty()) {
                copyAndAddMusicFiles(uriList);
            }
        }
//
//        // 处理B站音乐输入对话框返回
//        if (requestCode == REQUEST_CODE_BILI && resultCode == RESULT_OK) {
//            String bvId = data.getStringExtra("bvId");
//            if (bvId != null && !bvId.isEmpty()) {
//                // 这里可以调用添加B站音乐的逻辑
//                addBiliMusic(bvId);
//            } else {
//                Toast.makeText(this, "无效的BV号", Toast.LENGTH_SHORT).show();
//            }
//        }
    }

    //去重，歌曲本地储存唯一标识用的是musicname，复选删除同名歌曲将影响播放状态，但没有实现，可修改
    private boolean isSongDuplicate(Song newSong, List<Song> existingSongs) {
        for (Song song : existingSongs) {
            // 标准化URI字符串
            String existingUri = Uri.parse(song.getFilePath()).normalizeScheme().toString();
            String newUri = Uri.parse(newSong.getFilePath()).normalizeScheme().toString();

            if (song.getName().equalsIgnoreCase(newSong.getName())
                    && existingUri.equals(newUri)) {
                return true;
            }
        }
        return false;
    }

    //获取文件名，这里可以改进一下，使其能够获取在线歌曲的metadata的信息，但是注意不要修改数据库的内容，inputsong.html也可以改进一下，或者可以添加本机
    //的上传服务，实现从本机上传音乐至tomcat
    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void showProgressDialog() {
        // 使用布局加载器加载自定义布局
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.progress_dialog, null);

        // 获取布局中的控件引用
        dialogProgressBar = dialogView.findViewById(R.id.progressBarDialog);
        dialogMessage = dialogView.findViewById(R.id.tvProgressMessage);

        // 默认设置进度为 0
        dialogProgressBar.setProgress(0);
        dialogProgressBar.setMax(100);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView)
                .setCancelable(false); // 设置不可取消，直到任务完成
        progressDialog = builder.create();
        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void copyAndAddMusicFiles(List<Uri> uris) {
        // 在UI线程中显示进度弹窗
        runOnUiThread(() -> showProgressDialog());

        new Thread(() -> {
            List<Song> newSongs = new ArrayList<>();
            AtomicInteger fileCounter = new AtomicInteger(0); // 添加计数器
            int totalFiles = uris.size();

            for (int i = 0; i < totalFiles; i++) {
                final int currentIndex = i; // 声明一个 final 的临时变量
                Uri uri = uris.get(i);
                try {
                    // 直接获取文件名和时长，不复制文件
                    String fileName = getFileName(uri);
                    String songName = fileName.replace(".mp3", "");
                    // 通过 Uri 获取时长
                    MediaPlayer mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(getApplicationContext(), uri);
                    mediaPlayer.prepare();
                    int duration = mediaPlayer.getDuration() / 1000; // 毫秒转秒
                    mediaPlayer.release();
                    // 直接使用 Uri 作为文件标识
                    Song newSong = new Song(duration, songName, uri.toString(), currentPlaylist);
                    if (!isSongDuplicate(newSong, newSongs)) {
                        newSongs.add(newSong);
                    }
                } catch (IOException e) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "添加失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                // 计算当前进度百分比
                final int progressPercent = (int) (((i + 1) / (float) totalFiles) * 100);
                runOnUiThread(() -> {
                    // 更新进度条
                    if (dialogProgressBar != null) {
                        dialogProgressBar.setProgress(progressPercent);
                    }
                    // 更新提示文字
                    if (dialogMessage != null) {
                        dialogMessage.setText("正在添加歌曲 " + (currentIndex + 1) + "/" + totalFiles);
                    }
                });
            }

            // 添加全部歌曲到列表和持久化存储，更新UI
            runOnUiThread(() -> {
                if (!newSongs.isEmpty()) {
                    for (Song song : newSongs) {
                        MusicLoader.appendMusic(this, song);
                        Map<String, Object> songMap = song.toMap(musicList.size() + 1);
                        songMap.put("isSelected", false);
                        musicList.add(songMap);
                    }
                    BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
                    if (adapter != null) {
                        adapter.setData(musicList);
                        updateSongIndices();
                        adapter.notifyDataSetChanged();
                    } else {
                        loadMusicList(listview, currentPlaylist);
                        updateNavButtons();
                    }
                    if (!musicList.isEmpty()) {
                        btnNext.setEnabled(true);
                        btnPrevious.setEnabled(true);
                    }
                    updatePlaylistCover(currentPlaylist);
                }

                //dismissProgressDialog();
                //toast一个
                if (dialogMessage != null) {
                    dialogMessage.setText("歌曲添加完成");
                }
                setResult(RESULT_OK);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    dismissProgressDialog();
                }, 800);
            });

        }).start();
    }

    private void updatePlaylistCover(String playlistName) {
        new Thread(() -> {
            String latestCoverPath = MusicLoader.getLatestCoverForPlaylist(
                    MainActivity.this, playlistName);

            SharedPreferences prefs = getSharedPreferences(
                    PlaylistListActivity.PREFS, MODE_PRIVATE);
            String playlistsJson = prefs.getString(
                    PlaylistListActivity.KEY_PLAYLISTS, null);
            if (latestCoverPath == null) {
                latestCoverPath = ""; // 避免 null
            }
            if (playlistsJson != null) {
                List<Playlist> playlists = Playlist.fromJson(playlistsJson);
                for (Playlist playlist : playlists) {
                    if (playlist.getName() != null && playlist.getName().equals(playlistName)) {
                        playlist.setLatestCoverPath(latestCoverPath);
                        break;
                    }
                }
                // 保存更新后的歌单列表
                String updatedJson = Playlist.toJson(playlists);
                prefs.edit().putString(
                        PlaylistListActivity.KEY_PLAYLISTS, updatedJson).apply();
                Log.d("MainActivity", "最新封面路径为: " + latestCoverPath);
                Log.d("MainActivity", "更新并保存封面成功: " + playlistName);
            }
        }).start();

    }

    //删除音乐，注意只是删除按钮的逻辑，复选框逻辑在初始化那块写了，闲得无聊可以合并一下
    private void deleteSelectedSong(int position) {

        // 检查索引有效性
        if (position < 0 || position >= musicList.size()) {
            Toast.makeText(this, "无效的删除位置", Toast.LENGTH_SHORT).show();
            return;
        }
        Song currentSong = musicPlayer.getCurrentSong();
        boolean isCurrentSongDeleted = currentSong != null &&
                Song.fromMap(musicList.get(position)).equals(currentSong);
        if (selectedPositions.contains(position)) {
            selectedPositions.remove((Integer) position);
        }

//         检查是否删除的是当前播放歌曲
//        boolean isCurrentSongDeleted = false;

        if (currentSong != null) {
            Map<String, Object> deletedSongMap = musicList.get(position);
            Song deletedSong = Song.fromMap(deletedSongMap);
            if (deletedSong.equals(currentSong)) {
                musicPlayer.stop();
                progressBar.setProgress(0);
                preogress.setText("0");
                isCurrentSongDeleted = true;
                playBtn.setEnabled(false);
            }
        }
        if (isCurrentSongDeleted) {
            // 终止进度同步线程
            if (progressSyncThread != null && progressSyncThread.isAlive()) {
                progressSyncThread.interrupt();
                isProgressSyncRunning = false;
                progressSyncThread = null;
            }

            // 彻底释放播放器并重新初始化
            musicPlayer.stop();
            musicPlayer.release();
            musicPlayer = ((MyApp) getApplication()).getMusicPlayer();
            musicPlayer.setProgressListener((currentPos, totalDuration) -> {
                if (currentPos >= 0 && totalDuration >= 0) {
                    runOnUiThread(() -> {
                        progressBar.setMax(totalDuration);
                        progressBar.setProgress(currentPos);
                        preogress.setText(formatTime(currentPos) + "/" + formatTime(totalDuration));
                    });
                }
            });
            song = new Song(0, "暂无歌曲", "", "");
            // 重置UI
            musicPlayer.resetProgress();
            runOnUiThread(() -> {
                progressBar.setProgress(0);
                preogress.setText("00:00");
                albumArt.setImageResource(R.drawable.default_cover);
                TextView currentSongTV = findViewById(R.id.currentSong);
                currentSongTV.setText("当前播放：暂无歌曲");
            });
        }

        // 从数据源中移除项
        musicList.remove(position);
        updatePersistentStorage();
        setResult(RESULT_OK);
        // 调整后续项的序号
//        for (int i = position; i < musicList.size(); i++) {
//            musicList.get(i).put("index", i + 1);
//        }
        updateSongIndices();
        updatePersistentStorage();
        // 刷新列表适配器
//        MusicLoader.deleteMusic(this, position);
        ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
        // 更新播放按钮状态
        if (isCurrentSongDeleted) {
            switchPlayStatus(PlayerStatus.STOPPED);
            playBtn.setText("播放");
        }

        // 重置选中状态
        selectedPosition = -1;
        listview.clearChoices();

        // 滚动到所选
        if (musicList.size() > 0) {
            int newPosition = Math.min(position, musicList.size() - 1);
            listview.smoothScrollToPosition(newPosition);
        }

        if (musicPlayer != null) {
            musicPlayer.release(); // 释放资源
            musicPlayer = ((MyApp) getApplication()).getMusicPlayer(); // 重新初始化
        }
        if (progressSyncThread != null && progressSyncThread.isAlive()) {
            progressSyncThread.interrupt();
            isProgressSyncRunning = false;
            progressSyncThread = null;
        }
        // 彻底释放当前播放器资源
        if (isCurrentSongDeleted) {
            musicPlayer.release();
            musicPlayer = ((MyApp) getApplication()).getMusicPlayer();
            musicPlayer.setProgressListener(new MusicPlayer.ProgressListener() {
                // 重新绑定进度监听
                @Override
                public void onProgressUpdated(int currentPosition, int totalDuration) {
                    runOnUiThread(() -> {
                        // 更新进度条逻辑
                        progressBar.setMax(totalDuration);
                        progressBar.setProgress(currentPosition);
                        preogress.setText(formatTime(currentPosition) + "/" + formatTime(totalDuration));
                    });
                }
            });
            runOnUiThread(() -> {
                listview.clearChoices();
                listview.requestLayout(); // 强制刷新视图
            });
        }

        // 如果删完了列表中的歌曲，初始化播放器UI状态，停止线程必要性看看
        if (musicList.isEmpty()) {
            TextView currentSongTV = findViewById(R.id.currentSong);
            playBtn.setEnabled(false);
            progressBar.setProgress(0);
            preogress.setText("0");
            musicPlayer.setCurrentPositiontozero();
            btnPrevious.setEnabled(false);
            btnNext.setEnabled(false);
            albumArt.setImageResource(R.drawable.default_cover);

            currentSongTV.setText("当前播放: 暂无歌曲");
        }

        albumArt.setImageResource(R.drawable.default_cover);
    }

    //这个地方需要修改，增加一下一键删除的单选
    private void showDeleteConfirmationDialog() {
        // 动态创建包含复选框的布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10); // 调整内边距

        // 添加消息文本
        TextView message = new TextView(this);
        message.setText("确定要删除这首歌曲吗？");
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        message.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        layout.addView(message);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除")
                .setView(layout) // 设置动态创建的布局
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteSelectedSong(selectedPosition); // 传递复选框状态
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void loadMusicList(ListView listview, String playlist) {
        musicList = MusicLoader.loadSongs(this, playlist);
        for (Map<String, Object> item : musicList) {
            item.put("isSelected", false);
        }

        // 单例模式初始化适配器
        BatchModeAdapter adapter = null;
        if (listview.getAdapter() == null) {
            adapter = new BatchModeAdapter(
                    this,
                    musicList,
                    R.layout.playlist_layout,
                    new String[]{"index", "name", "TimeDuration", "isSelected"},
                    new int[]{R.id.seq, R.id.musicname, R.id.musiclength, R.id.cbSelect}
            );
            listview.setAdapter(adapter);
        } else {
            adapter.setData(musicList);
            ((BatchModeAdapter) listview.getAdapter()).notifyDataSetChanged();
        }
    }

    // 验证文件有效性
    private boolean isFileValid(String filePath) {
        if (filePath.startsWith("http")) {
            return true;
        }
        if (filePath.startsWith("content://")) {
            // 处理 Content URI
            try (Cursor cursor = getContentResolver().query(
                    Uri.parse(filePath), null, null, null, null)) {
                return cursor != null && cursor.getCount() > 0;
            } catch (Exception e) {
                return false;
            }
        } else {
            // 处理普通文件路径
            return new File(filePath).exists();
        }
    }

    private void playSongAt(int index) throws IOException {
        if (index < 0 || index >= musicList.size()) {
            Toast.makeText(this, "无效的歌曲位置", Toast.LENGTH_SHORT).show();
            return;
        }
        isAutoNextTriggered = false; // 重置自动触发标志
        isAutoTriggeredByCompletion = true;
        isSongChanging = true;
        if (musicPlayer.isPlaying() || musicPlayer.isPaused()) {
            musicPlayer.stop();
        }

        // 获取歌曲对象
        Map<String, Object> songMap = musicList.get(index);
        Song songToPlay = Song.fromMap(songMap);
        // 检查文件是否存在
        if (!isFileValid(songToPlay.getFilePath())) {
            runOnUiThread(() -> {
                Toast.makeText(this, "歌曲文件不存在或无法访问", Toast.LENGTH_SHORT).show();
                // 从列表中移除无效项TODO
//                musicList.remove(index);
                ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
            });
            return;
        }

        // 加载并播放歌曲
        musicPlayer.loadMusic(songToPlay);
        musicPlayer.setCurrentPositiontozero();
        ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();

        // 更新ListView的选中状态
        listview.setItemChecked(index, true);
        listview.smoothScrollToPosition(index);

        // 更新UI
        runOnUiThread(() -> {
            playBtn.setEnabled(true);
            progressBar.setMax(songToPlay.getTimeDuration());
            progressBar.setProgress(0);
            preogress.setText("0");
            playBtn.setText("暂停");
        });

        // 更新当前播放歌曲，并加载封面
        song = songToPlay;
        selectSong = songToPlay;
        loadMusicCover(songToPlay.getFilePath());
        TextView currentSongTV = findViewById(R.id.currentSong);
        currentSongTV.setText(songToPlay.getName());


        // 终止旧的进度同步线程，并启动新的线程
        if (progressSyncThread != null && progressSyncThread.isAlive()) {
            isProgressSyncRunning = false;
            progressSyncThread.interrupt();
            try {
                progressSyncThread.join(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        progressSyncThread = new Thread(new ProgressSync());
        progressSyncThread.start();
        isSongChanging = false;
        musicPlayer.setCompletionLegitimate(true);
    }

    //下面的上一首按钮与下一首按钮将来有可能调整UI的时候会删除
    public void next(View view) throws IOException {
        isAutoNextTriggered = true;
        RadioGroup radioGroup = findViewById(R.id.radiogroup);
        int checkedId = radioGroup.getCheckedRadioButtonId();
        int nextPos;

        if (musicList.isEmpty()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkedId == R.id.singleendless) {
            nextPos = selectedPosition;
        } else if (checkedId == R.id.shuffled) {
            nextPos = new Random().nextInt(musicList.size());
        } else {
            nextPos = (selectedPosition + 1) % musicList.size();
        }

        selectedPosition = nextPos;
        Map<String, Object> map = musicList.get(nextPos);
        selectSong = Song.fromMap(map);

        if (!selectSong.equals(song)) {
            playSongAt(nextPos);
            song = selectSong;

            TextView currentSongTV = findViewById(R.id.currentSong);
            currentSongTV.setText(selectSong.getName());
            playBtn.setEnabled(true);
            musicPlayer.setCurrentPositiontozero();
            loadMusicCover(selectSong.getFilePath());

            isResettingProgress = true;
            progressBar.setMax(selectSong.getTimeDuration());
            progressBar.setProgress(0);
            preogress.setText("0");
            playBtn.setText("暂停");
            isResettingProgress = false;

            if (progressSyncThread != null && progressSyncThread.isAlive()) {
                isProgressSyncRunning = false;
                progressSyncThread.interrupt();
            }
            new Thread(new ProgressSync()).start();
        }
    }

    //上一首按钮处理逻辑
    public void previous(View view) throws IOException {
        isAutoNextTriggered = true;
        RadioGroup radioGroup = findViewById(R.id.radiogroup);
        int currentPos = selectedPosition;
        int prePos;

        if (musicList.isEmpty()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = radioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.singleendless) {
            prePos = currentPos;
        } else if (checkedId == R.id.shuffled) {
            prePos = new Random().nextInt(musicList.size());
        } else {
            prePos = currentPos == 0 ? musicList.size() - 1 : currentPos - 1;
        }

        selectedPosition = prePos;
        Map<String, Object> map = musicList.get(prePos);
        selectSong = Song.fromMap(map);

        if (!selectSong.equals(song)) {
            playSongAt(prePos);
            song = selectSong;

            TextView currentSongTV = findViewById(R.id.currentSong);
            currentSongTV.setText(selectSong.getName());
            playBtn.setEnabled(true);
            musicPlayer.setCurrentPositiontozero();
            loadMusicCover(selectSong.getFilePath());

            isResettingProgress = true;
            progressBar.setMax(selectSong.getTimeDuration());
            progressBar.setProgress(0);
            preogress.setText("0");
            playBtn.setText("暂停");
            isResettingProgress = false;

            if (progressSyncThread != null && progressSyncThread.isAlive()) {
                isProgressSyncRunning = false;
                progressSyncThread.interrupt();
            }
            new Thread(new ProgressSync()).start();
        }
    }

    //更改歌曲播放状态
    private void switchPlayStatus(PlayerStatus status) {
        switch (status) {
            case PLAYING:
                playBtn.setText("暂停");
                musicPlayer.play();
                break;
            case PAUSED:
                playBtn.setText("播放");
                musicPlayer.pause();
                break;
            case STOPPED:
                playBtn.setText("播放");
                musicPlayer.stop();
                // 注意，接下来修改的时候不要在这里重置进度条，由播放器回调处理
                break;
        }
    }

    private void showBVInputDialog(myCallback<String> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("从BV号 / 链接添加");

        EditText input = new EditText(this);
        input.setHint("可输入BV号 / AV号 / 分享的链接");
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String bv = input.getText().toString().trim();
            if (bv.isEmpty()) {
                Toast.makeText(this, "不能为空", Toast.LENGTH_SHORT).show();
            } else {
                // 如果是bv或av直接返回
                if (bv.matches("BV[0-9A-Za-z]{10}") || bv.matches("av\\d+")) {
                    callback.onResult(bv); // 调用回调传递
                    dialog.dismiss();
                } else if (bv.contains("https://b23.tv/")) {
                    // 手机端分享，先提取出短链
                    Pattern pattern = Pattern.compile("https://b23\\.tv/\\S+");
                    Matcher matcher = pattern.matcher(bv);
                    if (matcher.find()) {
                        bv = matcher.group();  // 找到第一个短链
                    }
                    // 发送HEAD请求得到重定向的链接
                    OkHttpClient client = new OkHttpClient.Builder()
                            .followRedirects(false)  // 关键点：不要自动跟随重定向
                            .build();

                    Request request = new Request.Builder()
                            .url(bv)
                            .head() // 只取 Header，不要正文
                            .build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Log.e("ShortLink", "请求失败: " + e.getMessage());
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.code() == 301 || response.code() == 302) {
                                String redirectUrl = response.header("Location");

                                // 提取 BV 号
                                Pattern pattern = Pattern.compile("BV[0-9A-Za-z]+");
                                Matcher matcher = pattern.matcher(redirectUrl);
                                if (matcher.find()) {
                                    String bvid = matcher.group();
                                    Log.d("ShortLink", "提取出的 BV 号: " + bvid);
                                    callback.onResult(bvid); // 调用回调传递
                                }
                            } else {
                                Log.e("ShortLink", "不是重定向: " + response.code());
                            }
                        }
                    });

                    dialog.dismiss();
                } else if (bv.startsWith("https://www.bilibili.com/video/")) {
                    // 电脑端链接，直接提取bv号
                    Pattern pattern = Pattern.compile("BV[0-9A-Za-z]+");
                    Matcher matcher = pattern.matcher(bv);
                    if (matcher.find()) {
                        String bvid = matcher.group();
                        Log.d("ShortLink", "提取出的 BV 号: " + bvid);
                        callback.onResult(bvid); // 调用回调传递
                    }
                } else {
                    Toast.makeText(this, "无效的BV号格式", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            callback.onResult(null); // 取消则返回 null
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void getBiliMusic(String bv, Context context, myCallback<Map<String, Object>> callback) {
        OkHttpClient client = new OkHttpClient();

        // 获取音频流要av/bv的同时还要cid
        // Step 1: 获取 CID
        Request cidRequest = new Request.Builder()
                .url("https://api.bilibili.com/x/player/pagelist?bvid=" + bv)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();

        client.newCall(cidRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("BiliMusic", "onFailure: " + e);
            }

            @Override
            public void onResponse(Call call, Response res) throws IOException {
                if (!res.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(context, "cid请求失败: " + res.code(), Toast.LENGTH_SHORT).show());
                    return;
                }

                // 转成json取得cid
                String str = res.body().string();
                JsonObject json = JsonParser.parseString(str).getAsJsonObject().get("data").getAsJsonArray().get(0).getAsJsonObject();

                long cid = json.get("cid").getAsLong();
                Log.i("BiliMusic", "获取到的CID: " + cid);

                // 顺带获取标题，用于文件名。但文件名还是bv号，防止文件名异常
                String title = json.get("part").getAsString();

                // Step 2: 获取音频 URL
                HttpUrl url = HttpUrl.parse("https://api.bilibili.com/x/player/playurl").newBuilder()
                        .addQueryParameter("bvid", bv)
                        .addQueryParameter("cid", String.valueOf(cid))
                        .addQueryParameter("fnval", "16")
                        .build();

                Request audioUrlReq = new Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .addHeader("Referer", "https://www.bilibili.com/")
                        .build();

                client.newCall(audioUrlReq).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> Toast.makeText(context, "获取音频URL失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            runOnUiThread(() -> Toast.makeText(context, "音频URL请求失败: " + response.code(), Toast.LENGTH_SHORT).show());
                            return;
                        }

                        // 请求dash，分离视频和音频流
                        String json = response.body().string();
                        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                        JsonArray audioArray = obj.get("data").getAsJsonObject()
                                .get("dash").getAsJsonObject()
                                .get("audio").getAsJsonArray();

                        // 防止没有高质量音频
                        String audioUrl;
                        if (audioArray.size() > 2) {
                            audioUrl = audioArray.get(2).getAsJsonObject().get("baseUrl").getAsString();
                        } else if (audioArray.size() > 1) {
                            audioUrl = audioArray.get(1).getAsJsonObject().get("baseUrl").getAsString();
                        } else {
                            audioUrl = audioArray.get(0).getAsJsonObject().get("baseUrl").getAsString();
                        }
                        Log.i("BiliMusic", "获取到的音频URL: " + audioUrl);

                        // Step 3: 下载音频文件
                        Request downloadReq = new Request.Builder()
                                .url(audioUrl)
                                .addHeader("User-Agent", "Mozilla/5.0")
                                .addHeader("Referer", "https://www.bilibili.com/")
                                .build();

                        client.newCall(downloadReq).enqueue(new Callback() {
                            @Override
                            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                runOnUiThread(() -> Toast.makeText(context, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            }

                            @Override
                            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                // 存的文件名还是bv号
                                File file = new File(context.getExternalFilesDir("audio"), bv + ".mp4");
                                Log.i("BiliMusic", "下载文件路径: " + file.getAbsolutePath());
                                try (InputStream in = response.body().byteStream();
                                     FileOutputStream out = new FileOutputStream(file)) {

                                    byte[] buffer = new byte[4096];
                                    int len;
                                    while ((len = in.read(buffer)) != -1) {
                                        out.write(buffer, 0, len);
                                    }

                                    // 把文件和标题放一起返回
                                    Map<String, Object> result = new HashMap<>();
                                    result.put("file", file);
                                    result.put("title", title);

                                    callback.onResult(result);
                                } catch (IOException e) {
                                    Log.e("BiliMusic", "下载文件失败: ", e);
                                    callback.onResult(null); // 获取失败
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private class MusicListItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 500) {
                return; // 防止快速点击
            }
            lastClickTime = currentTime;
            if (progressSyncThread != null && progressSyncThread.isAlive()) {
                isProgressSyncRunning = false;
                progressSyncThread.interrupt(); // 强制中断线程
            }
            ListView lv = (ListView) parent;
            Map<String, Object> map = (Map<String, Object>) lv.getAdapter().getItem(position);
            selectSong = Song.fromMap(map);
            selectedPosition = position;

            if (!selectSong.equals(song)) {//!selectSong.getName().equals(song.getName())
                try {
//                    musicPlayer.loadMusic(selectSong);
                    playSongAt(position);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                song = selectSong;
                TextView currentSongTV = findViewById(R.id.currentSong);
                // 更新 UI
                currentSongTV.setText(selectSong.getName());
                playBtn.setEnabled(true);
                musicPlayer.setCurrentPositiontozero();
                loadMusicCover(selectSong.getFilePath());

                isResettingProgress = true;
                // 重新设置进度条最大值和归零
                progressBar.setMax(selectSong.getTimeDuration());
                progressBar.setProgress(0);// 进度条归零
                // 更新进度文本
                preogress.setText("0");
                playBtn.setText("暂停");
                isResettingProgress = false;

            } else {
                if (musicPlayer.isPlaying()) {
                    switchPlayStatus(PlayerStatus.PAUSED);
                    playBtn.setText("播放");
                } else if (musicPlayer.isPaused()) {
                    switchPlayStatus(PlayerStatus.PLAYING);
                    playBtn.setText("暂停");
                } else {
                    try {
                        playSongAt(position);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
//            song = selectSong;
            boolean isCurrentSong = selectSong.equals(song);
            if (!isCurrentSong) {
                ((BaseAdapter) lv.getAdapter()).notifyDataSetChanged();
                RadioGroup radioGroup = findViewById(R.id.radiogroup);
                if (radioGroup.getCheckedRadioButtonId() != R.id.shuffled) {
                    if (position <= lv.getChildCount() - 2) {
                        listview.setSelection(0);
                    } else if (position - lv.getFirstVisiblePosition() <= 0) {
                        listview.setSelection(position - (lv.getChildCount() - 2));
                    }
//                else if(position-lv.getFirstVisiblePosition() <= 0){
//                    listview.setSelection(position);
//                }
                } else {
                    listview.setSelection(position);
                }
                playBtn.setText("暂停");
                new Thread(new ProgressSync()).start();
            }
        }

    }

    private class ProgressSync implements Runnable {
        @Override
        public void run() {
            Log.d("ProgressSync", "线程启动");
            isProgressSyncRunning = true;
            try {
                while (!Thread.currentThread().isInterrupted() && isProgressSyncRunning) {
                    // 检查 MediaPlayer 是否已释放
                    if (musicPlayer.isStop() || musicPlayer.isReleased()) { //这段代码加上是为了防止吃内存导致缺页
                        break;
                    }
                    // 使用 musicPlayer 的公共方法获取数据，这里我已创建单例
                    int currentPosition = musicPlayer.getCurrentPosition();
                    int totalDuration = musicPlayer.getDuration();

                    runOnUiThread(() -> {
                        if (musicPlayer.isStop()) return;
                        progressBar.setMax(totalDuration);
                        progressBar.setProgress(currentPosition);
                        preogress.setText(formatTime(currentPosition) + "/" + formatTime(totalDuration));

//                        // 自动切换逻辑
//                        if (currentPosition >= totalDuration - 50
//                                && !isSongChanging
//                                && !isAutoNextTriggered
//                                && musicPlayer.getPlayStatus() == PlayerStatus.PLAYING
//                                && !musicList.isEmpty()) {
//                            isAutoNextTriggered = true;
//                            Log.d("MainActivity", "自动切歌");
//                            try {
//                                playNextSong();
//                            } catch (IOException e) {
//                                throw new RuntimeException(e);
//                            }
//                        }
                    });
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isProgressSyncRunning = false;
            }
        }
    }

}

