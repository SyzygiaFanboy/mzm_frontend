package com.example.myapplication;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.adapter.BatchModeAdapter;
import com.example.myapplication.adapter.BiliVideoAdapter;
import com.example.myapplication.model.MusicViewModel;
import com.example.myapplication.model.Song;
import com.example.myapplication.utils.ImageCacheManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
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
    // private List<Map<String, Object>> musicList = new ArrayList<>(); //
    // 确保非空//这里因为listitem是私有变量，得先创建个全局的先，，，
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
    private long lastNextClickTime = 0;
    private long lastPreviousClickTime = 0;
    private boolean isProcessingNext = false;
    private boolean isProcessingPrevious = false;
    private volatile boolean isResettingProgress = false;
    private volatile boolean shouldTerminate = false;
    private boolean isAutoNextTriggered = false;
    private volatile String currentCoverPath;
    private static final int PICK_MUSIC_REQUEST = 1;
    private String currentPlaylist;
    private boolean isAutoTriggeredByCompletion = false;
    // 进度加载弹窗的成员变量
    private AlertDialog progressDialog;
    private ProgressBar dialogProgressBar;
    private TextView dialogMessage;

    private static final int REQUEST_CODE_STORAGE_PERMISSION = 100;
    private static final int REQUEST_CODE_SEARCH = 1001; // 请求码

    private AtomicBoolean isSyncActive = new AtomicBoolean(false);

    // 添加B站音乐的回调接口
    public interface myCallback<T> {
        void onResult(T result);
    }

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

        // 添加到第一个并更新索引
        musicList.add(0, song.toMap(1));
        updateSongIndices();

        Log.d("PathCheck", "当前路径: " + song.getFilePath());
        for (Map<String, Object> item : musicList) {
            Log.d("PathCheck", "已存在路径: " + item.get("filePath"));
        }
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 获取全局MusicPlayer实例
        musicPlayer = ((MyApp) getApplication()).getMusicPlayer();
        musicPlayer.setOnSongCompletionListener(this);

        // 应用播放页面背景
        applyPlaybackBackground();

        // 检查是否有歌曲正在播放
        Song currentPlayingSong = musicPlayer.getCurrentSong();
        boolean isCurrentlyPlaying = musicPlayer.isPlaying() || musicPlayer.isPaused();

        // 如果没有歌曲在播放，加载默认歌曲
        if (currentPlayingSong == null) {
            Song defaultSong = new Song(0, "暂无歌曲", "", "");
            try {
                musicPlayer.loadMusic(defaultSong);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            song = defaultSong;
        } else {
            // 如果有歌曲在播放，保持当前状态
            song = currentPlayingSong;
        }

        // 初始化UI组件
        playBtn = findViewById(R.id.playerbutton);
        preogress = findViewById(R.id.textpreogress);
//        Button deletebtn = findViewById(R.id.removeMusic);
        ImageButton btnMoveUp = findViewById(R.id.btnMoveUp);
        ImageButton btnMoveDown = findViewById(R.id.btnMoveDown);
        ImageButton btnBatchSelect = findViewById(R.id.btnBatchSelect);
        btnNext = findViewById(R.id.next);
        btnPrevious = findViewById(R.id.previous);
        progressBar = findViewById(R.id.progressBar);
        listview = findViewById(R.id.playlist);
        albumArt = findViewById(R.id.albumArt);

        listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listview.setOnItemClickListener(new MusicListItemClickListener());

        // 根据当前播放状态设置UI
        if (isCurrentlyPlaying) {
            playBtn.setEnabled(true);
            playBtn.setText(musicPlayer.isPlaying() ? "暂停" : "播放");
            progressBar.setMax(song.getTimeDuration());
            // 保持当前进度
            TextView currentSongTV = findViewById(R.id.currentSong);
            currentSongTV.setText(song.getName());
            // 修复：直接使用当前播放歌曲的coverUrl
            loadMusicCover(song.getFilePath(), song.getCoverUrl());
        } else {
            playBtn.setEnabled(false);
            playBtn.setText("播放");
            progressBar.setMax(song.getTimeDuration());
            progressBar.setProgress(0);
        }

        MusicViewModel viewModel = new ViewModelProvider(this).get(MusicViewModel.class);

        // 获取要显示的歌单
        currentPlaylist = getIntent().getStringExtra("playlist");
        if (currentPlaylist == null)
            currentPlaylist = "默认歌单";

        // 加载歌单但不影响播放状态
        loadMusicList(listview, currentPlaylist);

        if (musicList.isEmpty()) {
            btnNext.setEnabled(false);
            btnPrevious.setEnabled(false);
        }

        updateNavButtons();

        // 左上角的返回按钮 toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> {
            getOnBackPressedDispatcher().onBackPressed(); // 触发系统的返回操作，效果与按下返回键相同
        });

        playBtn.setOnClickListener(v -> {
            if (musicPlayer.isPlaying()) {
                switchPlayStatus(PlayerStatus.PAUSED);
            } else {
                if (musicPlayer.getPlayStatus() == PlayerStatus.STOPPED) {
                    try {
                        musicPlayer.loadMusic(song);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                switchPlayStatus(PlayerStatus.PLAYING);
            }
        });

        // // 退出按钮监听
        // findViewById(R.id.menu_logout).setOnClickListener(v -> {
        // // 停止播放并释放播放器资源
        // if (musicPlayer != null) {
        // musicPlayer.stop();
        // musicPlayer.release();
        // }
        //
        // // 终止进度，同时同步线程
        // if (progressSyncThread != null && progressSyncThread.isAlive()) {
        // progressSyncThread.interrupt();
        // isProgressSyncRunning = false;
        // }
        //
        // SharedPreferences preferences = getSharedPreferences("user_pref",
        // MODE_PRIVATE);
        // preferences.edit().putBoolean("is_logged_in", false).apply();
        // startActivity(new Intent(this, LoginActivity.class));
        // finish();
        // });
        // DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        // ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
        // this,
        // drawerLayout,
        // toolbar,
        // R.string.navigation_drawer_open,
        // R.string.navigation_drawer_close
        // );
        // drawerLayout.addDrawerListener(toggle);
        // toggle.syncState();
        // // 隐藏 ActionBar 标题
        // if (getSupportActionBar() != null) {
        // getSupportActionBar().setDisplayShowTitleEnabled(false);
        // }

        // 请求存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 构造一个标准的权限管理 Intent，不增加多余的类别和类型
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                // 启动权限请求界面，只需要一个 requestCode
                startActivityForResult(intent, REQUEST_CODE_STORAGE_PERMISSION);
            }
        }
        // findViewById(R.id.menu_search).setOnClickListener(v -> {
        // Intent intent = new Intent(MainActivity.this, SearchActivity.class);
        // startActivityForResult(intent, REQUEST_CODE_SEARCH); // 使用
        // startActivityForResult
        // drawerLayout.closeDrawer(GravityCompat.START);
        // });

        // musicPlayer = new MusicPlayer(getApplicationContext());
        // musicPlayer.setOnSongCompletionListener(this);

        btnBatchSelect.setOnClickListener(v -> {
            isBatchMode = !isBatchMode;
            // 初始化适配器时设置监听
            BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
            if (adapter != null) {
                adapter.setBatchMode(isBatchMode);
            }
            // spqqxkz;
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
                findViewById(R.id.btnMoveUp).setVisibility(View.VISIBLE);
                findViewById(R.id.btnMoveDown).setVisibility(View.VISIBLE);
                findViewById(R.id.btnBatchDelete).setVisibility(View.VISIBLE); // 显示批量删除按钮
            } else {
                findViewById(R.id.btnMoveUp).setVisibility(View.GONE);
                findViewById(R.id.btnMoveDown).setVisibility(View.GONE);
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
            Song currentSong = musicPlayer.getCurrentSong(); // 这里用一个for循环而不是直接将已选择的currentsong传入将会增加运行时间，但是一时半会他也想不出更好的判读办法了
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
                    preogress.setText("请选择歌曲");
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
            positionsToDelete.sort(Collections.reverseOrder());
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
            findViewById(R.id.btnMoveUp).setVisibility(View.VISIBLE);
            findViewById(R.id.btnMoveDown).setVisibility(View.VISIBLE);
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
                    // musicPlayer.seekTo(progress);
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
                        onSongCompleted();// 回调
                    });
                }

                // 重新启动进度同步线程
                if (!isProgressSyncRunning) {
                    new Thread(new ProgressSync()).start();
                }
            }

        });

        btnMoveUp.setOnClickListener(v -> {
            if (selectedPosition != -1 && selectedPosition > 0) {
                // 上移逻辑
                Collections.swap(musicList, selectedPosition, selectedPosition - 1);
                selectedPosition--;

                // 更新歌曲索引
                updateSongIndices();

                // 刷新适配器
                ((BatchModeAdapter) listview.getAdapter()).notifyDataSetChanged();

                // 关键：设置新的选中状态
                listview.setItemChecked(selectedPosition, true);

                // 滚动到新位置
                listview.smoothScrollToPosition(selectedPosition);

                // 更新持久化存储
                updatePersistentStorage();
            }
        });

        btnMoveDown.setOnClickListener(v -> {
            if (selectedPosition != -1 && selectedPosition < musicList.size() - 1) {
                // 下移逻辑
                Collections.swap(musicList, selectedPosition, selectedPosition + 1);
                selectedPosition++;

                // 更新歌曲索引
                updateSongIndices();

                // 刷新适配器
                ((BatchModeAdapter) listview.getAdapter()).notifyDataSetChanged();

                // 关键：设置新的选中状态
                listview.setItemChecked(selectedPosition, true);

                // 滚动到新位置
                listview.smoothScrollToPosition(selectedPosition);

                // 更新持久化存储
                updatePersistentStorage();
            }
        });
//        deletebtn.setOnClickListener(v -> {
//            if (selectedPosition == -1) {
//                Toast.makeText(MainActivity.this, "请先选择要删除的歌曲", Toast.LENGTH_SHORT).show();
//                return;
//            }
//            showDeleteConfirmationDialog();
//        });

        // 添加按钮
        ImageButton Addbtn = findViewById(R.id.addMusic);
        Addbtn.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("选择添加方式");

            // 设置选项 - 添加"搜索在线音乐"选项
            String[] options = {"从本地添加", "从BV号 / 链接添加", "从B站收藏夹添加", "搜索在线音乐"};
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
                    showBiliDialog(bv -> {
                        // 按取消返回 null
                        if (bv == null) {
                            return;
                        }

                        try {
                            // 进度对话框
                            runOnUiThread(() -> {
                                showProgressDialog();
                                dialogProgressBar.setIndeterminate(true); // 设置为不确定进度模式
                                dialogMessage.setText("正在获取B站音频...");
                            });

                            // 获取音频流函数
                            getBiliMusic(bv, this, result -> runOnUiThread(() -> {
                                if (result != null) {
                                    String title = (String) result.get("title");
                                    File f = (File) result.get("file");
                                    String coverUrl = (String) result.get("coverUrl");
                                    String path = f.getAbsolutePath();

                                    // 获取文件信息，用于转化成song
                                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                    retriever.setDataSource(path);
                                    String durationStr = retriever
                                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                                    int duration = Integer.parseInt(durationStr) / 1000;

                                    // 封装、添加到歌单
                                    Song newSong = new Song(duration, title, path, currentPlaylist);
                                    newSong.setCoverUrl(coverUrl); // 设置封面URL
                                    addSongToPlaylist(newSong);
                                    updatePlaylistCover(currentPlaylist);
                                    MusicLoader.appendMusic(this, newSong);
                                    ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
                                    updateNavButtons(); // 添加这行来启用按钮
                                    Toast.makeText(MainActivity.this, "已添加到歌单", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "获取B站音乐失败", Toast.LENGTH_SHORT).show();
                                    Log.e("BiliMusic", "获取B站音乐失败，bv: " + bv);
                                }
                                dismissProgressDialog();
                            }));
                        } catch (Exception e) {
                            runOnUiThread(this::dismissProgressDialog);
                            Toast.makeText(MainActivity.this, "无效的输入：" + bv, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else if (which == 2) {
                    // 添加B站收藏夹音乐
                    showBiliCollectionDialog(list -> runOnUiThread(() -> {
                        if (list == null) {
                            return;
                        }
                        if (list.isEmpty()) {
                            Toast.makeText(MainActivity.this, "未获取到收藏夹信息", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // 进度对话框
                        AtomicInteger successCnt = new AtomicInteger();
                        AtomicInteger failCnt = new AtomicInteger();
                        runOnUiThread(() -> {
                            showProgressDialog();
                            dialogProgressBar.setIndeterminate(false); // 设置为确定进度模式
                            dialogProgressBar.setProgress(0);
                            dialogMessage.setText("正在获取B站音频...");
                        });

                        for (String bv : list) {
                            getBiliMusic(bv, this, result -> runOnUiThread(() -> {
                                if (result != null && !result.isEmpty()) {
                                    String title = (String) result.get("title");
                                    File f = (File) result.get("file");
                                    String coverUrl = (String) result.get("coverUrl");
                                    String path = f.getAbsolutePath();

                                    // 获取文件信息，用于转化成song
                                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                                    retriever.setDataSource(path);
                                    String durationStr = retriever
                                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                                    int duration = Integer.parseInt(durationStr) / 1000;

                                    // 封装、添加到歌单
                                    Song newSong = new Song(duration, title, path, currentPlaylist);
                                    newSong.setCoverUrl(coverUrl); // 设置封面URL
                                    addSongToPlaylist(newSong);
                                    updatePlaylistCover(currentPlaylist);
                                    MusicLoader.appendMusic(this, newSong);
                                    ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
                                    updateNavButtons(); // 添加这行来启用按钮

                                    // 更新进度条
                                    successCnt.getAndIncrement();
                                    int progress = successCnt.get() * 100 / list.size();
                                    dialogProgressBar.setProgress(progress);
                                    dialogMessage.setText("正在获取B站音频... (" + successCnt.get() + "/" + list.size() + ")");
                                } else {
                                    failCnt.getAndIncrement();
                                    dismissProgressDialog();
                                    Toast.makeText(MainActivity.this, "获取B站音乐失败", Toast.LENGTH_SHORT).show();
                                    Log.e("BiliCollection", "返回值为空，bv: " + bv);
                                }

                                // 放在回调里面，不然显示不出来
                                if (successCnt.get() >= list.size()) {
                                    dismissProgressDialog();
                                    Toast.makeText(MainActivity.this, "已添加到歌单", Toast.LENGTH_SHORT).show();
                                }
                            }));
                            // 检查是否已经失败并退出
                            if (failCnt.get() > 0) {
                                runOnUiThread(this::dismissProgressDialog);
                                break;
                            }
                        }
                    }));
                } else if (which == 3) {
                    // 搜索在线音乐
                    Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                    intent.putExtra("current_playlist", currentPlaylist); // 传递当前歌单名称
                    intent.putExtra("from_activity", "MainActivity"); // 标记来源页面
                    startActivityForResult(intent, REQUEST_CODE_SEARCH);
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
        initBottomPlayerBar();
    }

    // 回调
    @Override
    public void onSongCompleted() {
        Log.d("MainActivity", "收到播放完成回调");
        runOnUiThread(() -> {
            try {
                if (!musicList.isEmpty()) {
                    playNextSong();
                    new Handler().postDelayed(() -> {
                        updateAllPlayButtons();
                    }, 100); // 延迟100ms确保播放状态已更新
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void loadMusicCover(String musicFilePath) {
        // 获取当前歌曲的封面URL
        String coverUrl = null;
        if (selectSong != null) {
            coverUrl = selectSong.getCoverUrl();
        }

        // 使用智能加载方法
        MusicCoverUtils.loadCoverSmart(musicFilePath, coverUrl, this, albumArt);
    }

    // 添加一个重载方法
    private void loadMusicCover(String musicFilePath, String coverUrl) {
        MusicCoverUtils.loadCoverSmart(musicFilePath, coverUrl, this, albumArt);
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
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

        // 清理图片缓存
        clearImageCache();

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

    // 添加缓存管理方法
    private void showCacheInfo() {
        ImageCacheManager cacheManager = ImageCacheManager.getInstance(this);
        String info = cacheManager.getCacheInfo();
        Toast.makeText(this, "缓存信息: " + info, Toast.LENGTH_LONG).show();
    }

    private void clearImageCache() {
        ImageCacheManager cacheManager = ImageCacheManager.getInstance(this);
        cacheManager.clearCache();
        Toast.makeText(this, "图片缓存已清理", Toast.LENGTH_SHORT).show();
    }

    // 字面意思
    private void toggleBatchMode() {
        isBatchMode = !isBatchMode;
        BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
        adapter.setBatchMode(isBatchMode);
        cbSelectAll.setVisibility(isBatchMode ? View.VISIBLE : View.GONE);
        cbSelectAll.setChecked(false);

        // 修改：非管理模式下显示上移下移按钮，管理模式下隐藏
        findViewById(R.id.btnMoveUp).setVisibility(isBatchMode ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnMoveDown).setVisibility(isBatchMode ? View.GONE : View.VISIBLE);
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
        updateAllPlayButtons();
    }

    private void moveSongUp(int position) {// 字面意思
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
        // if (selectedPosition >= 0 && selectedPosition < musicList.size()) {
        // Map<String, Object> selectedSongMap = musicList.get(selectedPosition);
        // selectSong = Song.fromMap(selectedSongMap); // 更新成员变量
        // loadMusicCover(selectSong.getFilePath());
        // }
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
        // if (selectedPosition >= 0 && selectedPosition < musicList.size()) {
        // Map<String, Object> selectedSongMap = musicList.get(selectedPosition);
        // selectSong = Song.fromMap(selectedSongMap); // 更新成员变量
        // loadMusicCover(selectSong.getFilePath());
        // }
    }

    // 遍历 musicList 更新每项的 index
    private static void updateSongIndices() {
        for (int i = 0; i < musicList.size(); i++) {
            musicList.get(i).put("index", i + 1);
        }
    }

    private void updatePersistentStorage() {
        String currentPlaylist = this.currentPlaylist;
        File file = MusicLoader.getMusicFile(this);
        List<String> allLines = new ArrayList<>();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                allLines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            }
            List<String> kept = new ArrayList<>();
            // 保留其他歌单的数据
            for (String line : allLines) {
                try {
                    JSONObject jsonObject = new JSONObject(line);
                    String playlist = jsonObject.getString("playlist");
                    if (!playlist.equals(currentPlaylist)) {
                        kept.add(line);
                    }
                } catch (Exception e) {
                    // 如果解析失败，跳过这行
                    Log.e(TAG, "解析行失败: " + line, e);
                }
            }

            // 添加当前歌单的数据（JSON格式）
            for (Map<String, Object> item : musicList) {
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("playlist", item.get("playlist"));
                    jsonObject.put("duration", Song.parseTime((String) item.get("TimeDuration")));
                    jsonObject.put("name", item.get("name"));
                    jsonObject.put("filePath", item.get("filePath"));
                    jsonObject.put("coverUrl", item.get("coverUrl") != null ? item.get("coverUrl") : "");

                    kept.add(jsonObject.toString());
                } catch (Exception e) {
                    Log.e(TAG, "创建JSON失败", e);
                }
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

    private void initBottomPlayerBar() {
        // 使用全局管理器
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.attachToActivity(this);

        globalManager.setOnBottomPlayerClickListener(this, new GlobalBottomPlayerManager.OnBottomPlayerClickListener() {
            @Override
            public void onPlayPauseClick() {
                // 与主播放按钮逻辑同步
                if (musicPlayer.isPlaying()) {
                    switchPlayStatus(PlayerStatus.PAUSED);
                } else {
                    if (musicPlayer.getPlayStatus() == PlayerStatus.STOPPED) {
                        try {
                            if (selectedPosition >= 0 && selectedPosition < musicList.size()) {
                                playSongAt(selectedPosition);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        switchPlayStatus(PlayerStatus.PLAYING);
                    }
                }
            }

//            @Override
//            public void onPlayerBarClick() {
//                // 在MainActivity中点击底部栏不需要跳转
//            }
        });
    }

    // 添加方法来更新所有播放按钮状态
    private void updateAllPlayButtons() {
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.forceRefresh();
    }

    // 修改现有的switchPlayStatus方法，添加底部栏更新
    private void switchPlayStatus(PlayerStatus status) {
        switch (status) {
            case PLAYING:
                musicPlayer.play();
                playBtn.setText("暂停");
                break;
            case PAUSED:
                musicPlayer.pause();
                playBtn.setText("播放");
                break;
            case STOPPED:
                musicPlayer.stop();
                playBtn.setText("播放");
                break;
        }
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.forceRefresh();
    }

    // 这个函数可能会用到，别删
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
                        new int[]{R.id.seq, R.id.musicname, R.id.musiclength, R.id.cbSelect});
                listview.setAdapter(adapter);
            }
        }

        // 滚动到最新项
        if (!musicList.isEmpty()) {
            listview.smoothScrollToPosition(musicList.size() - 1);
        }
    }

    private void applyPlaybackBackground() {
        SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
        String playbackBgPath = prefs.getString("playback_background_path", null);

        // 使用DrawerLayout作为背景容器，与歌单页面保持一致
        androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        if (drawerLayout != null) {
            if (playbackBgPath != null && new File(playbackBgPath).exists()) {
                // 设置自定义背景
                try {
                    // 获取屏幕尺寸
                    int screenWidth = getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;

                    // 加载并缩放图片
                    Bitmap originalBitmap = BitmapFactory.decodeFile(playbackBgPath);
                    if (originalBitmap != null) {
                        // 创建缩放后的位图，使用CENTER_CROP效果
                        Bitmap scaledBitmap = createScaledBitmapForImageView(originalBitmap, screenWidth, screenHeight);

                        // 创建背景drawable
                        android.graphics.drawable.BitmapDrawable backgroundDrawable = new android.graphics.drawable.BitmapDrawable(
                                getResources(), scaledBitmap);
                        backgroundDrawable.setAlpha(180); // 设置透明度，保证文字可见
                        drawerLayout.setBackground(backgroundDrawable);

                        // 回收原始位图
                        if (originalBitmap != scaledBitmap) {
                            originalBitmap.recycle();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // 如果加载失败，设置系统主题背景
                    setSystemThemeBackground(drawerLayout);
                }
            } else {
                // 设置系统主题背景
                setSystemThemeBackground(drawerLayout);
            }
        }
    }

    // 新增方法：设置系统主题背景
    private void setSystemThemeBackground(View view) {
        // 获取系统主题背景颜色
        android.util.TypedValue typedValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);

        if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            // 如果是颜色值，直接使用
            view.setBackgroundColor(typedValue.data);
        } else {
            // 如果是drawable资源，使用系统默认背景
            view.setBackgroundResource(typedValue.resourceId);
        }
    }

    // 图片缩放方法（重命名以适应新用途）
    private Bitmap createScaledBitmapForImageView(Bitmap originalBitmap, int targetWidth, int targetHeight) {
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

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停时不关闭进度对话框，但记录状态
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次返回时重新应用背景，以防用户更改了设置
        applyPlaybackBackground();

        // 强制刷新底部播放栏状态
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.attachToActivity(this);
        globalManager.forceRefresh();
        // 恢复时检查进度对话框状态
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Activity不可见时，如果有进度对话框在显示，记录状态但不关闭
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存当前状态
        outState.putString("currentPlaylist", currentPlaylist);
        outState.putBoolean("isDownloading", progressDialog != null && progressDialog.isShowing());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 恢复状态
        currentPlaylist = savedInstanceState.getString("currentPlaylist", "默认歌单");
        boolean wasDownloading = savedInstanceState.getBoolean("isDownloading", false);
        if (wasDownloading) {
            // 如果之前在下载，重新加载歌单
            loadMusicList(listview, currentPlaylist);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 确保进度对话框被正确关闭
        dismissProgressDialog();
        // 从全局管理器中分离
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.detachFromActivity(this);
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
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                uriList.add(uri);
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            // 调用已有的方法批量复制添加，并在内部完成
            if (!uriList.isEmpty()) {
                copyAndAddMusicFiles(uriList);
            }
        }
    }

    // 去重，歌曲本地储存唯一标识用的是musicname，复选删除同名歌曲将影响播放状态，但没有实现，可修改
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

    // 获取文件名，这里可以改进一下，使其能够获取在线歌曲的metadata的信息，但是注意不要修改数据库的内容，inputsong.html也可以改进一下，或者可以添加本机
    // 的上传服务，实现从本机上传音乐至tomcat
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
        // 检查Activity状态
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        // 如果已经有进度对话框在显示，先关闭
        if (progressDialog != null && progressDialog.isShowing()) {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                Log.e("MainActivity", "关闭旧进度对话框失败: " + e.getMessage());
            }
        }
        
        try {
            // 使用布局加载器加载自定义布局
            LayoutInflater inflater = LayoutInflater.from(this);
            View dialogView = inflater.inflate(R.layout.progress_dialog, null);
    
            // 获取布局中的控件引用
            dialogProgressBar = dialogView.findViewById(R.id.progressBarDialog);
            dialogMessage = dialogView.findViewById(R.id.tvProgressMessage);
    
            // 默认设置进度为 0
            if (dialogProgressBar != null) {
                dialogProgressBar.setProgress(0);
                dialogProgressBar.setMax(100);
            }
            if (dialogMessage != null) {
                dialogMessage.setText("正在处理...");
            }
    
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setView(dialogView)
                    .setCancelable(false); // 设置不可取消，直到任务完成
            
            progressDialog = builder.create();
            
            // 确保在主线程中显示
            if (!isFinishing() && !isDestroyed()) {
                progressDialog.show();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "显示进度对话框失败: " + e.getMessage());
            progressDialog = null;
        }
    }

    private void dismissProgressDialog() {
        try {
            if (progressDialog != null) {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = null;
            }
            // 清空引用
            dialogProgressBar = null;
            dialogMessage = null;
        } catch (Exception e) {
            Log.e("MainActivity", "关闭进度对话框失败: " + e.getMessage());
            // 即使出现异常也要清空引用
            progressDialog = null;
            dialogProgressBar = null;
            dialogMessage = null;
        }
    }

    // 安全的进度更新方法
private void updateProgress(int progress, String message) {
    runOnUiThread(() -> {
        try {
            if (progressDialog != null && progressDialog.isShowing()) {
                if (dialogProgressBar != null) {
                    dialogProgressBar.setProgress(progress);
                }
                if (dialogMessage != null && message != null) {
                    dialogMessage.setText(message);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "更新进度失败: " + e.getMessage());
        }
    });
}
// 安全的进度显示方法
private void safeShowProgressDialog() {
    runOnUiThread(() -> {
        if (!isFinishing() && !isDestroyed()) {
            showProgressDialog();
        }
    });
}

// 安全的进度关闭方法
private void safeDismissProgressDialog() {
    runOnUiThread(() -> {
        dismissProgressDialog();
    });
}

// 检查进度对话框是否正在显示
private boolean isProgressDialogShowing() {
    return progressDialog != null && progressDialog.isShowing();
}

// 强制关闭进度对话框（用于异常情况）
private void forceCloseProgressDialog() {
    try {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    } catch (Exception e) {
        Log.e("MainActivity", "强制关闭进度对话框失败: " + e.getMessage());
    } finally {
        progressDialog = null;
        dialogProgressBar = null;
        dialogMessage = null;
    }
}

    private void copyAndAddMusicFiles(List<Uri> uris) {
        // 在UI线程中显示进度弹窗
        runOnUiThread(this::showProgressDialog);

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
                    runOnUiThread(() -> Toast.makeText(this, "添加失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
                        addSongToPlaylist(song);
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

                // dismissProgressDialog();
                // toast一个
                if (dialogMessage != null) {
                    dialogMessage.setText("歌曲添加完成");
                }
                setResult(RESULT_OK);
                new Handler(Looper.getMainLooper()).postDelayed(this::dismissProgressDialog, 800);
            });

        }).start();
    }

    private void updatePlaylistCover(String playlistName) {
        new Thread(() -> {
            try {
                String songInfo = getFirstSongInPlaylist(playlistName);
                if (songInfo == null) {
                    // 如果没有歌曲，直接发送广播让界面刷新
                    sendPlaylistCoverUpdateBroadcast(playlistName);
                    return;
                }
                try {
                    JSONObject jsonObject = new JSONObject(songInfo);
                    String songName = jsonObject.getString("name");
                    String coverUrl = jsonObject.optString("coverUrl", "");

                    if (!coverUrl.isEmpty() && !coverUrl.equals("null")) {
                        // 为每首歌生成唯一的缓存文件名（基于歌曲名和URL的hash）
                        String cacheFileName = "cover_" + Math.abs((songName + coverUrl).hashCode()) + ".jpg";
                        File cacheFile = new File(getFilesDir(), cacheFileName);

                        // 如果缓存文件不存在，下载并缓存
                        if (!cacheFile.exists()) {
                            try {
                                java.net.URL url = new java.net.URL(coverUrl);
                                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                                connection.setDoInput(true);
                                connection.connect();
                                InputStream input = connection.getInputStream();

                                FileOutputStream output = new FileOutputStream(cacheFile);
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = input.read(buffer)) != -1) {
                                    output.write(buffer, 0, bytesRead);
                                }
                                output.close();
                                input.close();

                                Log.d(TAG, "Bilibili音乐封面缓存成功: " + cacheFile.getAbsolutePath());
                            } catch (Exception e) {
                                Log.e(TAG, "下载Bilibili音乐封面失败: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析歌曲JSON失败: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "更新歌单封面失败: " + e.getMessage());
            }

            // 完成后发送广播通知界面刷新
            sendPlaylistCoverUpdateBroadcast(playlistName);
        }).start();
    }

    // 新增方法：发送歌单封面更新广播
    private void sendPlaylistCoverUpdateBroadcast(String playlistName) {
        runOnUiThread(() -> {
            Intent intent = new Intent("PLAYLIST_COVER_UPDATED");
            intent.putExtra("playlistName", playlistName);
            sendBroadcast(intent);
            Log.d(TAG, "发送歌单封面更新广播: " + playlistName);
        });
    }

    /**
     * 因为改成了在文件开头插入，获取第一首歌就行
     *
     * @param playlistName 歌单名称
     * @return 第一首歌曲的JSON字符串，没有找到为null
     */
    private String getFirstSongInPlaylist(String playlistName) {
        try {
            File file = MusicLoader.getMusicFile(this);
            if (!file.exists())
                return null;

            String firstSong = null;
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject json = new JSONObject(line);
                    if (json.has("playlist") && json.getString("playlist").equals(playlistName)) {
                        firstSong = line;
                        break;
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "解析JSON失败: " + e.getMessage());
                }
            }

            reader.close();
            return firstSong;
        } catch (Exception e) {
            Log.e(TAG, "获取第一首歌失败: " + e.getMessage());
            return null;
        }
    }

    // 删除音乐，注意只是删除按钮的逻辑，复选框逻辑在初始化那块写了，闲得无聊可以合并一下
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

        // 检查是否删除的是当前播放歌曲
        // boolean isCurrentSongDeleted = false;

        if (currentSong != null) {
            Map<String, Object> deletedSongMap = musicList.get(position);
            Song deletedSong = Song.fromMap(deletedSongMap);
            if (deletedSong.equals(currentSong)) {
                musicPlayer.stop();
                progressBar.setProgress(0);
                preogress.setText("请选择歌曲");
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
                        preogress.setText(formatTime(currentPos) + " / " + formatTime(totalDuration));
                    });
                }
            });
            song = new Song(0, "暂无歌曲", "", "");
            // 重置UI
            musicPlayer.resetProgress();
            runOnUiThread(() -> {
                progressBar.setProgress(0);
                preogress.setText("请选择歌曲");
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
        // for (int i = position; i < musicList.size(); i++) {
        // musicList.get(i).put("index", i + 1);
        // }
        updateSongIndices();
        updatePersistentStorage();
        // 刷新列表适配器
        // MusicLoader.deleteMusic(this, position);
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
        if (!musicList.isEmpty()) {
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
            // 重新绑定进度监听
            musicPlayer.setProgressListener((currentPosition, totalDuration) -> runOnUiThread(() -> {
                // 更新进度条逻辑
                progressBar.setMax(totalDuration);
                progressBar.setProgress(currentPosition);
                preogress.setText(formatTime(currentPosition) + " / " + formatTime(totalDuration));
            }));
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
            preogress.setText("请选择歌曲");
            musicPlayer.setCurrentPositiontozero();
            btnPrevious.setEnabled(false);
            btnNext.setEnabled(false);
            albumArt.setImageResource(R.drawable.default_cover);

            currentSongTV.setText("当前播放: 暂无歌曲");
        }

        albumArt.setImageResource(R.drawable.default_cover);
    }

    // 这个地方需要修改，增加一下一键删除的单选
    private void showDeleteConfirmationDialog() {
        // 动态创建包含复选框的布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10); // 调整内边距

        // 添加消息文本
        TextView message = new TextView(this);
        message.setText("确定要删除这首歌曲吗？");
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        layout.addView(message);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除")
                .setView(layout) // 设置动态创建的布局
                .setPositiveButton("确定", (dialog, which) -> {
                    deleteSelectedSong(selectedPosition); // 传递复选框状态
                    dialog.dismiss();
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void loadMusicList(ListView listview, String playlist) {
        // 保存当前播放的歌曲信息
        Song currentPlayingSong = musicPlayer.getCurrentSong();
        boolean wasPlaying = musicPlayer.isPlaying();
        boolean wasPaused = musicPlayer.isPaused();

        // 加载新歌单的歌曲列表
        musicList = MusicLoader.loadSongs(this, playlist);
        for (Map<String, Object> item : musicList) {
            item.put("isSelected", false);
        }

        // 如果有歌曲正在播放，检查是否在当前歌单中
        if (currentPlayingSong != null && (wasPlaying || wasPaused)) {
            // 查找当前播放歌曲在新歌单中的位置
            for (int i = 0; i < musicList.size(); i++) {
                Song songInList = Song.fromMap(musicList.get(i));
                if (songInList.equals(currentPlayingSong)) {
                    selectedPosition = i;
                    // 关键修复：保持原有的coverUrl信息
                    if (currentPlayingSong.getCoverUrl() != null && !currentPlayingSong.getCoverUrl().isEmpty()) {
                        songInList.setCoverUrl(currentPlayingSong.getCoverUrl());
                        // 更新MusicPlayer中的currentSong以保持coverUrl
                        musicPlayer.updateCurrentSongCoverUrl(currentPlayingSong.getCoverUrl());
                    }
                    break;
                }
            }
        }

        // 单例模式初始化适配器
        BatchModeAdapter adapter = null;
        if (listview.getAdapter() == null) {
            adapter = new BatchModeAdapter(
                    this,
                    musicList,
                    R.layout.playlist_layout,
                    new String[]{"index", "name", "TimeDuration", "isSelected"},
                    new int[]{R.id.seq, R.id.musicname, R.id.musiclength, R.id.cbSelect});
            listview.setAdapter(adapter);
        } else {
            adapter = (BatchModeAdapter) listview.getAdapter();
            adapter.setData(musicList);
            adapter.notifyDataSetChanged();
        }

        // 如果当前播放的歌曲在新歌单中，高亮显示
        if (selectedPosition >= 0 && selectedPosition < musicList.size()) {
            listview.setItemChecked(selectedPosition, true);
            listview.smoothScrollToPosition(selectedPosition);
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
        isAutoNextTriggered = false;
        isAutoTriggeredByCompletion = true;
        isSongChanging = true;

        if (musicPlayer.isPlaying() || musicPlayer.isPaused()) {
            musicPlayer.stop();
        }

        // 获取歌曲对象
        Map<String, Object> songMap = musicList.get(index);
        Song songToPlay = Song.fromMap(songMap);

        if (!isFileValid(songToPlay.getFilePath())) {
            runOnUiThread(() -> {
                Toast.makeText(this, "歌曲文件不存在或无法访问", Toast.LENGTH_SHORT).show();
                musicList.remove(index);
                ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
            });
            return;
        }

        // 加载并播放歌曲
        musicPlayer.loadMusic(songToPlay);
        musicPlayer.setCurrentPositiontozero();

        // 关键修改：实际启动播放并设置正确状态
        musicPlayer.play();  // 添加这行

        ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
        listview.setItemChecked(index, true);
        listview.smoothScrollToPosition(index);

        // 更新UI
        runOnUiThread(() -> {
            playBtn.setEnabled(true);
            progressBar.setMax(songToPlay.getTimeDuration());
            progressBar.setProgress(0);
            preogress.setText("0");
            playBtn.setText("暂停");  // 现在这个设置是正确的，因为确实在播放
        });

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

        // 确保底部栏状态更新 - 添加延迟确保状态已更新
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            updateAllPlayButtons();
        }, 100);
    }

    // 下面的上一首按钮与下一首按钮将来有可能调整UI的时候会删除
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
        updateAllPlayButtons();

    }

    // 上一首按钮处理逻辑
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

        // 移除条件判断，始终重新加载歌曲
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
        updateAllPlayButtons();

    }

    private void showBiliDialog(myCallback<String> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("从BV号 / 链接添加");

        // 用 LinearLayout 包裹输入框
        EditText input = new EditText(this);
        input.setHint("可输入BV号 / AV号 / 分享的链接");

        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.VERTICAL);
        inputLayout.setPadding(50, 20, 50, 10);
        inputLayout.addView(input);
        builder.setView(inputLayout);

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
                        bv = matcher.group(); // 找到第一个短链
                    }
                    // 发送HEAD请求得到重定向的链接
                    OkHttpClient client = new OkHttpClient.Builder()
                            .followRedirects(false) // 关键点：不要自动跟随重定向
                            .build();

                    Request request = new Request.Builder()
                            .url(bv)
                            .head() // 只取 Header，不要正文
                            .build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Log.e("ShortLink", "请求失败: " + e.getMessage());
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
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

    private void showBiliCollectionDialog(myCallback<List<String>> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("从B站收藏夹添加");

        // 用 LinearLayout 包裹输入框
        EditText userId = new EditText(this);
        EditText collectionName = new EditText(this);
        userId.setHint("请输入uid");
        collectionName.setHint("请输入收藏夹名");

        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.VERTICAL);
        inputLayout.setPadding(50, 20, 50, 10);
        inputLayout.addView(userId);
        inputLayout.addView(collectionName);
        builder.setView(inputLayout);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String uid = userId.getText().toString().trim();
            String collectionId = collectionName.getText().toString().trim();

            // 验证输入
            if (collectionId.isEmpty() || uid.isEmpty()) {
                Toast.makeText(this, "不能为空", Toast.LENGTH_SHORT).show();
            } else {
                // 直接复制前面会有"UID:"，去一下
                uid = uid.replace("UID:", "").trim();

                // 确保纯数字
                if (uid.matches("\\d+")) {
                    // 显示加载进度
                    showProgressDialog();
                    if (dialogMessage != null) {
                        dialogMessage.setText("正在获取收藏夹信息...");
                    }

                    // 先查找所有收藏夹，匹配收藏夹名
                    OkHttpClient client = new OkHttpClient();
                    Request req = new Request.Builder()
                            .url("https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&up_mid=" + uid)
                            .addHeader("User-Agent", "Mozilla/5.0")
                            .addHeader("Referer", "https://www.bilibili.com/")
                            .build();
                    String finalUid = uid;
                    client.newCall(req).enqueue(new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            Log.e("BiliCollection", "onFailure: " + e.getMessage());
                            runOnUiThread(() -> {
                                dismissProgressDialog();
                                Toast.makeText(MainActivity.this, "请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            if (!response.isSuccessful()) {
                                runOnUiThread(() -> {
                                    dismissProgressDialog();
                                    Toast.makeText(MainActivity.this, "请求失败: " + response.code(), Toast.LENGTH_SHORT)
                                            .show();
                                });
                                return;
                            }

                            String json = response.body().string();
                            JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                            JsonArray folders = jsonObj.getAsJsonObject("data").getAsJsonArray("list");

                            boolean found = false;
                            String fid = "";

                            // 遍历查找指定的收藏夹
                            for (JsonElement folder : folders) {
                                JsonObject folderObj = folder.getAsJsonObject();
                                if (folderObj.get("title").getAsString().equals(collectionId)) {
                                    found = true;
                                    fid = folderObj.get("id").getAsString();
                                    Log.d("BiliCollection", "找到收藏夹: " + fid);
                                    break;
                                }
                            }

                            if (!found) {
                                runOnUiThread(() -> {
                                    dismissProgressDialog();
                                    Toast.makeText(MainActivity.this, "未找到指定收藏夹", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            // 找到收藏夹后，获取视频详细信息并显示选择界面
                            showBiliVideoSelectionDialog(finalUid, fid, callback);
                        }
                    });
                } else {
                    Toast.makeText(this, "请检查 UID 是否正确", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            callback.onResult(null); // 取消则返回 null
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showBiliVideoSelectionDialog(String uid, String fid, myCallback<List<String>> callback) {
        // 在UI线程中创建和显示对话框
        runOnUiThread(() -> {
            // 创建对话框视图
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bili_video_selection, null);
            CheckBox cbSelectAll = dialogView.findViewById(R.id.cbSelectAll);
            TextView tvSelectedCount = dialogView.findViewById(R.id.tvSelectedCount);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);
            Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);
            ListView lvVideos = dialogView.findViewById(R.id.lvBiliVideos);
            ProgressBar loadMoreProgress = dialogView.findViewById(R.id.loadMoreProgress);

            // 创建适配器
            BiliVideoAdapter adapter = new BiliVideoAdapter(this);
            lvVideos.setAdapter(adapter);

            // 设置全选监听器
            adapter.setOnSelectAllListener(shouldSelectAll -> {
                cbSelectAll.setOnCheckedChangeListener(null);
                cbSelectAll.setChecked(shouldSelectAll);
                cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.selectAll(isChecked));
            });

            // 设置选择计数监听器
            adapter.setOnItemSelectListener(selectedCount -> {
                tvSelectedCount.setText(String.format(Locale.getDefault(), "已选择: %d", selectedCount));
            });

            // 设置全选复选框监听器
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
                adapter.selectAll(isChecked);
            });

            // 创建对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("选择视频");
            builder.setView(dialogView);
            builder.setCancelable(false);

            AlertDialog videoDialog = builder.create();

            // 设置取消按钮点击事件
            btnCancel.setOnClickListener(v -> {
                videoDialog.dismiss();
                callback.onResult(null); // 取消则返回 null
            });

            // 设置确认按钮点击事件
            btnConfirm.setOnClickListener(v -> {
                List<BiliVideoAdapter.BiliVideoItem> selectedItems = adapter.getSelectedItems();
                if (selectedItems.isEmpty()) {
                    Toast.makeText(this, "请选择至少一个视频", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 提取所有选中视频的BV号
                List<String> selectedBvids = new ArrayList<>();
                for (BiliVideoAdapter.BiliVideoItem item : selectedItems) {
                    selectedBvids.add(item.getBvid());
                }

                // 关闭对话框并返回结果
                videoDialog.dismiss();
                callback.onResult(selectedBvids);
            });

            // 显示对话框
            videoDialog.show();

            // 加载第一页数据。里面会设置滚动监听器递归加载下一页
            loadBiliVideoPage(uid, fid, 1, 20, adapter, lvVideos, loadMoreProgress);
        });
    }

    private void showBiliVideoCollectionDialog(String bvid, JsonObject videoInfo, JsonArray pages, myCallback<Map<String, Object>> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要添加的视频");
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bili_video_list, null);
        ListView listView = dialogView.findViewById(R.id.lvVideoList);
        CheckBox cbSelectAll = dialogView.findViewById(R.id.cbSelectAll);
        TextView tvSelectedCount = dialogView.findViewById(R.id.tvSelectedCount);
        
        BiliVideoAdapter adapter = new BiliVideoAdapter(this);
        listView.setAdapter(adapter);
        
        // 解析视频合集信息
        List<BiliVideoAdapter.BiliVideoItem> videoItems = new ArrayList<>();
        String mainTitle = videoInfo.get("title").getAsString();
        String uploader = videoInfo.get("owner").getAsJsonObject().get("name").getAsString();
        
        for (int i = 0; i < pages.size(); i++) {
            JsonObject page = pages.get(i).getAsJsonObject();
            String pageTitle = page.get("part").getAsString();
            int duration = page.get("duration").getAsInt();
            long cid = page.get("cid").getAsLong();
            
            // 创建唯一标识：bvid + cid
            String uniqueId = bvid + "_" + cid;
            String fullTitle = mainTitle + " - " + pageTitle;
            
            BiliVideoAdapter.BiliVideoItem item = new BiliVideoAdapter.BiliVideoItem(
                uniqueId, fullTitle, uploader, duration);
            videoItems.add(item);
        }
        
        adapter.addItems(videoItems);
        
        // 设置全选逻辑
        adapter.setOnSelectAllListener(shouldSelectAll -> {
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(shouldSelectAll);
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.selectAll(isChecked));
        });
        
        adapter.setOnItemSelectListener(selectedCount -> {
            tvSelectedCount.setText("已选择: " + selectedCount + "/" + videoItems.size());
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(selectedCount == videoItems.size());
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.selectAll(isChecked));
        });
        
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.selectAll(isChecked));
        
        builder.setView(dialogView);
        builder.setPositiveButton("添加选中项", (dialog, which) -> {
            List<BiliVideoAdapter.BiliVideoItem> selectedItems = adapter.getSelectedItems();
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "请至少选择一个视频", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 批量下载选中的视频
            downloadSelectedBiliVideos(selectedItems, pages, videoInfo, callback);
            dialog.dismiss();
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> {
            callback.onResult(null);
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showBiliUGCSeasonDialog(String bvid, JsonObject videoInfo, JsonObject ugcSeason, myCallback<Map<String, Object>> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要添加的视频");
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bili_video_list, null);
        ListView listView = dialogView.findViewById(R.id.lvVideoList);
        CheckBox cbSelectAll = dialogView.findViewById(R.id.cbSelectAll);
        TextView tvSelectedCount = dialogView.findViewById(R.id.tvSelectedCount);
        
        BiliVideoAdapter adapter = new BiliVideoAdapter(this);
        listView.setAdapter(adapter);
        
        // 分页相关变量
        final int PAGE_SIZE = 20;
        AtomicInteger currentPage = new AtomicInteger(0);
        List<BiliVideoAdapter.BiliVideoItem> allVideoItems = new ArrayList<>();
        
        // 解析UGC合集信息
        String mainTitle = videoInfo.get("title").getAsString();
        String uploader = videoInfo.get("owner").getAsJsonObject().get("name").getAsString();
        
        JsonArray sections = ugcSeason.getAsJsonArray("sections");
        for (JsonElement sectionElement : sections) {
            JsonObject section = sectionElement.getAsJsonObject();
            JsonArray episodes = section.getAsJsonArray("episodes");
            
            for (JsonElement episodeElement : episodes) {
                JsonObject episode = episodeElement.getAsJsonObject();
                String episodeBvid = episode.get("bvid").getAsString();
                String episodeTitle = episode.get("title").getAsString();
                long cid = episode.get("cid").getAsLong();
                
                // 获取每个视频的封面URL和时长
                String episodeCoverUrl = "";
                int duration = 0;
                if (episode.has("arc")) {
                    JsonObject arc = episode.getAsJsonObject("arc");
                    if (arc.has("pic")) {
                        episodeCoverUrl = arc.get("pic").getAsString();
                    }
                    if (arc.has("duration")) {
                        duration = arc.get("duration").getAsInt();
                    }
                }
                
                // 创建唯一标识：bvid + cid + coverUrl
                String uniqueId = episodeBvid + "_" + cid + "_" + episodeCoverUrl;
                
                BiliVideoAdapter.BiliVideoItem item = new BiliVideoAdapter.BiliVideoItem(
                    uniqueId, episodeTitle, uploader, duration);
                allVideoItems.add(item);
            }
        }
        
        // 加载第一页
        loadPage(adapter, allVideoItems, currentPage.get(), PAGE_SIZE);
        
        // 设置滑动监听器实现自动加载更多
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            private int visibleThreshold = 5;
            private boolean loading = false;
            private int previousTotal = 0;
            
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }
            
            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (loading && totalItemCount > previousTotal) {
                    loading = false;
                    previousTotal = totalItemCount;
                }
                
                // 检查是否需要加载更多
                if (!loading && (totalItemCount - visibleItemCount) <= (firstVisibleItem + visibleThreshold)) {
                    int nextPage = currentPage.get() + 1;
                    int startIndex = nextPage * PAGE_SIZE;
                    
                    // 检查是否还有更多数据
                    if (startIndex < allVideoItems.size()) {
                        loading = true;
                        currentPage.incrementAndGet();
                        loadPage(adapter, allVideoItems, nextPage, PAGE_SIZE);
                    }
                }
            }
        });
        
        // 设置全选逻辑
        adapter.setOnSelectAllListener(shouldSelectAll -> {
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(shouldSelectAll);
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.selectAll(isChecked));
        });
        
        adapter.setOnItemSelectListener(selectedCount -> {
            tvSelectedCount.setText("已选择: " + selectedCount + "/" + adapter.getCount());
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(selectedCount == adapter.getCount());
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.selectAll(isChecked));
        });
        
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.selectAll(isChecked));
        
        builder.setView(dialogView);
        builder.setPositiveButton("添加选中项", (dialog, which) -> {
            List<BiliVideoAdapter.BiliVideoItem> selectedItems = adapter.getSelectedItems();
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "请至少选择一个视频", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 批量下载选中的视频
            downloadSelectedUGCVideos(selectedItems, videoInfo, callback);
            dialog.dismiss();
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> {
            callback.onResult(null);
            dialog.dismiss();
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    // 新增分页加载方法
    private void loadPage(BiliVideoAdapter adapter, List<BiliVideoAdapter.BiliVideoItem> allItems, int page, int pageSize) {
        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allItems.size());
        
        if (startIndex < allItems.size()) {
            List<BiliVideoAdapter.BiliVideoItem> pageItems = allItems.subList(startIndex, endIndex);
            adapter.addItems(pageItems);
        }
    }

    private void downloadSelectedUGCVideos(List<BiliVideoAdapter.BiliVideoItem> selectedItems, 
                                      JsonObject videoInfo, 
                                      myCallback<Map<String, Object>> callback) {
    
    safeShowProgressDialog();
    updateProgress(0, "正在下载视频合集 (0/" + selectedItems.size() + ")");
    
    AtomicInteger completedCount = new AtomicInteger(0);
    AtomicInteger successCount = new AtomicInteger(0);
    
    for (BiliVideoAdapter.BiliVideoItem item : selectedItems) {
        // 从uniqueId中提取bvid、cid和coverUrl
        String[] parts = item.getBvid().split("_");
        String bvid = parts[0];
        long cid = Long.parseLong(parts[1]);
        String coverUrl = parts.length > 2 ? parts[2] : videoInfo.get("pic").getAsString();

        // 下载单个视频，使用正确的封面URL
        downloadSingleBiliVideo(bvid, cid, item.getTitle(), coverUrl,
            result -> {
                int completed = completedCount.incrementAndGet();
                if (result != null) {
                    successCount.incrementAndGet();
                    
                    // 添加到播放列表
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        
                        try {
                            String title = (String) result.get("title");
                            File f = (File) result.get("file");
                            String resultCoverUrl = (String) result.get("coverUrl");
                            String path = f.getAbsolutePath();
                            
                            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                            retriever.setDataSource(path);
                            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            int duration = Integer.parseInt(durationStr) / 1000;
                            
                            Song newSong = new Song(duration, title, path, currentPlaylist);
                            newSong.setCoverUrl(resultCoverUrl);
                            addSongToPlaylist(newSong);
                            MusicLoader.appendMusic(this, newSong);
                            
                            retriever.release();
                        } catch (Exception e) {
                            Log.e("BiliMusic", "处理音频文件失败: " + e.getMessage());
                        }
                    });
                }
                
                // 更新进度
                int progressPercent = (completed * 100) / selectedItems.size();
                updateProgress(progressPercent, "正在下载视频合集 (" + completed + "/" + selectedItems.size() + ")");
                
                if (completed == selectedItems.size()) {
                    // 全部完成
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            safeDismissProgressDialog();
                            return;
                        }
                        
                        try {
                            safeDismissProgressDialog();
                            updatePlaylistCover(currentPlaylist);
                            
                            if (listview != null && listview.getAdapter() != null) {
                                ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
                            }
                            updateNavButtons();
                            
                            Toast.makeText(MainActivity.this, 
                                "成功添加 " + successCount.get() + "/" + selectedItems.size() + " 首歌曲", 
                                Toast.LENGTH_SHORT).show();
                            
                            if (callback != null) {
                                Map<String, Object> finalResult = new HashMap<>();
                                finalResult.put("success", true);
                                finalResult.put("count", successCount.get());
                                callback.onResult(finalResult);
                            }
                        } catch (Exception e) {
                            Log.e("BiliMusic", "完成处理失败: " + e.getMessage());
                            safeDismissProgressDialog();
                        }
                    });
                }
            });
    }
}

    private void downloadSelectedBiliVideos(List<BiliVideoAdapter.BiliVideoItem> selectedItems, 
                                       JsonArray pages, JsonObject videoInfo, 
                                       myCallback<Map<String, Object>> callback) {
    
    safeShowProgressDialog();
    updateProgress(0, "正在下载视频合集 (0/" + selectedItems.size() + ")");
    
    AtomicInteger completedCount = new AtomicInteger(0);
    AtomicInteger successCount = new AtomicInteger(0);
    
    for (BiliVideoAdapter.BiliVideoItem item : selectedItems) {
        // 从uniqueId中提取cid
        String[] parts = item.getBvid().split("_");
        String bvid = parts[0];
        long cid = Long.parseLong(parts[1]);
        
        // 下载单个视频
        downloadSingleBiliVideo(bvid, cid, item.getTitle(), videoInfo.get("pic").getAsString(), 
            result -> {
                int completed = completedCount.incrementAndGet();
                if (result != null) {
                    successCount.incrementAndGet();
                    
                    // 添加到播放列表
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        
                        String title = (String) result.get("title");
                        File f = (File) result.get("file");
                        String coverUrl = (String) result.get("coverUrl");
                        String path = f.getAbsolutePath();
                        
                        try {
                            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                            retriever.setDataSource(path);
                            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            int duration = Integer.parseInt(durationStr) / 1000;
                            
                            Song newSong = new Song(duration, title, path, currentPlaylist);
                            newSong.setCoverUrl(coverUrl);
                            addSongToPlaylist(newSong);
                            MusicLoader.appendMusic(this, newSong);
                            
                            retriever.release();
                        } catch (Exception e) {
                            Log.e("BiliMusic", "处理音频文件失败: " + e.getMessage());
                        }
                    });
                }
                
                // 更新进度
                int progressPercent = (completed * 100) / selectedItems.size();
                updateProgress(progressPercent, "正在下载视频合集 (" + completed + "/" + selectedItems.size() + ")");
                
                if (completed == selectedItems.size()) {
                    // 全部完成
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            safeDismissProgressDialog();
                            return;
                        }
                        
                        try {
                            safeDismissProgressDialog();
                            updatePlaylistCover(currentPlaylist);
                            
                            if (listview != null && listview.getAdapter() != null) {
                                ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
                            }
                            updateNavButtons();
                            
                            Toast.makeText(MainActivity.this, 
                                "成功添加 " + successCount.get() + "/" + selectedItems.size() + " 首歌曲", 
                                Toast.LENGTH_SHORT).show();
                            
                            if (callback != null) {
                                Map<String, Object> finalResult = new HashMap<>();
                                finalResult.put("success", true);
                                finalResult.put("count", successCount.get());
                                callback.onResult(finalResult);
                            }
                        } catch (Exception e) {
                            Log.e("BiliMusic", "完成处理失败: " + e.getMessage());
                            safeDismissProgressDialog();
                        }
                    });
                }
            });
    }
}

    private void loadBiliVideoPage(String uid, String fid, int pageNum, int pageSize,
                                   BiliVideoAdapter adapter, ListView listView, ProgressBar loadMoreProgress) {
        // 如果是第一页，显示进度对话框
        if (pageNum == 1) {
            if (dialogMessage != null) {
                dialogMessage.setText("正在获取视频信息...");
            }
        } else {
            // 如果是加载更多，显示底部进度条
            runOnUiThread(() -> loadMoreProgress.setVisibility(View.VISIBLE));
        }

        OkHttpClient client = new OkHttpClient();
        String url = String.format(Locale.getDefault(), "https://api.bilibili.com/x/v3/fav/resource/list?media_id=%s&pn=%d&ps=%d",
                fid, pageNum, pageSize);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("BiliCollection", "获取视频列表失败: " + e.getMessage());
                runOnUiThread(() -> {
                    if (pageNum == 1) {
                        dismissProgressDialog();
                    } else {
                        loadMoreProgress.setVisibility(View.GONE);
                    }
                    Toast.makeText(MainActivity.this, "获取视频列表失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        if (pageNum == 1) {
                            dismissProgressDialog();
                        } else {
                            loadMoreProgress.setVisibility(View.GONE);
                        }
                        Toast.makeText(MainActivity.this, "获取视频列表失败: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String json = response.body().string();
                JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                JsonObject data = jsonObj.getAsJsonObject("data");

                // 收藏夹为空的处理
                JsonArray medias;
                if (data.has("medias") && !data.get("medias").isJsonNull()) {
                    medias = data.getAsJsonArray("medias");
                } else if (pageNum == 1) {
                    runOnUiThread(() -> {
                        dismissProgressDialog();
                        loadMoreProgress.setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this, "收藏夹为空", Toast.LENGTH_SHORT).show();
                    });
                    return;
                } else {
                    runOnUiThread(() -> {
                        loadMoreProgress.setVisibility(View.GONE);
                    });
                    return;
                }

                boolean hasMore = data.get("has_more").getAsBoolean();

                // 解析视频信息
                List<BiliVideoAdapter.BiliVideoItem> videoItems = new ArrayList<>();
                for (JsonElement media : medias) {
                    JsonObject videoObj = media.getAsJsonObject();
                    String bvid = videoObj.get("bvid").getAsString();
                    String title = videoObj.get("title").getAsString();
                    String uploader = videoObj.getAsJsonObject("upper").get("name").getAsString();
                    int duration = videoObj.get("duration").getAsInt();

                    // 失效视频后续无法获取，直接筛掉
                    if (!Objects.equals(title, "已失效视频")) {
                        BiliVideoAdapter.BiliVideoItem item = new BiliVideoAdapter.BiliVideoItem(
                                bvid, title, uploader, duration);
                        videoItems.add(item);
                    }
                }

                // 在UI线程中更新列表
                final boolean finalHasMore = hasMore;
                final int finalPageNum = pageNum;
                runOnUiThread(() -> {
                    // 如果是第一页，关闭进度对话框
                    if (finalPageNum == 1) {
                        dismissProgressDialog();
                    } else {
                        loadMoreProgress.setVisibility(View.GONE);
                    }

                    // 如果全都是失效视频
                    if (videoItems.isEmpty() && finalPageNum == 1) {
                        dismissProgressDialog();
                        Toast.makeText(MainActivity.this, "收藏夹内不存在有效视频", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 添加数据到适配器
                    adapter.addItems(videoItems);

                    // 如果是第一次加载，设置滚动监听器
                    if (finalPageNum == 1) {
                        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                            private int page = finalPageNum;
                            private int visibleThreshold = 5;
                            private boolean loading = true;
                            private int previousTotal = 0;

                            @Override
                            public void onScrollStateChanged(AbsListView view, int scrollState) {
                            }

                            @Override
                            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                                 int totalItemCount) {
                                if (loading && totalItemCount > previousTotal) {
                                    loading = false;
                                    previousTotal = totalItemCount;
                                }

                                if (!loading && finalHasMore && (totalItemCount - visibleItemCount) <=
                                        (firstVisibleItem + visibleThreshold)) {
                                    // 加载下一页
                                    loading = true;
                                    page++;
                                    loadBiliVideoPage(uid, fid, page, pageSize, adapter, listView,
                                            loadMoreProgress);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private void getBiliMusic(String bv, Context context, myCallback<Map<String, Object>> callback) {
        OkHttpClient client = new OkHttpClient();

        // 获取视频信息
        Request cidRequest = new Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/view?bvid=" + bv)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();

        client.newCall(cidRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("BiliMusic", "onFailure: " + e);
                runOnUiThread(() -> Toast.makeText(context, "请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response res) throws IOException {
                String str = res.body().string();
                Log.d("BiliMusic", "API响应: " + str); // 调试
                
                JsonObject json = JsonParser.parseString(str).getAsJsonObject().get("data").getAsJsonObject();
                
                // 检查是否为视频合集
                JsonArray pages = json.getAsJsonArray("pages");
                JsonObject ugcSeason = json.has("ugc_season") ? json.getAsJsonObject("ugc_season") : null;
                Log.d("BiliMusic", "Pages数量: " + (pages != null ? pages.size() : "null"));
                Log.d("BiliMusic", "UGC Season: " + (ugcSeason != null ? "存在" : "不存在"));

                // 检测合集：要么是多P视频，要么是UGC合集
                boolean isCollection = (pages != null && pages.size() > 1) || 
                                      (ugcSeason != null && ugcSeason.has("sections"));

                if (isCollection) {
                    Log.d("BiliMusic", "检测到视频合集，显示选择列表");
                    // 处理合集逻辑
                    if (ugcSeason != null) {
                        // UGC合集，需要从sections中提取视频列表
                        runOnUiThread(() -> showBiliUGCSeasonDialog(bv, json, ugcSeason, callback));
                    } else {
                        // 多P视频
                        runOnUiThread(() -> showBiliVideoCollectionDialog(bv, json, pages, callback));
                    }
                } else {
                    Log.d("BiliMusic", "单个视频，继续原有逻辑");
                    // 单个视频，继续原有逻辑
                    long cid = json.get("cid").getAsLong();
                    String title = json.get("title").getAsString();
                    String coverUrl = json.get("pic").getAsString();
                    
                    downloadSingleBiliVideo(bv, cid, title, coverUrl, callback);
                }
            }
        });
    }

    private void downloadSingleBiliVideo(String bv, long cid, String title, String coverUrl, myCallback<Map<String, Object>> callback) {
        OkHttpClient client = new OkHttpClient();
        
        // Step 2: 获取音频 URL
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://api.bilibili.com/x/player/playurl"))
                .newBuilder()
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
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "获取音频URL失败: " + e.getMessage(), Toast.LENGTH_SHORT)
                        .show());
                callback.onResult(null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
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
                        runOnUiThread(() -> Toast
                                .makeText(MainActivity.this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        callback.onResult(null);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        // 生成唯一文件名（对于合集视频使用bv_cid格式）
                        String fileName = bv + "_" + cid + ".mp3";
                        File audioFile = new File(MainActivity.this.getExternalFilesDir("audio"), fileName);
                        try (InputStream in = response.body().byteStream();
                             FileOutputStream out = new FileOutputStream(audioFile)) {
                            byte[] buffer = new byte[4096];
                            int len;
                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                            }

                            // 预加载封面到缓存
                            if (coverUrl != null && !coverUrl.isEmpty()) {
                                MusicCoverUtils.preloadCover(coverUrl, MainActivity.this);
                            }

                            // 把东西放一起返回，包括封面URL
                            Map<String, Object> result = new HashMap<>();
                            result.put("file", audioFile);
                            result.put("title", title);
                            result.put("coverUrl", coverUrl); // 添加封面URL

                            callback.onResult(result);
                        } catch (IOException e) {
                            Log.e("BiliMusic", "下载文件失败: ", e);
                            callback.onResult(null); // 获取失败
                            runOnUiThread(() -> Toast
                                    .makeText(MainActivity.this, "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
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

            if (!selectSong.equals(song)) {// !selectSong.getName().equals(song.getName())
                try {
                    // musicPlayer.loadMusic(selectSong);
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
                updateAllPlayButtons();

                // // 添加延迟强制刷新
                // new Handler(Looper.getMainLooper()).postDelayed(() -> {
                //     GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
                //     globalManager.forceRefresh();
                // }, 200);


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
                updateAllPlayButtons();

                // 同样添加延迟强制刷新
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
                    globalManager.forceRefresh();
                }, 200);

            }
            // song = selectSong;
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
                    // else if(position-lv.getFirstVisiblePosition() <= 0){
                    // listview.setSelection(position);
                    // }
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
                    if (musicPlayer.isStop() || musicPlayer.isReleased()) { // 这段代码加上是为了防止吃内存导致缺页
                        break;
                    }
                    // 使用 musicPlayer 的公共方法获取数据，这里我已创建单例
                    int currentPosition = musicPlayer.getCurrentPosition();
                    int totalDuration = musicPlayer.getDuration();

                    runOnUiThread(() -> {
                        if (musicPlayer.isStop())
                            return;
                        progressBar.setMax(totalDuration);
                        progressBar.setProgress(currentPosition);
                        preogress.setText(formatTime(currentPosition) + "/" + formatTime(totalDuration));

                        // // 自动切换逻辑
                        // if (currentPosition >= totalDuration - 50
                        // && !isSongChanging
                        // && !isAutoNextTriggered
                        // && musicPlayer.getPlayStatus() == PlayerStatus.PLAYING
                        // && !musicList.isEmpty()) {
                        // isAutoNextTriggered = true;
                        // Log.d("MainActivity", "自动切歌");
                        // try {
                        // playNextSong();
                        // } catch (IOException e) {
                        // throw new RuntimeException(e);
                        // }
                        // }
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
