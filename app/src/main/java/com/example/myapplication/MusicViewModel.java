package com.example.myapplication;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Map;

//歌单，这里要改进
//1. 可以添加类似网易云的功能，在歌单封面显示最新加入的音乐的封面，或者能自己添加上传
//2. 可以将歌单的xml转移至MainActivityxml的侧边栏之中，不然就需要在现在这个页面最底部放置一个通用currentMusicPlayermanager来管理正在播放音乐
//3. 复选状态修改一下，
//4. 美化一下现在不太美观

public class MusicViewModel extends ViewModel {
    private MutableLiveData<List<Map<String, Object>>> musicList = new MutableLiveData<>();
    private String currentPlaylist;
    public void setCurrentPlaylist(String playlist) {
        this.currentPlaylist = playlist;
    }
    public void loadSongs(Context context, String playlist) {
        new Thread(() -> {
            List<Map<String, Object>> data = MusicLoader.loadSongs(context, playlist); // 传递两个参数
            musicList.postValue(data);
        }).start();
    }

    public LiveData<List<Map<String, Object>>> getMusicList() {
        return musicList;
    }
}
