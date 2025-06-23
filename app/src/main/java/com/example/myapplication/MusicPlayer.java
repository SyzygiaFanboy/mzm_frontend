package com.example.myapplication;
import static android.content.ContentValues.TAG;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.myapplication.model.Song;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayer {
    private MediaPlayer mediaPlayer;
    private static MusicPlayer instance;
    private Song currentSong;
    //private PlayerThread playerThread = new PlayerThread();
    private Handler handler = new Handler();
    private boolean isPrepared = false;
    private int currentPosition = 0;
    private Handler progressHandler = new Handler();
    private Runnable progressRunnable;
    private Context context; // 添加上下文成员变量

    private int startPosition = 0;

    private volatile boolean isReleased = false;
    private boolean isCompletionLegitimate = true;

    private boolean isSeeking = false;

    private PlayerStatus playStatus = PlayerStatus.STOPPED;
    
    // 播放完成的监听器接口
    public interface OnSongCompletionListener {
        void onSongCompleted();
    }
    public interface OnPlaybackStateChangeListener {
        void onPlaybackStateChanged();
        void onSongChanged();
    }
    private OnPlaybackStateChangeListener stateChangeListener;
    private enum InternalState {
        IDLE, PREPARING, PREPARED, PLAYING, PAUSED, STOPPED, RELEASED
    }
    private boolean canStop() {
        return playStatus == PlayerStatus.PLAYING || playStatus == PlayerStatus.PAUSED;
    }

    private InternalState internalState = InternalState.IDLE;


    private OnSongCompletionListener completionListener;
    // 通过构造函数接收 Context
    public void setCompletionLegitimate(boolean legit) {
        this.isCompletionLegitimate = legit;
    }

    public MusicPlayer(Context context) {
        this.context = context.getApplicationContext(); // 使用全局上下文
        mediaPlayer = new MediaPlayer();
    }
    public boolean isReleased(){
        return isReleased;
    }
    public PlayerStatus getPlayStatus(){
        return playStatus;
    }
    public void setOnSongCompletionListener(OnSongCompletionListener listener) {
        this.completionListener = listener;
    }
    public Song getCurrentSong() {
        return currentSong;
    }
    public interface ProgressListener {
        void onProgressUpdated(int currentPosition, int totalDuration);
    }
    private ProgressListener progressListener;
    public void setOnPlaybackStateChangeListener(OnPlaybackStateChangeListener listener) {
        this.stateChangeListener = listener;
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }
    public void loadMusic(Song song) throws IOException {
        // 加入此段代码以防止 completion 回调在 reset 后误触发
        isCompletionLegitimate = true;
        if (mediaPlayer != null) {
            mediaPlayer.setOnCompletionListener(null);  // 清除旧监听器
        }

        // 统一 reset 或 new， 需要保证回到 Idle
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
        } else {
            mediaPlayer.reset();
        }
        currentSong = song;
        isPrepared  = false;


        // 在后台异步通知服务器，play_count 嘉嘉
        String onlineId = song.getOnlineSongId();
        Log.d("MusicPlayer", "loadMusic → triggering play-count for ID=" + song.getOnlineSongId());
        if (onlineId != null && !onlineId.isEmpty()) {
            new Thread(() -> {
                HttpURLConnection conn = null;
                try {
                    String urlStr = "http://192.168.1.100:8080/play/stream?songId="
                            + URLEncoder.encode(onlineId, "UTF-8");
                    conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    int responseCode = conn.getResponseCode();  // 触发请求
                    Log.d(TAG, "播放热度请求响应码: " + responseCode);
                } catch (Exception e) {
                    Log.e(TAG, "播放热度增加失败" + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }).start();
        }

        // 监听器（网络跟本地公用）
        // 在loadMusic方法中修改OnPreparedListener
        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            playStatus = PlayerStatus.PLAYING;
            mp.start();
            startProgressUpdates();
            
            // 修改这部分：通知所有播放状态变化监听器
            notifyPlaybackStateChanged();
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            playStatus = PlayerStatus.STOPPED;
            stopProgressUpdates();

            // 加保护：仅在合法状态下才回调
            if (isCompletionLegitimate && completionListener != null) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    completionListener.onSongCompleted();
                });
            }

            // 重置标志
            isCompletionLegitimate = true;
        });


        // 根据路径类型，设置 DataSource
        String path = song.getFilePath();
        if (path == null || (!path.startsWith("http") && song.getTimeDuration() <= 0)) {
            Log.e("MusicPlayer", "无效的歌曲或时长");
            return;
        }
        if (path.startsWith("http")) {
            // 网络音频流
            mediaPlayer.setDataSource(path);
        } else {
            // 本地 Content URI
            Uri uri = Uri.parse(path);
            mediaPlayer.setDataSource(context, uri);
        }
        
        // 修改这部分：通知所有歌曲变化监听器
        notifySongChanged();
        
        // 异步准备，然后onPreparedListener 会被回调
        mediaPlayer.prepareAsync();
    }

    public void resetProgress() {
        currentPosition = 0;
        if (progressListener != null) {
            progressListener.onProgressUpdated(0, 0);
        }
    }
    public void release() {
        Log.d("MusicPlayer", "释放 MediaPlayer");
        stopProgressUpdates(); // 先停止进度更新
        if (mediaPlayer != null) {
            try {
                isReleased = true;
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "release error", e);
            }
            mediaPlayer = null;
        }
        isPrepared = false;
        playStatus = PlayerStatus.STOPPED;
        currentPosition = 0; // 重置当前位置
    }
    private void startPlayback() {
        if (isPrepared) {
            mediaPlayer.start();
            startProgressUpdates();
        }
    }
    public int getDuration() {
        if (mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }
    private void startProgressUpdates() {
        stopProgressUpdates(); // 先停止之前的更新

        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && playStatus == PlayerStatus.PLAYING) {
                    int currentPos = mediaPlayer.getCurrentPosition();
                    currentPosition = currentPos;
                    int duration = mediaPlayer.getDuration();
                    if (progressListener != null) {
                        progressListener.onProgressUpdated(currentPos, duration);
                    }

                    progressHandler.postDelayed(this, 20);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
    }
    // 播放，按钮和通用
    public void play() {
        try {
            synchronized (this) {
                if (mediaPlayer != null && isPrepared && !mediaPlayer.isPlaying()) {
                    playStatus = PlayerStatus.PLAYING;
                    mediaPlayer.start();
                    startProgressUpdates();
                    Log.d("MusicPlayer", "播放开始，通知监听器");
                    // 通知状态变化
                    notifyPlaybackStateChanged();
                }
            }
        } catch(IllegalStateException e) {
            Log.e("MusicPlayer", "播放时发生状态异常", e);
            release();
            isPrepared = false;
        }
    }
//停，通用
public void pause() {
    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
        playStatus = PlayerStatus.PAUSED;
        mediaPlayer.pause();
        Log.d("MusicPlayer", "播放暂停，通知监听器");
        // 使用新的多监听器通知方法
        notifyPlaybackStateChanged();
    }
}

public void stop() {
    playStatus = PlayerStatus.STOPPED;
    isCompletionLegitimate = false;
    if (mediaPlayer != null) {
        try {
            if (playStatus == PlayerStatus.PLAYING || playStatus == PlayerStatus.PAUSED) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
        } catch (IllegalStateException e) {
            Log.e(TAG, "stop: MediaPlayer 状态异常", e);
        }
        mediaPlayer = null;
    }
    stopProgressUpdates();
    // 添加状态通知
    notifyPlaybackStateChanged();
}



    public boolean isPlaying(){
        return playStatus == PlayerStatus.PLAYING;
    }

    public boolean isPaused(){
        return playStatus == PlayerStatus.PAUSED;
    }

    public boolean isStop(){
        return playStatus == PlayerStatus.STOPPED;
    }

    public void seekTo(int position) {
        if (mediaPlayer != null && position >= 0 && position <= mediaPlayer.getDuration()) {
            mediaPlayer.seekTo(position);
            if (progressListener != null) {
                progressListener.onProgressUpdated(position, mediaPlayer.getDuration());
            }
        }
    }
    public int getCurrentPosition() {
        if (mediaPlayer != null && !isReleased) { // 检查状态
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                Log.e("MusicPlayer", "MediaPlayer 状态异常", e);
                return 0;
            }
        }
        return 0;
    }
    //归零
    public void setCurrentPositiontozero(){
        startPosition = 0;
        currentPosition = 0;
    }
    
    // 改为支持多个监听器
    private List<OnPlaybackStateChangeListener> stateChangeListeners = new ArrayList<>();
    
    public void addOnPlaybackStateChangeListener(OnPlaybackStateChangeListener listener) {
        if (!stateChangeListeners.contains(listener)) {
            stateChangeListeners.add(listener);
        }
    }
    
    public void removeOnPlaybackStateChangeListener(OnPlaybackStateChangeListener listener) {
        stateChangeListeners.remove(listener);
    }
    
    // 修改通知方法
    private void notifyPlaybackStateChanged() {
        for (OnPlaybackStateChangeListener listener : stateChangeListeners) {
            if (listener != null) {
                listener.onPlaybackStateChanged();
            }
        }
    }
    
    private void notifySongChanged() {
        for (OnPlaybackStateChangeListener listener : stateChangeListeners) {
            if (listener != null) {
                listener.onSongChanged();
            }
        }
    }
    // 在MusicPlayer类中添加新方法
    public void updateCurrentSongCoverUrl(String coverUrl) {
        if (currentSong != null) {
            currentSong.setCoverUrl(coverUrl);
        }
    }
}
