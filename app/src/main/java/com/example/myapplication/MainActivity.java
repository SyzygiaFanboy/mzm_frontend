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
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.animation.ValueAnimator;
import android.text.TextUtils;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.adapter.BatchModeAdapter;
import com.example.myapplication.adapter.BiliVideoAdapter;
import com.example.myapplication.model.MusicViewModel;
import com.example.myapplication.model.Playlist;
import com.example.myapplication.model.Song;
import com.example.myapplication.utils.BiliAudioDownloadHelper;
import com.example.myapplication.utils.BiliLinkParser;
import com.example.myapplication.utils.ImageCacheManager;
import com.example.myapplication.utils.SongDeletionUtils;
import com.example.myapplication.utils.UiAutoRefreshHelper;
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
    private static final int BILI_FAV_MAX_DURATION_SECONDS = 30 * 60;
    private static final int REQUEST_CODE_BILI_FAV_PICKER = 2207;
    private static final int REQUEST_CODE_BILI_SHARE_PICKER = 2208;

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
    private TextView playlistTitle;
    private TextView currentSongText;
    private HorizontalScrollView currentSongScroll;
    private View currentSongLayout;
    private ValueAnimator marqueeAnimator;
    private int marqueeExtraPaddingRightPx = 0;
    private int marqueeOriginalPaddingRightPx = 0;
    private String lastCurrentSongDisplayText = null;
    private final UiAutoRefreshHelper visibleUiAutoRefresh = new UiAutoRefreshHelper(1200L, this::refreshVisibleUiIfNeeded);

    private final MusicPlayer.OnPlaybackStateChangeListener playlistPagePlaybackListener = new MusicPlayer.OnPlaybackStateChangeListener() {
        @Override
        public void onPlaybackStateChanged() {
            runOnUiThread(MainActivity.this::refreshCurrentSongBar);
        }

        @Override
        public void onSongChanged() {
            runOnUiThread(MainActivity.this::refreshCurrentSongBar);
        }
    };
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
    private boolean isPlaylistEditLocked = false;

    // 添加B站音乐的回调接口
    public interface biliCallback<T> {
        void onResult(T result);
    }

    public TextView getDialogMessage() {
        return dialogMessage;
    }

    public ProgressBar getDialogProgressBar() {
        return dialogProgressBar;
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

        playlistTitle = findViewById(R.id.playlistTitle);
        currentSongText = findViewById(R.id.currentSong);
        currentSongScroll = findViewById(R.id.currentSongScroll);
        currentSongLayout = findViewById(R.id.currentSongLayout);
        if (currentSongText != null) {
            marqueeOriginalPaddingRightPx = currentSongText.getPaddingRight();
        }

        if (currentSongLayout != null) {
            currentSongLayout.setOnClickListener(v -> toggleMarquee());
        } else if (currentSongText != null) {
            currentSongText.setOnClickListener(v -> toggleMarquee());
        }

        // 获取全局MusicPlayer实例
        musicPlayer = ((MyApp) getApplication()).getMusicPlayer();

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
        listview.setOnItemLongClickListener((parent, view, position, id) -> handleSongLongClick(position));

        // 根据当前播放状态设置UI
        if (isCurrentlyPlaying) {
            playBtn.setEnabled(true);
            playBtn.setText(musicPlayer.isPlaying() ? "暂停" : "播放");
            progressBar.setMax(song.getTimeDuration());
            // 保持当前进度
            TextView currentSongTV = findViewById(R.id.currentSong);
            currentSongTV.setText(song.getName());
        } else {
            playBtn.setEnabled(false);
            playBtn.setText("播放");
            progressBar.setMax(song.getTimeDuration());
            progressBar.setProgress(0);
        }

        MusicViewModel viewModel = new ViewModelProvider(this).get(MusicViewModel.class);

        // 获取要显示的歌单
        currentPlaylist = getIntent().getStringExtra("playlist");
        if (currentPlaylist == null) {
            currentPlaylist = "";
        }

        if (playlistTitle != null) {
            playlistTitle.setText(currentPlaylist.isEmpty() ? "未选择歌单" : currentPlaylist);
        }

        // 加载歌单但不影响播放状态
        if (!currentPlaylist.isEmpty()) {
            loadMusicList(listview, currentPlaylist);
            updatePlaylistEditLockUi();
        } else {
            musicList.clear();
            if (listview.getAdapter() instanceof BatchModeAdapter) {
                ((BatchModeAdapter) listview.getAdapter()).setData(musicList);
            } else {
                BatchModeAdapter emptyAdapter = new BatchModeAdapter(
                        this,
                        musicList,
                        R.layout.playlist_layout,
                        new String[]{"index", "name", "TimeDuration", "isSelected"},
                        new int[]{R.id.seq, R.id.musicname, R.id.musiclength, R.id.cbSelect});
                listview.setAdapter(emptyAdapter);
            }
            updateNavButtons();
        }

        refreshCurrentSongBar();
        refreshHeaderCover();

        RadioGroup radioGroup = findViewById(R.id.radiogroup);
        if (radioGroup != null) {
            MusicPlayer.PlayMode mode = musicPlayer.getPlayMode();
            if (mode == MusicPlayer.PlayMode.SHUFFLE) {
                radioGroup.check(R.id.shuffled);
            } else if (mode == MusicPlayer.PlayMode.SINGLE_LOOP) {
                radioGroup.check(R.id.singleendless);
            } else {
                radioGroup.check(R.id.sequential);
            }
            radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (checkedId == R.id.shuffled) {
                    musicPlayer.setPlayMode(MusicPlayer.PlayMode.SHUFFLE);
                } else if (checkedId == R.id.singleendless) {
                    musicPlayer.setPlayMode(MusicPlayer.PlayMode.SINGLE_LOOP);
                } else {
                    musicPlayer.setPlayMode(MusicPlayer.PlayMode.SEQUENTIAL);
                }
            });
        }

        new Thread(() -> {
            try {
                java.util.Set<String> keep = MusicLoader.loadAllSongFilePaths(MainActivity.this);
                int deleted = SongDeletionUtils.cleanupOrphanedAppOwnedAudioFiles(MainActivity.this, keep);
                int deletedCovers = SongDeletionUtils.cleanupOrphanedCoverCaches(MainActivity.this);
                if (deleted > 0) {
                    Log.d(TAG, "已清理无引用下载文件: " + deleted);
                }
                if (deletedCovers > 0) {
                    Log.d(TAG, "已清理无引用封面缓存: " + deletedCovers);
                }
            } catch (Exception e) {
                Log.e(TAG, "清理无引用下载文件失败: " + e.getMessage());
            }
        }).start();

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
            if (isPlaylistEditLocked) {
                return;
            }
            List<Integer> positionsToDelete = new ArrayList<>();
            List<Song> songsToDelete = new ArrayList<>();
            for (int i = 0; i < musicList.size(); i++) {
                if ((Boolean) musicList.get(i).get("isSelected")) {
                    positionsToDelete.add(i); // 直接记录索引
                    songsToDelete.add(Song.fromMap(musicList.get(i)));
                }
            }

            if (positionsToDelete.isEmpty()) {
                Toast.makeText(MainActivity.this, "请先选择要删除的歌曲", Toast.LENGTH_SHORT).show();
                return;
            }

            LinearLayout layout = new LinearLayout(MainActivity.this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 20, 50, 10);

            TextView message = new TextView(MainActivity.this);
            message.setText("确定要删除所选歌曲吗？");
            message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            layout.addView(message);

            CheckBox cbDeleteFile = new CheckBox(MainActivity.this);
            cbDeleteFile.setText("同时删除对应文件（仅限App下载/导入目录）");
            cbDeleteFile.setChecked(true);
            layout.addView(cbDeleteFile);

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("确认删除")
                    .setView(layout)
                    .setPositiveButton("确定", (dialog, which) -> {
                        boolean shouldDeleteFiles = cbDeleteFile.isChecked();

                        java.util.HashMap<String, Integer> deleteCounts = new java.util.HashMap<>();
                        for (Song s : songsToDelete) {
                            String fp = s.getFilePath();
                            deleteCounts.put(fp, deleteCounts.getOrDefault(fp, 0) + 1);
                        }
                        java.util.HashSet<String> processedPaths = new java.util.HashSet<>();

                        for (Song s : songsToDelete) {
                            if (shouldDeleteFiles) {
                                String fp = s.getFilePath();
                                if (!processedPaths.contains(fp)) {
                                    processedPaths.add(fp);
                                    int refs = MusicLoader.countFilePathReferences(MainActivity.this, fp);
                                    int delCount = deleteCounts.getOrDefault(fp, 1);
                                    if (refs <= delCount) {
                                        SongDeletionUtils.deleteAppOwnedAudioFile(MainActivity.this, fp);
                                    }
                                }
                            }
                        }

                        boolean isCurrentSongDeleted = false;
                        Song currentSong = musicPlayer.getCurrentSong();
                        String currentPath = (currentSong != null) ? currentSong.getFilePath() : "";
                        for (Song s : songsToDelete) {
                            if (s.getFilePath().equals(currentPath)) {
                                isCurrentSongDeleted = true;
                                break;
                            }
                        }

                        if (isCurrentSongDeleted) {
                            musicPlayer.resetProgress();
                            runOnUiThread(() -> {
                                if (musicPlayer.isPlaying()) {
                                    musicPlayer.stop();
                                    musicPlayer.release();
                                }
                                progressBar.setProgress(0);
                                preogress.setText("请选择歌曲");
                                TextView currentSongTV = findViewById(R.id.currentSong);
                                currentSongTV.setText("当前播放：暂无歌曲");
                                playBtn.setText("播放");
                                playBtn.setEnabled(false);
                                selectedPosition = -1;
                                listview.clearChoices();
                                listview.requestLayout();
                            });
                        }

                        positionsToDelete.sort(Collections.reverseOrder());
                        for (int pos : positionsToDelete) {
                            musicList.remove(pos);
                        }
                        updateSongIndices();
                        updatePersistentStorage();
                        SongDeletionUtils.cleanupOrphanedCoverCaches(MainActivity.this);

                        BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
                        if (adapter != null) {
                            adapter.setData(musicList);
                            adapter.setBatchMode(false);
                        }

                        refreshHeaderCover();

                        isBatchMode = false;
                        cbSelectAll.setVisibility(View.GONE);
                        cbSelectAll.setChecked(false);
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

                        dialog.dismiss();
                    })
                    .setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
            builder.create().show();
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
            if (isPlaylistEditLocked) {
                return;
            }
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
                refreshHeaderCover();
            }
        });

        btnMoveDown.setOnClickListener(v -> {
            if (isPlaylistEditLocked) {
                return;
            }
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
                refreshHeaderCover();
            }
        });
//        deletebtn.setOnClickListener(v -> {
//            if (selectedPosition == -1) {
//                Toast.makeText(MainActivity.this, "请先选择要删除的歌曲", Toast.LENGTH_SHORT).show();
//                return;
//            }
//            showDeleteConfirmationDialog();
//        });

        // 添加分享按钮点击事件
        ImageButton shareBtn = findViewById(R.id.sharePlaylist);
        shareBtn.setOnClickListener(v -> {
            showSharePlaylistDialog();
        });

        ImageButton playlistSearchBtn = findViewById(R.id.btnPlaylistSearch);
        if (playlistSearchBtn != null) {
            playlistSearchBtn.setOnClickListener(v -> showPlaylistSearchDialog());
        }

        // 添加按钮
        ImageButton Addbtn = findViewById(R.id.addMusic);
        Addbtn.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("选择添加方式");

            // 设置选项 - 添加"搜索在线音乐"选项
            String[] options = {"从本地添加", "从BV号 / 链接添加", "从B站收藏夹添加", "搜索在线音乐", "导入分享的歌曲"};
            builder.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // 添加本地音乐
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("audio/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // 允许多选
                    startActivityForResult(intent, PICK_MUSIC_REQUEST);
                } else if (which == 1) {
                    Intent intent = new Intent(MainActivity.this, BiliSharePickerActivity.class);
                    startActivityForResult(intent, REQUEST_CODE_BILI_SHARE_PICKER);
                } else if (which == 2) {
                    Intent intent = new Intent(MainActivity.this, BiliFavPickerActivity.class);
                    intent.putExtra(BiliFavPickerActivity.EXTRA_CURRENT_PLAYLIST, currentPlaylist);
                    startActivityForResult(intent, REQUEST_CODE_BILI_FAV_PICKER);
                } else if (which == 3) {
                    // 搜索在线音乐
                    Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                    intent.putExtra("current_playlist", currentPlaylist); // 传递当前歌单名称
                    intent.putExtra("from_activity", "MainActivity"); // 标记来源页面
                    startActivityForResult(intent, REQUEST_CODE_SEARCH);
                } else if (which == 4) {
                    // 导入分享歌单
                    showImportShareCodeDialog();

                    updatePlaylistCover(currentPlaylist);
                    ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
                    refreshHeaderCover();
                    updateNavButtons(); // 添加这行来启用按钮
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

    private void showImportShareCodeDialog() {
        SharePlaylistUtils.showImportShareCodeDialog(this, currentPlaylist);
    }

    private void showSharePlaylistDialog() {
        SharePlaylistUtils.showSharePlaylistDialog(this, currentPlaylist, musicList);
    }

    // 回调
    @Override
    public void onSongCompleted() {
        Log.d("MainActivity", "收到播放完成回调");
        runOnUiThread(() -> {
            try {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (musicPlayer.isSongChanging()) {
                    return;
                }
                if (isSongChanging) {
                    return;
                }
                if (musicPlayer.getPlayQueueSize() <= 0 && !musicList.isEmpty()) {
                    playNextSong();
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    updateAllPlayButtons();
                    refreshCurrentSongBar();
                }, 100);
            } catch (IOException e) {
                Log.e("MainActivity", "播放完成后切换下一首失败", e);
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

    private boolean handleSongLongClick(int position) {
        if (position < 0 || position >= musicList.size()) {
            return false;
        }
        Song s = Song.fromMap(musicList.get(position));
        if (s == null) {
            return false;
        }
        String original = buildBiliOriginalLink(s);
        if (original == null || original.isEmpty()) {
            return false;
        }

        String[] options = {"复制B站原始链接"};
        new AlertDialog.Builder(this)
                .setTitle("操作")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        android.content.ClipData clip = android.content.ClipData.newPlainText("bili_link", original);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(clip);
                        }
                        Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        return true;
    }

    private String buildBiliOriginalLink(Song song) {
        if (song == null) {
            return null;
        }
        String fp = song.getFilePath();
        if (fp == null || fp.isEmpty()) {
            return null;
        }
        String raw = fp;
        if (raw.toLowerCase(Locale.ROOT).startsWith("bili://")) {
            raw = raw.substring("bili://".length());
        }
        String bvid = com.example.myapplication.utils.BiliLinkParser.extractBvid(raw);
        if (bvid == null || bvid.isEmpty()) {
            return null;
        }
        return "https://www.bilibili.com/video/" + bvid;
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
            progressBar.setProgress(0);
            preogress.setText("00:00");
        });
        refreshHeaderCover();
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

    private boolean isBiliBoundPlaylist(String playlistName) {
        if (playlistName == null || playlistName.isEmpty()) {
            return false;
        }
        SharedPreferences prefs = getSharedPreferences(PlaylistListActivity.PREFS, MODE_PRIVATE);
        String json = prefs.getString(PlaylistListActivity.KEY_PLAYLISTS, null);
        if (json == null || json.isEmpty()) {
            return false;
        }
        try {
            List<Playlist> list = Playlist.fromJson(json);
            if (list == null) {
                return false;
            }
            for (Playlist p : list) {
                if (p != null && playlistName.equals(p.getName())) {
                    return p.isBiliBound();
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void updatePlaylistEditLockUi() {
        isPlaylistEditLocked = isBiliBoundPlaylist(currentPlaylist);

        View btnAddMusic = findViewById(R.id.addMusic);
        View btnSharePlaylist = findViewById(R.id.sharePlaylist);
        View btnPlaylistSearch = findViewById(R.id.btnPlaylistSearch);
        View btnBatchSelect = findViewById(R.id.btnBatchSelect);
        View btnMoveUp = findViewById(R.id.btnMoveUp);
        View btnMoveDown = findViewById(R.id.btnMoveDown);
        View btnBatchDelete = findViewById(R.id.btnBatchDelete);

        if (isPlaylistEditLocked) {
            if (isBatchMode) {
                isBatchMode = false;
                if (listview != null && listview.getAdapter() instanceof BatchModeAdapter) {
                    ((BatchModeAdapter) listview.getAdapter()).setBatchMode(false);
                }
            }
            selectedPositions.clear();
            isAllSelected = false;
            if (cbSelectAll != null) {
                cbSelectAll.setChecked(false);
                cbSelectAll.setVisibility(View.GONE);
            }
            if (btnBatchDelete != null) {
                btnBatchDelete.setVisibility(View.GONE);
            }
            if (btnMoveUp != null) {
                btnMoveUp.setVisibility(View.GONE);
            }
            if (btnMoveDown != null) {
                btnMoveDown.setVisibility(View.GONE);
            }
            if (btnBatchSelect != null) {
                btnBatchSelect.setVisibility(View.GONE);
            }
            if (btnAddMusic != null) {
                btnAddMusic.setVisibility(View.GONE);
            }
            if (btnSharePlaylist != null) {
                btnSharePlaylist.setVisibility(View.GONE);
            }
            if (btnPlaylistSearch != null) {
                btnPlaylistSearch.setVisibility(View.VISIBLE);
            }
            if (listview != null) {
                listview.clearChoices();
            }
            if (listview != null && listview.getAdapter() instanceof BaseAdapter) {
                ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
            }
            updateTopActionButtonsLayout(true);
            return;
        }

        if (btnAddMusic != null) {
            btnAddMusic.setVisibility(View.VISIBLE);
        }
        if (btnSharePlaylist != null) {
            btnSharePlaylist.setVisibility(View.VISIBLE);
        }
        if (btnPlaylistSearch != null) {
            btnPlaylistSearch.setVisibility(View.VISIBLE);
        }
        if (btnBatchSelect != null) {
            btnBatchSelect.setVisibility(View.VISIBLE);
        }
        if (btnMoveUp != null) {
            btnMoveUp.setVisibility(isBatchMode ? View.GONE : View.VISIBLE);
        }
        if (btnMoveDown != null) {
            btnMoveDown.setVisibility(isBatchMode ? View.GONE : View.VISIBLE);
        }
        if (btnBatchDelete != null) {
            btnBatchDelete.setVisibility(isBatchMode ? View.VISIBLE : View.GONE);
        }
        if (cbSelectAll != null) {
            cbSelectAll.setVisibility(isBatchMode ? View.VISIBLE : View.GONE);
        }
        updateTopActionButtonsLayout(false);
    }

    private void updateTopActionButtonsLayout(boolean locked) {
        ConstraintLayout root = findViewById(R.id.main);
        View btnSearch = findViewById(R.id.btnPlaylistSearch);
        if (root == null || btnSearch == null) {
            return;
        }
        ConstraintSet set = new ConstraintSet();
        set.clone(root);
        set.clear(R.id.btnPlaylistSearch, ConstraintSet.START);
        set.clear(R.id.btnPlaylistSearch, ConstraintSet.END);
        if (locked) {
            set.connect(R.id.btnPlaylistSearch, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, dpToPx(16));
        } else {
            set.connect(R.id.btnPlaylistSearch, ConstraintSet.END, R.id.sharePlaylist, ConstraintSet.START, dpToPx(2));
        }
        set.applyTo(root);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void showPlaylistSearchDialog() {
        List<Map<String, Object>> sourceSongs = new ArrayList<>(musicList);
        if (sourceSongs.isEmpty()) {
            Toast.makeText(this, "当前歌单暂无歌曲", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(16);
        layout.setPadding(padding, padding, padding, dpToPx(8));

        EditText etKeyword = new EditText(this);
        etKeyword.setHint("输入歌曲名、歌手名或关键字");
        layout.addView(etKeyword, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ListView resultListView = new ListView(this);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(320)
        );
        listParams.topMargin = dpToPx(12);
        layout.addView(resultListView, listParams);

        TextView emptyView = new TextView(this);
        emptyView.setText("未找到匹配歌曲");
        emptyView.setPadding(0, dpToPx(12), 0, dpToPx(12));
        emptyView.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        layout.addView(emptyView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        resultListView.setEmptyView(emptyView);

        List<String> labels = new ArrayList<>();
        List<Integer> matchedIndices = new ArrayList<>();
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                labels
        );
        resultListView.setAdapter(adapter);

        Runnable refreshResults = () -> {
            String keyword = etKeyword.getText() != null ? etKeyword.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
            labels.clear();
            matchedIndices.clear();
            for (int i = 0; i < sourceSongs.size(); i++) {
                Map<String, Object> songMap = sourceSongs.get(i);
                Song candidate = Song.fromMap(songMap);
                if (candidate == null) {
                    continue;
                }
                String name = candidate.getName() != null ? candidate.getName() : "";
                String filePath = candidate.getFilePath() != null ? candidate.getFilePath() : "";
                String haystack = (name + " " + filePath).toLowerCase(Locale.ROOT);
                if (!keyword.isEmpty() && !haystack.contains(keyword)) {
                    continue;
                }
                labels.add(name);
                matchedIndices.add(i);
            }
            adapter.notifyDataSetChanged();
        };

        refreshResults.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("歌单内搜索")
                .setView(layout)
                .setNegativeButton("关闭", null)
                .create();

        resultListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= matchedIndices.size()) {
                return;
            }
            int targetIndex = matchedIndices.get(position);
            selectedPosition = targetIndex;
            if (listview != null) {
                listview.setItemChecked(targetIndex, true);
                listview.smoothScrollToPosition(targetIndex);
            }
            Toast.makeText(this, "已定位到该歌曲", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        etKeyword.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshResults.run();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });

        dialog.show();
    }

    private void appendResolvedBiliSongToCurrentPlaylist(Map<String, Object> result, @Nullable String fallbackTitle,
                                                         @Nullable String fallbackCoverUrl) {
        if (result == null || result.isEmpty()) {
            return;
        }
        String title = (String) result.get("title");
        if (title == null || title.trim().isEmpty()) {
            title = fallbackTitle;
        }
        if (title == null || title.trim().isEmpty()) {
            title = "B站音频";
        }

        String path = (String) result.get("filePath");
        if (path == null || path.trim().isEmpty()) {
            Object bvid = result.get("bvid");
            Object cid = result.get("cid");
            long cidValue = cid instanceof Number ? ((Number) cid).longValue() : -1L;
            path = BiliAudioDownloadHelper.buildPlaceholderPath(bvid != null ? String.valueOf(bvid) : null, cidValue);
        }
        if (path == null || path.trim().isEmpty()) {
            return;
        }

        int duration = 0;
        Object durationObj = result.get("durationSec");
        if (durationObj instanceof Number) {
            duration = Math.max(0, ((Number) durationObj).intValue());
        }

        String coverUrl = (String) result.get("coverUrl");
        if (coverUrl == null || coverUrl.trim().isEmpty()) {
            coverUrl = fallbackCoverUrl;
        }

        Song newSong = new Song(duration, title, path, currentPlaylist);
        newSong.setCoverUrl(coverUrl);
        addSongToPlaylist(newSong);
        updatePlaylistCover(currentPlaylist);
        MusicLoader.appendMusic(this, newSong);
        if (listview != null && listview.getAdapter() != null) {
            ((BaseAdapter) listview.getAdapter()).notifyDataSetChanged();
        }
        refreshHeaderCover();
        updateNavButtons();
    }

    // 字面意思
    private void toggleBatchMode() {
        if (isPlaylistEditLocked) {
            return;
        }
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

        int newPosition;
        int basePos = musicPlayer.getQueueIndex();
        if (basePos < 0 || basePos >= musicList.size()) {
            basePos = selectedPosition;
        }
        MusicPlayer.PlayMode mode = musicPlayer.getPlayMode();
        if (mode == MusicPlayer.PlayMode.SHUFFLE) {
            newPosition = new Random().nextInt(musicList.size());
        } else if (mode == MusicPlayer.PlayMode.SINGLE_LOOP) {
            newPosition = basePos;
        } else {
            newPosition = (basePos + 1) % musicList.size();
        }

        // 播放新歌曲
        selectedPosition = newPosition;
        musicPlayer.setQueueContext(currentPlaylist, selectedPosition);
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
        refreshHeaderCover();
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
        refreshHeaderCover();
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

    void updateNavButtons() {
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
        List<Map<String, Object>> loaded = MusicLoader.loadSongs(listview.getContext(), currentPlaylist);
        musicList.clear();
        musicList.addAll(loaded);
        // 确保适配器更新
        if (listview != null) {
            BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
            if (adapter != null) {
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
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;

                new Thread(() -> {
                    Bitmap outputBitmap = null;
                    try {
                        Bitmap originalBitmap = decodeSampledBitmapFromFile(playbackBgPath, screenWidth, screenHeight);
                        if (originalBitmap != null) {
                            Bitmap scaledBitmap = createScaledBitmapForImageView(originalBitmap, screenWidth, screenHeight);
                            if (originalBitmap != scaledBitmap) {
                                originalBitmap.recycle();
                            }
                            outputBitmap = scaledBitmap;
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
                            android.graphics.drawable.BitmapDrawable backgroundDrawable = new android.graphics.drawable.BitmapDrawable(
                                    getResources(), finalBitmap);
                            backgroundDrawable.setAlpha(180);
                            drawerLayout.setBackground(backgroundDrawable);
                        } else {
                            setSystemThemeBackground(drawerLayout);
                        }
                    });
                }).start();
            } else {
                // 设置系统主题背景
                setSystemThemeBackground(drawerLayout);
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

    // 图片缩放方法
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
        visibleUiAutoRefresh.stop();
        if (musicPlayer != null) {
            musicPlayer.setOnSongCompletionListener(null);
            musicPlayer.removeOnPlaybackStateChangeListener(playlistPagePlaybackListener);
        }
        // 暂停时不关闭进度对话框，但记录状态
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (musicPlayer != null) {
            musicPlayer.setOnSongCompletionListener(this);
        }
        // 每次返回时重新应用背景，以防用户更改了设置
        applyPlaybackBackground();

        RadioGroup radioGroup = findViewById(R.id.radiogroup);
        if (radioGroup != null) {
            MusicPlayer.PlayMode mode = musicPlayer.getPlayMode();
            if (mode == MusicPlayer.PlayMode.SHUFFLE) {
                radioGroup.check(R.id.shuffled);
            } else if (mode == MusicPlayer.PlayMode.SINGLE_LOOP) {
                radioGroup.check(R.id.singleendless);
            } else {
                radioGroup.check(R.id.sequential);
            }
        }

        int pos = getCurrentSongPosition();
        if (pos >= 0 && pos < musicList.size()) {
            selectedPosition = pos;
            if (listview != null) {
                listview.setItemChecked(pos, true);
            }
        }

        musicPlayer.addOnPlaybackStateChangeListener(playlistPagePlaybackListener);
        refreshCurrentSongBar();

        // 强制刷新底部播放栏状态
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.attachToActivity(this);
        globalManager.forceRefresh();
        updatePlaylistEditLockUi();
        visibleUiAutoRefresh.start();
        // 恢复时检查进度对话框状态
    }

    private void refreshVisibleUiIfNeeded() {
        refreshCurrentSongBar();
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.forceRefresh();
    }

    private void refreshCurrentSongBar() {
        if (currentSongText == null) {
            currentSongText = findViewById(R.id.currentSong);
        }

        Song current = musicPlayer != null ? musicPlayer.getCurrentSong() : null;
        if (currentSongText != null) {
            String displayText;
            if (current == null || current.getName() == null || current.getName().isEmpty() || "暂无歌曲".equals(current.getName())) {
                displayText = "当前播放：暂无歌曲";
            } else {
                String p = musicPlayer != null ? musicPlayer.getQueuePlaylist() : null;
                if (p == null || p.isEmpty()) {
                    p = current.getPlaylist();
                }
                if (p == null || p.isEmpty()) {
                    p = currentPlaylist;
                }
                displayText = "当前播放：" + current.getName() + "  ·  " + p;
            }

            if (lastCurrentSongDisplayText == null || !lastCurrentSongDisplayText.equals(displayText)) {
                lastCurrentSongDisplayText = displayText;
                currentSongText.setText(displayText);
                stopMarquee();
            }
        }

        int pos = getCurrentSongPosition();
        if (pos >= 0 && listview != null && pos < musicList.size()) {
            selectedPosition = pos;
            listview.setItemChecked(pos, true);
        }
    }

    private void toggleMarquee() {
        if (currentSongText == null) {
            currentSongText = findViewById(R.id.currentSong);
        }
        if (currentSongScroll == null) {
            currentSongScroll = findViewById(R.id.currentSongScroll);
        }
        if (currentSongText == null) {
            return;
        }

        if (marqueeAnimator == null) {
            startMarqueeIfNeeded();
            return;
        }

        if (marqueeAnimator.isPaused()) {
            marqueeAnimator.resume();
        } else if (marqueeAnimator.isRunning()) {
            marqueeAnimator.pause();
        } else {
            startMarqueeIfNeeded();
        }
    }

    private void startMarqueeIfNeeded() {
        if (currentSongText == null || currentSongScroll == null) {
            return;
        }
        currentSongText.post(() -> {
            if (currentSongText == null || currentSongScroll == null) {
                return;
            }
            int containerWidth = currentSongScroll.getWidth();
            float textWidth = currentSongText.getPaint().measureText(String.valueOf(currentSongText.getText()));
            if (containerWidth <= 0 || textWidth <= containerWidth) {
                stopMarquee();
                return;
            }

            marqueeExtraPaddingRightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) currentSongScroll.getLayoutParams();
            if (lp != null) {
                lp.width = 0;
                lp.weight = 1;
                currentSongScroll.setLayoutParams(lp);
            }

            ViewGroup.LayoutParams tvLp = currentSongText.getLayoutParams();
            tvLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            currentSongText.setLayoutParams(tvLp);
            currentSongText.setSingleLine(true);
            currentSongText.setEllipsize(null);
            currentSongText.setPadding(currentSongText.getPaddingLeft(), currentSongText.getPaddingTop(), marqueeOriginalPaddingRightPx + marqueeExtraPaddingRightPx, currentSongText.getPaddingBottom());

            currentSongText.requestLayout();
            currentSongText.invalidate();
            currentSongScroll.scrollTo(0, 0);

            int maxScroll = (int) Math.ceil(textWidth - containerWidth + marqueeExtraPaddingRightPx);
            long duration = (long) (maxScroll * 15);
            duration = Math.max(3000L, Math.min(duration, 22000L));

            cancelMarqueeAnimator();
            marqueeAnimator = ValueAnimator.ofInt(0, Math.max(1, maxScroll));
            marqueeAnimator.setDuration(duration);
            marqueeAnimator.setInterpolator(new LinearInterpolator());
            marqueeAnimator.setRepeatCount(ValueAnimator.INFINITE);
            marqueeAnimator.setRepeatMode(ValueAnimator.RESTART);
            marqueeAnimator.addUpdateListener(animation -> {
                if (currentSongScroll != null) {
                    currentSongScroll.scrollTo((int) animation.getAnimatedValue(), 0);
                }
            });
            marqueeAnimator.start();
        });
    }

    private void cancelMarqueeAnimator() {
        if (marqueeAnimator != null) {
            try {
                marqueeAnimator.cancel();
            } catch (Exception ignored) {
            }
            marqueeAnimator = null;
        }
        if (currentSongScroll != null) {
            currentSongScroll.scrollTo(0, 0);
        }
    }

    private void stopMarquee() {
        cancelMarqueeAnimator();
        if (currentSongText == null) {
            return;
        }
        ViewGroup.LayoutParams tvLp = currentSongText.getLayoutParams();
        tvLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        currentSongText.setLayoutParams(tvLp);
        currentSongText.setSingleLine(true);
        currentSongText.setEllipsize(TextUtils.TruncateAt.END);
        currentSongText.setPadding(currentSongText.getPaddingLeft(), currentSongText.getPaddingTop(), marqueeOriginalPaddingRightPx, currentSongText.getPaddingBottom());
        currentSongText.requestLayout();
        currentSongText.invalidate();
    }

    private void togglePlayPauseFromBar() {
        Song current = musicPlayer != null ? musicPlayer.getCurrentSong() : null;
        if (current == null || current.getName() == null || current.getName().isEmpty() || "暂无歌曲".equals(current.getName())) {
            if (selectedPosition >= 0 && selectedPosition < musicList.size()) {
                try {
                    playSongAt(selectedPosition);
                } catch (IOException e) {
                    Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "暂无歌曲", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (musicPlayer.isPlaying()) {
            switchPlayStatus(PlayerStatus.PAUSED);
        } else if (musicPlayer.isPaused()) {
            switchPlayStatus(PlayerStatus.PLAYING);
        } else {
            if (selectedPosition >= 0 && selectedPosition < musicList.size()) {
                try {
                    playSongAt(selectedPosition);
                } catch (IOException e) {
                    Toast.makeText(this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
        updateAllPlayButtons();
        GlobalBottomPlayerManager globalManager = ((MyApp) getApplication()).getGlobalBottomPlayerManager();
        globalManager.forceRefresh();
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
        currentPlaylist = savedInstanceState.getString("currentPlaylist", "");
        boolean wasDownloading = savedInstanceState.getBoolean("isDownloading", false);
        if (wasDownloading) {
            // 如果之前在下载，重新加载歌单
            if (currentPlaylist != null && !currentPlaylist.isEmpty()) {
                loadMusicList(listview, currentPlaylist);
            }
        }
    }

    @Override
    protected void onDestroy() {
        visibleUiAutoRefresh.stop();
        if (musicPlayer != null) {
            musicPlayer.setOnSongCompletionListener(null);
        }
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

        if (requestCode == REQUEST_CODE_BILI_SHARE_PICKER && resultCode == RESULT_OK && data != null) {
            ArrayList<String> ids = data.getStringArrayListExtra(BiliSharePickerActivity.EXTRA_SELECTED_IDS);
            ArrayList<String> titles = data.getStringArrayListExtra(BiliSharePickerActivity.EXTRA_SELECTED_TITLES);
            ArrayList<String> covers = data.getStringArrayListExtra(BiliSharePickerActivity.EXTRA_SELECTED_COVERS);
            long[] epIds = data.getLongArrayExtra(BiliSharePickerActivity.EXTRA_SELECTED_EPIDS);
            if (ids != null && !ids.isEmpty()) {
                downloadBiliShareSelected(ids, titles, covers, epIds);
            }
        }

        if (requestCode == REQUEST_CODE_BILI_FAV_PICKER && resultCode == RESULT_OK && data != null) {
            ArrayList<String> bvids = data.getStringArrayListExtra(BiliFavPickerActivity.EXTRA_SELECTED_BVIDS);
            if (bvids != null && !bvids.isEmpty()) {
                downloadBiliFavSelected(bvids);
            }
        }

        // 处理“搜索并添加在线音乐”返回
        if (requestCode == REQUEST_CODE_SEARCH && resultCode == RESULT_OK) {
            if (data != null && data.getBooleanExtra("should_refresh", false)) {
                // 先把最新的 musicList 同步给 adapter 了
                BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
                if (adapter != null) {
                    adapter.setData(musicList);
                    adapter.notifyDataSetChanged();
                }
                refreshHeaderCover();
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

    private void downloadBiliFavSelected(List<String> bvids) {
        AtomicInteger successCnt = new AtomicInteger();
        AtomicInteger failCnt = new AtomicInteger();
        runOnUiThread(() -> {
            showProgressDialog();
            dialogProgressBar.setIndeterminate(false);
            dialogProgressBar.setProgress(0);
            dialogMessage.setText("正在添加B站音频...");
        });

        for (String bv : bvids) {
            getBiliMusic(bv, this, true, result -> runOnUiThread(() -> {
                if (result != null && !result.isEmpty()) {
                    appendResolvedBiliSongToCurrentPlaylist(result, null, null);

                    successCnt.getAndIncrement();
                    int progress = successCnt.get() * 100 / bvids.size();
                    dialogProgressBar.setProgress(progress);
                    dialogMessage.setText("正在添加B站音频... (" + successCnt.get() + "/" + bvids.size() + ")");
                } else {
                    failCnt.getAndIncrement();
                    Toast.makeText(MainActivity.this, "获取B站音乐失败", Toast.LENGTH_SHORT).show();
                }

                if (successCnt.get() + failCnt.get() >= bvids.size()) {
                    dismissProgressDialog();
                    Toast.makeText(MainActivity.this, "已添加到歌单", Toast.LENGTH_SHORT).show();
                }
            }));
        }
    }

    private void downloadBiliShareSelected(ArrayList<String> ids, @Nullable ArrayList<String> titles,
                                          @Nullable ArrayList<String> covers, @Nullable long[] epIds) {
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger successCnt = new AtomicInteger();
        AtomicInteger failCnt = new AtomicInteger();
        int total = ids.size();

        runOnUiThread(() -> {
            showProgressDialog();
            dialogProgressBar.setIndeterminate(false);
            dialogProgressBar.setProgress(0);
            dialogMessage.setText("正在添加B站音频... (0/" + total + ")");
        });

        for (int i = 0; i < total; i++) {
            String uniqueId = ids.get(i);
            String[] parts = uniqueId != null ? uniqueId.split("_") : new String[0];
            if (parts.length < 2) {
                failCnt.incrementAndGet();
                int done = completed.incrementAndGet();
                updateShareProgress(done, total, successCnt.get(), failCnt.get());
                continue;
            }
            String bvid = parts[0];
            long cid;
            try {
                cid = Long.parseLong(parts[1]);
            } catch (Exception e) {
                failCnt.incrementAndGet();
                int done = completed.incrementAndGet();
                updateShareProgress(done, total, successCnt.get(), failCnt.get());
                continue;
            }
            Long epId = null;
            if (epIds != null && i < epIds.length && epIds[i] > 0) {
                epId = epIds[i];
            }
            String title = titles != null && i < titles.size() ? titles.get(i) : uniqueId;
            String coverUrl = covers != null && i < covers.size() ? covers.get(i) : null;

            downloadSingleBiliVideo(bvid, cid, title, coverUrl, epId, result -> {
                int done = completed.incrementAndGet();
                if (result != null && !result.isEmpty()) {
                    successCnt.incrementAndGet();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        appendResolvedBiliSongToCurrentPlaylist(result, title, coverUrl);
                    });
                } else {
                    failCnt.incrementAndGet();
                }

                updateShareProgress(done, total, successCnt.get(), failCnt.get());
            });
        }
    }

    private void updateShareProgress(int completed, int total, int success, int fail) {
        runOnUiThread(() -> {
            if (progressDialog == null || dialogProgressBar == null || dialogMessage == null) {
                return;
            }
            int percent = total > 0 ? (completed * 100 / total) : 0;
            dialogProgressBar.setProgress(percent);
            dialogMessage.setText("正在获取B站音频... (" + completed + "/" + total + ")");
            if (completed >= total) {
                dismissProgressDialog();
                Toast.makeText(MainActivity.this, "完成：成功 " + success + "，失败 " + fail, Toast.LENGTH_SHORT).show();
            }
        });
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

    void showProgressDialog() {
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

    void dismissProgressDialog() {
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
                    int firstVisiblePosBefore = listview != null ? listview.getFirstVisiblePosition() : -1;
                    int firstChildTopBefore = 0;
                    if (listview != null && listview.getChildCount() > 0 && listview.getChildAt(0) != null) {
                        firstChildTopBefore = listview.getChildAt(0).getTop();
                    }

                    for (Song song : newSongs) {
                        MusicLoader.appendMusic(this, song);
                        addSongToPlaylist(song);
                    }

                    BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
                    if (adapter != null) {
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

                    int playingPos = getCurrentSongPosition();
                    if (playingPos >= 0 && playingPos < musicList.size()) {
                        selectedPosition = playingPos;
                        musicPlayer.setQueueContext(currentPlaylist, playingPos);
                        listview.setItemChecked(playingPos, true);
                    }

                    if (firstVisiblePosBefore == 0 && firstChildTopBefore == 0) {
                        listview.setSelection(0);
                    }
                    updatePlaylistCover(currentPlaylist);
                    refreshHeaderCover();
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
    private void deleteSelectedSong(int position, boolean deleteLocalFile) {

        // 检查索引有效性
        if (position < 0 || position >= musicList.size()) {
            Toast.makeText(this, "无效的删除位置", Toast.LENGTH_SHORT).show();
            return;
        }

        Song songToDelete = Song.fromMap(musicList.get(position));
        if (deleteLocalFile) {
            int refs = MusicLoader.countFilePathReferences(this, songToDelete.getFilePath());
            if (refs <= 1) {
                SongDeletionUtils.deleteAppOwnedAudioFile(this, songToDelete.getFilePath());
            } else {
                Toast.makeText(this, "该歌曲文件还被其他歌单引用，已仅从当前歌单移除", Toast.LENGTH_SHORT).show();
            }
        }

        Song currentSong = musicPlayer.getCurrentSong();
        boolean isCurrentSongDeleted = currentSong != null && songToDelete.equals(currentSong);
        if (selectedPositions.contains(position)) {
            selectedPositions.remove((Integer) position);
        }

        // 检查是否删除的是当前播放歌曲
        // boolean isCurrentSongDeleted = false;

        if (currentSong != null && songToDelete.equals(currentSong)) {
            musicPlayer.stop();
            progressBar.setProgress(0);
            preogress.setText("请选择歌曲");
            isCurrentSongDeleted = true;
            playBtn.setEnabled(false);
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
        SongDeletionUtils.cleanupOrphanedCoverCaches(this);
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
            currentSongTV.setText("当前播放: 暂无歌曲");
        }

        refreshHeaderCover();
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

        CheckBox cbDeleteFile = new CheckBox(this);
        cbDeleteFile.setText("同时删除对应文件（仅限App下载/导入目录）");
        cbDeleteFile.setChecked(true);
        layout.addView(cbDeleteFile);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除")
                .setView(layout) // 设置动态创建的布局
                .setPositiveButton("确定", (dialog, which) -> {
                    deleteSelectedSong(selectedPosition, cbDeleteFile.isChecked());
                    dialog.dismiss();
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void loadMusicList(ListView listview, String playlist) {
        if (playlistTitle != null) {
            playlistTitle.setText(playlist);
        }
        // 保存当前播放的歌曲信息
        Song currentPlayingSong = musicPlayer.getCurrentSong();
        boolean wasPlaying = musicPlayer.isPlaying();
        boolean wasPaused = musicPlayer.isPaused();

        // 加载新歌单的歌曲列表
        List<Map<String, Object>> loaded = MusicLoader.loadSongs(this, playlist);
        musicList.clear();
        musicList.addAll(loaded);
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

        syncBiliBoundPlaylistIfNeeded(playlist);
        refreshHeaderCover();
    }

    private void syncBiliBoundPlaylistIfNeeded(String playlistName) {
        if (playlistName == null || playlistName.isEmpty()) {
            return;
        }
        com.example.myapplication.model.Playlist meta = com.example.myapplication.utils.PlaylistStore.findByName(this, playlistName);
        if (meta == null || !meta.isBiliBound()) {
            return;
        }
        String uid = meta.getBiliUid();
        if (uid == null || uid.isEmpty()) {
            return;
        }
        com.example.myapplication.utils.BiliPlaylistSyncManager.SyncCallback refreshCallback = songCount -> runOnUiThread(() -> {
            if (songCount < 0) {
                return;
            }
            if (isFinishing() || isDestroyed()) {
                return;
            }
            List<Map<String, Object>> refreshed = MusicLoader.loadSongs(this, playlistName);
            musicList.clear();
            musicList.addAll(refreshed);
            for (Map<String, Object> item : musicList) {
                item.put("isSelected", false);
            }
            if (listview != null && listview.getAdapter() instanceof BatchModeAdapter) {
                BatchModeAdapter adapter = (BatchModeAdapter) listview.getAdapter();
                adapter.setData(musicList);
                adapter.notifyDataSetChanged();
            }
            refreshHeaderCover();
            updateNavButtons();
        });

        String bindingType = meta.getEffectiveBiliBindingType();
        if (com.example.myapplication.model.Playlist.BILI_BIND_TYPE_SEASON.equals(bindingType)) {
            String seasonId = meta.getBiliSeasonId();
            if (seasonId == null || seasonId.isEmpty()) {
                return;
            }
            com.example.myapplication.utils.BiliPlaylistSyncManager.syncBoundSeasonToPlaylist(
                    this,
                    new okhttp3.OkHttpClient(),
                    uid,
                    seasonId,
                    playlistName,
                    refreshCallback
            );
            return;
        }
        if (com.example.myapplication.model.Playlist.BILI_BIND_TYPE_SERIES.equals(bindingType)) {
            String seriesId = meta.getBiliSeriesId();
            if (seriesId == null || seriesId.isEmpty()) {
                return;
            }
            com.example.myapplication.utils.BiliPlaylistSyncManager.syncBoundSeriesToPlaylist(
                    this,
                    new okhttp3.OkHttpClient(),
                    uid,
                    seriesId,
                    playlistName,
                    refreshCallback
            );
            return;
        }

        String folderId = meta.getBiliFolderId();
        if (folderId == null || folderId.isEmpty()) {
            return;
        }
        com.example.myapplication.utils.BiliPlaylistSyncManager.syncBoundFolderToPlaylist(
                this,
                new okhttp3.OkHttpClient(),
                uid,
                folderId,
                playlistName,
                refreshCallback
        );
    }

    // 验证文件有效性
    private boolean isFileValid(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        if (filePath.startsWith("bili://")) {
            return true;
        }
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
        } else if (filePath.startsWith("file://")) {
            try {
                return new File(Uri.parse(filePath).getPath()).exists();
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
        musicPlayer.setQueueContext(currentPlaylist, index);
        List<Song> queue = new ArrayList<>();
        for (Map<String, Object> item : musicList) {
            queue.add(Song.fromMap(item));
        }
        musicPlayer.setPlayQueue(currentPlaylist, queue, index);

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
        if (musicPlayer.getPlayQueueSize() > 0) {
            musicPlayer.playNextInQueue();
            updateAllPlayButtons();
            refreshCurrentSongBar();
            return;
        }
        int nextPos;

        if (musicList.isEmpty()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show();
            return;
        }

        int basePos = musicPlayer.getQueueIndex();
        if (basePos < 0 || basePos >= musicList.size()) {
            basePos = selectedPosition;
        }

        MusicPlayer.PlayMode mode = musicPlayer.getPlayMode();
        if (mode == MusicPlayer.PlayMode.SINGLE_LOOP) {
            nextPos = basePos;
        } else if (mode == MusicPlayer.PlayMode.SHUFFLE) {
            nextPos = new Random().nextInt(musicList.size());
        } else {
            nextPos = (basePos + 1) % musicList.size();
        }

        selectedPosition = nextPos;
        musicPlayer.setQueueContext(currentPlaylist, selectedPosition);
        Map<String, Object> map = musicList.get(nextPos);
        selectSong = Song.fromMap(map);

        playSongAt(nextPos);
        song = selectSong;

        TextView currentSongTV = findViewById(R.id.currentSong);
        currentSongTV.setText(selectSong.getName());
        playBtn.setEnabled(true);
        musicPlayer.setCurrentPositiontozero();

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
        if (musicPlayer.getPlayQueueSize() > 0) {
            musicPlayer.playPrevInQueue();
            updateAllPlayButtons();
            refreshCurrentSongBar();
            return;
        }
        int currentPos = musicPlayer.getQueueIndex();
        if (currentPos < 0 || currentPos >= musicList.size()) {
            currentPos = selectedPosition;
        }
        int prePos;

        if (musicList.isEmpty()) {
            Toast.makeText(this, "播放列表为空", Toast.LENGTH_SHORT).show();
            return;
        }

        MusicPlayer.PlayMode mode = musicPlayer.getPlayMode();
        if (mode == MusicPlayer.PlayMode.SINGLE_LOOP) {
            prePos = currentPos;
        } else if (mode == MusicPlayer.PlayMode.SHUFFLE) {
            prePos = new Random().nextInt(musicList.size());
        } else {
            prePos = currentPos == 0 ? musicList.size() - 1 : currentPos - 1;
        }

        selectedPosition = prePos;
        musicPlayer.setQueueContext(currentPlaylist, selectedPosition);
        Map<String, Object> map = musicList.get(prePos);
        selectSong = Song.fromMap(map);

        // 移除条件判断，始终重新加载歌曲
        playSongAt(prePos);
        song = selectSong;

        TextView currentSongTV = findViewById(R.id.currentSong);
        currentSongTV.setText(selectSong.getName());
        playBtn.setEnabled(true);
        musicPlayer.setCurrentPositiontozero();

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

    private void showBiliDialog(biliCallback<String> callback) {
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
            String raw = input.getText().toString().trim();
            if (raw.isEmpty()) {
                Toast.makeText(this, "不能为空", Toast.LENGTH_SHORT).show();
            } else {
                String resolved = extractBiliUrlOrId(raw);
                if (resolved == null || resolved.isEmpty()) {
                    Toast.makeText(this, "无法识别输入内容", Toast.LENGTH_SHORT).show();
                    return;
                }
                callback.onResult(resolved);
                dialog.dismiss();
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            callback.onResult(null); // 取消则返回 null
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showBiliCollectionDialog(biliCallback<List<String>> callback) {
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
        
        // 设置不可取消，用户只能通过按钮退出
        builder.setCancelable(false);

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
                            int code = jsonObj.has("code") ? jsonObj.get("code").getAsInt() : -1;
                            if (code != 0 || !jsonObj.has("data") || jsonObj.get("data").isJsonNull()) {
                                String msg = jsonObj.has("message") ? jsonObj.get("message").getAsString() : "未知错误";
                                runOnUiThread(() -> {
                                    dismissProgressDialog();
                                    Toast.makeText(MainActivity.this, "无法获取收藏夹（可能未公开）: " + msg, Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            JsonObject dataObj = jsonObj.getAsJsonObject("data");
                            if (!dataObj.has("list") || dataObj.get("list").isJsonNull()) {
                                runOnUiThread(() -> {
                                    dismissProgressDialog();
                                    Toast.makeText(MainActivity.this, "该用户没有可用收藏夹（可能未公开）", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }
                            JsonArray folders = dataObj.getAsJsonArray("list");

                            boolean found = false;
                            String fid = "";

                            // 遍历查找指定的收藏夹
                            String favCover = null;
                            String favOwnerName = null;
                            String favOwnerUid = finalUid;
                            int favMediaCount = -1;
                            for (JsonElement folder : folders) {
                                JsonObject folderObj = folder.getAsJsonObject();
                                if (folderObj.get("title").getAsString().equals(collectionId)) {
                                    found = true;
                                    fid = folderObj.get("id").getAsString();
                                    if (folderObj.has("media_count") && !folderObj.get("media_count").isJsonNull()) {
                                        favMediaCount = folderObj.get("media_count").getAsInt();
                                    }
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
                            showBiliVideoSelectionDialog(finalUid, fid, collectionId, favCover, favOwnerName, favOwnerUid, favMediaCount, callback);
                        }
                    });
                } else {
                    Toast.makeText(this, "请检查 UID 是否正确", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> {
            // 确保关闭可能存在的进度条
            dismissProgressDialog();
            callback.onResult(null); // 取消则返回 null
        });

        AlertDialog dialog = builder.create();
        // 再次确保弹窗不可取消
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        
        // 设置弹窗关闭监听器，防止意外关闭时进度条不消失
        dialog.setOnDismissListener(dialogInterface -> {
            // 如果弹窗被意外关闭，确保进度条也被关闭
            dismissProgressDialog();
        });
        
        dialog.show();
    }

    private void showBiliVideoSelectionDialog(String uid, String fid, String collectionName, String favCoverUrl, String ownerName, String ownerUid, int favMediaCount, biliCallback<List<String>> callback) {
        // 在UI线程中创建和显示对话框
        runOnUiThread(() -> {
            // 创建对话框视图
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bili_video_selection, null);
            ImageView ivAvatar = dialogView.findViewById(R.id.ivBiliUserAvatar);
            TextView tvUserName = dialogView.findViewById(R.id.tvBiliUserName);
            TextView tvUserUid = dialogView.findViewById(R.id.tvBiliUserUid);
            TextView tvFavInfo = dialogView.findViewById(R.id.tvBiliFavInfo);
            TextView tvTotalCount = dialogView.findViewById(R.id.tvBiliTotalCount);
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

            int screenH = getResources().getDisplayMetrics().heightPixels;
            boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            float fraction = isLandscape ? 0.40f : 0.50f;
            ViewGroup.LayoutParams lp = lvVideos.getLayoutParams();
            lp.height = Math.max(lvVideos.getLayoutParams().height, (int) (screenH * fraction));
            lvVideos.setLayoutParams(lp);

            if (tvUserUid != null) {
                String displayUid = ownerUid != null && !ownerUid.isEmpty() ? ownerUid : uid;
                tvUserUid.setText("UID: " + displayUid);
            }
            if (tvFavInfo != null) {
                tvFavInfo.setText("收藏夹: " + collectionName + "  FID: " + fid);
            }
            if (tvTotalCount != null) {
                if (favMediaCount >= 0) {
                    tvTotalCount.setText("总数: " + favMediaCount);
                } else {
                    tvTotalCount.setText("总数: -");
                }
            }
            if (tvUserName != null) {
                if (ownerName != null && !ownerName.isEmpty()) {
                    tvUserName.setText(ownerName);
                } else {
                    tvUserName.setText("UP主");
                }
            }
            if (ivAvatar != null && favCoverUrl != null && !favCoverUrl.isEmpty()) {
                MusicCoverUtils.loadCoverFromUrl(favCoverUrl, MainActivity.this, ivAvatar);
            }
            fetchBiliFavFolderInfo(fid, info -> {
                if (info == null) {
                    return;
                }
                String cover = (String) info.get("cover");
                String upName = (String) info.get("upName");
                String upUid = (String) info.get("upUid");
                String title = (String) info.get("title");
                Integer count = (Integer) info.get("mediaCount");
                runOnUiThread(() -> {
                    if (tvFavInfo != null && title != null && !title.isEmpty()) {
                        tvFavInfo.setText("收藏夹: " + title + "  FID: " + fid);
                    }
                    if (tvUserName != null && upName != null && !upName.isEmpty()) {
                        tvUserName.setText(upName);
                    }
                    if (tvUserUid != null && upUid != null && !upUid.isEmpty()) {
                        tvUserUid.setText("UID: " + upUid);
                    }
                    if (tvTotalCount != null && count != null && count >= 0) {
                        tvTotalCount.setText("总数: " + count);
                    }
                    if (ivAvatar != null && cover != null && !cover.isEmpty()) {
                        MusicCoverUtils.loadCoverFromUrl(cover, MainActivity.this, ivAvatar);
                    }
                });
            });

            // 加载第一页数据。里面会设置滚动监听器递归加载下一页
            loadBiliVideoPage(uid, fid, 1, 20, adapter, lvVideos, loadMoreProgress, tvTotalCount);
        });
    }

    private void fetchBiliFavFolderInfo(String mediaId, biliCallback<Map<String, Object>> callback) {
        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder()
                .url("https://api.bilibili.com/x/v3/fav/folder/info?media_id=" + mediaId)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .addHeader("Accept", "application/json")
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onResult(null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onResult(null);
                    return;
                }
                String json = response.body().string();
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                int code = obj.has("code") ? obj.get("code").getAsInt() : -1;
                if (code != 0 || !obj.has("data") || obj.get("data").isJsonNull()) {
                    callback.onResult(null);
                    return;
                }
                JsonObject data = obj.getAsJsonObject("data");
                String title = data.has("title") && !data.get("title").isJsonNull() ? data.get("title").getAsString() : null;
                String cover = data.has("cover") && !data.get("cover").isJsonNull() ? data.get("cover").getAsString() : null;
                Integer mediaCount = data.has("media_count") && !data.get("media_count").isJsonNull() ? data.get("media_count").getAsInt() : null;
                String upName = null;
                String upUid = null;
                if (data.has("upper") && !data.get("upper").isJsonNull()) {
                    JsonObject upper = data.getAsJsonObject("upper");
                    upName = upper.has("name") && !upper.get("name").isJsonNull() ? upper.get("name").getAsString() : null;
                    upUid = upper.has("mid") && !upper.get("mid").isJsonNull() ? upper.get("mid").getAsString() : null;
                }
                Map<String, Object> result = new HashMap<>();
                result.put("title", title);
                result.put("cover", cover);
                result.put("mediaCount", mediaCount);
                result.put("upName", upName);
                result.put("upUid", upUid);
                callback.onResult(result);
            }
        });
    }

    private void fetchBiliUserInfo(String uid, ImageView avatarView, TextView nameView) {
        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder()
                .url("https://api.bilibili.com/x/space/acc/info?mid=" + uid)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("BiliCollection", "获取用户信息失败: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e("BiliCollection", "获取用户信息HTTP失败: " + response.code());
                    return;
                }
                String json = response.body().string();
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                int code = obj.has("code") ? obj.get("code").getAsInt() : -1;
                if (code != 0) {
                    String msg = obj.has("message") ? obj.get("message").getAsString() : "未知错误";
                    Log.e("BiliCollection", "获取用户信息失败: code=" + code + " msg=" + msg);
                    return;
                }
                if (!obj.has("data") || obj.get("data").isJsonNull()) {
                    Log.e("BiliCollection", "获取用户信息失败: data为空");
                    return;
                }
                JsonObject data = obj.getAsJsonObject("data");
                String name = data.has("name") && !data.get("name").isJsonNull() ? data.get("name").getAsString() : null;
                String face = data.has("face") && !data.get("face").isJsonNull() ? data.get("face").getAsString() : null;
                if (face != null) {
                    if (face.startsWith("//")) {
                        face = "https:" + face;
                    } else if (face.startsWith("http://")) {
                        face = "https://" + face.substring("http://".length());
                    }
                }
                Log.d("BiliCollection", "用户信息: name=" + name + " face=" + face);
                final String faceFinal = face;
                runOnUiThread(() -> {
                    if (nameView != null && name != null) {
                        nameView.setText(name);
                    }
                    if (avatarView != null && faceFinal != null && !faceFinal.isEmpty()) {
                        MusicCoverUtils.loadCoverFromUrl(faceFinal, MainActivity.this, avatarView);
                    }
                });
            }
        });
    }

    private void showBiliVideoCollectionDialog(String bvid, JsonObject videoInfo, JsonArray pages, long targetCid, biliCallback<Map<String, Object>> callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择要添加的视频");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bili_video_list, null);
        ListView listView = dialogView.findViewById(R.id.lvVideoList);
        CheckBox cbSelectAll = dialogView.findViewById(R.id.cbSelectAll);
        TextView tvSelectedCount = dialogView.findViewById(R.id.tvSelectedCount);

        BiliVideoAdapter adapter = new BiliVideoAdapter(this);
        // 如果有 targetCid，设置高亮标识
        if (targetCid != -1) {
            adapter.setHighlightBvid(bvid + "_" + targetCid);
        }
        listView.setAdapter(adapter);

        // 解析视频合集信息
        List<BiliVideoAdapter.BiliVideoItem> videoItems = new ArrayList<>();
        String mainTitle = videoInfo.get("title").getAsString();
        String uploader = videoInfo.get("owner").getAsJsonObject().get("name").getAsString();
        
        int targetIndex = -1;

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
                    
            if (cid == targetCid) {
                targetIndex = i;
                item.setSelected(true); // 默认选中目标项
            }
                    
            videoItems.add(item);
        }

        adapter.addItems(videoItems);
        
        // 滚动到目标项
        if (targetIndex != -1) {
            final int scrollIndex = targetIndex;
            listView.post(() -> listView.setSelection(scrollIndex));
        }

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

    private void showBiliUGCSeasonDialog(String bvid, JsonObject videoInfo, JsonObject ugcSeason, long targetCid, biliCallback<Map<String, Object>> callback) {
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

        int targetGlobalIndex = -1;

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
                        
                if (cid == targetCid) {
                    targetGlobalIndex = allVideoItems.size();
                    item.setSelected(true);
                    adapter.setHighlightBvid(episodeBvid + "_" + targetCid);
                }
                        
                allVideoItems.add(item);
            }
        }

        // 计算目标视频所在的页码并加载到该页
        if (targetGlobalIndex != -1) {
            int targetPage = targetGlobalIndex / PAGE_SIZE;
            currentPage.set(targetPage);
            // 将从第0页到目标页的所有数据一次性加载到Adapter中
            for (int p = 0; p <= targetPage; p++) {
                loadPage(adapter, allVideoItems, p, PAGE_SIZE);
            }
            
            // 滚动到目标项
            final int scrollIndex = targetGlobalIndex;
            listView.post(() -> listView.setSelection(scrollIndex));
        } else {
            // 没有目标视频，只加载第一页
            loadPage(adapter, allVideoItems, currentPage.get(), PAGE_SIZE);
        }

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
                                           biliCallback<Map<String, Object>> callback) {

        safeShowProgressDialog();
        updateProgress(0, "正在添加视频合集 (0/" + selectedItems.size() + ")");

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
                                    appendResolvedBiliSongToCurrentPlaylist(result, item.getTitle(), coverUrl);
                                } catch (Exception e) {
                                    Log.e("BiliMusic", "处理音频文件失败: " + e.getMessage());
                                }
                            });
                        }

                        // 更新进度
                        int progressPercent = (completed * 100) / selectedItems.size();
                        updateProgress(progressPercent, "正在添加视频合集 (" + completed + "/" + selectedItems.size() + ")");

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
                                            biliCallback<Map<String, Object>> callback) {

        safeShowProgressDialog();
        updateProgress(0, "正在添加视频合集 (0/" + selectedItems.size() + ")");

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

                                try {
                                    appendResolvedBiliSongToCurrentPlaylist(result, item.getTitle(), videoInfo.get("pic").getAsString());
                                } catch (Exception e) {
                                    Log.e("BiliMusic", "处理音频文件失败: " + e.getMessage());
                                }
                            });
                        }

                        // 更新进度
                        int progressPercent = (completed * 100) / selectedItems.size();
                        updateProgress(progressPercent, "正在添加视频合集 (" + completed + "/" + selectedItems.size() + ")");

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
                                   BiliVideoAdapter adapter, ListView listView, ProgressBar loadMoreProgress, TextView tvTotalCount) {
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

                int totalCount = -1;
                if (data != null && data.has("info") && !data.get("info").isJsonNull()) {
                    JsonObject info = data.getAsJsonObject("info");
                    if (info.has("media_count") && !info.get("media_count").isJsonNull()) {
                        totalCount = info.get("media_count").getAsInt();
                    }
                }

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
                int skippedTooLong = 0;
                for (JsonElement media : medias) {
                    JsonObject videoObj = media.getAsJsonObject();
                    String bvid = videoObj.get("bvid").getAsString();
                    String title = videoObj.get("title").getAsString();
                    String uploader = videoObj.getAsJsonObject("upper").get("name").getAsString();
                    int duration = videoObj.get("duration").getAsInt();

                    // 失效视频后续无法获取，直接筛掉
                    if (!Objects.equals(title, "已失效视频")) {
                        if (duration > BILI_FAV_MAX_DURATION_SECONDS) {
                            skippedTooLong++;
                            continue;
                        }
                        BiliVideoAdapter.BiliVideoItem item = new BiliVideoAdapter.BiliVideoItem(
                                bvid, title, uploader, duration);
                        videoItems.add(item);
                    }
                }

                // 在UI线程中更新列表
                final boolean finalHasMore = hasMore;
                final int finalPageNum = pageNum;
                final int skippedTooLongFinal = skippedTooLong;
                final int totalCountFinal = totalCount;
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
                    if (finalPageNum == 1 && tvTotalCount != null && totalCountFinal >= 0) {
                        tvTotalCount.setText("总数: " + totalCountFinal);
                    }
                    if (skippedTooLongFinal > 0 && finalPageNum == 1) {
                        Toast.makeText(MainActivity.this, "已跳过 " + skippedTooLongFinal + " 个超长视频", Toast.LENGTH_SHORT).show();
                    }

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
                                            loadMoreProgress, tvTotalCount);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    void getBiliMusic(String bv, Context context, biliCallback<Map<String, Object>> callback) {
        getBiliMusic(bv, context, false, callback);
    }

    void getBiliMusic(String bv, Context context, boolean disableCollectionPick, biliCallback<Map<String, Object>> callback) {
        getBiliMusicInternal(bv, context, disableCollectionPick, callback, 0);
    }

    private String extractBiliUrlOrId(String raw) {
        return BiliLinkParser.extractToken(raw);
    }

    private String extractBvid(String token) {
        return BiliLinkParser.extractBvid(token);
    }

    private String extractAid(String token) {
        return BiliLinkParser.extractAid(token);
    }

    private void getBiliMusicInternal(String input, Context context, boolean disableCollectionPick,
                                      biliCallback<Map<String, Object>> callback, int depth) {
        if (depth > 3) {
            callback.onResult(null);
            runOnUiThread(() -> Toast.makeText(context, "链接解析失败", Toast.LENGTH_SHORT).show());
            return;
        }

        String token = extractBiliUrlOrId(input);
        if (token == null || token.isEmpty()) {
            callback.onResult(null);
            runOnUiThread(() -> Toast.makeText(context, "无法识别链接", Toast.LENGTH_SHORT).show());
            return;
        }

        if (token.startsWith("b23.tv/")) {
            token = "https://" + token;
        } else if (token.startsWith("www.bilibili.com/")) {
            token = "https://" + token;
        } else if (token.startsWith("m.bilibili.com/")) {
            token = "https://" + token;
        }

        if (token.startsWith("http://") || token.startsWith("https://")) {
            Uri uri = Uri.parse(token);
            String host = uri.getHost() != null ? uri.getHost() : "";
            if (host.contains("b23.tv")) {
                resolveFinalUrl(token, finalUrl -> {
                    if (finalUrl == null) {
                        callback.onResult(null);
                        runOnUiThread(() -> Toast.makeText(context, "短链解析失败", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    getBiliMusicInternal(finalUrl, context, disableCollectionPick, callback, depth + 1);
                });
                return;
            }

            String path = uri.getPath() != null ? uri.getPath() : "";
            int p = 0;
            try {
                String pStr = uri.getQueryParameter("p");
                if (pStr != null) {
                    p = Integer.parseInt(pStr);
                }
            } catch (Exception ignored) {
            }

            Long epId = extractBangumiEpId(path);
            if (epId != null) {
                fetchBangumiEpisode(epId, null, info -> {
                    if (info == null) {
                        callback.onResult(null);
                        runOnUiThread(() -> Toast.makeText(context, "番剧解析失败", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    String bvid = (String) info.get("bvid");
                    Long cid = (Long) info.get("cid");
                    String title = (String) info.get("title");
                    String cover = (String) info.get("cover");
                    downloadSingleBiliVideo(bvid, cid != null ? cid : -1L, title, cover, epId, callback);
                });
                return;
            }

            Long ssId = extractBangumiSeasonId(path);
            if (ssId != null) {
                fetchBangumiEpisode(null, ssId, info -> {
                    if (info == null) {
                        callback.onResult(null);
                        runOnUiThread(() -> Toast.makeText(context, "番剧解析失败", Toast.LENGTH_SHORT).show());
                        return;
                    }
                    String bvid = (String) info.get("bvid");
                    Long cid = (Long) info.get("cid");
                    String title = (String) info.get("title");
                    String cover = (String) info.get("cover");
                    Long ep = (Long) info.get("epId");
                    downloadSingleBiliVideo(bvid, cid != null ? cid : -1L, title, cover, ep, callback);
                });
                return;
            }

            String bvid = extractBvid(token);
            if (bvid != null) {
                requestViewAndDownloadByBvid(bvid, p, context, disableCollectionPick, callback);
                return;
            }

            String aid = extractAid(token);
            if (aid != null) {
                requestViewAndDownloadByAid(aid, p, context, disableCollectionPick, callback);
                return;
            }

            callback.onResult(null);
            runOnUiThread(() -> Toast.makeText(context, "不支持的B站链接", Toast.LENGTH_SHORT).show());
            return;
        }

        if (token.matches("BV[0-9A-Za-z]{10,}")) {
            requestViewAndDownloadByBvid(token, 0, context, disableCollectionPick, callback);
            return;
        }
        if (token.matches("av\\d+")) {
            requestViewAndDownloadByAid(token.substring(2), 0, context, disableCollectionPick, callback);
            return;
        }
        if (token.matches("ep\\d+")) {
            try {
                Long ep = Long.parseLong(token.substring(2));
                getBiliMusicInternal("https://www.bilibili.com/bangumi/play/ep" + ep, context, disableCollectionPick, callback, depth + 1);
                return;
            } catch (Exception ignored) {
            }
        }
        if (token.matches("ss\\d+")) {
            try {
                Long ss = Long.parseLong(token.substring(2));
                getBiliMusicInternal("https://www.bilibili.com/bangumi/play/ss" + ss, context, disableCollectionPick, callback, depth + 1);
                return;
            } catch (Exception ignored) {
            }
        }

        callback.onResult(null);
        runOnUiThread(() -> Toast.makeText(context, "无法识别链接", Toast.LENGTH_SHORT).show());
    }

    private void requestViewAndDownloadByBvid(String bvid, int p, Context context, boolean disableCollectionPick,
                                              biliCallback<Map<String, Object>> callback) {
        OkHttpClient client = new OkHttpClient();

        // 获取视频信息
        Request cidRequest = new Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid)
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
                JsonObject root = JsonParser.parseString(str).getAsJsonObject();
                int code = root.has("code") ? root.get("code").getAsInt() : -1;
                if (code != 0 || !root.has("data") || root.get("data").isJsonNull()) {
                    String msg = root.has("message") ? root.get("message").getAsString() : "未知错误";
                    callback.onResult(null);
                    runOnUiThread(() -> Toast.makeText(context, "解析失败: " + msg, Toast.LENGTH_SHORT).show());
                    return;
                }
                JsonObject json = root.getAsJsonObject("data");

                // 检查是否为视频合集
                JsonArray pages = json.getAsJsonArray("pages");
                JsonObject ugcSeason = json.has("ugc_season") ? json.getAsJsonObject("ugc_season") : null;
                Log.d("BiliMusic", "Pages数量: " + (pages != null ? pages.size() : "null"));
                Log.d("BiliMusic", "UGC Season: " + (ugcSeason != null ? "存在" : "不存在"));

                // 检测合集：要么是多P视频，要么是UGC合集
                boolean isCollection = (pages != null && pages.size() > 1) ||
                        (ugcSeason != null && ugcSeason.has("sections"));

                if (p > 0 && pages != null && pages.size() > 0) {
                    int index = Math.max(0, Math.min(p - 1, pages.size() - 1));
                    JsonObject pageObj = pages.get(index).getAsJsonObject();
                    long cid = pageObj.has("cid") ? pageObj.get("cid").getAsLong() : json.get("cid").getAsLong();
                    String title = json.get("title").getAsString();
                    if (pageObj.has("part") && !pageObj.get("part").isJsonNull()) {
                        title = title + " - " + pageObj.get("part").getAsString();
                    }
                    String coverUrl = json.get("pic").getAsString();
                    downloadSingleBiliVideo(bvid, cid, title, coverUrl, null, callback);
                    return;
                }

                if (isCollection && !disableCollectionPick) {
                    Log.d("BiliMusic", "检测到视频合集，显示选择列表");
                    // 尝试提取目标P对应的 cid 以便高亮
                    long targetCid = json.has("cid") ? json.get("cid").getAsLong() : -1;
                    
                    // 处理合集逻辑
                    if (ugcSeason != null) {
                        // UGC合集，需要从sections中提取视频列表
                        runOnUiThread(() -> showBiliUGCSeasonDialog(bvid, json, ugcSeason, targetCid, callback));
                    } else {
                        // 多P视频
                        runOnUiThread(() -> showBiliVideoCollectionDialog(bvid, json, pages, targetCid, callback));
                    }
                } else {
                    Log.d("BiliMusic", "单个视频，继续原有逻辑");
                    // 单个视频，继续原有逻辑
                    long cid = json.get("cid").getAsLong();
                    String title = json.get("title").getAsString();
                    String coverUrl = json.get("pic").getAsString();

                    downloadSingleBiliVideo(bvid, cid, title, coverUrl, null, callback);
                }
            }
        });
    }

    private void requestViewAndDownloadByAid(String aid, int p, Context context, boolean disableCollectionPick,
                                             biliCallback<Map<String, Object>> callback) {
        OkHttpClient client = new OkHttpClient();
        Request cidRequest = new Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/view?aid=" + aid)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();
        client.newCall(cidRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("BiliMusic", "onFailure: " + e);
                runOnUiThread(() -> Toast.makeText(context, "请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                callback.onResult(null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response res) throws IOException {
                String str = res.body().string();
                JsonObject root = JsonParser.parseString(str).getAsJsonObject();
                int code = root.has("code") ? root.get("code").getAsInt() : -1;
                if (code != 0 || !root.has("data") || root.get("data").isJsonNull()) {
                    String msg = root.has("message") ? root.get("message").getAsString() : "未知错误";
                    callback.onResult(null);
                    runOnUiThread(() -> Toast.makeText(context, "解析失败: " + msg, Toast.LENGTH_SHORT).show());
                    return;
                }
                JsonObject json = root.getAsJsonObject("data");
                String bvid = json.has("bvid") && !json.get("bvid").isJsonNull() ? json.get("bvid").getAsString() : null;
                if (bvid == null || bvid.isEmpty()) {
                    callback.onResult(null);
                    runOnUiThread(() -> Toast.makeText(context, "解析失败: 缺少BV号", Toast.LENGTH_SHORT).show());
                    return;
                }
                requestViewAndDownloadByBvid(bvid, p, context, disableCollectionPick, callback);
            }
        });
    }

    private void resolveFinalUrl(String url, biliCallback<String> callback) {
        OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build();
        resolveFinalUrlWithMethod(client, url, true, callback);
    }

    private void resolveFinalUrlWithMethod(OkHttpClient client, String url, boolean headFirst, biliCallback<String> callback) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/");
        if (headFirst) {
            b.head();
        } else {
            b.get();
        }
        Request req = b.build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onResult(null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (headFirst) {
                        int code = response.code();
                        if (code == 403 || code == 405 || code == 400) {
                            response.close();
                            resolveFinalUrlWithMethod(client, url, false, callback);
                            return;
                        }
                    }
                    String finalUrl = response.request().url().toString();
                    callback.onResult(finalUrl);
                } catch (Exception e) {
                    callback.onResult(null);
                } finally {
                    try {
                        response.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private Long extractBangumiEpId(String path) {
        if (path == null) {
            return null;
        }
        Matcher m = Pattern.compile("/bangumi/play/ep(\\d+)").matcher(path);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Long extractBangumiSeasonId(String path) {
        if (path == null) {
            return null;
        }
        Matcher m = Pattern.compile("/bangumi/play/ss(\\d+)").matcher(path);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void fetchBangumiEpisode(Long epId, Long seasonId, biliCallback<Map<String, Object>> callback) {
        OkHttpClient client = new OkHttpClient();
        String url = epId != null
                ? ("https://api.bilibili.com/pgc/view/web/season?ep_id=" + epId)
                : ("https://api.bilibili.com/pgc/view/web/season?season_id=" + seasonId);
        Request req = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onResult(null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onResult(null);
                    return;
                }
                String json = response.body().string();
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                int code = root.has("code") ? root.get("code").getAsInt() : -1;
                if (code != 0 || !root.has("result") || root.get("result").isJsonNull()) {
                    callback.onResult(null);
                    return;
                }
                JsonObject result = root.getAsJsonObject("result");
                String seasonTitle = result.has("title") && !result.get("title").isJsonNull() ? result.get("title").getAsString() : "";
                String seasonCover = result.has("cover") && !result.get("cover").isJsonNull() ? result.get("cover").getAsString() : null;
                JsonArray episodes = result.has("episodes") && result.get("episodes").isJsonArray() ? result.getAsJsonArray("episodes") : null;
                if (episodes == null || episodes.size() == 0) {
                    callback.onResult(null);
                    return;
                }
                JsonObject chosen = null;
                if (epId != null) {
                    for (JsonElement el : episodes) {
                        JsonObject ep = el.getAsJsonObject();
                        long id = ep.has("id") ? ep.get("id").getAsLong() : -1;
                        if (id == epId) {
                            chosen = ep;
                            break;
                        }
                    }
                }
                if (chosen == null) {
                    chosen = episodes.get(0).getAsJsonObject();
                }
                String bvid = chosen.has("bvid") && !chosen.get("bvid").isJsonNull() ? chosen.get("bvid").getAsString() : null;
                long cid = chosen.has("cid") && !chosen.get("cid").isJsonNull() ? chosen.get("cid").getAsLong() : -1;
                long chosenEpId = chosen.has("id") && !chosen.get("id").isJsonNull() ? chosen.get("id").getAsLong() : -1;
                String epTitle = chosen.has("title") && !chosen.get("title").isJsonNull() ? chosen.get("title").getAsString() : "";
                String epLong = chosen.has("long_title") && !chosen.get("long_title").isJsonNull() ? chosen.get("long_title").getAsString() : "";
                String title = seasonTitle;
                if (!epTitle.isEmpty()) {
                    title = title + " - " + epTitle;
                }
                if (!epLong.isEmpty()) {
                    title = title + " " + epLong;
                }
                String cover = chosen.has("cover") && !chosen.get("cover").isJsonNull() ? chosen.get("cover").getAsString() : seasonCover;
                int durationSec = chosen.has("duration") && !chosen.get("duration").isJsonNull() ? chosen.get("duration").getAsInt() : 0;
                Map<String, Object> out = new HashMap<>();
                out.put("bvid", bvid);
                out.put("cid", cid);
                out.put("title", title);
                out.put("cover", cover);
                out.put("epId", chosenEpId);
                out.put("durationSec", durationSec);
                callback.onResult(out);
            }
        });
    }

    private void downloadSingleBiliVideo(String bv, long cid, String title, String coverUrl, biliCallback<Map<String, Object>> callback) {
        downloadSingleBiliVideo(bv, cid, title, coverUrl, null, callback);
    }

    private void downloadSingleBiliVideo(String bv, long cid, String title, String coverUrl, Long epId, biliCallback<Map<String, Object>> callback) {
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
                int code = obj.has("code") ? obj.get("code").getAsInt() : -1;
                if (code != 0 || !obj.has("data") || obj.get("data").isJsonNull()) {
                    String msg = obj.has("message") ? obj.get("message").getAsString() : "未知错误";
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "获取音频URL失败: " + msg, Toast.LENGTH_SHORT).show());
                    callback.onResult(null);
                    return;
                }
                JsonObject data = obj.getAsJsonObject("data");
                if (!data.has("dash") || data.get("dash").isJsonNull()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "获取音频URL失败: 无dash音频流", Toast.LENGTH_SHORT).show());
                    callback.onResult(null);
                    return;
                }
                JsonObject dash = data.getAsJsonObject("dash");
                if (!dash.has("audio") || dash.get("audio").isJsonNull()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "获取音频URL失败: 无音频流", Toast.LENGTH_SHORT).show());
                    callback.onResult(null);
                    return;
                }
                JsonArray audioArray = dash.getAsJsonArray("audio");
                if (audioArray == null || audioArray.size() == 0) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "获取音频URL失败: 音频流为空", Toast.LENGTH_SHORT).show());
                    callback.onResult(null);
                    return;
                }

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
                int durationSec = 0;
                if (data.has("timelength") && !data.get("timelength").isJsonNull()) {
                    try {
                        durationSec = Math.max(0, data.get("timelength").getAsInt() / 1000);
                    } catch (Exception ignored) {
                        durationSec = 0;
                    }
                }

                if (coverUrl != null && !coverUrl.isEmpty()) {
                    MusicCoverUtils.preloadCover(coverUrl, MainActivity.this);
                }

                Map<String, Object> result = new HashMap<>();
                result.put("filePath", BiliAudioDownloadHelper.buildPlaceholderPath(bv, cid));
                result.put("title", title);
                result.put("coverUrl", coverUrl);
                result.put("durationSec", durationSec);
                result.put("bvid", bv);
                result.put("cid", cid);
                if (epId != null) {
                    result.put("epId", epId);
                }
                callback.onResult(result);
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
            if (isBatchMode) {
                Map<String, Object> item = (Map<String, Object>) parent.getAdapter().getItem(position);
                if (item == null) {
                    return;
                }
                boolean newSelected = !Boolean.TRUE.equals(item.get("isSelected"));
                item.put("isSelected", newSelected);
                if (newSelected) {
                    if (!selectedPositions.contains(position)) {
                        selectedPositions.add(position);
                    }
                } else {
                    selectedPositions.remove((Integer) position);
                }
                ((BaseAdapter) parent.getAdapter()).notifyDataSetChanged();
                return;
            }

            Map<String, Object> item = (Map<String, Object>) parent.getAdapter().getItem(position);
            Song clickedSong = item != null ? Song.fromMap(item) : null;
            Song current = musicPlayer != null ? musicPlayer.getCurrentSong() : null;
            if (clickedSong != null && current != null && current.equals(clickedSong) && (musicPlayer.isPlaying() || musicPlayer.isPaused())) {
                startActivity(new Intent(MainActivity.this, MusicDetailActivity.class));
                return;
            }

            selectedPosition = position;
            try {
                playSongAt(position);
            } catch (IOException e) {
                Toast.makeText(MainActivity.this, "播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

    }

    private void refreshHeaderCover() {
        if (albumArt == null) {
            return;
        }
        runOnUiThread(() -> {
            if (musicList == null || musicList.isEmpty()) {
                albumArt.setImageResource(R.drawable.default_playlist_cover);
                return;
            }

            Map<String, Object> first = musicList.get(0);
            Object filePathObj = first.get("filePath");
            String filePath = filePathObj != null ? String.valueOf(filePathObj) : null;
            Object coverUrlObj = first.get("coverUrl");
            String coverUrl = coverUrlObj != null ? String.valueOf(coverUrlObj) : null;

            if (filePath == null || filePath.isEmpty()) {
                albumArt.setImageResource(R.drawable.default_playlist_cover);
                return;
            }
            MusicCoverUtils.loadCoverSmart(filePath, coverUrl, this, albumArt);
        });
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
