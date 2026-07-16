package com.example.myapplication.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ImageCacheManager {
    private static final String TAG = "ImageCacheManager";
    private static final String CACHE_DIR = "image_cache";
    private static final int MAX_MEMORY_CACHE_SIZE_KB = 20 * 1024;
    private static final long MAX_TOTAL_DISK_CACHE_SIZE_BYTES = 100L * 1024L * 1024L;

    private static ImageCacheManager instance;

    private final Context appContext;
    private final LruCache<String, Bitmap> memoryCache;
    private final File diskCacheDir;

    private ImageCacheManager(Context context) {
        appContext = context.getApplicationContext();
        memoryCache = new LruCache<String, Bitmap>(MAX_MEMORY_CACHE_SIZE_KB) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                if (bitmap == null) {
                    return 0;
                }
                return Math.max(1, bitmap.getByteCount() / 1024);
            }
        };

        File externalDir = appContext.getExternalFilesDir(null);
        if (externalDir == null) {
            externalDir = appContext.getCacheDir();
        }
        diskCacheDir = new File(externalDir, CACHE_DIR);
        if (!diskCacheDir.exists() && !diskCacheDir.mkdirs()) {
            Log.w(TAG, "Failed to create cache dir: " + diskCacheDir.getAbsolutePath());
        }
        trimDiskCacheIfNeeded();
    }

    public static synchronized ImageCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new ImageCacheManager(context);
        }
        return instance;
    }

    public Bitmap getBitmap(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        String key = generateKey(url);
        Bitmap bitmap = memoryCache.get(key);
        if (bitmap != null) {
            Log.d(TAG, "Hit memory cache: " + url);
            return bitmap;
        }

        bitmap = getBitmapFromDisk(key);
        if (bitmap != null) {
            Log.d(TAG, "Hit disk cache: " + url);
            memoryCache.put(key, bitmap);
            return bitmap;
        }

        return null;
    }

    public void putBitmap(String url, Bitmap bitmap) {
        if (url == null || url.isEmpty() || bitmap == null) {
            return;
        }

        String key = generateKey(url);
        memoryCache.put(key, bitmap);
        saveBitmapToDisk(key, bitmap);
        Log.d(TAG, "Cached image: " + url);
    }

    public void remove(String url) {
        if (url == null || url.isEmpty()) {
            return;
        }

        String key = generateKey(url);
        memoryCache.remove(key);

        File cacheFile = new File(diskCacheDir, key + ".jpg");
        if (cacheFile.exists() && !cacheFile.delete()) {
            Log.w(TAG, "Failed to delete cache file: " + cacheFile.getAbsolutePath());
        }
    }

    public File getDiskCacheDir() {
        return diskCacheDir;
    }

    public void trimDiskCacheIfNeeded() {
        List<File> cacheFiles = listManagedDiskCacheFiles();
        if (cacheFiles.isEmpty()) {
            return;
        }

        long totalBytes = 0L;
        for (File file : cacheFiles) {
            totalBytes += file.length();
        }
        if (totalBytes <= MAX_TOTAL_DISK_CACHE_SIZE_BYTES) {
            return;
        }

        cacheFiles.sort(Comparator.comparingLong(File::lastModified));
        for (File file : cacheFiles) {
            long fileSize = file.length();
            if (file.delete()) {
                totalBytes -= fileSize;
            }
            if (totalBytes <= MAX_TOTAL_DISK_CACHE_SIZE_BYTES) {
                break;
            }
        }
    }

    private Bitmap getBitmapFromDisk(String key) {
        File cacheFile = new File(diskCacheDir, key + ".jpg");
        if (!cacheFile.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(cacheFile)) {
            return BitmapFactory.decodeStream(fis);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read cached bitmap", e);
            return null;
        }
    }

    private void saveBitmapToDisk(String key, Bitmap bitmap) {
        File cacheFile = new File(diskCacheDir, key + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.flush();
            trimDiskCacheIfNeeded();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save cached bitmap", e);
        }
    }

    private List<File> listManagedDiskCacheFiles() {
        List<File> files = new ArrayList<>();

        if (diskCacheDir.exists()) {
            File[] diskFiles = diskCacheDir.listFiles();
            if (diskFiles != null) {
                for (File file : diskFiles) {
                    if (file != null && file.isFile()) {
                        files.add(file);
                    }
                }
            }
        }

        File filesDir = appContext.getFilesDir();
        File[] legacyFiles = filesDir != null ? filesDir.listFiles() : null;
        if (legacyFiles != null) {
            for (File file : legacyFiles) {
                if (file == null || !file.isFile()) {
                    continue;
                }
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.startsWith("cover_") && name.endsWith(".jpg")) {
                    files.add(file);
                }
            }
        }

        return files;
    }

    public String generateKey(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(url.hashCode());
        }
    }

    public void clearCache() {
        memoryCache.evictAll();
        for (File file : listManagedDiskCacheFiles()) {
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Failed to delete cache file: " + file.getAbsolutePath());
            }
        }
        Log.d(TAG, "Cache cleared");
    }

    public String getCacheInfo() {
        int memorySizeKb = memoryCache.size();
        int diskCount = 0;
        long diskBytes = 0L;

        for (File file : listManagedDiskCacheFiles()) {
            diskCount++;
            diskBytes += file.length();
        }

        return String.format(Locale.US, "内存缓存: %.2fMB/20MB, 磁盘缓存: %d项, %.2fMB/100MB",
                memorySizeKb / 1024f, diskCount, diskBytes / 1024f / 1024f);
    }
}
