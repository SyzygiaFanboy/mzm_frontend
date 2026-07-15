package com.example.myapplication;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;

public class AboutActivity extends AppCompatActivity {

    private ImageView ivBg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        ivBg = findViewById(R.id.detail_bg_blur);
        applyPlaylistBackground();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPlaylistBackground();
    }

    private void applyPlaylistBackground() {
        if (ivBg == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
        String playlistBgPath = prefs.getString("playlist_background_path", null);
        int transparency = prefs.getInt("playlist_background_path_transparency", 180);
        int blurRadius = prefs.getInt("playlist_background_path_blur", 0);
        if (playlistBgPath == null || !new File(playlistBgPath).exists()) {
            // 背景未设置时：跟随歌单页面背景，直接清空，不再兜底使用 background.jpg
            ivBg.setImageDrawable(null);
            ivBg.setAlpha(0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ivBg.setRenderEffect(null);
            }
            return;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        new Thread(() -> {
            Bitmap out = null;
            try {
                Bitmap original = decodeSampledBitmapFromFile(playlistBgPath, screenWidth, screenHeight);
                if (original != null) {
                    Bitmap scaled = createScaledBitmap(original, screenWidth, screenHeight);
                    if (original != scaled) {
                        original.recycle();
                    }
                    out = scaled;
                }
            } catch (Exception ignored) {
            }
            Bitmap finalOut = out;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    if (finalOut != null) {
                        finalOut.recycle();
                    }
                    return;
                }
                if (finalOut != null) {
                    ivBg.setImageBitmap(finalOut);
                    ivBg.setAlpha(Math.min(1f, Math.max(0f, transparency / 255f)));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (blurRadius > 0) {
                            float r = Math.min(25f, Math.max(0f, blurRadius));
                            ivBg.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
                        } else {
                            ivBg.setRenderEffect(null);
                        }
                    }
                } else {
                    ivBg.setImageDrawable(null);
                    ivBg.setAlpha(0f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ivBg.setRenderEffect(null);
                    }
                }
            });
        }).start();
    }

    private Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, options);
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return Math.max(1, inSampleSize);
    }

    private Bitmap createScaledBitmap(Bitmap originalBitmap, int targetWidth, int targetHeight) {
        int originalWidth = originalBitmap.getWidth();
        int originalHeight = originalBitmap.getHeight();
        float scaleX = (float) targetWidth / originalWidth;
        float scaleY = (float) targetHeight / originalHeight;
        float scale = Math.max(scaleX, scaleY);
        int scaledWidth = Math.round(originalWidth * scale);
        int scaledHeight = Math.round(originalHeight * scale);
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, scaledWidth, scaledHeight, true);
        if (scaledWidth > targetWidth || scaledHeight > targetHeight) {
            int x = Math.max(0, (scaledWidth - targetWidth) / 2);
            int y = Math.max(0, (scaledHeight - targetHeight) / 2);
            Bitmap cropped = Bitmap.createBitmap(scaledBitmap, x, y,
                    Math.min(targetWidth, scaledWidth),
                    Math.min(targetHeight, scaledHeight));
            if (scaledBitmap != cropped) {
                scaledBitmap.recycle();
            }
            return cropped;
        }
        return scaledBitmap;
    }
}

