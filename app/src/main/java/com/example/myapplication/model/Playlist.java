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

    public static List<Playlist> fromJson(String json) {
        // 使用 Gson 或其他库解析 JSON 字符串为 List<Playlist>
        return new Gson().fromJson(json, new TypeToken<List<Playlist>>() {}.getType());
    }

    public static String toJson(List<Playlist> playlists) {
        // 使用 Gson 或其他库将 List<Playlist> 转换为 JSON 字符串
        return new Gson().toJson(playlists);
    }
}