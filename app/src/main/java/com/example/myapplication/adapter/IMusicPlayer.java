package com.example.myapplication.adapter;

import com.example.myapplication.model.Song;
import com.example.myapplication.PlayerStatus;

public interface IMusicPlayer {
    void load(Song song);
    void play();
    void pause();
    void stop();
    void seekTo(int position);
    int getCurrentPosition();
    PlayerStatus getStatus();
    void setOnSongCompletionListener(OnSongCompletionListener listener);

    interface OnSongCompletionListener {
        void onSongCompleted();
    }
}