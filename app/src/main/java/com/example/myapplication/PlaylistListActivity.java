package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
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

    // 新增的变量
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private int listBackgroundType = 0;
    private int currentBackgroundType = 0; // 等于 listBackgroundType: 歌单页面, 反之: 播放页面

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_list);

        // 初始化图片选择器
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            saveBackgroundImage(imageUri, currentBackgroundType);
                        }
                    }
                }
        );

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
            selected.sort(Collections.reverseOrder()); // 先删除后面的，防止下标错乱

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

        // 添加设置按钮点击事件
        findViewById(R.id.menu_item1).setOnClickListener(v -> {
            showSettingsDialog();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // 应用保存的背景图片
        applyBackgroundImage();
    }

    private void showSettingsDialog() {
        // 史山魅力时刻：根据 currentBackgroundType 来修改歌单还是播放页背景。存 listBackgroundType 好自由修改选项位置
        String[] options = {"设置歌单页面背景", "设置播放页面背景", "恢复默认背景"};
        listBackgroundType = Arrays.asList(options).indexOf("设置歌单页面背景");

        new AlertDialog.Builder(this)
                .setTitle("背景设置")
                .setItems(options, (dialog, which) -> {
                    if (which == 2) {
                        // 恢复默认背景
                        showRestoreDefaultDialog();
                    } else {
                        currentBackgroundType = which;
                        openImagePicker();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRestoreDefaultDialog() {
        String[] options = {"恢复歌单页面默认背景", "恢复播放页面默认背景", "恢复所有默认背景"};

        new AlertDialog.Builder(this)
                .setTitle("恢复默认背景")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            restoreDefaultBackground(0); // 歌单页面
                            break;
                        case 1:
                            restoreDefaultBackground(1); // 播放页面
                            break;
                        case 2:
                            restoreDefaultBackground(-1); // 所有页面
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void restoreDefaultBackground(int backgroundType) {
        SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (backgroundType == 0 || backgroundType == -1) {
            // 清除歌单页面背景设置
            String playlistBgPath = prefs.getString("playlist_background_path", null);
            if (playlistBgPath != null) {
                // 删除保存的背景图片文件
                File bgFile = new File(playlistBgPath);
                if (bgFile.exists()) {
                    bgFile.delete();
                }
            }
            editor.remove("playlist_background_path");

            // 清除DrawerLayout的背景
            androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
            if (drawerLayout != null) {
                drawerLayout.setBackgroundResource(0);
            }
        }

        if (backgroundType == 1 || backgroundType == -1) {
            // 清除播放页面背景设置
            String playbackBgPath = prefs.getString("playback_background_path", null);
            if (playbackBgPath != null) {
                // 删除保存的背景图片文件
                File bgFile = new File(playbackBgPath);
                if (bgFile.exists()) {
                    bgFile.delete();
                }
            }
            editor.remove("playback_background_path");
        }

        editor.apply();

        // 立即应用默认背景
        applyBackgroundImage();

        String message;
        if (backgroundType == -1) {
            message = "已恢复所有默认背景";
        } else if (backgroundType == 0) {
            message = "已恢复歌单页面默认背景";
        } else {
            message = "已恢复播放页面默认背景";
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void saveBackgroundImage(Uri imageUri, int backgroundType) {
        try {
            // 获取图片并显示预览对话框
            showBackgroundPreviewDialog(imageUri, backgroundType);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBackgroundPreviewDialog(Uri imageUri, int backgroundType) {
        // 创建对话框布局
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_bg_preview, null);
        SeekBar transparencySeekBar = dialogView.findViewById(R.id.transparencySeekBar);
        SeekBar blurSeekBar = dialogView.findViewById(R.id.blurSeekBar);

        // 记住原始背景
        Drawable originalBackground = drawerLayout.getBackground();

        // 异步加载和处理图片
        new Thread(() -> {
            try {
                // 获取屏幕尺寸
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;

                // 使用采样率减小图片大小
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();

                int sampleSize = calculateInSampleSize(options, screenWidth, screenHeight);
                options = new BitmapFactory.Options();
                options.inSampleSize = sampleSize;

                // 重新加载图片
                inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream, null, options);
                inputStream.close();

                // 预先缩放到屏幕尺寸
                final Bitmap preScaledBitmap = createScaledBitmap(originalBitmap, screenWidth, screenHeight);
                if (originalBitmap != preScaledBitmap) {
                    originalBitmap.recycle();
                }

                // 在UI线程中设置对话框
                runOnUiThread(() -> {
                    // 初始透明度和模糊值
                    final int[] transparency = {130};
                    final int[] blurRadius = {0};

                    // 设置滑块初始值
                    transparencySeekBar.setMax(255);
                    transparencySeekBar.setProgress(transparency[0]);
                    blurSeekBar.setMax(25);
                    blurSeekBar.setProgress(blurRadius[0]);

                    // 更新预览的帮助方法
                    Runnable updatePreview = () -> {
                        // 应用模糊效果
                        Bitmap processedBitmap;
                        if (blurRadius[0] > 0) {
                            processedBitmap = blurBitmap(preScaledBitmap, blurRadius[0]);
                        } else {
                            processedBitmap = preScaledBitmap;
                        }

                        // 创建背景drawable并设置透明度
                        BitmapDrawable backgroundDrawable = new BitmapDrawable(getResources(), processedBitmap);
                        backgroundDrawable.setAlpha(transparency[0]);

                        // 更新实时预览
                        drawerLayout.setBackground(backgroundDrawable.getConstantState().newDrawable());
                    };
                    updatePreview.run(); // 马上更新一次

                    // 设置滑块监听器
                    transparencySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            transparency[0] = progress;
                            updatePreview.run();
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });

                    blurSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            blurRadius[0] = progress;
                            updatePreview.run();
                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {
                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {
                        }
                    });

                    // 创建对话框
                    AlertDialog dialog = new AlertDialog.Builder(PlaylistListActivity.this)
                            .setTitle("预览中")
                            .setView(dialogView)
                            .setPositiveButton("确认", (dialogInterface, i) -> {
                                saveProcessedBackground(imageUri, backgroundType, transparency[0], blurRadius[0]);
                                if (backgroundType != listBackgroundType) {
                                    // 如果修改播放页面背景，还原回去
                                    drawerLayout.setBackground(originalBackground);
                                }
                            })
                            .setNegativeButton("取消", (dialogInterface, i) -> {
                                drawerLayout.setBackground(originalBackground);
                            })
                            .create();

                    // 设置对话框属性
                    Window window = dialog.getWindow();
                    if (window != null) {
                        window.setGravity(android.view.Gravity.BOTTOM);
                        window.setDimAmount(0f); // 背景不变暗
                    }
                    // 设置点击外部不关闭
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 计算合适的采样率以减小图片大小，免得拖滑条会卡
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private void saveProcessedBackground(Uri imageUri, int backgroundType, int transparency, int blurRadius) {
        try {
            // 创建保存目录
            File backgroundDir = new File(getExternalFilesDir("backgrounds"), "");
            if (!backgroundDir.exists()) {
                backgroundDir.mkdirs();
            }

            // 确定文件名
            String fileName = (backgroundType == listBackgroundType) ? "playlist_background.jpg" : "playback_background.jpg";
            File backgroundFile = new File(backgroundDir, fileName);

            // 直接获取当前显示的背景图
            Drawable currentBackground = drawerLayout.getBackground();
            if (!(currentBackground instanceof BitmapDrawable)) {
                Toast.makeText(this, "背景设置失败：无法保存当前背景", Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap currentBitmap = ((BitmapDrawable) currentBackground).getBitmap();

            // 保存当前显示的图片 (已经应用了透明度和模糊)
            FileOutputStream outputStream = new FileOutputStream(backgroundFile);
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.close();

            // 保存设置到SharedPreferences
            SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
            String key = (backgroundType == listBackgroundType) ? "playlist_background_path" : "playback_background_path";
            prefs.edit()
                    .putString(key, backgroundFile.getAbsolutePath())
                    .putInt(key + "_transparency", transparency)
                    .putInt(key + "_blur", blurRadius)
                    .apply();

            Toast.makeText(this, "背景设置成功", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "背景设置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 添加模糊效果的方法
    private Bitmap blurBitmap(Bitmap bitmap, int radius) {
        if (radius == 0) return bitmap;

        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 对于Android 12及以上，使用RenderEffect
            try {
                // 使用ScriptIntrinsicBlur通过Canvas应用模糊
                RenderScript rs = RenderScript.create(this);
                Allocation input = Allocation.createFromBitmap(rs, bitmap);
                Allocation outputAlloc = Allocation.createFromBitmap(rs, output);

                // 将模糊半径限制在0.0f到25.0f之间
                float blurRadius = Math.min(Math.max(radius, 0), 25);

                ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
                blurScript.setInput(input);
                blurScript.setRadius(blurRadius);
                blurScript.forEach(outputAlloc);

                outputAlloc.copyTo(output);

                // 清理资源
                input.destroy();
                outputAlloc.destroy();
                blurScript.destroy();
                rs.destroy();
            } catch (Exception e) {
                e.printStackTrace();
                return bitmap; // 如果失败就返回原图
            }
        } else {
            // 较老版本使用RenderScript
            RenderScript rs = RenderScript.create(this);
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Allocation input = Allocation.createFromBitmap(rs, bitmap);
            Allocation outputAlloc = Allocation.createFromBitmap(rs, output);
            script.setRadius(radius);
            script.setInput(input);
            script.forEach(outputAlloc);
            outputAlloc.copyTo(output);
            rs.destroy();
        }

        return output;
    }

    // 修改applyBackgroundImage方法，应用保存的模糊效果
    private void applyBackgroundImage() {
        SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
        String playlistBgPath = prefs.getString("playlist_background_path", null);
        int transparency = prefs.getInt("playlist_background_path_transparency", 130);
        int blurRadius = prefs.getInt("playlist_background_path_blur", 0);

        // 使用DrawerLayout作为背景容器，而不是有边距的LinearLayout
        androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        if (drawerLayout != null) {
            if (playlistBgPath != null && new File(playlistBgPath).exists()) {
                // 为歌单页面设置自定义背景
                try {
                    // 获取屏幕尺寸
                    int screenWidth = getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;

                    // 加载并缩放图片
                    Bitmap originalBitmap = BitmapFactory.decodeFile(playlistBgPath);
                    if (originalBitmap != null) {
                        // 创建缩放后的位图，使用CENTER_CROP效果
                        Bitmap scaledBitmap = createScaledBitmap(originalBitmap, screenWidth, screenHeight);

                        // 应用模糊效果（如果有的话）
                        Bitmap processedBitmap = scaledBitmap;
                        if (blurRadius > 0) {
                            processedBitmap = blurBitmap(scaledBitmap, blurRadius);
                            if (processedBitmap != scaledBitmap) {
                                scaledBitmap.recycle();
                            }
                        }

                        // 创建背景drawable
                        BitmapDrawable backgroundDrawable = new BitmapDrawable(getResources(), processedBitmap);
                        backgroundDrawable.setAlpha(transparency); // 使用保存的透明度
                        drawerLayout.setBackground(backgroundDrawable);

                        // 回收原始位图
                        if (originalBitmap != processedBitmap) {
                            originalBitmap.recycle();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // 如果加载失败，恢复默认背景
                    drawerLayout.setBackgroundResource(0);
                }
            } else {
                // 恢复默认背景
                drawerLayout.setBackgroundResource(0);
            }
        }
    }

    // 保持原有的图片缩放方法不变
    private Bitmap createScaledBitmap(Bitmap originalBitmap, int targetWidth, int targetHeight) {
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
        updateAllSongCounts();
        for (Playlist playlist : playlists) {
            String coverPath = MusicLoader.getLatestCoverForPlaylist(this, playlist.getName());
            playlist.setLatestCoverPath(coverPath);
        }
        // 每次返回时重新应用背景
        applyBackgroundImage();
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
        EditText input = new EditText(this);
        LinearLayout inputLayout = new LinearLayout(this);
        inputLayout.setOrientation(LinearLayout.VERTICAL);
        inputLayout.setPadding(50, 20, 50, 10);
        inputLayout.addView(input);

        builder.setTitle("新建歌单")
                .setView(inputLayout)
                .setPositiveButton("确定", null) // 暂时设为null，在下面手动处理点击事件
                .setNegativeButton("取消", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // 实现判空和判重
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(PlaylistListActivity.this, "歌单名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 判重
            boolean isDuplicate = false;
            for (Playlist playlist : playlists) {
                if (playlist.getName().equals(name)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                Toast.makeText(PlaylistListActivity.this, "歌单名称已存在", Toast.LENGTH_SHORT).show();
            } else {
                Playlist newPlaylist = new Playlist(name, 0, R.drawable.default_playlist_cover);
                adapter.addPlaylist(newPlaylist);
                savePlaylist(); // 保存到文件或数据库
                updateAllSongCounts();
                dialog.dismiss(); // 关闭对话框
            }
        });
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
