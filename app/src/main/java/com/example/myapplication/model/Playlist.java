package com.example.myapplication.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;

public class Playlist {
    private String name;
    private int songCount;
    private int coverRes;

    public Playlist(String name, int songCount, int coverRes) {
        this.name = name;
        this.songCount = songCount;
        this.coverRes = coverRes;
    }

    public String getName() {
        return name;
    }

    public int getSongCount() {
        return songCount;
    }

    public int getCoverRes() {
        return coverRes;
    }

    public void setSongCount(int count) {
        this.songCount = count;
    }

    public static List<Playlist> fromJson(String json) {
        return new Gson().fromJson(json, new TypeToken<List<Playlist>>() {}.getType());
    }

    public static String toJson(List<Playlist> playlists) {
        return new Gson().toJson(playlists);
    }
}
