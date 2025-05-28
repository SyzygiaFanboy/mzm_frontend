package com.example.myapplication.adapter;

import android.content.Context;
import android.media.MediaPlayer;

import com.example.myapplication.PlayerStatus;
import com.example.myapplication.Song;
import java.io.IOException;

public class RealPlayerAdapter implements IMusicPlayer {
    private final MediaPlayer mediaPlayer;
    private Song currentSong;
    private OnSongCompletionListener listener;

    public RealPlayerAdapter(Context context) {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> {
            if (listener != null) listener.onSongCompleted();
        });
    }
    @Override
    public void load(Song song) {
        try {
            mediaPlayer.reset();
            //mediaPlayer.setDataSource(song.getPath());
            mediaPlayer.prepare();
            currentSong = song;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load audio", e);
        }
    }

    @Override
    public void play() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    public void pause() {  // 补全缺失的方法
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    @Override
    public void stop() {
        mediaPlayer.stop();
    }

    @Override
    public void seekTo(int position) {
        mediaPlayer.seekTo(position);
    }

    @Override
    public int getCurrentPosition() {
        return mediaPlayer.getCurrentPosition();
    }

    @Override
    public PlayerStatus getStatus() {
        if (mediaPlayer.isPlaying()) return PlayerStatus.PLAYING;
        else if (mediaPlayer.getCurrentPosition() > 0) return PlayerStatus.PAUSED;
        else return PlayerStatus.STOPPED;
    }

    @Override
    public void setOnSongCompletionListener(OnSongCompletionListener listener) {
        this.listener = listener;
    }
}