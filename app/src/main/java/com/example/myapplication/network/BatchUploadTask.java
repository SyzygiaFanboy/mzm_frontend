package com.example.myapplication.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.myapplication.model.SelectedSong;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatchUploadTask {
    private static final String TAG = "BatchUploadTask";
    
    private Context context;
    private List<SelectedSong> songs;
    private BatchUploadCallback callback;
    private ExecutorService executor;
    private Handler mainHandler;
    private boolean isCancelled = false;
    
    public static class UploadProgress {
        public int currentIndex;
        public int totalCount;
        public int currentProgress;
        public String currentSongName;
        
        public UploadProgress(int currentIndex, int totalCount, int currentProgress, String currentSongName) {
            this.currentIndex = currentIndex;
            this.totalCount = totalCount;
            this.currentProgress = currentProgress;
            this.currentSongName = currentSongName;
        }
    }
    
    public interface BatchUploadCallback {
        void onSuccess(int successCount, int totalCount);
        void onError(String error, int failedIndex, int successCount);
        void onProgress(UploadProgress progress);
        void onSongUploadComplete(int index, String songName, boolean success);
    }
    
    public BatchUploadTask(Context context, List<SelectedSong> songs, BatchUploadCallback callback) {
        this.context = context;
        this.songs = songs;
        this.callback = callback;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public void execute() {
        executor.execute(() -> {
            int successCount = 0;
            int totalCount = songs.size();
            
            for (int i = 0; i < totalCount; i++) {
                if (isCancelled) {
                    int finalSuccessCount1 = successCount;
                    int finalI = i;
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onError("上传已取消", finalI, finalSuccessCount1);
                        }
                    });
                    return;
                }
                
                SelectedSong song = songs.get(i);
                final int currentIndex = i;
                final int currentSuccessCount = successCount;
                
                try {
                    //太带派了
                    UploadTask singleUploadTask = new UploadTask(context, new UploadTask.UploadCallback() {
                        @Override
                        public void onSuccess() {

                        }
                        
                        @Override
                        public void onError(String error) {

                        }
                        
                        @Override
                        public void onProgress(int progress) {

                            mainHandler.post(() -> {
                                if (callback != null) {
                                    callback.onProgress(new UploadProgress(currentIndex, totalCount, progress, song.getSongName()));
                                }
                            });
                        }
                    });
                    

                    String result = singleUploadTask.uploadSingleFile(song);
                    
                    if ("SUCCESS".equals(result)) {
                        successCount++;
                        final int finalSuccessCount = successCount;
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onProgress(new UploadProgress(currentIndex, totalCount, 100, song.getSongName()));
                                callback.onSongUploadComplete(currentIndex, song.getSongName(), true);
                            }
                        });
                    } else {
                        Log.e(TAG, "歌曲上传失败: " + song.getSongName() + ", 错误: " + result);
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onSongUploadComplete(currentIndex, song.getSongName(), false);
                            }
                        });
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "上传异常: " + song.getSongName(), e);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSongUploadComplete(currentIndex, song.getSongName(), false);
                        }
                    });
                }
            }
            
            // 上传完成
            final int finalSuccessCount = successCount;
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onSuccess(finalSuccessCount, totalCount);
                }
            });
        });
    }
    
    public void cancel() {
        isCancelled = true;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}