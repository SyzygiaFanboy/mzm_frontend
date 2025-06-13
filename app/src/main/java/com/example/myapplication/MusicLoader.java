package com.example.myapplication;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.example.myapplication.model.Song;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//读
public class MusicLoader {
    private static final String TAG = "MusicLoader";
    private static final String FILE_NAME = "musicList.txt";

    public static File getMusicFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static void clearAllMusic(Context context) {
        File musicFile = getMusicFile(context);
        try {
            // 清空歌曲列表文件
            FileWriter fw = new FileWriter(musicFile);
            fw.write("");
            fw.close();
        } catch (IOException e) {
            Log.e("MusicLoader", "清空歌曲列表失败", e);
        }
    }

    //（必须是静态方法）
    public static List<Map<String, Object>> loadSongs(Context context, String targetPlaylist) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(getMusicFile(context)))) {
            String line;
            int index = 1;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 4) { // 至少需要4个部分：playlist, duration, name, filePath
                    String playlist = parts[0].trim();
                    int duration = Integer.parseInt(parts[1].trim());
                    String name = parts[2].trim();
                    String uriString = parts[3].trim();
                    // 检查是否有第五个部分（coverUrl），如果没有则默认为空字符串
                    String coverUrl = parts.length >= 5 ? parts[4].trim() : "";

                    if (playlist.equals(targetPlaylist)) {
                        Song song = new Song(duration, name, uriString, playlist);
                        song.setCoverUrl(coverUrl); // 设置读取到的 coverUrl
                        list.add(song.toMap(index++));
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            Log.e(TAG, "加载歌曲列表失败: " + e.getMessage()); // 添加日志记录
        }
        return list;
    }

    public static void appendMusic(Context context, Song song) {
        File file = getMusicFile(context);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            String coverUrl = song.getCoverUrl() != null ? song.getCoverUrl() : ""; // 获取 coverUrl，如果为 null 则为空字符串
            // 将 coverUrl 添加到要写入的行中
            String line = song.getPlaylist() + "," + song.getRawDuration() + "," + song.getName() + "," + song.getFilePath() + "," + coverUrl;
            bw.write(line);
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            Log.e(TAG, "写入失败: " + e.getMessage());
        }
    }

    public static void removePlaylistEntries(Context ctx, String playlist) throws IOException {
        File file = new File(ctx.getFilesDir(), "musicList.txt");
        List<String> lines = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        }
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith(playlist + ",")) {
                kept.add(line);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Files.write(file.toPath(), kept, StandardCharsets.UTF_8);
        }
    }

    public static int getSongCountFromPlaylist(Context context, String playlistName) throws IOException {
        File file = getMusicFile(context);
        if (!file.exists()) return 0;

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 格式: 歌单名,时长,歌曲名,文件路径
                if (line.startsWith(playlistName + ",")) {
                    count++;
                }
            }
        }
        return count;
    }

    public static String getLatestCoverForPlaylist(Context context, String playlistName) {
        File file = getMusicFile(context);
        if (!file.exists()) return null;

        String lastSongInfo = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length >= 2 && parts[0].trim().equals(playlistName)) {
                    lastSongInfo = line; // 保存最后一首歌的信息
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "读取歌单文件失败: " + e.getMessage());
            return null;
        }

        if (lastSongInfo == null) {
            Log.d(TAG, "歌单 " + playlistName + " 中没有歌曲");
            return null;
        }

        // 解析最后一首歌的信息
        String[] parts = lastSongInfo.split(",", 5);
        if (parts.length < 5) {
            Log.d(TAG, "歌曲信息格式不正确: " + lastSongInfo);
            return null;
        }

        String songName = parts[2].trim();
        String filePath = parts[3].trim();
        String coverUrl = parts[4].trim();

        // 如果最后一首歌是Bilibili音乐（有coverUrl）
        if (coverUrl != null && !coverUrl.isEmpty() && !coverUrl.equals("null")) {
            // 生成对应的缓存文件名
            String cacheFileName = "cover_" + Math.abs((songName + coverUrl).hashCode()) + ".jpg";
            File cacheFile = new File(context.getFilesDir(), cacheFileName);

            if (cacheFile.exists()) {
                Log.d(TAG, "找到歌单 " + playlistName + " 最后一首歌的缓存封面: " + cacheFile.getAbsolutePath());
                return cacheFile.getAbsolutePath();
            } else {
                Log.d(TAG, "歌单 " + playlistName + " 最后一首歌的缓存封面不存在，返回网络URL: " + coverUrl);
                return coverUrl; // 返回网络URL作为备选
            }
        } else {
            // 如果最后一首歌是本地音乐，返回文件路径用于提取嵌入封面
            Log.d(TAG, "歌单 " + playlistName + " 最后一首歌是本地音乐: " + filePath);
            return filePath;
        }
    }
}