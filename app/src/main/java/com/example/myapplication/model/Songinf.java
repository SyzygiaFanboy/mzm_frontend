package com.example.myapplication.model;

import com.google.gson.annotations.SerializedName;

// 后端歌曲信息模型
public class Songinf {
    private String songid;
    private String songname;
    private String musician;
    private String songpath;
    private int songduration;
    @SerializedName("playcount")
    private int playCount;
    private boolean selected;

    // 必须有空参构造函数（用于 Gson 解析）
    public Songinf() {
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    // Getter 和 Setter 方法（必须与后端字段名称一致）
    public String getSongid() {
        return songid;
    }

    public void setSongid(String songid) {
        this.songid = songid;
    }

    public String getSongname() {
        return songname;
    }

    public void setSongname(String songname) {
        this.songname = songname;
    }

    public String getMusician() {
        return musician;
    }

    public void setMusician(String musician) {
        this.musician = musician;
    }

    public String getSongpath() {
        return songpath;
    }

    public void setSongpath(String songpath) {
        this.songpath = songpath;
    }

    public int getSongduration() {
        return songduration;
    }

    public void setSongduration(int songduration) {
        this.songduration = songduration;
    }

    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        this.playCount = playCount;
    }
}