package com.example.myapplication;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Handler;

import com.example.myapplication.model.Song;

public class BottomPlayerBarManager {
    private Activity activity;
    private MusicPlayer musicPlayer;
    private View bottomPlayerContainer;
    private ImageView bottomAlbumCover;
    private TextView bottomSongName;
    private TextView bottomArtistName;
    private ImageButton bottomPlayPauseBtn;
    
    private OnBottomPlayerClickListener clickListener;
    
    // 改为静态变量，跨实例保持状态
    private static String lastSongPath = "";
    private static boolean lastPlayingState = false;
    private static boolean hasInitialized = false;
    
    public interface OnBottomPlayerClickListener {
        void onPlayPauseClick();
        void onPlayerBarClick();
    }
    
    public BottomPlayerBarManager(Activity activity, MusicPlayer musicPlayer) {
        this.activity = activity;
        this.musicPlayer = musicPlayer;
        initViews();
        setupListeners();
        updatePlayerState();
    }
    
    private void initViews() {
        bottomPlayerContainer = activity.findViewById(R.id.bottom_player_container);
        bottomAlbumCover = activity.findViewById(R.id.bottom_album_cover);
        bottomSongName = activity.findViewById(R.id.bottom_song_name);
        bottomArtistName = activity.findViewById(R.id.bottom_artist_name);
        bottomPlayPauseBtn = activity.findViewById(R.id.bottom_play_pause_btn);
    }
    
    private void setupListeners() {
        bottomPlayPauseBtn.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPlayPauseClick();
            }
        });
        
        // 点击播放栏跳转到播放页面（如果在歌单页面）
        bottomPlayerContainer.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPlayerBarClick();
            }
        });
    }
    
    public void updatePlayerState() {
        Song currentSong = musicPlayer.getCurrentSong();
        boolean isPlaying = musicPlayer.isPlaying();
        boolean isPaused = musicPlayer.getPlayStatus() == PlayerStatus.PAUSED;
        boolean isStopped = musicPlayer.getPlayStatus() == PlayerStatus.STOPPED;
        
        if (currentSong != null) {
            String currentSongPath = currentSong.getFilePath();
            
            // 检查是否需要更新UI
            boolean shouldUpdateSong = !currentSongPath.equals(lastSongPath) || !hasInitialized;
            boolean shouldUpdatePlayState = lastPlayingState != isPlaying || !hasInitialized;
            
            if (shouldUpdateSong || shouldUpdatePlayState) {
                bottomPlayerContainer.setVisibility(View.VISIBLE);
                
                // 只在歌曲变化时更新歌曲信息
                if (shouldUpdateSong) {
                    String fullName = currentSong.getName();
                    String songName = fullName;
                    String artistName = "未知艺术家";
                    
                    if (fullName.contains(" - ")) {
                        String[] parts = fullName.split(" - ", 2);
                        if (parts.length == 2) {
                            artistName = parts[0].trim();
                            songName = parts[1].trim();
                        }
                    }
                    
                    bottomSongName.setText(songName);
                    bottomArtistName.setText(artistName);
                    loadBottomCover(currentSong.getFilePath());
                    lastSongPath = currentSongPath;
                }
                
                // 更新播放状态（包括STOPPED状态）
                if (shouldUpdatePlayState || isStopped) {
                    if (isPlaying) {
                        bottomPlayPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
                        bottomPlayPauseBtn.setEnabled(true);
                    } else if (isPaused) {
                        bottomPlayPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                        bottomPlayPauseBtn.setEnabled(true);
                    } else if (isStopped) {
                        // STOPPED状态：显示播放按钮但保持歌曲信息
                        bottomPlayPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                        bottomPlayPauseBtn.setEnabled(true);
                    }
                    lastPlayingState = isPlaying;
                }
                
                hasInitialized = true;
            }
        } else {
            // 只有在真正没有歌曲时才显示默认状态
            // 移除对lastSongPath的重置，避免状态丢失
            if (!hasInitialized) {
                bottomPlayerContainer.setVisibility(View.VISIBLE);
                bottomSongName.setText("暂无歌曲");
                bottomArtistName.setText("未知艺术家");
                bottomAlbumCover.setImageResource(R.drawable.default_cover);
                bottomPlayPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                bottomPlayPauseBtn.setEnabled(false);
                hasInitialized = true;
            }
        }
    }
    
    private void loadBottomCover(String filePath) {
        // 复用MusicCoverUtils的封面加载逻辑
        Song currentSong = musicPlayer.getCurrentSong();
        if (currentSong != null) {
            String coverUrl = currentSong.getCoverUrl(); // 如果Song类有coverUrl字段
            MusicCoverUtils.loadCoverSmart(filePath, coverUrl, activity, bottomAlbumCover);
        } else {
            // 如果没有coverUrl，只从文件加载
            new Thread(() -> {
                Bitmap coverBitmap = MusicCoverUtils.getCoverFromFile(filePath, activity);
                activity.runOnUiThread(() -> {
                    if (coverBitmap != null) {
                        bottomAlbumCover.setImageBitmap(coverBitmap);
                    } else {
                        bottomAlbumCover.setImageResource(R.drawable.default_cover);
                    }
                });
            }).start();
        }
    }
    
    public void setOnBottomPlayerClickListener(OnBottomPlayerClickListener listener) {
        this.clickListener = listener;
    }
    
    public void show() {
        if (bottomPlayerContainer != null) {
            bottomPlayerContainer.setVisibility(View.VISIBLE);
        }
    }
    
    public void hide() {
        if (bottomPlayerContainer != null) {
            bottomPlayerContainer.setVisibility(View.GONE);
        }
    }
    public void updateActivity(Activity newActivity) {
        this.activity = newActivity;
        // 重新初始化视图引用
        initViews();
        // 更新状态
        updatePlayerState();
    }
}