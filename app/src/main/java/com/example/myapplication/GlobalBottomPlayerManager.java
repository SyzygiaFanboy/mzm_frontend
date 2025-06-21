package com.example.myapplication;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.myapplication.model.Song;

public class GlobalBottomPlayerManager {
    private static GlobalBottomPlayerManager instance;
    private MusicPlayer musicPlayer;
    private Activity currentActivity;
    
    // 全局状态
    private Song currentSong;
    private boolean isPlaying;
    private PlayerStatus playStatus;
    
    // UI组件引用
    private View bottomPlayerContainer;
    private ImageView bottomAlbumCover;
    private TextView bottomSongName;
    private TextView bottomArtistName;
    private ImageButton bottomPlayPauseBtn;
    
    private OnBottomPlayerClickListener clickListener;
    
    public interface OnBottomPlayerClickListener {
        void onPlayPauseClick();
        void onPlayerBarClick();
    }
    
    public static GlobalBottomPlayerManager getInstance() {
        if (instance == null) {
            instance = new GlobalBottomPlayerManager();
        }
        return instance;
    }
    
    public void initialize(MusicPlayer musicPlayer) {
        this.musicPlayer = musicPlayer;
        // 添加全局监听器
        musicPlayer.addOnPlaybackStateChangeListener(new MusicPlayer.OnPlaybackStateChangeListener() {
            @Override
            public void onPlaybackStateChanged() {
                updateGlobalState();
                updateUI();
            }
            
            @Override
            public void onSongChanged() {
                updateGlobalState();
                updateUI();
            }
        });
    }
    
    public void attachToActivity(Activity activity) {
        this.currentActivity = activity;
        initViews();
        setupListeners();
        updateUI();
    }
    
    public void detachFromActivity() {
        this.currentActivity = null;
        this.bottomPlayerContainer = null;
        this.bottomAlbumCover = null;
        this.bottomSongName = null;
        this.bottomArtistName = null;
        this.bottomPlayPauseBtn = null;
    }
    
    private void initViews() {
        if (currentActivity == null) return;
        
        bottomPlayerContainer = currentActivity.findViewById(R.id.bottom_player_container);
        bottomAlbumCover = currentActivity.findViewById(R.id.bottom_album_cover);
        bottomSongName = currentActivity.findViewById(R.id.bottom_song_name);
        bottomArtistName = currentActivity.findViewById(R.id.bottom_artist_name);
        bottomPlayPauseBtn = currentActivity.findViewById(R.id.bottom_play_pause_btn);
    }
    
    private void setupListeners() {
        if (bottomPlayPauseBtn != null) {
            bottomPlayPauseBtn.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onPlayPauseClick();
                }
            });
        }
        
        if (bottomPlayerContainer != null) {
            bottomPlayerContainer.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onPlayerBarClick();
                }
            });
        }
    }
    
    private void updateGlobalState() {
        if (musicPlayer != null) {
            currentSong = musicPlayer.getCurrentSong();
            isPlaying = musicPlayer.isPlaying();
            playStatus = musicPlayer.getPlayStatus();
        }
    }
    
    private void updateUI() {
        if (currentActivity == null || bottomPlayerContainer == null) return;
        
        currentActivity.runOnUiThread(() -> {
            if (currentSong != null) {
                bottomPlayerContainer.setVisibility(View.VISIBLE);
                
                // 更新歌曲信息
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
                
                // 更新播放按钮状态
                if (isPlaying) {
                    bottomPlayPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
                    bottomPlayPauseBtn.setEnabled(true);
                } else if (playStatus == PlayerStatus.PAUSED) {
                    bottomPlayPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                    bottomPlayPauseBtn.setEnabled(true);
                } else if (playStatus == PlayerStatus.STOPPED) {
                    bottomPlayPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                    bottomPlayPauseBtn.setEnabled(true);
                }
            } else {
                // 没有歌曲时的默认状态
                bottomPlayerContainer.setVisibility(View.VISIBLE);
                bottomSongName.setText("暂无歌曲");
                bottomArtistName.setText("未知艺术家");
                bottomAlbumCover.setImageResource(R.drawable.default_cover);
                bottomPlayPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                bottomPlayPauseBtn.setEnabled(false);
            }
        });
    }
    
    private void loadBottomCover(String filePath) {
        if (currentSong != null && currentActivity != null) {
            String coverUrl = currentSong.getCoverUrl();
            MusicCoverUtils.loadCoverSmart(filePath, coverUrl, currentActivity, bottomAlbumCover);
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
    
    // 强制刷新状态
    public void forceRefresh() {
        updateGlobalState();
        updateUI();
    }
}