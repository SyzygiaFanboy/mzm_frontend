package com.example.myapplication;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import com.example.myapplication.model.Song;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalBottomPlayerManager {
    private static GlobalBottomPlayerManager instance;
    private MusicPlayer musicPlayer;
    // 多 Activity 管理
    private final List<Activity> attachedActivities = new ArrayList<>();
    private final Map<Activity, View> bottomPlayerContainerMap = new HashMap<>();
    private final Map<Activity, ImageView> bottomAlbumCoverMap = new HashMap<>();
    private final Map<Activity, TextView> bottomSongNameMap = new HashMap<>();
    private final Map<Activity, TextView> bottomArtistNameMap = new HashMap<>();
    private final Map<Activity, ImageButton> bottomPlayPauseBtnMap = new HashMap<>();
    private final Map<Activity, OnBottomPlayerClickListener> clickListenerMap = new HashMap<>();

    // 全局状态
    private Song currentSong;
    private boolean isPlaying;
    private PlayerStatus playStatus;

    public interface OnBottomPlayerClickListener {
        void onPlayPauseClick();
    }

    public static GlobalBottomPlayerManager getInstance() {
        if (instance == null) {
            instance = new GlobalBottomPlayerManager();
        }
        return instance;
    }

    public void initialize(MusicPlayer musicPlayer) {
        this.musicPlayer = musicPlayer;
        if (this.musicPlayer != null) {
            this.musicPlayer.removeOnPlaybackStateChangeListener(stateChangeListener);
        }
        stateChangeListener = new MusicPlayer.OnPlaybackStateChangeListener() {
            @Override
            public void onPlaybackStateChanged() {
                Log.d("GlobalBottomPlayer", "播放状态变更回调触发");
                updateGlobalState();
                updateUI();
            }
            @Override
            public void onSongChanged() {
                Log.d("GlobalBottomPlayer", "歌曲变更回调触发");
                updateGlobalState();
                updateUI();
            }
        };
        musicPlayer.addOnPlaybackStateChangeListener(stateChangeListener);
        Log.d("GlobalBottomPlayer", "监听器注册完成");
    }

    private MusicPlayer.OnPlaybackStateChangeListener stateChangeListener;

    public void attachToActivity(Activity activity) {
        if (!attachedActivities.contains(activity)) {
            attachedActivities.add(activity);
            initViews(activity);
            setupListeners(activity);
            updateUI();
        }
    }

    public void detachFromActivity(Activity activity) {
        attachedActivities.remove(activity);
        bottomPlayerContainerMap.remove(activity);
        bottomAlbumCoverMap.remove(activity);
        bottomSongNameMap.remove(activity);
        bottomArtistNameMap.remove(activity);
        bottomPlayPauseBtnMap.remove(activity);
        clickListenerMap.remove(activity);
    }

    private void initViews(Activity activity) {
        View bottomPlayerContainer = activity.findViewById(R.id.bottom_player_container);
        ImageView bottomAlbumCover = activity.findViewById(R.id.bottom_album_cover);
        TextView bottomSongName = activity.findViewById(R.id.bottom_song_name);
        TextView bottomArtistName = activity.findViewById(R.id.bottom_artist_name);
        ImageButton bottomPlayPauseBtn = activity.findViewById(R.id.bottom_play_pause_btn);
        bottomPlayerContainerMap.put(activity, bottomPlayerContainer);
        bottomAlbumCoverMap.put(activity, bottomAlbumCover);
        bottomSongNameMap.put(activity, bottomSongName);
        bottomArtistNameMap.put(activity, bottomArtistName);
        bottomPlayPauseBtnMap.put(activity, bottomPlayPauseBtn);
    }

    private void setupListeners(Activity activity) {
        ImageButton bottomPlayPauseBtn = bottomPlayPauseBtnMap.get(activity);
        if (bottomPlayPauseBtn != null) {
            bottomPlayPauseBtn.setOnClickListener(v -> {
                OnBottomPlayerClickListener listener = clickListenerMap.get(activity);
                if (listener != null) {
                    listener.onPlayPauseClick();
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
        for (Activity activity : attachedActivities) {
            View bottomPlayerContainer = bottomPlayerContainerMap.get(activity);
            TextView bottomSongName = bottomSongNameMap.get(activity);
            TextView bottomArtistName = bottomArtistNameMap.get(activity);
            ImageView bottomAlbumCover = bottomAlbumCoverMap.get(activity);
            ImageButton bottomPlayPauseBtn = bottomPlayPauseBtnMap.get(activity);
            if (activity == null || bottomPlayerContainer == null) continue;
            activity.runOnUiThread(() -> {
                if (bottomPlayerContainer == null || bottomSongName == null || bottomArtistName == null || bottomAlbumCover == null || bottomPlayPauseBtn == null) {
                    return;
                }
                if (currentSong != null) {
                    bottomPlayerContainer.setVisibility(View.VISIBLE);
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
                    loadBottomCover(activity, currentSong.getFilePath());
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
                    bottomPlayerContainer.setVisibility(View.VISIBLE);
                    bottomSongName.setText("暂无歌曲");
                    bottomArtistName.setText("未知艺术家");
                    bottomAlbumCover.setImageResource(R.drawable.default_cover);
                    bottomPlayPauseBtn.setImageResource(android.R.drawable.ic_media_play);
                    bottomPlayPauseBtn.setEnabled(false);
                }
            });
        }
    }

    private void loadBottomCover(Activity activity, String filePath) {
        if (currentSong != null && activity != null) {
            String coverUrl = currentSong.getCoverUrl();
            ImageView bottomAlbumCover = bottomAlbumCoverMap.get(activity);
            MusicCoverUtils.loadCoverSmart(filePath, coverUrl, activity, bottomAlbumCover);
        }
    }

    public void setOnBottomPlayerClickListener(Activity activity, OnBottomPlayerClickListener listener) {
        clickListenerMap.put(activity, listener);
    }

    public void show(Activity activity) {
        View bottomPlayerContainer = bottomPlayerContainerMap.get(activity);
        if (bottomPlayerContainer != null) {
            bottomPlayerContainer.setVisibility(View.VISIBLE);
        }
    }

    public void hide(Activity activity) {
        View bottomPlayerContainer = bottomPlayerContainerMap.get(activity);
        if (bottomPlayerContainer != null) {
            bottomPlayerContainer.setVisibility(View.GONE);
        }
    }

    public void forceRefresh() {
        updateGlobalState();
        updateUI();
    }
}