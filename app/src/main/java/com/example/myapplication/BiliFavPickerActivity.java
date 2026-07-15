package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.adapter.BiliFavFolderAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BiliFavPickerActivity extends AppCompatActivity {
    private interface ResultCallback<T> {
        void onResult(T result);
    }

    public static final String EXTRA_SELECTED_BVIDS = "selected_bvids";
    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_CURRENT_PLAYLIST = "current_playlist";

    private static final int REQUEST_CODE_DETAIL = 3011;
    private static final int PAGE_SIZE = 10;

    private DrawerLayout drawerLayout;
    private ImageView ivBgBlur;
    private View vBgTint;
    private EditText etUid;
    private ImageButton btnSearch;
    private EditText etFilter;
    private View userInfoBar;
    private ImageView ivUserAvatar;
    private TextView tvUserName;
    private TextView tvUserUid;
    private RecyclerView rvFolders;
    private ProgressBar progress;
    private TextView tvEmpty;

    private final List<BiliFavFolderAdapter.FavFolder> allFolders = new ArrayList<>();
    private int visibleCount = 0;
    private boolean loading = false;
    private boolean hasSearched = false;
    private String currentUid = null;

    private BiliFavFolderAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bili_fav_picker);

        drawerLayout = findViewById(R.id.drawer_layout);
        ivBgBlur = findViewById(R.id.detail_bg_blur);
        vBgTint = findViewById(R.id.detail_bg_tint);
        applyPlaybackBackground();

        View contentRoot = findViewById(R.id.contentRoot);
        if (contentRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (v, insets) -> {
                int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), imeBottom);
                return insets;
            });
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        etUid = findViewById(R.id.etUid);
        btnSearch = findViewById(R.id.btnUidSearch);
        etFilter = findViewById(R.id.etFolderFilter);
        userInfoBar = findViewById(R.id.userInfoBar);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserUid = findViewById(R.id.tvUserUid);
        rvFolders = findViewById(R.id.rvFavFolders);
        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);

        rvFolders.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BiliFavFolderAdapter(this, folder -> {
            if (currentUid == null || currentUid.isEmpty()) {
                return;
            }
            Intent intent = new Intent(BiliFavPickerActivity.this, BiliFavDetailActivity.class);
            intent.putExtra(BiliFavDetailActivity.EXTRA_UID, currentUid);
            intent.putExtra(BiliFavDetailActivity.EXTRA_MEDIA_ID, folder.mediaId);
            intent.putExtra(BiliFavDetailActivity.EXTRA_FOLDER_TITLE, folder.title);
            intent.putExtra(BiliFavDetailActivity.EXTRA_CURRENT_PLAYLIST, getIntent().getStringExtra(EXTRA_CURRENT_PLAYLIST));
            startActivityForResult(intent, REQUEST_CODE_DETAIL);
        });
        rvFolders.setAdapter(adapter);

        rvFolders.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0) {
                    return;
                }
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) {
                    return;
                }
                int last = lm.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 3) {
                    loadNextPageIfNeeded();
                }
            }
        });

        btnSearch.setOnClickListener(v -> {
            String uid = etUid.getText().toString().trim();
            uid = uid.replace("UID:", "").trim();
            if (!uid.matches("\\d+")) {
                Toast.makeText(this, "请输入正确的 UID", Toast.LENGTH_SHORT).show();
                return;
            }
            searchByUid(uid);
        });

        etFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                visibleCount = Math.min(PAGE_SIZE, getFilteredAll().size());
                refreshList();
            }
        });

        showEmptyHint();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPlaybackBackground();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_DETAIL && resultCode == RESULT_OK && data != null) {
            ArrayList<String> bvids = data.getStringArrayListExtra(EXTRA_SELECTED_BVIDS);
            if (bvids != null && !bvids.isEmpty()) {
                Intent result = new Intent();
                result.putStringArrayListExtra(EXTRA_SELECTED_BVIDS, bvids);
                setResult(RESULT_OK, result);
                finish();
            }
        }
    }

    private void searchByUid(String uid) {
        if (loading) {
            return;
        }
        hasSearched = true;
        currentUid = uid;
        allFolders.clear();
        visibleCount = 0;
        adapter.submitList(new ArrayList<>());
        refreshList();

        if (userInfoBar != null) {
            userInfoBar.setVisibility(View.GONE);
        }

        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        OkHttpClient client = new OkHttpClient();
        String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&up_mid=" + uid;
        Request req = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .addHeader("Accept", "application/json")
                .build();

        loading = true;
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    Toast.makeText(BiliFavPickerActivity.this, "请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    showEmptyHint();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> {
                        loading = false;
                        progress.setVisibility(View.GONE);
                        Toast.makeText(BiliFavPickerActivity.this, "请求失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        showEmptyHint();
                    });
                    return;
                }
                String json = response.body().string();
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                int code = obj.has("code") ? obj.get("code").getAsInt() : -1;
                if (code != 0 || !obj.has("data") || obj.get("data").isJsonNull()) {
                    String msg = obj.has("message") ? obj.get("message").getAsString() : "未知错误";
                    runOnUiThread(() -> {
                        loading = false;
                        progress.setVisibility(View.GONE);
                        Toast.makeText(BiliFavPickerActivity.this, "无法获取收藏夹（可能未公开）: " + msg, Toast.LENGTH_SHORT).show();
                        showEmptyHint();
                    });
                    return;
                }
                JsonObject dataObj = obj.getAsJsonObject("data");
                if (!dataObj.has("list") || dataObj.get("list").isJsonNull()) {
                    runOnUiThread(() -> {
                        loading = false;
                        progress.setVisibility(View.GONE);
                        showEmptyHint();
                    });
                    return;
                }
                JsonArray list = dataObj.getAsJsonArray("list");
                List<BiliFavFolderAdapter.FavFolder> folders = new ArrayList<>();
                for (JsonElement el : list) {
                    JsonObject fo = el.getAsJsonObject();
                    String mediaId = fo.has("id") && !fo.get("id").isJsonNull() ? fo.get("id").getAsString() : null;
                    String title = fo.has("title") && !fo.get("title").isJsonNull() ? fo.get("title").getAsString() : "";
                    int count = fo.has("media_count") && !fo.get("media_count").isJsonNull() ? fo.get("media_count").getAsInt() : -1;
                    if (mediaId != null) {
                        folders.add(new BiliFavFolderAdapter.FavFolder(mediaId, title, count));
                    }
                }
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    allFolders.clear();
                    allFolders.addAll(folders);
                    visibleCount = Math.min(PAGE_SIZE, getFilteredAll().size());
                    refreshList();
                    showEmptyHint();

                    if (!allFolders.isEmpty()) {
                        fetchBiliFavFolderInfo(allFolders.get(0).mediaId, info -> {
                            if (info == null) {
                                return;
                            }
                            String upName = (String) info.get("upName");
                            String upUid = (String) info.get("upUid");
                            String upFace = (String) info.get("upFace");
                            runOnUiThread(() -> {
                                if (userInfoBar != null) {
                                    userInfoBar.setVisibility(View.VISIBLE);
                                }
                                if (tvUserName != null && upName != null && !upName.isEmpty()) {
                                    tvUserName.setText(upName);
                                }
                                if (tvUserUid != null) {
                                    tvUserUid.setText("UID: " + (upUid != null && !upUid.isEmpty() ? upUid : currentUid));
                                }
                                if (ivUserAvatar != null && upFace != null && !upFace.isEmpty()) {
                                    MusicCoverUtils.loadCoverFromUrl(upFace, BiliFavPickerActivity.this, ivUserAvatar);
                                }
                            });
                        });
                    }
                });
            }
        });
    }

    private void loadNextPageIfNeeded() {
        if (loading) {
            return;
        }
        List<BiliFavFolderAdapter.FavFolder> filtered = getFilteredAll();
        if (visibleCount >= filtered.size()) {
            return;
        }
        visibleCount = Math.min(filtered.size(), visibleCount + PAGE_SIZE);
        refreshList();
    }

    private void refreshList() {
        List<BiliFavFolderAdapter.FavFolder> filtered = getFilteredAll();
        int end = Math.min(visibleCount, filtered.size());
        List<BiliFavFolderAdapter.FavFolder> slice = new ArrayList<>();
        if (end > 0) {
            slice.addAll(filtered.subList(0, end));
        }
        adapter.submitList(slice);
    }

    private List<BiliFavFolderAdapter.FavFolder> getFilteredAll() {
        String kw = etFilter != null ? etFilter.getText().toString().trim() : "";
        if (kw.isEmpty()) {
            return new ArrayList<>(allFolders);
        }
        String low = kw.toLowerCase(Locale.getDefault());
        List<BiliFavFolderAdapter.FavFolder> out = new ArrayList<>();
        for (BiliFavFolderAdapter.FavFolder f : allFolders) {
            if (f.title != null && f.title.toLowerCase(Locale.getDefault()).contains(low)) {
                out.add(f);
            }
        }
        return out;
    }

    private void showEmptyHint() {
        if (!hasSearched) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("请输入 UID 搜索收藏夹");
            return;
        }
        if (allFolders.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("未找到收藏夹");
            return;
        }
        if (getFilteredAll().isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("无匹配的收藏夹");
            return;
        }
        tvEmpty.setVisibility(View.GONE);
    }

    private void applyPlaybackBackground() {
        if (ivBgBlur == null || vBgTint == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences("background_prefs", MODE_PRIVATE);
        String playbackBgPath = prefs.getString("playback_background_path", null);
        int transparency = 180;
        int blurRadius = 0;
        if (playbackBgPath == null || !new File(playbackBgPath).exists()) {
            ivBgBlur.setImageDrawable(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ivBgBlur.setRenderEffect(null);
            }
            return;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        AtomicBoolean cancelled = new AtomicBoolean(false);
        new Thread(() -> {
            Bitmap out = null;
            try {
                Bitmap original = decodeSampledBitmapFromFile(playbackBgPath, screenWidth, screenHeight);
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
                if (isFinishing() || isDestroyed() || cancelled.get()) {
                    if (finalOut != null) {
                        finalOut.recycle();
                    }
                    return;
                }
                if (finalOut != null) {
                    ivBgBlur.setImageBitmap(finalOut);
                    ivBgBlur.setAlpha(Math.min(1f, Math.max(0f, transparency / 255f)));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (blurRadius > 0) {
                            float r = Math.min(25f, Math.max(0f, blurRadius));
                            ivBgBlur.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
                        } else {
                            ivBgBlur.setRenderEffect(null);
                        }
                    } else {
                        if (blurRadius > 0) {
                            Bitmap blurred = blurBitmap(finalOut, Math.min(25, blurRadius));
                            if (blurred != finalOut) {
                                ivBgBlur.setImageBitmap(blurred);
                                try {
                                    finalOut.recycle();
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                } else {
                    ivBgBlur.setImageDrawable(null);
                }
            });
        }).start();
    }

    private void fetchBiliFavFolderInfo(String mediaId, ResultCallback<java.util.Map<String, Object>> callback) {
        OkHttpClient client = new OkHttpClient();
        Request req = new Request.Builder()
                .url("https://api.bilibili.com/x/v3/fav/folder/info?media_id=" + mediaId)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .addHeader("Accept", "application/json")
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                callback.onResult(null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onResult(null);
                    return;
                }
                String json = response.body().string();
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                int code = obj.has("code") ? obj.get("code").getAsInt() : -1;
                if (code != 0 || !obj.has("data") || obj.get("data").isJsonNull()) {
                    callback.onResult(null);
                    return;
                }
                JsonObject data = obj.getAsJsonObject("data");
                String upName = null;
                String upUid = null;
                String upFace = null;
                if (data.has("upper") && !data.get("upper").isJsonNull()) {
                    JsonObject upper = data.getAsJsonObject("upper");
                    upName = upper.has("name") && !upper.get("name").isJsonNull() ? upper.get("name").getAsString() : null;
                    upUid = upper.has("mid") && !upper.get("mid").isJsonNull() ? upper.get("mid").getAsString() : null;
                    upFace = upper.has("face") && !upper.get("face").isJsonNull() ? upper.get("face").getAsString() : null;
                }
                java.util.Map<String, Object> result = new HashMap<>();
                result.put("upName", upName);
                result.put("upUid", upUid);
                result.put("upFace", upFace);
                callback.onResult(result);
            }
        });
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

    private Bitmap blurBitmap(Bitmap bitmap, int radius) {
        if (radius <= 0) {
            return bitmap;
        }
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                android.renderscript.RenderScript rs = android.renderscript.RenderScript.create(this);
                android.renderscript.Allocation input = android.renderscript.Allocation.createFromBitmap(rs, bitmap);
                android.renderscript.Allocation outputAlloc = android.renderscript.Allocation.createFromBitmap(rs, output);
                float blurRadius = Math.min(Math.max(radius, 0), 25);
                android.renderscript.ScriptIntrinsicBlur blurScript = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs));
                blurScript.setInput(input);
                blurScript.setRadius(blurRadius);
                blurScript.forEach(outputAlloc);
                outputAlloc.copyTo(output);
                input.destroy();
                outputAlloc.destroy();
                blurScript.destroy();
                rs.destroy();
                return output;
            } catch (Exception e) {
                return bitmap;
            }
        }
        android.renderscript.RenderScript rs = android.renderscript.RenderScript.create(this);
        android.renderscript.ScriptIntrinsicBlur script = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs));
        android.renderscript.Allocation input = android.renderscript.Allocation.createFromBitmap(rs, bitmap);
        android.renderscript.Allocation outputAlloc = android.renderscript.Allocation.createFromBitmap(rs, output);
        script.setRadius(radius);
        script.setInput(input);
        script.forEach(outputAlloc);
        outputAlloc.copyTo(output);
        rs.destroy();
        return output;
    }
}
