package com.example.myapplication;

import android.app.Application;

public class MyApp extends Application {
    private MusicPlayer musicPlayer;
    private GlobalBottomPlayerManager globalBottomPlayerManager;

    @Override
    public void onCreate() {
        super.onCreate();
        // 在整个 App 的生命周期内只创建一个 MusicPlayer
        musicPlayer = new MusicPlayer(this);
        
        // 初始化全局底部播放栏管理器
        globalBottomPlayerManager = GlobalBottomPlayerManager.getInstance();
        globalBottomPlayerManager.initialize(musicPlayer);
    }

    public MusicPlayer getMusicPlayer() {
        return musicPlayer;
    }
    
    public GlobalBottomPlayerManager getGlobalBottomPlayerManager() {
        return globalBottomPlayerManager;
    }
}
