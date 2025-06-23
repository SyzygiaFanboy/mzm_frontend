package com.example.myapplication;

import static com.example.myapplication.MainActivity.addSongToPlaylist;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.myapplication.model.SelectedSong;
import com.example.myapplication.model.Song;
import com.example.myapplication.network.UploadTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 歌单分享工具类
 * 提供歌单分享和导入功能
 */
public class SharePlaylistUtils {
    private static final String TAG = "SharePlaylistUtils";
    private static final String SERVER_URL = "http://192.168.0.22:8080";

    /**
     * 显示歌单分享对话框
     */
    public static void showSharePlaylistDialog(MainActivity activity, String currentPlaylist, List<Map<String, Object>> musicList) {
        // 获取当前歌单中的歌曲，分析有多少本地歌曲
        List<Song> playlistSongs = new ArrayList<>();
        List<Song> localSongs = new ArrayList<>();
        List<Song> onlineSongs = new ArrayList<>();

        for (Map<String, Object> item : musicList) {
            Song song = Song.fromMap(item);
            playlistSongs.add(song);

            // 判断是否为本地歌曲
            if (song.getFilePath().startsWith("content://")) {
                localSongs.add(song);
            } else {
                onlineSongs.add(song);
            }
        }

        // 如果歌单为空，显示提示
        if (playlistSongs.isEmpty()) {
            Toast.makeText(activity, "歌单为空，无法分享", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建对话框选项
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("分享歌单: " + currentPlaylist);

        String message;
        if (localSongs.isEmpty()) {
            message = "歌单中有 " + onlineSongs.size() + " 首在线歌曲可以分享。";
        } else {
            message = "歌单中有 " + localSongs.size() + " 首本地歌曲和 " + onlineSongs.size() + " 首在线歌曲。\n" +
                    "本地歌曲需要上传后才能分享。";
        }

        builder.setMessage(message);

        // 如果有本地歌曲，提供两个选项
        if (!localSongs.isEmpty()) {
            builder.setPositiveButton("上传并分享全部", (dialog, which) -> {
                // 上传本地歌曲，完成后生成分享码
                uploadAndSharePlaylist(activity, currentPlaylist, playlistSongs);
            });

            if (!onlineSongs.isEmpty()) {
                // 显示分享在线歌曲
                builder.setNegativeButton("只分享在线歌曲", (dialog, which) -> {
                    sharePlaylist(activity, currentPlaylist, onlineSongs);
                });
            }
        } else {
            // 没有本地歌曲，只有一个选项
            builder.setPositiveButton("分享", (dialog, which) -> {
                sharePlaylist(activity, currentPlaylist, onlineSongs);
            });
        }

        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * 上传并分享歌单
     */
    private static void uploadAndSharePlaylist(MainActivity activity, String currentPlaylist, List<Song> allSongs) {
        // 显示进度对话框
        activity.showProgressDialog();
        TextView dialogMessage = activity.getDialogMessage();
        ProgressBar dialogProgressBar = activity.getDialogProgressBar();

        dialogMessage.setText("准备上传歌曲...");

        // 创建一个待上传的歌曲列表
        List<Song> songsToUpload = new ArrayList<>();
        for (Song song : allSongs) {
            if (song.getFilePath().startsWith("content://")) {
                songsToUpload.add(song);
            }
        }

        if (songsToUpload.isEmpty()) {
            // 没有需要上传的歌曲，直接分享
            sharePlaylist(activity, currentPlaylist, allSongs);
            return;
        }

        // 设置进度条最大值
        dialogProgressBar.setMax(songsToUpload.size());
        AtomicInteger uploadedCount = new AtomicInteger(0);

        // 上传每首歌曲
        for (int i = 0; i < songsToUpload.size(); i++) {
            final int songIndex = i;
            Song currentSong = songsToUpload.get(i);

            // 更新进度对话框
            activity.runOnUiThread(() -> {
                dialogMessage.setText("正在上传歌曲 (" + (songIndex + 1) + "/" + songsToUpload.size() + ")");
            });

            // 使用UploadTask上传歌曲
            uploadSong(activity, currentSong, () -> {
                int completed = uploadedCount.incrementAndGet();
                dialogProgressBar.setProgress(completed);

                // 当所有歌曲上传完成后，生成分享码
                if (completed == songsToUpload.size()) {
                    // 延迟一小段时间确保服务器处理完成
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        sharePlaylist(activity, currentPlaylist, allSongs);
                    }, 500);
                }
            });
        }
    }

    /**
     * 上传单首歌曲
     */
    private static void uploadSong(MainActivity activity, Song song, Runnable onComplete) {
        // 将Song转换为SelectedSong
        SelectedSong selectedSong = new SelectedSong("", "", 0, null, null);
        selectedSong.setSongName(song.getName());
        selectedSong.setArtist("Unknown"); // 默认艺术家
        selectedSong.setDuration(song.getTimeDuration() * 1000); // 将秒转为毫秒

        if (song.getFilePath().startsWith("content://")) {
            // 处理Content URI
            selectedSong.setUri(Uri.parse(song.getFilePath()));
        } else {
            // 处理文件路径
            selectedSong.setFilePath(song.getFilePath());
        }

        // 使用UploadTask上传歌曲
        UploadTask uploadTask = new UploadTask(activity, new UploadTask.UploadCallback() {
            @Override
            public void onSuccess() {
                // 上传成功
                if (onComplete != null) {
                    activity.runOnUiThread(onComplete);
                }
            }

            @Override
            public void onError(String error) {
                // 上传失败，但继续后续处理
                Log.e(TAG, "上传歌曲失败: " + error);
                if (onComplete != null) {
                    activity.runOnUiThread(onComplete);
                }
            }

            @Override
            public void onProgress(int progress) {
                // 更新单首歌曲的上传进度 (可选)
            }
        });

        uploadTask.execute(selectedSong);
    }

    /**
     * 分享歌单，生成分享码
     */
    private static void sharePlaylist(MainActivity activity, String currentPlaylist, List<Song> songs) {
        // 更新进度对话框状态
        activity.runOnUiThread(() -> {
            TextView dialogMessage = activity.getDialogMessage();
            ProgressBar dialogProgressBar = activity.getDialogProgressBar();

            if (dialogMessage != null) {
                dialogMessage.setText("正在生成分享码...");
            }

            if (dialogProgressBar != null) {
                dialogProgressBar.setIndeterminate(true);
            }
        });

        try {
            // 准备要发送的数据
            JSONObject requestData = new JSONObject();
            requestData.put("playlistName", currentPlaylist);

            JSONArray songsArray = new JSONArray();
            for (Song song : songs) {
                JSONObject songObj = new JSONObject();
                songObj.put("name", song.getName());
                songObj.put("duration", song.getTimeDuration());
                songObj.put("coverUrl", song.getCoverUrl() != null ? song.getCoverUrl() : "");

                // 添加歌曲类型信息
                String filePath = song.getFilePath();
                if (filePath.startsWith("http")) {
                    // 在线歌曲
                    songObj.put("type", "online");
                    songObj.put("url", filePath);
                } else if (filePath.contains("bilibili") || filePath.contains("BV") || filePath.contains("av")) {
                    // B站歌曲
                    songObj.put("type", "bili");

                    // 提取BV号
                    String path = filePath;
                    int startIndex = path.indexOf("BV");
                    int endIndex = path.indexOf("_", startIndex); // 从刚刚的位置开始找
                    if (endIndex < 0) {
                        endIndex = path.indexOf(".", startIndex); // 如果没有下划线就是纯BV号，直接找后缀
                    }
                    path = path.substring(startIndex, endIndex > 0 ? endIndex : path.length());
                    songObj.put("url", path);
                } else {
                    // 本地歌曲
                    songObj.put("type", "local");
                }

                songsArray.put(songObj);
            }

            requestData.put("songs", songsArray);

            // 发送请求创建分享码
            OkHttpClient client = new OkHttpClient();
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestData.toString()
            );

            Request request = new Request.Builder()
                    .url(SERVER_URL + "/CreateShareCodeServlet")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    activity.runOnUiThread(() -> {
                        activity.dismissProgressDialog();
                        Toast.makeText(activity, "生成分享码失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    activity.dismissProgressDialog();

                    if (!response.isSuccessful()) {
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, "服务器响应错误: " + response.code(), Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);

                        if (jsonResponse.has("shareCode")) {
                            final String shareCode = jsonResponse.getString("shareCode");
                            activity.runOnUiThread(() -> showShareCodeResultDialog(activity, shareCode));
                        } else if (jsonResponse.has("error")) {
                            final String errorMsg = jsonResponse.getString("error");
                            activity.runOnUiThread(() -> {
                                Toast.makeText(activity, errorMsg, Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (JSONException e) {
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, "解析响应失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });

        } catch (Exception e) {
            activity.dismissProgressDialog();
            Toast.makeText(activity, "生成分享请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示分享码结果对话框
     */
    private static void showShareCodeResultDialog(Context context, String shareCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("歌单分享成功");

        // 创建布局
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        // 添加说明文本
        TextView message = new TextView(context);
        message.setText("分享码已生成，现在可以分享给朋友啦！");
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        layout.addView(message);

        // 添加分享码显示
        TextView codeView = new TextView(context);
        codeView.setText(shareCode);
        codeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        codeView.setTypeface(null, Typeface.BOLD);
        codeView.setGravity(Gravity.CENTER);
        codeView.setPadding(10, 30, 10, 30);
        layout.addView(codeView);

        builder.setView(layout);

        builder.setPositiveButton("复制分享码", (dialog, which) -> {
            // 复制到剪贴板
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("分享码", shareCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        builder.setNegativeButton("关闭", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * 显示导入分享码对话框
     */
    public static void showImportShareCodeDialog(MainActivity activity, String currentPlaylist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("导入分享歌单");

        // 创建输入框布局
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);
        EditText input = new EditText(activity);
        input.setHint("请输入分享码");
        layout.addView(input);
        builder.setView(layout);

        // 确认按钮
        builder.setPositiveButton("导入", (dialog, which) -> {
            String shareCode = input.getText().toString().trim();
            if (shareCode.isEmpty()) {
                Toast.makeText(activity, "请输入分享码", Toast.LENGTH_SHORT).show();
                return;
            }

            // 显示进度对话框
            activity.showProgressDialog();
            if (activity.getDialogMessage() != null) {
                activity.getDialogMessage().setText("正在导入歌单...");
            }

            // 发送请求获取歌单信息
            fetchSharedPlaylist(activity, shareCode, currentPlaylist);
        });

        // 取消按钮
        builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

        builder.show();
    }

    /**
     * 获取共享歌单数据
     */
    private static void fetchSharedPlaylist(MainActivity activity, String shareCode, String currentPlaylist) {
        new Thread(() -> {
            try {
                // 构建API请求URL
                String apiUrl = SERVER_URL + "/GetSharedPlaylistServlet?shareCode=" + shareCode;
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(apiUrl)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        activity.runOnUiThread(() -> {
                            activity.dismissProgressDialog();
                            Toast.makeText(activity, "获取歌单失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            activity.runOnUiThread(() -> {
                                activity.dismissProgressDialog();
                                Toast.makeText(activity, "服务器响应错误: " + response.code(), Toast.LENGTH_SHORT).show();
                            });
                            return;
                        }

                        String responseBody = response.body().string();
                        try {
                            // 解析服务器返回的JSON
                            JSONObject jsonResponse = new JSONObject(responseBody);

                            if (jsonResponse.has("error")) {
                                // 处理错误
                                String errorMsg = jsonResponse.getString("error");
                                activity.runOnUiThread(() -> {
                                    activity.dismissProgressDialog();
                                    Toast.makeText(activity, errorMsg, Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }

                            // 成功获取歌单数据
                            if (jsonResponse.has("songs")) {
                                JSONArray songsArray = jsonResponse.getJSONArray("songs");

                                // 导入歌曲
                                importSongs(activity, songsArray, currentPlaylist);
                            } else {
                                activity.runOnUiThread(() -> {
                                    activity.dismissProgressDialog();
                                    Toast.makeText(activity, "歌单为空或格式错误", Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (JSONException e) {
                            activity.runOnUiThread(() -> {
                                activity.dismissProgressDialog();
                                Toast.makeText(activity, "解析歌单数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                });

            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    activity.dismissProgressDialog();
                    Toast.makeText(activity, "请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * 导入歌曲
     */
    private static void importSongs(MainActivity activity, JSONArray songsArray, String playlistName) {
        activity.runOnUiThread(() -> {
            ProgressBar dialogProgressBar = activity.getDialogProgressBar();
            TextView dialogMessage = activity.getDialogMessage();

            if (dialogMessage != null) {
                dialogMessage.setText("正在导入歌曲...");
            }

            if (dialogProgressBar != null) {
                dialogProgressBar.setMax(songsArray.length());
                dialogProgressBar.setProgress(0);
            }
        });

        // 用于保存导入结果的列表
        List<Song> importedSongs = new ArrayList<>();

        // 常规歌曲计数器
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // B站歌曲的特殊计数
        AtomicInteger biliSongCount = new AtomicInteger(0);
        AtomicInteger biliProcessedCount = new AtomicInteger(0);

        // 先处理所有非B站歌曲
        for (int i = 0; i < songsArray.length(); i++) {
            try {
                JSONObject songJson = songsArray.getJSONObject(i);
                String songName = songJson.getString("name");
                String songType = songJson.getString("type"); // online, bili, local
                int duration = songJson.getInt("duration");
                String coverUrl = songJson.optString("coverUrl", "");

                if ("bili".equals(songType)) {
                    // 只计算B站歌曲数量，稍后处理
                    biliSongCount.incrementAndGet();
                    continue;
                }

                switch (songType) {
                    case "online":
                        // 在线歌曲直接添加
                        String fileUrl = songJson.getString("url");

                        Song newSong = new Song(duration, songName, fileUrl, playlistName);
                        if (!coverUrl.isEmpty()) {
                            newSong.setCoverUrl(coverUrl);
                        }

                        importedSongs.add(newSong);
                        successCount.incrementAndGet();
                        break;

                    case "local":
                        // 需要从服务器下载本地歌曲
                        String songId = songJson.getString("id");
                        downloadSong(activity, songId, songName, duration, coverUrl, playlistName, importedSongs, successCount);
                        break;
                }

                // 更新进度
                final int processed = processedCount.incrementAndGet();
                activity.runOnUiThread(() -> {
                    ProgressBar progressBar = activity.getDialogProgressBar();
                    TextView message = activity.getDialogMessage();

                    if (progressBar != null) {
                        progressBar.setProgress(processed);
                    }

                    if (message != null) {
                        message.setText("正在导入歌曲... (" + processed + "/" + songsArray.length() + ")");
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "导入歌曲失败: " + e.getMessage());
                processedCount.incrementAndGet();
            }
        }

        // 如果没有B站歌曲，直接完成
        if (biliSongCount.get() == 0) {
            finalizeImport(activity, importedSongs, successCount.get());
            return;
        }

        // 再处理B站歌曲
        for (int i = 0; i < songsArray.length(); i++) {
            try {
                JSONObject songJson = songsArray.getJSONObject(i);
                String songType = songJson.getString("type");

                if (!"bili".equals(songType)) {
                    continue; // 跳过非B站歌曲
                }

                String songName = songJson.getString("name");
                int duration = songJson.getInt("duration");
                String bvid = songJson.getString("url");

                // 处理B站歌曲
                activity.getBiliMusic(bvid, activity, result -> {
                    try {
                        if (result != null) {
                            String title = (String) result.get("title");
                            File f = (File) result.get("file");
                            String cover = (String) result.get("coverUrl");
                            String path = f.getAbsolutePath();

                            // 创建Song对象
                            Song biliSong = new Song(duration, title, path, playlistName);
                            biliSong.setCoverUrl(cover);

                            synchronized (importedSongs) {
                                importedSongs.add(biliSong);
                                successCount.incrementAndGet();
                            }

                            activity.runOnUiThread(() ->
                                    Toast.makeText(activity, "已添加B站音乐: " + title, Toast.LENGTH_SHORT).show()
                            );
                        } else {
                            activity.runOnUiThread(() ->
                                    Toast.makeText(activity, "获取B站音乐失败", Toast.LENGTH_SHORT).show()
                            );
                        }
                    } finally {
                        // 更新B站歌曲处理进度
                        int biliProcessed = biliProcessedCount.incrementAndGet();
                        final int totalProcessed = processedCount.get() + biliProcessed;

                        activity.runOnUiThread(() -> {
                            ProgressBar progressBar = activity.getDialogProgressBar();
                            TextView message = activity.getDialogMessage();

                            if (progressBar != null) {
                                progressBar.setProgress(totalProcessed);
                            }

                            if (message != null) {
                                message.setText("正在导入歌曲... (" + totalProcessed + "/" + songsArray.length() + ")");
                            }
                        });

                        // 如果所有B站歌曲处理完毕，完成导入
                        if (biliProcessed == biliSongCount.get()) {
                            finalizeImport(activity, importedSongs, successCount.get());
                        }
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "处理B站歌曲失败: " + e.getMessage());

                // 更新处理进度并检查是否完成
                int biliProcessed = biliProcessedCount.incrementAndGet();
                if (biliProcessed == biliSongCount.get()) {
                    finalizeImport(activity, importedSongs, successCount.get());
                }
            }
        }
    }

    /**
     * 完成导入过程
     */
    private static void finalizeImport(MainActivity activity, List<Song> importedSongs, int successCount) {
        // 添加所有导入的歌曲到播放列表
        for (Song song : importedSongs) {
            MusicLoader.appendMusic(activity, song);
            addSongToPlaylist(song);
        }

        // 更新UI
        activity.runOnUiThread(() -> {
            activity.dismissProgressDialog();

            if (successCount > 0) {
                Toast.makeText(activity,
                        "成功导入 " + successCount + " 首歌曲",
                        Toast.LENGTH_SHORT).show();

                // 刷新列表
                activity.refreshPlaylist();
                activity.updateNavButtons();
            } else {
                Toast.makeText(activity, "未导入任何歌曲", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 下载歌曲
     */
    private static void downloadSong(MainActivity activity, String songId, String songName, int duration, String coverUrl,
                                     String playlistName, List<Song> importedSongs, AtomicInteger successCount) {
        try {
            // 下载歌曲文件
            String downloadUrl = SERVER_URL + "/DownloadSongServlet?id=" + songId;
            OkHttpClient downloadClient = new OkHttpClient();
            Request downloadRequest = new Request.Builder()
                    .url(downloadUrl)
                    .build();

            downloadClient.newCall(downloadRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "下载歌曲失败: " + e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        Log.e(TAG, "下载歌曲响应错误: " + response.code());
                        return;
                    }

                    // 保存文件到本地
                    File musicDir = new File(activity.getExternalFilesDir("music"), "shared");
                    if (!musicDir.exists()) {
                        musicDir.mkdirs();
                    }

                    // 使用songId作为文件名避免重复
                    File outputFile = new File(musicDir, "shared_" + songId + ".mp3");

                    try (java.io.InputStream inputStream = response.body().byteStream();
                         java.io.FileOutputStream outputStream = new java.io.FileOutputStream(outputFile)) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.flush();

                        // 创建Song对象并添加到列表
                        Song newSong = new Song(duration, songName, outputFile.getAbsolutePath(), playlistName);
                        if (coverUrl != null && !coverUrl.isEmpty()) {
                            newSong.setCoverUrl(coverUrl);
                        }

                        synchronized (importedSongs) {
                            importedSongs.add(newSong);
                            successCount.incrementAndGet();
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "下载歌曲错误: " + e.getMessage());
        }
    }

}
