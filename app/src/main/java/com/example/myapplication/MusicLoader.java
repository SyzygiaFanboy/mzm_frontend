package com.example.myapplication;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.myapplication.model.Song;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

    public static List<Map<String, Object>> loadSongs(Context context, String targetPlaylist) {
        File musicFile = getMusicFile(context);
        List<Map<String, Object>> list = new ArrayList<>();
        List<String> validLines = new ArrayList<>();
        boolean needCleanup = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(musicFile))) {
            String line;
            int index = 1;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject jsonObject = new JSONObject(line);
                    validLines.add(line);
                    String playlist = jsonObject.getString("playlist");

                    // 检查是否是目标歌单
                    if (!playlist.equals(targetPlaylist)) {
                        continue;
                    }

                    int duration = jsonObject.getInt("duration");
                    String name = jsonObject.getString("name");
                    String uriString = jsonObject.getString("filePath");
                    String coverUrl = jsonObject.optString("coverUrl", ""); // 默认为空

                    Song song = new Song(duration, name, uriString, playlist);
                    song.setCoverUrl(coverUrl);
                    list.add(song.toMap(index++));
                } catch (Exception e) {
                    Log.e(TAG, "解析歌曲行失败: " + line, e);
                    needCleanup = true;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "加载歌曲列表失败: " + e.getMessage());
        }

        // 如果发现了无效行，删除它们
        if (needCleanup) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(musicFile, false))) {
                for (String validLine : validLines) {
                    writer.write(validLine);
                    writer.newLine();
                }
            } catch (IOException e) {
                Log.e(TAG, "清理无效JSON行失败: " + e.getMessage());
            }
        }

        return list;
    }

    /**
     * 在文件开头插入新歌曲
     *
     * @param context 用于获取路径的上下文
     * @param song    要插入的歌曲
     */
    public static void appendMusic(Context context, Song song) {
        File file = getMusicFile(context);
        File tempFile = new File(context.getFilesDir(), FILE_NAME + ".temp");

        try {
            // 创建临时文件并写入新歌曲
            try (BufferedWriter tempWriter = new BufferedWriter(new FileWriter(tempFile))) {
                // 首先写入新歌曲
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("playlist", song.getPlaylist());
                jsonObject.put("duration", song.getRawDuration());
                jsonObject.put("name", song.getName());
                jsonObject.put("filePath", song.getFilePath());
                jsonObject.put("coverUrl", song.getCoverUrl() != null ? song.getCoverUrl() : "");

                tempWriter.write(jsonObject.toString());
                tempWriter.newLine();

                // 如果原文件存在，复制原有内容
                if (file.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            tempWriter.write(line);
                            tempWriter.newLine();
                        }
                    }
                }
            }

            // 删除原文件
            if (file.exists()) {
                file.delete();
            }

            // 重命名临时文件
            tempFile.renameTo(file);

        } catch (Exception e) {
            Log.e(TAG, "在文件开头插入歌曲失败: " + e.getMessage());
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public static void removePlaylistEntries(Context ctx, String playlist) throws IOException {
        File file = new File(ctx.getFilesDir(), "musicList.txt");
        List<String> lines;

        // 读取文件内容
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } else {
            lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "读取文件失败: " + e.getMessage());
            }
        }

        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            try {
                JSONObject jsonObject = new JSONObject(line);
                String songPlaylist = jsonObject.getString("playlist");
                if (!songPlaylist.equals(playlist)) {
                    kept.add(line);
                }
            } catch (Exception e) {
                Log.e(TAG, "解析JSON失败: " + line, e);
                // 保留无法解析的行
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
                try {
                    JSONObject jsonObject = new JSONObject(line);
                    String playlist = jsonObject.getString("playlist");
                    if (playlist.equals(playlistName)) {
                        count++;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析JSON失败: " + line, e);
                }
            }
        }
        return count;
    }

    public static String getFirstCoverForPlaylist(Context context, String playlistName) {
        File file = getMusicFile(context);
        if (!file.exists()) return null;

        String songInfo = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject jsonObject = new JSONObject(line);
                    String playlist = jsonObject.getString("playlist");
                    if (playlist.equals(playlistName)) {
                        songInfo = line; // 保存第一首歌的信息
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析JSON失败: " + line, e);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "读取歌单文件失败: " + e.getMessage());
            return null;
        }

        if (songInfo == null) {
            Log.d(TAG, "歌单 " + playlistName + " 中没有歌曲");
            return null;
        }

        try {
            JSONObject jsonObject = new JSONObject(songInfo);
            String songName = jsonObject.getString("name");
            String filePath = jsonObject.getString("filePath");
            String coverUrl = jsonObject.optString("coverUrl", "");

            // 如果第一首歌是Bilibili音乐（有coverUrl）
            if (!coverUrl.isEmpty() && !coverUrl.equals("null")) {
                // 生成对应的缓存文件名
                String cacheFileName = "cover_" + Math.abs((songName + coverUrl).hashCode()) + ".jpg";
                File cacheFile = new File(context.getFilesDir(), cacheFileName);

                if (cacheFile.exists()) {
                    Log.d(TAG, "找到歌单 " + playlistName + " 第一首歌的缓存封面: " + cacheFile.getAbsolutePath());
                    return cacheFile.getAbsolutePath();
                } else {
                    Log.d(TAG, "歌单 " + playlistName + " 第一首歌的缓存封面不存在，返回网络URL: " + coverUrl);
                    return coverUrl; // 返回网络URL作为备选
                }
            } else {
                // 如果第一首歌是本地音乐，返回文件路径用于提取嵌入封面
                Log.d(TAG, "歌单 " + playlistName + " 第一首歌是本地音乐: " + filePath);
                return filePath;
            }
        } catch (Exception e) {
            Log.e(TAG, "解析第一首歌JSON失败", e);
            return null;
        }
    }
}