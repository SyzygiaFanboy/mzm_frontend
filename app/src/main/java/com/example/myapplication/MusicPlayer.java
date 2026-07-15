package com.example.myapplication;
import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.myapplication.model.Song;
import com.example.myapplication.network.ServerConfig;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MusicPlayer {
    private static MusicPlayer instance;
    private Song currentSong;
    //private PlayerThread playerThread = new PlayerThread();
    private Handler handler = new Handler();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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
    private final AtomicBoolean isSongChanging = new AtomicBoolean(false);
    private volatile int queueIndex = -1;
    private volatile String queuePlaylist = null;
    private final List<Song> playQueue = new ArrayList<>();
    private final Random random = new Random();

    private ExoPlayer exoPlayer;
    private final OkHttpClient okHttpClient;
    private final OkHttpDataSource.Factory okHttpDataSourceFactory;
    private final AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus = false;
    private boolean resumeOnAudioFocusGain = false;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                hasAudioFocus = true;
                if (resumeOnAudioFocusGain && currentSong != null && !isSongChanging.get()) {
                    resumeOnAudioFocusGain = false;
                    play();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (isPlaying()) {
                    resumeOnAudioFocusGain = true;
                    pauseInternal(false);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                hasAudioFocus = false;
                resumeOnAudioFocusGain = false;
                if (isPlaying()) {
                    pauseInternal(true);
                }
                break;
            default:
                break;
        }
    };
    
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
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        this.okHttpClient = new OkHttpClient();
        this.okHttpDataSourceFactory = new OkHttpDataSource.Factory(okHttpClient);

        Map<String, String> defaultHeaders = new HashMap<>();
        defaultHeaders.put("User-Agent", "Mozilla/5.0");
        okHttpDataSourceFactory.setDefaultRequestProperties(defaultHeaders);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(
                this.context,
                okHttpDataSourceFactory
        );
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
        exoPlayer = new ExoPlayer.Builder(this.context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        exoPlayer.setAudioAttributes(audioAttributes, false);
        exoPlayer.setHandleAudioBecomingNoisy(true);
        exoPlayer.setWakeMode(C.WAKE_MODE_NETWORK);
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    isPrepared = true;
                    if (exoPlayer != null && !exoPlayer.getPlayWhenReady()) {
                        isSongChanging.set(false);
                    }
                    notifyPlaybackStateChanged();
                } else if (playbackState == Player.STATE_ENDED) {
                    handleCompletion();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    playStatus = PlayerStatus.PLAYING;
                    isSongChanging.set(false);
                    startProgressUpdates();
                } else {
                    int playbackState = exoPlayer != null ? exoPlayer.getPlaybackState() : Player.STATE_IDLE;
                    boolean isTransientTransition = isSongChanging.get()
                            || playbackState == Player.STATE_BUFFERING
                            || playbackState == Player.STATE_ENDED;
                    if (!isTransientTransition && isPrepared && playStatus == PlayerStatus.PLAYING) {
                        playStatus = PlayerStatus.PAUSED;
                    }
                    stopProgressUpdates();
                }
                notifyPlaybackStateChanged();
            }
        });
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
    private final List<ProgressListener> extraProgressListeners = new ArrayList<>();

    public enum PlayMode {
        SEQUENTIAL,
        SINGLE_LOOP,
        SHUFFLE
    }

    private PlayMode playMode = PlayMode.SEQUENTIAL;
    public void setOnPlaybackStateChangeListener(OnPlaybackStateChangeListener listener) {
        this.stateChangeListener = listener;
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    public void addProgressListener(ProgressListener listener) {
        if (listener == null) {
            return;
        }
        if (!extraProgressListeners.contains(listener)) {
            extraProgressListeners.add(listener);
        }
    }

    public void removeProgressListener(ProgressListener listener) {
        extraProgressListeners.remove(listener);
    }

    public PlayMode getPlayMode() {
        return playMode;
    }

    public void setPlayMode(PlayMode playMode) {
        if (playMode == null) {
            return;
        }
        this.playMode = playMode;
        notifyPlaybackStateChanged();
    }

    public boolean isSongChanging() {
        return isSongChanging.get();
    }

    public void setQueueContext(String playlist, int index) {
        this.queuePlaylist = playlist;
        this.queueIndex = index;
    }

    public int getQueueIndex() {
        return queueIndex;
    }

    public String getQueuePlaylist() {
        return queuePlaylist;
    }

    public synchronized void setPlayQueue(String playlist, List<Song> songs, int startIndex) {
        playQueue.clear();
        if (songs != null) {
            playQueue.addAll(songs);
        }
        queuePlaylist = playlist;
        if (playQueue.isEmpty()) {
            queueIndex = -1;
        } else {
            queueIndex = Math.max(0, Math.min(startIndex, playQueue.size() - 1));
        }
    }

    public synchronized List<Song> getPlayQueueSnapshot() {
        return new ArrayList<>(playQueue);
    }

    public synchronized int getPlayQueueSize() {
        return playQueue.size();
    }

    public synchronized void playAtQueueIndex(int index) throws IOException {
        if (playQueue.isEmpty()) {
            return;
        }
        int target = Math.max(0, Math.min(index, playQueue.size() - 1));
        queueIndex = target;
        Song original = playQueue.get(target);
        Song toPlay = original;
        if (queuePlaylist != null && (original.getPlaylist() == null || original.getPlaylist().isEmpty())) {
            Song s = new Song(original.getTimeDuration(), original.getName(), original.getFilePath(), queuePlaylist);
            s.setCoverUrl(original.getCoverUrl());
            s.setOnlineSongId(original.getOnlineSongId());
            toPlay = s;
        }
        loadMusic(toPlay);
    }

    public synchronized int computeNextQueueIndex() {
        if (playQueue.isEmpty()) {
            return -1;
        }
        int base = queueIndex;
        if (base < 0 || base >= playQueue.size()) {
            base = 0;
        }
        if (playMode == PlayMode.SINGLE_LOOP) {
            return base;
        }
        if (playMode == PlayMode.SHUFFLE) {
            return random.nextInt(playQueue.size());
        }
        return (base + 1) % playQueue.size();
    }

    public synchronized int computePrevQueueIndex() {
        if (playQueue.isEmpty()) {
            return -1;
        }
        int base = queueIndex;
        if (base < 0 || base >= playQueue.size()) {
            base = 0;
        }
        if (playMode == PlayMode.SINGLE_LOOP) {
            return base;
        }
        if (playMode == PlayMode.SHUFFLE) {
            return random.nextInt(playQueue.size());
        }
        return base == 0 ? playQueue.size() - 1 : base - 1;
    }

    public void playNextInQueue() throws IOException {
        int next;
        synchronized (this) {
            next = computeNextQueueIndex();
        }
        if (next >= 0) {
            playAtQueueIndex(next);
        }
    }

    public void playPrevInQueue() throws IOException {
        int prev;
        synchronized (this) {
            prev = computePrevQueueIndex();
        }
        if (prev >= 0) {
            playAtQueueIndex(prev);
        }
    }

    public void loadMusic(Song song) throws IOException {
        isSongChanging.set(true);
        // 加入此段代码以防止 completion 回调在 reset 后误触发
        isCompletionLegitimate = true;
        currentSong = song;
        isPrepared  = false;
        playStatus = PlayerStatus.STOPPED;
        stopProgressUpdates();
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }

        triggerPlayCount(song);
        notifySongChanged();

        String path = song.getFilePath();
        if (path == null || path.trim().isEmpty()) {
            isSongChanging.set(false);
            return;
        }

        if (isBiliPath(path)) {
            BiliId biliId = parseBiliId(path);
            if (biliId == null) {
                isSongChanging.set(false);
                return;
            }
            resolveBiliAudioUrl(biliId.bvid, biliId.cid, audioUrl -> {
                if (audioUrl == null || audioUrl.isEmpty()) {
                    isSongChanging.set(false);
                    return;
                }
                Map<String, String> headers = new HashMap<>();
                headers.put("User-Agent", "Mozilla/5.0");
                headers.put("Referer", "https://www.bilibili.com/");
                okHttpDataSourceFactory.setDefaultRequestProperties(headers);

                setAndPrepare(Uri.parse(audioUrl));
            });
            return;
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0");
        okHttpDataSourceFactory.setDefaultRequestProperties(headers);

        Uri uri = Uri.parse(path);
        setAndPrepare(uri);
    }

    private void setAndPrepare(Uri uri) {
        if (exoPlayer == null || uri == null) {
            isSongChanging.set(false);
            return;
        }
        if (!requestAudioFocusIfNeeded()) {
            isSongChanging.set(false);
            return;
        }
        if (!PlaybackService.isRunning()) {
            PlaybackService.start(context);
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(uri));
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.prepare();
    }

    private void triggerPlayCount(Song song) {
        if (song == null) {
            return;
        }
        String onlineId = song.getOnlineSongId();
        Log.d("MusicPlayer", "loadMusic → triggering play-count for ID=" + onlineId);
        if (onlineId == null || onlineId.isEmpty()) {
            return;
        }
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = ServerConfig.playStreamUrl(URLEncoder.encode(onlineId, "UTF-8"));
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int responseCode = conn.getResponseCode();
                Log.d(TAG, "播放热度请求响应码: " + responseCode);
            } catch (Exception e) {
                Log.e(TAG, "播放热度增加失败" + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void handleCompletion() {
        stopProgressUpdates();

        boolean shouldAutoNext = !isSongChanging.get() && isCompletionLegitimate && getPlayQueueSize() > 0;
        if (shouldAutoNext) {
            isSongChanging.set(true);
            try {
                playNextInQueue();
            } catch (Exception e) {
                isSongChanging.set(false);
                playStatus = PlayerStatus.STOPPED;
                Log.e(TAG, "自动播放下一首失败", e);
            }
        } else {
            playStatus = PlayerStatus.STOPPED;
        }

        if (!isSongChanging.get() && isCompletionLegitimate && completionListener != null) {
            new Handler(Looper.getMainLooper()).post(() -> completionListener.onSongCompleted());
        }
        isCompletionLegitimate = true;
        notifyPlaybackStateChanged();
    }

    public void resetProgress() {
        currentPosition = 0;
        if (progressListener != null) {
            progressListener.onProgressUpdated(0, 0);
        }
        for (ProgressListener listener : extraProgressListeners) {
            if (listener != null) {
                listener.onProgressUpdated(0, 0);
            }
        }
    }
    public void release() {
        Log.d("MusicPlayer", "释放 ExoPlayer");
        stopProgressUpdates();
        abandonAudioFocusIfNeeded();
        if (exoPlayer != null) {
            isReleased = true;
            exoPlayer.release();
            exoPlayer = null;
        }
        isPrepared = false;
        playStatus = PlayerStatus.STOPPED;
        currentPosition = 0; // 重置当前位置
    }
    private void startPlayback() {
        if (isPrepared) {
            if (exoPlayer != null) {
                exoPlayer.play();
            }
            startProgressUpdates();
        }
    }
    public int getDuration() {
        return runOnPlayerThread(() -> {
            if (exoPlayer == null) {
                return 0;
            }
            long d = exoPlayer.getDuration();
            if (d < 0) {
                return 0;
            }
            return (int) Math.min(Integer.MAX_VALUE, d);
        });
    }
    private void startProgressUpdates() {
        stopProgressUpdates(); // 先停止之前的更新

        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && playStatus == PlayerStatus.PLAYING) {
                    long currentPosLong = exoPlayer.getCurrentPosition();
                    int currentPos = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, currentPosLong));
                    currentPosition = currentPos;
                    int duration = getDuration();
                    if (progressListener != null) {
                        progressListener.onProgressUpdated(currentPos, duration);
                    }
                    for (ProgressListener listener : extraProgressListeners) {
                        if (listener != null) {
                            listener.onProgressUpdated(currentPos, duration);
                        }
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
        synchronized (this) {
            if (exoPlayer == null) {
                return;
            }
            if (!requestAudioFocusIfNeeded()) {
                return;
            }
            if (!PlaybackService.isRunning()) {
                PlaybackService.start(context);
            }
            playStatus = PlayerStatus.PLAYING;
            exoPlayer.play();
            startProgressUpdates();
            notifyPlaybackStateChanged();
        }
    }
//停，通用
public void pause() {
    pauseInternal(true);
}

private void pauseInternal(boolean abandonFocus) {
    if (exoPlayer != null && playStatus == PlayerStatus.PLAYING) {
        playStatus = PlayerStatus.PAUSED;
        exoPlayer.pause();
        if (abandonFocus) {
            abandonAudioFocusIfNeeded();
        }
        notifyPlaybackStateChanged();
    }
}

public void stop() {
    playStatus = PlayerStatus.STOPPED;
    isCompletionLegitimate = false;
    isSongChanging.set(false);
    if (exoPlayer != null) {
        exoPlayer.stop();
        exoPlayer.clearMediaItems();
    }
    stopProgressUpdates();
    abandonAudioFocusIfNeeded();
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
        int duration = getDuration();
        if (exoPlayer != null && position >= 0 && (duration <= 0 || position <= duration)) {
            exoPlayer.seekTo(position);
            if (progressListener != null) {
                progressListener.onProgressUpdated(position, duration);
            }
            for (ProgressListener listener : extraProgressListeners) {
                if (listener != null) {
                    listener.onProgressUpdated(position, duration);
                }
            }
        }
    }
    public int getCurrentPosition() {
        return runOnPlayerThread(() -> {
            if (exoPlayer == null || isReleased) {
                return 0;
            }
            long pos = exoPlayer.getCurrentPosition();
            if (pos < 0) {
                return 0;
            }
            return (int) Math.min(Integer.MAX_VALUE, pos);
        });
    }

    private int runOnPlayerThread(java.util.concurrent.Callable<Integer> callable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                return callable.call();
            } catch (Exception e) {
                Log.e(TAG, "播放器状态读取失败", e);
                return 0;
            }
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(0);
        mainHandler.post(() -> {
            try {
                result.set(callable.call());
            } catch (Exception e) {
                Log.e(TAG, "播放器状态读取失败", e);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result.get();
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

    private static boolean isBiliPath(String filePath) {
        return filePath != null && filePath.toLowerCase(Locale.ROOT).startsWith("bili://");
    }

    private static final class BiliId {
        final String bvid;
        final long cid;
        BiliId(String bvid, long cid) {
            this.bvid = bvid;
            this.cid = cid;
        }
    }

    private static BiliId parseBiliId(String filePath) {
        if (filePath == null) {
            return null;
        }
        String raw = filePath.substring("bili://".length());
        String[] parts = raw.split("_", 2);
        String bvid = parts.length > 0 ? parts[0] : null;
        long cid = -1;
        if (parts.length >= 2) {
            try {
                cid = Long.parseLong(parts[1]);
            } catch (Exception ignored) {
                cid = -1;
            }
        }
        if (bvid == null || bvid.isEmpty()) {
            return null;
        }
        return new BiliId(bvid, cid);
    }

    private void resolveBiliAudioUrl(String bvid, long cid, java.util.function.Consumer<String> callback) {
        if (bvid == null || bvid.isEmpty()) {
            if (callback != null) {
                callback.accept(null);
            }
            return;
        }

        HttpUrl.Builder builder = HttpUrl.parse(ServerConfig.appBaseUrl() + "BiliResolveServlet")
                .newBuilder()
                .addQueryParameter("bvid", bvid);
        if (cid > 0) {
            builder.addQueryParameter("cid", String.valueOf(cid));
        }
        HttpUrl url = builder.build();

        Request req = new Request.Builder()
                .url(url)
                .get()
                .build();

        okHttpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) {
                        callback.accept(null);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String audioUrl = null;
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
                    int code = obj.has("code") ? obj.get("code").getAsInt() : -1;
                    if (code == 0 && obj.has("audio_url") && !obj.get("audio_url").isJsonNull()) {
                        audioUrl = obj.get("audio_url").getAsString();
                    }
                } catch (Exception ignored) {
                    audioUrl = null;
                } finally {
                    String finalAudioUrl = audioUrl;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (callback != null) {
                            callback.accept(finalAudioUrl);
                        }
                    });
                }
            }
        });
    }

    private boolean requestAudioFocusIfNeeded() {
        if (audioManager == null) {
            return true;
        }
        if (hasAudioFocus) {
            return true;
        }
        int result;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .setWillPauseWhenDucked(true)
                        .setAudioAttributes(new android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .build();
            }
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            );
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasAudioFocus;
    }

    private void abandonAudioFocusIfNeeded() {
        if (audioManager == null || !hasAudioFocus) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
        hasAudioFocus = false;
    }
}
