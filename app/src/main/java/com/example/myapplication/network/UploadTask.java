package com.example.myapplication.network;

import static com.example.myapplication.HttpUtil.BASE_URL;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.example.myapplication.model.SelectedSong;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UploadTask extends AsyncTask<SelectedSong, Integer, String> {
    private static final String TAG = "UploadTask";
    private static final String UPLOAD_URL = BASE_URL + "/SongServlet";

    private Context context;
    private UploadCallback callback;

    public interface UploadCallback {
        void onSuccess();

        void onError(String error);

        void onProgress(int progress);
    }

    public UploadTask(Context context, UploadCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    @Override
    protected String doInBackground(SelectedSong... songs) {
        if (songs.length == 0) {
            return "没有选择歌曲";
        }

        SelectedSong song = songs[0];

        try {
            return uploadFile(song);
        } catch (Exception e) {
            Log.e(TAG, "上传失败", e);
            return "上传失败: " + e.getMessage();
        }
    }

    // 添加公共方法供BatchUploadTask调用
    public String uploadSingleFile(SelectedSong song) throws Exception {
        try {
            return uploadFile(song);
        } catch (Exception e) {
            Log.e(TAG, "上传失败", e);
            throw e;
        }
    }

    private String uploadFile(SelectedSong song) throws IOException {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        String lineEnd = "\r\n";
        String twoHyphens = "--";

        URL url = new URL(UPLOAD_URL);
        Log.d(TAG, "正在连接URL: " + UPLOAD_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("Accept-Charset", "UTF-8");

        DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());

        // 方法1：使用URL编码（推荐）
        try {
            // 对日文字符进行URL编码
            String encodedSongName = java.net.URLEncoder.encode(song.getSongName(), "UTF-8");
            String encodedArtist = java.net.URLEncoder.encode(song.getArtist(), "UTF-8");

            Log.d(TAG, "原始歌曲名: " + song.getSongName());
            Log.d(TAG, "URL编码后: " + encodedSongName);
            Log.d(TAG, "原始艺术家: " + song.getArtist());
            Log.d(TAG, "URL编码后: " + encodedArtist);

            // 添加歌曲名称参数
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\"Songname\"" + lineEnd);
            outputStream.writeBytes(lineEnd);
            outputStream.writeBytes(encodedSongName);
            outputStream.writeBytes(lineEnd);

            // 添加艺术家参数
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\"musician\"" + lineEnd);
            outputStream.writeBytes(lineEnd);
            outputStream.writeBytes(encodedArtist);
            outputStream.writeBytes(lineEnd);

            // 添加歌曲时长参数
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\"songduration\"" + lineEnd);
            outputStream.writeBytes(lineEnd);
            outputStream.writeBytes(String.valueOf(song.getDuration() / 1000));
            outputStream.writeBytes(lineEnd);

        } catch (Exception e) {
            Log.e(TAG, "编码失败", e);
            throw new IOException("字符编码失败: " + e.getMessage());
        }

        // 文件上传部分保持不变
        InputStream inputStream = null;
        String fileName = "unknown.mp3";
        long fileSize = 0;

        try {
            // 优先使用文件路径
            if (song.getFilePath() != null && !song.getFilePath().isEmpty()) {
                File file = new File(song.getFilePath());
                if (file.exists()) {
                    inputStream = new FileInputStream(file);
                    fileName = file.getName();
                    fileSize = file.length();
                } else {
                    Log.w(TAG, "文件路径无效，尝试使用URI: " + song.getFilePath());
                }
            }

            // 如果文件路径无效，使用URI
            if (inputStream == null && song.getUri() != null) {
                inputStream = context.getContentResolver().openInputStream(song.getUri());
                fileName = getFileNameFromUri(song.getUri());
                fileSize = -1;
            }

            if (inputStream == null) {
                throw new IOException("无法打开文件: filePath=" + song.getFilePath() + ", uri=" + song.getUri());
            }

            // 文件上传
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\"SongFile\"; filename=\"" + fileName + "\"" + lineEnd);
            outputStream.writeBytes("Content-Type: audio/mpeg" + lineEnd);
            outputStream.writeBytes(lineEnd);

            // 读取并写入文件数据
            byte[] buffer = new byte[4096];
            int bytesRead;
            long uploadedBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                uploadedBytes += bytesRead;

                if (fileSize > 0) {
                    int progress = (int) ((uploadedBytes * 100) / fileSize);
                    publishProgress(progress);
                }
            }

        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }

        outputStream.writeBytes(lineEnd);
        outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
        outputStream.flush();
        outputStream.close();

        // 获取响应
        int responseCode = connection.getResponseCode();
        Log.d(TAG, "服务器响应码: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return "404错误：服务器找不到请求的资源 " + UPLOAD_URL;
        }

        if (responseCode == HttpURLConnection.HTTP_OK) {
            return "SUCCESS";
        } else {
            return "服务器响应错误: " + responseCode;
        }
    }

    /**
     * 从URI获取文件名
     */
    private String getFileNameFromUri(Uri uri) {
        String fileName = "unknown.mp3";
        if (uri != null) {
            String uriString = uri.toString();
            if (uriString.contains("/")) {
                fileName = uriString.substring(uriString.lastIndexOf("/") + 1);
            }
            // 确保有扩展名
            if (!fileName.contains(".")) {
                fileName += ".mp3";
            }
        }
        return fileName;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (callback != null && values.length > 0) {
            callback.onProgress(values[0]);
        }
    }

    @Override
    protected void onPostExecute(String result) {
        if (callback != null) {
            if ("SUCCESS".equals(result)) {
                callback.onSuccess();
            } else {
                callback.onError(result);
            }
        }
    }
}