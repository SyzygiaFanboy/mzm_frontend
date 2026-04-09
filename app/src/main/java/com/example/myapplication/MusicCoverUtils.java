package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import com.example.myapplication.utils.ImageCacheManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MusicCoverUtils {
    private static final String TAG = "MusicCoverUtils";

    public interface CoverLoadCallback {
        void onCoverLoaded(Bitmap bitmap);
    }

    /**
     * 从音频文件获取嵌入的封面
     */
    public static Bitmap getCoverFromFile(String filePath, Context context) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return null;
        }

        // 如果是图片文件，直接加载
        if (filePath.endsWith(".jpg") || filePath.endsWith(".jpeg") || filePath.endsWith(".png")) {
            try {
                File imageFile = new File(filePath);
                if (imageFile.exists()) {
                    return BitmapFactory.decodeFile(filePath);
                }
            } catch (Exception e) {
                Log.e(TAG, "加载图片文件失败: " + e.getMessage());
            }
        }

        // 原有的音频文件封面提取逻辑
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            if (filePath.startsWith("content://")) {
                retriever.setDataSource(context, Uri.parse(filePath));
            } else {
                retriever.setDataSource(filePath);
            }
            byte[] coverBytes = retriever.getEmbeddedPicture();
            if (coverBytes != null) {
                return BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.length);
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "文件路径无效或格式不支持: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "未知错误: " + e.getMessage());
        } finally {
            try {
                if (retriever != null) {
                    retriever.release();
                }
            } catch (IOException e) {
                Log.e(TAG, "释放资源失败: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * 从URL加载封面图片（带缓存）
     */
    public static void loadCoverFromUrl(String coverUrl, Context context, ImageView imageView) {
        loadCoverFromUrl(coverUrl, context, imageView, null);
    }

    public static void loadCoverFromUrl(String coverUrl, Context context, ImageView imageView, CoverLoadCallback callback) {
        if (coverUrl == null || coverUrl.isEmpty()) {
            imageView.setImageResource(R.drawable.default_cover);
            return;
        }

        ImageCacheManager cacheManager = ImageCacheManager.getInstance(context);

        // 先检查缓存
        Bitmap cachedBitmap = cacheManager.getBitmap(coverUrl);
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap);
            if (callback != null) {
                callback.onCoverLoaded(cachedBitmap);
            }
            return;
        }

        // 缓存中没有，从网络下载
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(coverUrl)
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .addHeader("Referer", "https://www.bilibili.com/")
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    InputStream inputStream = response.body().byteStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    if (bitmap != null) {
                        // 保存到缓存
                        cacheManager.putBitmap(coverUrl, bitmap);

                        // 更新UI
                        ((Activity) context).runOnUiThread(() -> {
                            imageView.setImageBitmap(bitmap);
                        });

                        if (callback != null) {
                            callback.onCoverLoaded(bitmap);
                        }
                    } else {
                        ((Activity) context).runOnUiThread(() -> {
                            imageView.setImageResource(R.drawable.default_cover);
                        });
                    }
                    response.close();
                } else {
                    ((Activity) context).runOnUiThread(() -> {
                        imageView.setImageResource(R.drawable.default_cover);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "从URL加载封面失败: " + e.getMessage());
                ((Activity) context).runOnUiThread(() -> {
                    imageView.setImageResource(R.drawable.default_cover);
                });
            }
        }).start();
    }

    /**
     * 智能加载封面：优先从文件嵌入的封面加载，如果没有则从URL加载
     */
    public static void loadCoverSmart(String musicFilePath, String coverUrl, Context context, ImageView imageView) {
        loadCoverSmart(musicFilePath, coverUrl, context, imageView, null);
    }

    public static void loadCoverSmart(String musicFilePath, String coverUrl, Context context, ImageView imageView, CoverLoadCallback callback) {
        new Thread(() -> {
            // 首先尝试从音频文件中获取嵌入的封面
            Bitmap coverBitmap = getCoverFromFile(musicFilePath, context);

            if (coverBitmap != null) {
                // 如果音频文件中有封面，直接使用
                ((Activity) context).runOnUiThread(() -> {
                    imageView.setImageBitmap(coverBitmap);
                });
                if (callback != null) {
                    callback.onCoverLoaded(coverBitmap);
                }
            } else {
                if (musicFilePath != null
                        && (musicFilePath.startsWith("http://") || musicFilePath.startsWith("https://"))
                        && (coverUrl == null || coverUrl.isEmpty() || "null".equalsIgnoreCase(coverUrl))) {
                    Bitmap netCover = getCoverFromNetworkAudio(musicFilePath, context);
                    if (netCover != null) {
                        ((Activity) context).runOnUiThread(() -> imageView.setImageBitmap(netCover));
                        if (callback != null) {
                            callback.onCoverLoaded(netCover);
                        }
                        return;
                    }
                }
                // 如果音频文件中没有封面，从URL加载
                ((Activity) context).runOnUiThread(() -> {
                    loadCoverFromUrl(coverUrl, context, imageView, callback);
                });
            }
        }).start();
    }

    private static Bitmap getCoverFromNetworkAudio(String url, Context context) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        ImageCacheManager cacheManager = ImageCacheManager.getInstance(context);
        Bitmap cachedBitmap = cacheManager.getBitmap(url);
        if (cachedBitmap != null) {
            return cachedBitmap;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(url, new HashMap<>());
            byte[] coverBytes = retriever.getEmbeddedPicture();
            if (coverBytes != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.length);
                if (bitmap != null) {
                    cacheManager.putBitmap(url, bitmap);
                }
                return bitmap;
            }
        } catch (Exception e) {
            Log.e(TAG, "在线音频提取封面失败: " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static String downloadAndCacheCover(String coverUrl, Context context) {
        if (coverUrl == null || coverUrl.isEmpty()) return null;
        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(coverUrl)
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .build();
            Response response = client.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                InputStream inputStream = response.body().byteStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap != null) {
                    // 生成本地文件名
                    String fileName = "cover_" + coverUrl.hashCode() + ".jpg";
                    File coverFile = new File(context.getFilesDir(), fileName);
                    FileOutputStream fos = new FileOutputStream(coverFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.close();
                    response.close();
                    return coverFile.getAbsolutePath();
                }
                response.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "下载并缓存封面失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 预加载封面到缓存
     */
    public static void preloadCover(String coverUrl, Context context) {
        if (coverUrl == null || coverUrl.isEmpty()) {
            return;
        }

        ImageCacheManager cacheManager = ImageCacheManager.getInstance(context);

        // 如果已经缓存，直接返回
        if (cacheManager.getBitmap(coverUrl) != null) {
            return;
        }

        // 后台下载并缓存
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(coverUrl)
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .addHeader("Referer", "https://www.bilibili.com/")
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    InputStream inputStream = response.body().byteStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    if (bitmap != null) {
                        cacheManager.putBitmap(coverUrl, bitmap);
                        Log.d(TAG, "封面预加载完成: " + coverUrl);
                    }
                    response.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "封面预加载失败: " + e.getMessage());
            }
        }).start();
    }
}
