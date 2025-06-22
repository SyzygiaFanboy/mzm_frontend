package com.example.myapplication.model;

import android.net.Uri;

public class SelectedSong {
    private String songName;
    private String artist;
    private int duration;
    private String filePath;
    private Uri uri;
    private int playCount; // 新增字段
    
    public SelectedSong(String songName, String artist, int duration, String filePath, Uri uri) {
        this.songName = songName;
        this.artist = artist;
        this.duration = duration;
        this.filePath = filePath;
        this.uri = uri;
        this.playCount = 0; // 默认播放次数为0
    }
    
    // Getters and Setters
    public String getSongName() { return songName; }
    public void setSongName(String songName) { this.songName = songName; }
    
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public Uri getUri() { return uri; }
    public void setUri(Uri uri) { this.uri = uri; }
    
    public int getPlayCount() { return playCount; }
    public void setPlayCount(int playCount) { this.playCount = playCount; }
}