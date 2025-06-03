package com.example.myapplication.model;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.util.List;

public class Playlist {
    @Expose
    @SerializedName("name")
    private String name;

    @Expose
    @SerializedName("songCount")
    private int songCount;

    @Expose
    @SerializedName("coverRes")
    private int coverRes;

    @Expose
    @SerializedName("latestCoverPath")
    private String latestCoverPath;

    public Playlist(String name, int songCount, int coverRes) {
        this.name = name;
        this.songCount = songCount;
        this.coverRes = coverRes;
        this.latestCoverPath = null;
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

    public String getLatestCoverPath() {
        return latestCoverPath;
    }

    public void setLatestCoverPath(String latestCoverPath) {
        this.latestCoverPath = latestCoverPath;
    }

    public static List<Playlist> fromJson(String json) {
        return new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
                .fromJson(json, new TypeToken<List<Playlist>>() {}.getType());
    }

    public static String toJson(List<Playlist> playlists) {
        return new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .serializeNulls()
                .create()
                .toJson(playlists);
    }
}
