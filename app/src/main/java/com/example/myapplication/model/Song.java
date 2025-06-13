package com.example.myapplication.model;

import java.util.Map;
import java.util.HashMap;

//服务端歌曲信息模型
public class Song {
    //    private  int id;
    private String playlist; //   歌单字段
    private String onlineSongId;  // 在线音乐的唯一ID
    private String name;
    private int timeDuration;
    private final String filePath; // 字段文件路径
    private int playCount = 0; // 播放次数字段
    private String coverUrl; // 添加封面URL字段

    public Song(int timeDuration, String name, String filePath, int playCount, String playlist) {
        this.timeDuration = timeDuration;
        this.name = name;
        this.filePath = filePath;
        this.playCount = playCount;
        this.playlist = playlist;
    }

    public Song(int timeDuration, String name, String filePath, String playlist) {
        this(timeDuration, name, filePath, 0, playlist); // 默认播放次数为0
    }

    //    getter/setter
    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        this.playCount = playCount;
    }

    public void setOnlineSongId(String id) {
        this.onlineSongId = id;
    }

    public String getOnlineSongId() {
        return this.onlineSongId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTimeDuration(int timeDuration) {
        this.timeDuration = timeDuration;
    }

    //public void set(int id){this.id = id;}

    //public int getId(){return id; }
    public String getPlaylist() {
        return playlist;
    }

    public void setPlaylist(String playlist) {
        this.playlist = playlist;
    }

    public String getName() {
        return name;
    }

    public int getTimeDuration() {
        return timeDuration;
    }

    // 添加封面URL的getter和setter
    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Map<String, Object> toMap(int index) {
        Map<String, Object> map = new HashMap<>();
        map.put("playlist", playlist);
        map.put("index", index);
        map.put("name", getName());
        map.put("TimeDuration", formatTime());
        map.put("filePath", filePath);
        map.put("playCount", playCount);
        map.put("onlineSongId", onlineSongId);
        map.put("coverUrl", coverUrl); // 添加封面URL到Map
        map.put("isSelected", false);
        return map;
    }

    private String formatTime() {
        int minutes = timeDuration / 60;
        int seconds = timeDuration % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public int getRawDuration() {
        return timeDuration;
    }

    public static int parseTime(String formattedTime) {
        String[] parts = formattedTime.split(":");
        int minutes = Integer.parseInt(parts[0]);
        int seconds = Integer.parseInt(parts[1]);
        return minutes * 60 + seconds;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Song) {
            Song other = (Song) obj;
            return this.name.equals(other.name) &&
                    this.timeDuration == other.timeDuration &&
                    this.filePath.equals(other.filePath);
        }
        return false;
    }

    public static Song fromMap(Map<String, Object> map) {
        String playlist = (String) map.get("playlist");
        String name = (String) map.get("name");
        String formattedTime = (String) map.get("TimeDuration");
        String filePath = (String) map.get("filePath");
        int timeDuration = parseTime(formattedTime);
        int playCount = (int) map.getOrDefault("playCount", 0);
        String onlineId = (String) map.get("onlineSongId");
        String coverUrl = (String) map.get("coverUrl"); // 获取封面URL

        Song song = new Song(timeDuration, name, filePath, playlist);
        song.setPlayCount(playCount);
        song.setOnlineSongId(onlineId);
        song.setCoverUrl(coverUrl); // 设置封面URL
        return song;
    }
}
