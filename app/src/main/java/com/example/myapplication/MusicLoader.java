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

    //（必须是静态方法）
//    public static List<Map<String, Object>> loadSongs(Context context, String targetPlaylist) {
//        List<Map<String, Object>> list = new ArrayList<>();
//        try (BufferedReader reader = new BufferedReader(new FileReader(getMusicFile(context)))) {
//            String line;
//            int index = 1;
//            while ((line = reader.readLine()) != null) {
//                try {
//                    // 在 extractFirstElement 里分割元素
//                    String playlist = extractFirstElement(line);
//                    if (!playlist.equals(targetPlaylist)) {
//                        continue;  // 如果不是目标歌单，跳过
//                    }
//
//                    // 移除已提取的歌单名和第一个逗号
//                    line = line.substring(playlist.length() + 1);
//
//                    // 提取持续时间
//                    String durationStr = extractFirstElement(line);
//                    int duration = Integer.parseInt(durationStr);
//
//                    // 移除已提取的持续时间和逗号
//                    line = line.substring(durationStr.length() + 1);
//
//                    // 提取歌曲名称 - 查找最后一个content://开头的URI
//                    int uriIndex = line.lastIndexOf("content://");
//                    if (uriIndex == -1) {
//                        // 尝试查找file://
//                        uriIndex = line.lastIndexOf("file://");
//                    }
//
//                    // 没有找到有效URI
//                    if (uriIndex == -1) {
//                        // 尝试使用最后一个逗号进行分割
//                        int lastComma = line.lastIndexOf(",");
//                        if (lastComma == -1) {
//                            // 没有找到合法格式，跳过此行
//                            Log.e(TAG, "无法解析的歌曲格式: " + line);
//                            continue;
//                        }
//
//                        String name = line.substring(0, lastComma).trim();
//                        String uriString = line.substring(lastComma + 1).trim();
//                        String coverUrl = "";  // 默认没有封面
//
//                        Song song = new Song(duration, name, uriString, playlist);
//                        song.setCoverUrl(coverUrl);
//                        list.add(song.toMap(index++));
//                    } else {
//                        // 查找URI前面的最后一个逗号
//                        int nameEndIndex = line.substring(0, uriIndex).lastIndexOf(",");
//                        if (nameEndIndex == -1) {
//                            // 如果没有找到逗号，则整个URI前面的内容是歌名
//                            nameEndIndex = uriIndex - 1;
//                        }
//
//                        String name = line.substring(0, nameEndIndex + 1).trim();
//                        // 移除歌名末尾可能的逗号
//                        if (name.endsWith(",")) {
//                            name = name.substring(0, name.length() - 1).trim();
//                        }
//
//                        String remainingPart = line.substring(nameEndIndex + 1).trim();
//                        // 提取URI和可能的coverUrl
//                        int coverSeparator = remainingPart.indexOf(",", uriIndex);
//                        String uriString;
//                        String coverUrl = "";
//
//                        if (coverSeparator != -1) {
//                            uriString = remainingPart.substring(0, coverSeparator).trim();
//                            coverUrl = remainingPart.substring(coverSeparator + 1).trim();
//                        } else {
//                            uriString = remainingPart.trim();
//                        }
//
//                        Song song = new Song(duration, name, uriString, playlist);
//                        song.setCoverUrl(coverUrl);
//                        list.add(song.toMap(index++));
//                    }
//                } catch (Exception e) {
//                    Log.e(TAG, "解析歌曲行失败: " + line, e);
//                }
//            }
//        } catch (IOException e) {
//            Log.e(TAG, "加载歌曲列表失败: " + e.getMessage());
//        }
//        return list;
//    }
//
//    // 辅助方法：提取以逗号分隔的第一个元素
//    private static String extractFirstElement(String input) {
//        int commaIndex = input.indexOf(',');
//        if (commaIndex != -1) {
//            return input.substring(0, commaIndex).trim();
//        }
//        return input.trim();
//    }

//    public static void appendMusic(Context context, Song song) {
//        File file = getMusicFile(context);
//        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
//            String coverUrl = song.getCoverUrl() != null ? song.getCoverUrl() : ""; // 获取 coverUrl，如果为 null 则为空字符串
//            // 将 coverUrl 添加到要写入的行中
//            String line = song.getPlaylist() + "," + song.getRawDuration() + "," + song.getName() + "," + song.getFilePath() + "," + coverUrl;
//            bw.write(line);
//            bw.newLine();
//            bw.flush();
//        } catch (IOException e) {
//            Log.e(TAG, "写入失败: " + e.getMessage());
//        }
//    }

    public static List<Map<String, Object>> loadSongs(Context context, String targetPlaylist) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(getMusicFile(context)))) {
            String line;
            int index = 1;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject jsonObject = new JSONObject(line);
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
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "加载歌曲列表失败: " + e.getMessage());
        }
        return list;
    }

    public static void appendMusic(Context context, Song song) {
        File file = getMusicFile(context);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("playlist", song.getPlaylist());
            jsonObject.put("duration", song.getRawDuration());
            jsonObject.put("name", song.getName());
            jsonObject.put("filePath", song.getFilePath());
            jsonObject.put("coverUrl", song.getCoverUrl() != null ? song.getCoverUrl() : "");

            bw.write(jsonObject.toString());
            bw.newLine();
            bw.flush();
        } catch (Exception e) {
            Log.e(TAG, "写入失败: " + e.getMessage());
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

    public static String getLatestCoverForPlaylist(Context context, String playlistName) {
        File file = getMusicFile(context);
        if (!file.exists()) return null;

        String lastSongInfo = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JSONObject jsonObject = new JSONObject(line);
                    String playlist = jsonObject.getString("playlist");
                    if (playlist.equals(playlistName)) {
                        lastSongInfo = line; // 保存最后一首歌的信息
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析JSON失败: " + line, e);
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

        try {
            JSONObject jsonObject = new JSONObject(lastSongInfo);
            String songName = jsonObject.getString("name");
            String filePath = jsonObject.getString("filePath");
            String coverUrl = jsonObject.optString("coverUrl", "");

            // 如果最后一首歌是Bilibili音乐（有coverUrl）
            if (!coverUrl.isEmpty() && !coverUrl.equals("null")) {
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
        } catch (Exception e) {
            Log.e(TAG, "解析最后一首歌JSON失败", e);
            return null;
        }
    }
}