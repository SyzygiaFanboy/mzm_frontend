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

public class ImageCacheManager {
    private static final String TAG = "ImageCacheManager";
    private static final String CACHE_DIR = "image_cache";
    private static final int MAX_MEMORY_CACHE_SIZE = 20; // 内存缓存最大数量
    
    private static ImageCacheManager instance;
    private LruCache<String, Bitmap> memoryCache;
    private File diskCacheDir;
    
    private ImageCacheManager(Context context) {
        // 初始化内存缓存
        memoryCache = new LruCache<String, Bitmap>(MAX_MEMORY_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return 1; // 每个bitmap计为1个单位
            }
        };
        
        // 初始化磁盘缓存目录
        diskCacheDir = new File(context.getExternalFilesDir(null), CACHE_DIR);
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdirs();
        }
    }
    
    public static synchronized ImageCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new ImageCacheManager(context.getApplicationContext());
        }
        return instance;
    }
    
    /**
     * 从缓存获取图片
     */
    public Bitmap getBitmap(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        String key = generateKey(url);
        
        // 先从内存缓存获取
        Bitmap bitmap = memoryCache.get(key);
        if (bitmap != null) {
            Log.d(TAG, "从内存缓存获取图片: " + url);
            return bitmap;
        }
        
        // 再从磁盘缓存获取
        bitmap = getBitmapFromDisk(key);
        if (bitmap != null) {
            Log.d(TAG, "从磁盘缓存获取图片: " + url);
            // 加入内存缓存
            memoryCache.put(key, bitmap);
            return bitmap;
        }
        
        return null;
    }
    
    /**
     * 保存图片到缓存
     */
    public void putBitmap(String url, Bitmap bitmap) {
        if (url == null || url.isEmpty() || bitmap == null) {
            return;
        }
        
        String key = generateKey(url);
        
        // 保存到内存缓存
        memoryCache.put(key, bitmap);
        
        // 保存到磁盘缓存
        saveBitmapToDisk(key, bitmap);
        
        Log.d(TAG, "图片已缓存: " + url);
    }
    
    /**
     * 从磁盘缓存获取图片
     */
    private Bitmap getBitmapFromDisk(String key) {
        File cacheFile = new File(diskCacheDir, key + ".jpg");
        if (cacheFile.exists()) {
            try (FileInputStream fis = new FileInputStream(cacheFile)) {
                return BitmapFactory.decodeStream(fis);
            } catch (IOException e) {
                Log.e(TAG, "从磁盘读取缓存图片失败", e);
            }
        }
        return null;
    }
    
    /**
     * 保存图片到磁盘缓存
     */
    private void saveBitmapToDisk(String key, Bitmap bitmap) {
        File cacheFile = new File(diskCacheDir, key + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "保存图片到磁盘缓存失败", e);
        }
    }
    
    /**
     * 生成缓存key
     */
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
            // 如果MD5不可用，使用hashCode
            return String.valueOf(url.hashCode());
        }
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        memoryCache.evictAll();
        
        // 清理磁盘缓存
        if (diskCacheDir.exists()) {
            File[] files = diskCacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
        
        Log.d(TAG, "缓存已清理");
    }
    
    /**
     * 获取缓存大小信息
     */
    public String getCacheInfo() {
        int memorySize = memoryCache.size();
        int diskSize = 0;
        
        if (diskCacheDir.exists()) {
            File[] files = diskCacheDir.listFiles();
            if (files != null) {
                diskSize = files.length;
            }
        }
        
        return String.format("内存缓存: %d张, 磁盘缓存: %d张", memorySize, diskSize);
    }
}