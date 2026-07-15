package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.palette.graphics.Palette;

import com.example.myapplication.adapter.BiliVideoAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BiliFavDetailActivity extends AppCompatActivity {

    public interface ResultCallback<T> {
        void onResult(T result);
    }

    public static final String EXTRA_UID = "uid";
    public static final String EXTRA_MEDIA_ID = "media_id";
    public static final String EXTRA_FOLDER_TITLE = "folder_title";
    public static final String EXTRA_CURRENT_PLAYLIST = "current_playlist";

    private static final int BILI_FAV_MAX_DURATION_SECONDS = 30 * 60;

    private ImageView ivBgBlur;
    private View vBgTint;
    private ImageView ivFolderCover;
    private TextView tvUserUid;
    private TextView tvFolderTitle;
    private TextView tvFolderMeta;
    private TextView tvSelected;
    private TextView tvTotal;
    private CheckBox cbSelectAll;
    private ListView lvVideos;
    private ProgressBar loadMore;
    private Button btnConfirm;

    private String uid;
    private String mediaId;

    private BiliVideoAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bili_fav_detail);

        uid = getIntent().getStringExtra(EXTRA_UID);
        mediaId = getIntent().getStringExtra(EXTRA_MEDIA_ID);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        ivBgBlur = findViewById(R.id.detail_bg_blur);
        vBgTint = findViewById(R.id.detail_bg_tint);
        ivFolderCover = findViewById(R.id.ivFolderCover);
        tvUserUid = findViewById(R.id.tvUserUid);
        tvFolderTitle = findViewById(R.id.tvFolderTitle);
        tvFolderMeta = findViewById(R.id.tvFolderMeta);
        tvSelected = findViewById(R.id.tvSelectedCount);
        tvTotal = findViewById(R.id.tvTotalCount);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        lvVideos = findViewById(R.id.lvBiliVideos);
        loadMore = findViewById(R.id.loadMoreProgress);
        btnConfirm = findViewById(R.id.btnConfirm);
        Button btnCancel = findViewById(R.id.btnCancel);

        adapter = new BiliVideoAdapter(this, true);
        lvVideos.setAdapter(adapter);

        adapter.setOnSelectAllListener(shouldSelectAll -> {
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(shouldSelectAll);
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.selectAll(isChecked));
        });
        adapter.setOnItemSelectListener(selectedCount -> tvSelected.setText(String.format(Locale.getDefault(), "已选择: %d", selectedCount)));
        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> adapter.selectAll(isChecked));

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnConfirm.setOnClickListener(v -> {
            List<BiliVideoAdapter.BiliVideoItem> selectedItems = adapter.getSelectedItems();
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "请选择至少一个视频", Toast.LENGTH_SHORT).show();
                return;
            }
            ArrayList<String> bvids = new ArrayList<>();
            for (BiliVideoAdapter.BiliVideoItem it : selectedItems) {
                bvids.add(it.getBvid());
            }
            Intent result = new Intent();
            result.putStringArrayListExtra(BiliFavPickerActivity.EXTRA_SELECTED_BVIDS, bvids);
            setResult(RESULT_OK, result);
            finish();
        });

        tvUserUid.setText(uid != null ? ("UID: " + uid) : "UID: -");
        tvFolderTitle.setText(getIntent().getStringExtra(EXTRA_FOLDER_TITLE) != null ? getIntent().getStringExtra(EXTRA_FOLDER_TITLE) : "收藏夹");
        tvFolderMeta.setText(mediaId != null ? ("FID: " + mediaId) : "FID: -");
        tvTotal.setText("总数: -");
        applyDefaultBackground();

        if (mediaId != null && !mediaId.isEmpty()) {
            fetchFavFolderInfo(mediaId, info -> {
                if (info == null) {
                    return;
                }
                String title = (String) info.get("title");
                String cover = (String) info.get("cover");
                String upUid = (String) info.get("upUid");
                Integer count = (Integer) info.get("mediaCount");
                runOnUiThread(() -> {
                    if (title != null && !title.isEmpty()) {
                        tvFolderTitle.setText(title);
                    }
                    if (count != null && count >= 0) {
                        tvTotal.setText("总数: " + count);
                    }
                    if (upUid != null && !upUid.isEmpty()) {
                        tvUserUid.setText("UID: " + upUid);
                    }
                    if (cover != null && !cover.isEmpty()) {
                        MusicCoverUtils.loadCoverFromUrl(cover, BiliFavDetailActivity.this, ivFolderCover, BiliFavDetailActivity.this::applyDynamicBackground);
                    }
                });
            });
            loadBiliVideoPage(uid, mediaId, 1, 20);
        }
    }

    private void loadBiliVideoPage(String uid, String mediaId, int pageNum, int pageSize) {
        if (pageNum == 1) {
            loadMore.setVisibility(View.GONE);
        } else {
            loadMore.setVisibility(View.VISIBLE);
        }

        OkHttpClient client = new OkHttpClient();
        String url = String.format(Locale.getDefault(), "https://api.bilibili.com/x/v3/fav/resource/list?media_id=%s&pn=%d&ps=%d", mediaId, pageNum, pageSize);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    loadMore.setVisibility(View.GONE);
                    Toast.makeText(BiliFavDetailActivity.this, "获取视频列表失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> {
                        loadMore.setVisibility(View.GONE);
                        Toast.makeText(BiliFavDetailActivity.this, "获取视频列表失败: " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                String json = response.body().string();
                JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
                JsonObject data = jsonObj.getAsJsonObject("data");
                int totalCount = -1;
                if (data != null && data.has("info") && !data.get("info").isJsonNull()) {
                    JsonObject info = data.getAsJsonObject("info");
                    if (info.has("media_count") && !info.get("media_count").isJsonNull()) {
                        totalCount = info.get("media_count").getAsInt();
                    }
                }

                JsonArray medias;
                if (data != null && data.has("medias") && !data.get("medias").isJsonNull()) {
                    medias = data.getAsJsonArray("medias");
                } else {
                    runOnUiThread(() -> {
                        loadMore.setVisibility(View.GONE);
                        if (pageNum == 1) {
                            Toast.makeText(BiliFavDetailActivity.this, "收藏夹为空", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                boolean hasMore = data != null && data.has("has_more") && data.get("has_more").getAsBoolean();
                List<BiliVideoAdapter.BiliVideoItem> videoItems = new ArrayList<>();
                int skippedTooLong = 0;
                for (JsonElement media : medias) {
                    JsonObject vo = media.getAsJsonObject();
                    String bvid = vo.has("bvid") ? vo.get("bvid").getAsString() : null;
                    String title = vo.has("title") ? vo.get("title").getAsString() : "";
                    String uploader = vo.has("upper") && vo.getAsJsonObject("upper").has("name") ? vo.getAsJsonObject("upper").get("name").getAsString() : "";
                    int duration = vo.has("duration") ? vo.get("duration").getAsInt() : 0;
                    if (bvid == null) {
                        continue;
                    }
                    if (!Objects.equals(title, "已失效视频")) {
                        if (duration > BILI_FAV_MAX_DURATION_SECONDS) {
                            skippedTooLong++;
                            continue;
                        }
                        videoItems.add(new BiliVideoAdapter.BiliVideoItem(bvid, title, uploader, duration));
                    }
                }
                int totalCountFinal = totalCount;
                int skippedFinal = skippedTooLong;
                runOnUiThread(() -> {
                    loadMore.setVisibility(View.GONE);
                    adapter.addItems(videoItems);
                    if (pageNum == 1 && totalCountFinal >= 0) {
                        tvTotal.setText("总数: " + totalCountFinal);
                    }
                    if (pageNum == 1 && skippedFinal > 0) {
                        Toast.makeText(BiliFavDetailActivity.this, "已跳过 " + skippedFinal + " 个超长视频", Toast.LENGTH_SHORT).show();
                    }
                    if (pageNum == 1) {
                        lvVideos.setOnScrollListener(new AbsListView.OnScrollListener() {
                            private int page = pageNum;
                            private final int visibleThreshold = 5;
                            private boolean loading = true;
                            private int previousTotal = 0;

                            @Override
                            public void onScrollStateChanged(AbsListView view, int scrollState) {
                            }

                            @Override
                            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                                if (loading && totalItemCount > previousTotal) {
                                    loading = false;
                                    previousTotal = totalItemCount;
                                }
                                if (!loading && hasMore && (totalItemCount - visibleItemCount) <= (firstVisibleItem + visibleThreshold)) {
                                    loading = true;
                                    page++;
                                    loadBiliVideoPage(uid, mediaId, page, pageSize);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    private void fetchFavFolderInfo(String mediaId, ResultCallback<java.util.Map<String, Object>> callback) {
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
                String title = data.has("title") && !data.get("title").isJsonNull() ? data.get("title").getAsString() : null;
                String cover = data.has("cover") && !data.get("cover").isJsonNull() ? data.get("cover").getAsString() : null;
                Integer mediaCount = data.has("media_count") && !data.get("media_count").isJsonNull() ? data.get("media_count").getAsInt() : null;
                String upName = null;
                String upUid = null;
                String upFace = null;
                if (data.has("upper") && !data.get("upper").isJsonNull()) {
                    JsonObject upper = data.getAsJsonObject("upper");
                    upName = upper.has("name") && !upper.get("name").isJsonNull() ? upper.get("name").getAsString() : null;
                    upUid = upper.has("mid") && !upper.get("mid").isJsonNull() ? upper.get("mid").getAsString() : null;
                    upFace = upper.has("face") && !upper.get("face").isJsonNull() ? upper.get("face").getAsString() : null;
                }
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                result.put("title", title);
                result.put("cover", cover);
                result.put("mediaCount", mediaCount);
                result.put("upName", upName);
                result.put("upUid", upUid);
                result.put("upFace", upFace);
                callback.onResult(result);
            }
        });
    }

    private void applyDefaultBackground() {
        if (ivBgBlur != null) {
            ivBgBlur.setImageDrawable(null);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ivBgBlur.setRenderEffect(null);
            }
        }
        if (vBgTint != null) {
            vBgTint.setBackgroundColor(0x33000000);
        }
    }

    private void applyDynamicBackground(Bitmap coverBitmap) {
        if (coverBitmap == null || coverBitmap.isRecycled()) {
            return;
        }
        new Thread(() -> {
            Bitmap paletteSource = scaleBitmap(coverBitmap, 64);
            Palette palette = Palette.from(paletteSource).generate();
            int baseColor = palette.getVibrantColor(palette.getDominantColor(0xFF2E2E2E));

            Bitmap blurSource = scaleBitmap(coverBitmap, 240);
            Bitmap blurred = null;
            boolean useRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
            if (!useRenderEffect) {
                blurred = fastBlur(blurSource, 18);
            }
            Bitmap finalBlurred = blurred;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (ivBgBlur != null) {
                    if (useRenderEffect) {
                        ivBgBlur.setImageBitmap(blurSource);
                        ivBgBlur.setRenderEffect(RenderEffect.createBlurEffect(26f, 26f, Shader.TileMode.CLAMP));
                    } else {
                        ivBgBlur.setImageBitmap(finalBlurred != null ? finalBlurred : blurSource);
                    }
                }
                if (vBgTint != null) {
                    vBgTint.setBackground(createBackgroundGradient(baseColor));
                }
            });
        }).start();
    }

    private GradientDrawable createBackgroundGradient(int baseColor) {
        int top = adjustAlpha(baseColor, 0.48f);
        int mid = adjustAlpha(baseColor, 0.26f);
        int bottom = adjustAlpha(Color.BLACK, 0.10f);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, mid, bottom});
        drawable.setDither(true);
        return drawable;
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.min(255, Math.max(0, (int) (255 * factor)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private Bitmap scaleBitmap(Bitmap source, int targetMinEdge) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= 0 || h <= 0) {
            return source;
        }
        int minEdge = Math.min(w, h);
        if (minEdge <= targetMinEdge) {
            return source;
        }
        float scale = targetMinEdge / (float) minEdge;
        int outW = Math.max(1, Math.round(w * scale));
        int outH = Math.max(1, Math.round(h * scale));
        return Bitmap.createScaledBitmap(source, outW, outH, true);
    }

    private Bitmap fastBlur(Bitmap source, int radius) {
        if (radius < 1) {
            return source;
        }
        Bitmap bitmap = source.copy(Bitmap.Config.ARGB_8888, true);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);
        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;
        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int rsum;
        int gsum;
        int bsum;
        int x;
        int y;
        int i;
        int p;
        int yp;
        int yi;
        int yw;
        int[] vmin = new int[Math.max(w, h)];
        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }
        yw = yi = 0;
        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum;
        int goutsum;
        int boutsum;
        int rinsum;
        int ginsum;
        int binsum;
        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;
            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];
                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];
                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];
                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];
                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];
                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];
                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                sir = stack[i + radius];
                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];
                rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];
                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];
                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];
                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];
                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];
                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];
                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];
                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];
                yi += w;
            }
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap;
    }
}
