package com.example.myapplication;

import android.app.Application;

public class MyApp extends Application {
    private MusicPlayer musicPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        // 在整个 App 的生命周期内只创建一个 MusicPlayer
        musicPlayer = new MusicPlayer(this);
    }

    public MusicPlayer getMusicPlayer() {
        return musicPlayer;
    }
}
