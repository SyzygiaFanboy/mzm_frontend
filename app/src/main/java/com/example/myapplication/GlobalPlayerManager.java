package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import com.example.myapplication.model.Song;

public class GlobalPlayerManager {
    private static GlobalPlayerManager instance;
    private Song currentSong;
    private boolean isPlaying = false;
    private Bitmap currentCover;
    private OnPlayerStateChangeListener listener;
    
    public interface OnPlayerStateChangeListener {
        void onSongChanged(Song song);
        void onPlayStateChanged(boolean isPlaying);
        void onCoverChanged(Bitmap cover);
    }
    
    public static GlobalPlayerManager getInstance() {
        if (instance == null) {
            instance = new GlobalPlayerManager();
        }
        return instance;
    }
    
    public void setCurrentSong(Song song) {
        this.currentSong = song;
        if (listener != null) {
            listener.onSongChanged(song);
        }
    }
    
    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        if (listener != null) {
            listener.onPlayStateChanged(playing);
        }
    }
    
    public void setCurrentCover(Bitmap cover) {
        this.currentCover = cover;
        if (listener != null) {
            listener.onCoverChanged(cover);
        }
    }
    
    public Song getCurrentSong() { return currentSong; }
    public boolean isPlaying() { return isPlaying; }
    public Bitmap getCurrentCover() { return currentCover; }
    
    public void setOnPlayerStateChangeListener(OnPlayerStateChangeListener listener) {
        this.listener = listener;
    }
    
    public void removeListener() {
        this.listener = null;
    }
}