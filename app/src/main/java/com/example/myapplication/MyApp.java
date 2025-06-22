package com.example.myapplication;

import android.app.Application;
import android.util.Log;

public class MyApp extends Application {
    private MusicPlayer musicPlayer;
    private GlobalBottomPlayerManager globalBottomPlayerManager;

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 确保 MusicPlayer 只创建一次
        musicPlayer = new MusicPlayer(this);
        
        // 确保 GlobalBottomPlayerManager 只初始化一次
        globalBottomPlayerManager = GlobalBottomPlayerManager.getInstance();
        globalBottomPlayerManager.initialize(musicPlayer);
        
        Log.d("MyApp", "全局组件初始化完成");
    }

    public MusicPlayer getMusicPlayer() {
        return musicPlayer;
    }
    
    public GlobalBottomPlayerManager getGlobalBottomPlayerManager() {
        return globalBottomPlayerManager;
    }
}
