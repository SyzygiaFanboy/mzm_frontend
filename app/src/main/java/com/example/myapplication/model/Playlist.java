package com.example.myapplication.model;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.util.List;

public class Playlist {
    public static final String BILI_BIND_TYPE_FAV = "fav";
    public static final String BILI_BIND_TYPE_SEASON = "season";
    public static final String BILI_BIND_TYPE_SERIES = "series";

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

    @Expose
    @SerializedName("biliBound")
    private boolean biliBound;

    @Expose
    @SerializedName("biliUid")
    private String biliUid;

    @Expose
    @SerializedName("biliUserName")
    private String biliUserName;

    @Expose
    @SerializedName("biliAvatarUrl")
    private String biliAvatarUrl;

    @Expose
    @SerializedName("biliFolderId")
    private String biliFolderId;

    @Expose
    @SerializedName("biliFolderTitle")
    private String biliFolderTitle;

    @Expose
    @SerializedName("biliBindingType")
    private String biliBindingType;

    @Expose
    @SerializedName("biliSeasonId")
    private String biliSeasonId;

    @Expose
    @SerializedName("biliSeasonTitle")
    private String biliSeasonTitle;

    @Expose
    @SerializedName("biliSeriesId")
    private String biliSeriesId;

    @Expose
    @SerializedName("biliSeriesTitle")
    private String biliSeriesTitle;

    public Playlist(String name, int songCount, int coverRes) {
        this.name = name;
        this.songCount = songCount;
        this.coverRes = coverRes;
        this.latestCoverPath = null;
        this.biliBound = false;
        this.biliUid = null;
        this.biliUserName = null;
        this.biliAvatarUrl = null;
        this.biliFolderId = null;
        this.biliFolderTitle = null;
        this.biliBindingType = null;
        this.biliSeasonId = null;
        this.biliSeasonTitle = null;
        this.biliSeriesId = null;
        this.biliSeriesTitle = null;
    }

//    public Playlist(String name) {
//    }

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

    public boolean isBiliBound() {
        return biliBound;
    }

    public void setBiliBound(boolean biliBound) {
        this.biliBound = biliBound;
    }

    public String getBiliUid() {
        return biliUid;
    }

    public void setBiliUid(String biliUid) {
        this.biliUid = biliUid;
    }

    public String getBiliUserName() {
        return biliUserName;
    }

    public void setBiliUserName(String biliUserName) {
        this.biliUserName = biliUserName;
    }

    public String getBiliAvatarUrl() {
        return biliAvatarUrl;
    }

    public void setBiliAvatarUrl(String biliAvatarUrl) {
        this.biliAvatarUrl = biliAvatarUrl;
    }

    public String getBiliFolderId() {
        return biliFolderId;
    }

    public void setBiliFolderId(String biliFolderId) {
        this.biliFolderId = biliFolderId;
    }

    public String getBiliFolderTitle() {
        return biliFolderTitle;
    }

    public void setBiliFolderTitle(String biliFolderTitle) {
        this.biliFolderTitle = biliFolderTitle;
    }

    public String getBiliBindingType() {
        return biliBindingType;
    }

    public void setBiliBindingType(String biliBindingType) {
        this.biliBindingType = biliBindingType;
    }

    public String getBiliSeasonId() {
        return biliSeasonId;
    }

    public void setBiliSeasonId(String biliSeasonId) {
        this.biliSeasonId = biliSeasonId;
    }

    public String getBiliSeasonTitle() {
        return biliSeasonTitle;
    }

    public void setBiliSeasonTitle(String biliSeasonTitle) {
        this.biliSeasonTitle = biliSeasonTitle;
    }

    public String getBiliSeriesId() {
        return biliSeriesId;
    }

    public void setBiliSeriesId(String biliSeriesId) {
        this.biliSeriesId = biliSeriesId;
    }

    public String getBiliSeriesTitle() {
        return biliSeriesTitle;
    }

    public void setBiliSeriesTitle(String biliSeriesTitle) {
        this.biliSeriesTitle = biliSeriesTitle;
    }

    public String getEffectiveBiliBindingType() {
        if (biliBindingType != null && !biliBindingType.isEmpty()) {
            return biliBindingType;
        }
        if (biliFolderId != null && !biliFolderId.isEmpty()) {
            return BILI_BIND_TYPE_FAV;
        }
        if (biliSeasonId != null && !biliSeasonId.isEmpty()) {
            return BILI_BIND_TYPE_SEASON;
        }
        if (biliSeriesId != null && !biliSeriesId.isEmpty()) {
            return BILI_BIND_TYPE_SERIES;
        }
        return null;
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
