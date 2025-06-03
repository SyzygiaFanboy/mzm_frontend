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
                if (parts.length >= 4) {
                    String playlist = parts[0].trim(); // 新增歌单字段
                    int duration = Integer.parseInt(parts[1].trim());
                    String name = parts[2].trim();
                    String uriString = parts[3].trim();
                    // 仅加载目标歌单的歌曲
                    if (playlist.equals(targetPlaylist)) {
                        Song song = new Song(duration, name, uriString, playlist);
                        list.add(song.toMap(index++));
                    }
                }
            }
        } catch (IOException | NumberFormatException e) { /* 错误处理 */ }
        return list;
    }

    public static void appendMusic(Context context, Song song) {
        File file = getMusicFile(context);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            String line = song.getPlaylist() + "," + song.getRawDuration() + "," + song.getName() + "," + song.getFilePath();
            bw.write(line);
            bw.newLine();
            bw.flush(); // 强制把缓冲区的内容写入
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

        String latestCoverPath = null;
        long latestTime = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("MusicLoader", "Line: " + line);
                String[] parts = line.split(",");
                if (parts.length >= 4 && parts[0].trim().equals(playlistName)) {
                    String filePath = parts[3].trim();
                    Uri uri = Uri.parse(filePath);
                    try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                        if (input != null) {
                            long lastModified = System.currentTimeMillis(); // 无法直接获取，只能用当前时间
                            if (lastModified > latestTime) {
                                latestTime = lastModified;
                                latestCoverPath = filePath;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "无法读取 URI: " + filePath + " 错误：" + e.getMessage());
                    }

                }
            }
        } catch (IOException e) {
            Log.e(TAG, "获取最新封面失败: " + e.getMessage());
        }
        return latestCoverPath;
    }
}