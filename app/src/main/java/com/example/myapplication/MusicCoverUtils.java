package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;
import java.io.IOException;

public class MusicCoverUtils {

    public static Bitmap getCoverFromFile(String filePath, Context context) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (filePath.startsWith("content://")) {
                // 处理 Uri
                retriever.setDataSource(context, Uri.parse(filePath));
            } else {
                retriever.setDataSource(filePath);
            }
            byte[] coverBytes = retriever.getEmbeddedPicture();
            if (coverBytes != null) {
                return BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.length);
            }
        } catch (IllegalArgumentException e) {
            Log.e("CoverUtils", "文件路径无效或格式不支持: " + e.getMessage());
        } catch (Exception e) {
            Log.e("CoverUtils", "未知错误: " + e.getMessage());
        } finally {
            try {
                if (retriever != null) {
                    retriever.release(); // 确保释放资源
                }
            } catch (IOException e) {
                Log.e("CoverUtils", "释放资源失败: " + e.getMessage());
            }
        }
        return null;
    }
}