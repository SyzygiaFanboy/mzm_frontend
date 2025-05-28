package com.example.myapplication;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

import com.example.myapplication.Song;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.*;
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


    private static void saveRemoteUrl(Context context, Song song) {
        File file = getMusicFile(context);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            String line = song.getRawDuration() + "," + song.getName() + "," + song.getFilePath();
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            Log.e(TAG, "保存在线音乐失败: " + e.getMessage());
        }
    }

    // 删除指定位置的音乐（对应文件中的行号）
    public static void deleteMusic(Context context, int position) {
        File file = getMusicFile(context);
        List<String> lines = new ArrayList<>();

        try {
            // 读取所有行到内存
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
            // 检查position有效性
            if (position < 0 || position >= lines.size()) {
                Log.e(TAG, "无效的删除位置: " + position);
                return;
            }
            // 移除指定行
            lines.remove(position);
            // 重新写入文件（覆盖）
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            for (String l : lines) {
                writer.write(l);
                writer.newLine();
            }
            writer.close();

            Log.d(TAG, "删除成功，位置: " + position);

        } catch (IOException e) {
            Log.e(TAG, "删除失败: " + e.getMessage());
        }
    }
}