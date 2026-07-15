package com.example.myapplication;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.adapter.BiliVideoAdapter;
import com.example.myapplication.utils.BiliLinkParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BiliSharePickerActivity extends AppCompatActivity {
    public static final String EXTRA_SELECTED_IDS = "selected_ids";
    public static final String EXTRA_SELECTED_TITLES = "selected_titles";
    public static final String EXTRA_SELECTED_COVERS = "selected_covers";
    public static final String EXTRA_SELECTED_EPIDS = "selected_epids";

    private static final int PAGE_SIZE = 20;

    private ImageView ivBgBlur;
    private View vBgTint;
    private EditText etShareInput;
    private ImageButton btnParse;
    private EditText etItemFilter;
    private View userInfoBar;
    private ImageView ivUserAvatar;
    private TextView tvUserName;
    private TextView tvUserUid;
    private View videoInfoCard;
    private ImageView ivVideoCover;
    private TextView tvVideoInfo;
    private HorizontalScrollView hsvTabs;
    private LinearLayout tabsContainer;
    private View selectionBar;
    private CheckBox cbSelectAll;
    private TextView tvSelectedCount;
    private Button btnClear;
    private ListView lvItems;
    private ProgressBar progress;
    private TextView tvEmpty;
    private Button btnCancel;
    private Button btnConfirm;

    private final OkHttpClient httpClient = new OkHttpClient();
    private BiliVideoAdapter adapter;

    private final List<CollectionTab> tabs = new ArrayList<>();
    private int selectedTabIndex = -1;
    private String headerCoverUrl = null;
    private String currentVideoTitle = null;
    private String currentVideoInfoText = null;
    private int displayedCount = 0;
    private boolean isLoadingMore = false;
    private List<BiliVideoAdapter.BiliVideoItem> filteredItems = new ArrayList<>();
    private final Map<String, String> coverByUniqueId = new HashMap<>();
    private boolean suppressFilterApply = false;

    private interface ResultCallback<T> {
        void onResult(T result);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bili_share_picker);

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

        etShareInput = findViewById(R.id.etShareInput);
        btnParse = findViewById(R.id.btnParse);
        etItemFilter = findViewById(R.id.etItemFilter);
        userInfoBar = findViewById(R.id.userInfoBar);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserUid = findViewById(R.id.tvUserUid);
        videoInfoCard = findViewById(R.id.videoInfoCard);
        ivVideoCover = findViewById(R.id.ivVideoCover);
        tvVideoInfo = findViewById(R.id.tvVideoInfo);
        hsvTabs = findViewById(R.id.hsvTabs);
        tabsContainer = findViewById(R.id.tabsContainer);
        selectionBar = findViewById(R.id.selectionBar);
        cbSelectAll = findViewById(R.id.cbSelectAll);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnClear = findViewById(R.id.btnClear);
        lvItems = findViewById(R.id.lvItems);
        progress = findViewById(R.id.progress);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnCancel = findViewById(R.id.btnCancel);
        btnConfirm = findViewById(R.id.btnConfirm);

        adapter = new BiliVideoAdapter(this, true);
        lvItems.setAdapter(adapter);
        adapter.setOnSelectAllListener(shouldSelectAll -> {
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(isCurrentFilterAllSelected());
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> setSelectAllForCurrentFilter(isChecked));
        });
        adapter.setOnItemSelectListener(selectedCount -> updateSelectionUi());

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> setSelectAllForCurrentFilter(isChecked));

        lvItems.setOnScrollListener(new AbsListView.OnScrollListener() {
            private final int visibleThreshold = 5;
            private int previousTotal = 0;
            private boolean loading = true;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (loading && totalItemCount > previousTotal) {
                    loading = false;
                    previousTotal = totalItemCount;
                }
                if (!loading && (totalItemCount - visibleItemCount) <= (firstVisibleItem + visibleThreshold)) {
                    loading = true;
                    loadNextPage();
                }
            }
        });

        btnParse.setOnClickListener(v -> parseAndLoad());
        etItemFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressFilterApply) {
                    return;
                }
                applyFilterAndReset();
            }
        });

        btnClear.setOnClickListener(v -> {
            if (selectedTabIndex < 0 || selectedTabIndex >= tabs.size()) {
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("清空选择")
                    .setMessage("清空当前合集已选内容？")
                    .setPositiveButton("清空", (d, w) -> {
                        CollectionTab tab = tabs.get(selectedTabIndex);
                        for (BiliVideoAdapter.BiliVideoItem it : tab.items) {
                            it.setSelected(false);
                        }
                        adapter.notifyDataSetChanged();
                        updateSelectionUi();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        btnConfirm.setOnClickListener(v -> {
            SelectionPayload payload = collectSelections();
            if (payload.ids.isEmpty()) {
                Toast.makeText(this, "请选择至少一个条目", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent data = new Intent();
            data.putStringArrayListExtra(EXTRA_SELECTED_IDS, payload.ids);
            data.putStringArrayListExtra(EXTRA_SELECTED_TITLES, payload.titles);
            data.putStringArrayListExtra(EXTRA_SELECTED_COVERS, payload.covers);
            data.putExtra(EXTRA_SELECTED_EPIDS, toLongArray(payload.epIds));
            setResult(RESULT_OK, data);
            finish();
        });

        showEmptyHint("粘贴分享链接后点击右侧按钮解析");
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPlaybackBackground();
    }

    private void parseAndLoad() {
        String raw = etShareInput != null ? etShareInput.getText().toString().trim() : "";
        if (raw.isEmpty()) {
            Toast.makeText(this, "请输入链接或BV号", Toast.LENGTH_SHORT).show();
            return;
        }
        String token = BiliLinkParser.extractToken(raw);
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "无法识别输入内容", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        clearAllUi();

        String normalized = token;
        if (normalized.startsWith("b23.tv/")) {
            normalized = "https://" + normalized;
        } else if (normalized.startsWith("www.bilibili.com/")) {
            normalized = "https://" + normalized;
        } else if (normalized.startsWith("m.bilibili.com/")) {
            normalized = "https://" + normalized;
        }

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            Uri uri = Uri.parse(normalized);
            String host = uri.getHost() != null ? uri.getHost() : "";
            if (host.contains("b23.tv")) {
                resolveFinalUrl(normalized, finalUrl -> {
                    if (finalUrl == null) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showEmptyHint("短链解析失败");
                        });
                        return;
                    }
                    parseResolvedUrl(finalUrl);
                });
                return;
            }
            parseResolvedUrl(normalized);
            return;
        }

        if (normalized.matches("BV[0-9A-Za-z]{10,}")) {
            fetchViewByBvid(normalized, 0);
            return;
        }
        if (normalized.matches("av\\d+")) {
            fetchViewByAid(normalized.substring(2), 0);
            return;
        }
        if (normalized.matches("ep\\d+")) {
            long ep = safeLong(normalized.substring(2));
            fetchBangumiSeason(ep, null);
            return;
        }
        if (normalized.matches("ss\\d+")) {
            long ss = safeLong(normalized.substring(2));
            fetchBangumiSeason(null, ss);
            return;
        }

        runOnUiThread(() -> {
            setLoading(false);
            showEmptyHint("不支持的输入");
        });
    }

    private void parseResolvedUrl(String url) {
        runOnUiThread(() -> {
            if (etShareInput != null) {
                etShareInput.setText(url);
            }
        });
        Uri uri = Uri.parse(url);
        String path = uri.getPath() != null ? uri.getPath() : "";
        int p = 0;
        try {
            String pStr = uri.getQueryParameter("p");
            if (pStr != null) {
                p = Integer.parseInt(pStr);
            }
        } catch (Exception ignored) {
        }

        Long epId = extractBangumiEpId(path);
        if (epId != null) {
            fetchBangumiSeason(epId, null);
            return;
        }
        Long ssId = extractBangumiSeasonId(path);
        if (ssId != null) {
            fetchBangumiSeason(null, ssId);
            return;
        }

        String bvid = BiliLinkParser.extractBvid(url);
        if (bvid != null) {
            fetchViewByBvid(bvid, p);
            return;
        }
        String aid = BiliLinkParser.extractAid(url);
        if (aid != null) {
            fetchViewByAid(aid, p);
            return;
        }

        runOnUiThread(() -> {
            setLoading(false);
            showEmptyHint("无法识别链接");
        });
    }

    private void fetchViewByAid(String aid, int p) {
        Request req = new Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/view?aid=" + aid)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();
        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showEmptyHint("请求失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showEmptyHint("请求失败: " + response.code());
                    });
                    return;
                }
                String str = response.body().string();
                JsonObject root = JsonParser.parseString(str).getAsJsonObject();
                int code = root.has("code") ? root.get("code").getAsInt() : -1;
                if (code != 0 || !root.has("data") || root.get("data").isJsonNull()) {
                    String msg = root.has("message") ? root.get("message").getAsString() : "未知错误";
                    runOnUiThread(() -> {
                        setLoading(false);
                        showEmptyHint("解析失败: " + msg);
                    });
                    return;
                }
                JsonObject data = root.getAsJsonObject("data");
                String bvid = data.has("bvid") && !data.get("bvid").isJsonNull() ? data.get("bvid").getAsString() : null;
                if (bvid == null || bvid.isEmpty()) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showEmptyHint("解析失败: 缺少BV号");
                    });
                    return;
                }
                fetchViewByBvid(bvid, p);
            }
        });
    }

    private void fetchViewByBvid(String bvid, int p) {
        Request req = new Request.Builder()
                .url("https://api.bilibili.com/x/web-interface/view?bvid=" + bvid)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();
        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showEmptyHint("请求失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showEmptyHint("请求失败: " + response.code());
                    });
                    return;
                }
                String str = response.body().string();
                JsonObject root = JsonParser.parseString(str).getAsJsonObject();
                int code = root.has("code") ? root.get("code").getAsInt() : -1;
                if (code != 0 || !root.has("data") || root.get("data").isJsonNull()) {
                    String msg = root.has("message") ? root.get("message").getAsString() : "未知错误";
                    runOnUiThread(() -> {
                        setLoading(false);
                        showEmptyHint("解析失败: " + msg);
                    });
                    return;
                }
                JsonObject json = root.getAsJsonObject("data");
                buildCollectionsFromViewJson(bvid, json, p);
            }
        });
    }

    private void buildCollectionsFromViewJson(String bvid, JsonObject json, int p) {
        tabs.clear();
        coverByUniqueId.clear();
        headerCoverUrl = json.has("pic") && !json.get("pic").isJsonNull() ? json.get("pic").getAsString() : null;
        currentVideoTitle = json.has("title") && !json.get("title").isJsonNull() ? json.get("title").getAsString() : null;

        String desc = json.has("desc") && !json.get("desc").isJsonNull() ? json.get("desc").getAsString() : "";
        String tname = json.has("tname") && !json.get("tname").isJsonNull() ? json.get("tname").getAsString() : "";
        String pubdate = json.has("pubdate") && !json.get("pubdate").isJsonNull() ? json.get("pubdate").getAsString() : "";

        JsonObject owner = json.has("owner") && !json.get("owner").isJsonNull() ? json.getAsJsonObject("owner") : null;
        String upName = owner != null && owner.has("name") && !owner.get("name").isJsonNull() ? owner.get("name").getAsString() : null;
        String upUid = owner != null && owner.has("mid") && !owner.get("mid").isJsonNull() ? owner.get("mid").getAsString() : null;
        String upFace = owner != null && owner.has("face") && !owner.get("face").isJsonNull() ? owner.get("face").getAsString() : null;

        currentVideoInfoText = buildInfoText(currentVideoTitle, tname, pubdate, json, desc);

        JsonObject ugcSeason = json.has("ugc_season") && !json.get("ugc_season").isJsonNull() ? json.getAsJsonObject("ugc_season") : null;
        if (ugcSeason != null && ugcSeason.has("sections") && ugcSeason.get("sections").isJsonArray()) {
            int targetTabIndex = -1;
            int targetItemIndex = -1;
            String targetUniqueId = null;
            JsonArray sections = ugcSeason.getAsJsonArray("sections");
            for (JsonElement sectionEl : sections) {
                JsonObject section = sectionEl.getAsJsonObject();
                String secTitle = section.has("title") && !section.get("title").isJsonNull() ? section.get("title").getAsString() : "合集";
                List<BiliVideoAdapter.BiliVideoItem> items = new ArrayList<>();
                JsonArray episodes = section.has("episodes") && section.get("episodes").isJsonArray() ? section.getAsJsonArray("episodes") : null;
                if (episodes != null) {
                    for (JsonElement epEl : episodes) {
                        JsonObject ep = epEl.getAsJsonObject();
                        String epBvid = ep.has("bvid") && !ep.get("bvid").isJsonNull() ? ep.get("bvid").getAsString() : null;
                        long cid = ep.has("cid") && !ep.get("cid").isJsonNull() ? ep.get("cid").getAsLong() : -1;
                        String title = ep.has("title") && !ep.get("title").isJsonNull() ? ep.get("title").getAsString() : "";
                        int duration = 0;
                        String epCover = null;
                        if (ep.has("arc") && ep.get("arc").isJsonObject()) {
                            JsonObject arc = ep.getAsJsonObject("arc");
                            duration = arc.has("duration") && !arc.get("duration").isJsonNull() ? arc.get("duration").getAsInt() : 0;
                            epCover = arc.has("pic") && !arc.get("pic").isJsonNull() ? arc.get("pic").getAsString() : null;
                        }
                        if (epBvid == null || cid <= 0) {
                            continue;
                        }
                        String uniqueId = epBvid + "_" + cid;
                        BiliVideoAdapter.BiliVideoItem item = new BiliVideoAdapter.BiliVideoItem(uniqueId, title, upName != null ? upName : "UP主", duration);
                        if (targetTabIndex < 0 && epBvid.equalsIgnoreCase(bvid)) {
                            targetTabIndex = tabs.size();
                            targetItemIndex = items.size();
                            targetUniqueId = uniqueId;
                        }
                        items.add(item);
                        coverByUniqueId.put(uniqueId, epCover != null && !epCover.isEmpty() ? epCover : headerCoverUrl);
                    }
                }
                tabs.add(new CollectionTab(secTitle, items));
            }
            final int finalTargetTabIndex = targetTabIndex;
            final int finalTargetItemIndex = targetItemIndex;
            final String finalTargetUniqueId = targetUniqueId;
            runOnUiThread(() -> {
                applyHeader(upName, upUid, upFace);
                setLoading(false);
                showParsedUi();
                setFilterTextSilently("");
                if (finalTargetTabIndex >= 0 && finalTargetTabIndex < tabs.size() && finalTargetItemIndex >= 0 && finalTargetItemIndex < tabs.get(finalTargetTabIndex).items.size()) {
                    clearAllSelections();
                    tabs.get(finalTargetTabIndex).items.get(finalTargetItemIndex).setSelected(true);
                    adapter.setHighlightBvid(finalTargetUniqueId);
                    selectTab(finalTargetTabIndex);
                    jumpToIndexInCurrentList(finalTargetItemIndex);
                } else {
                    adapter.setHighlightBvid(null);
                    selectTab(0);
                }
                adapter.notifyDataSetChanged();
                updateSelectionUi();
            });
            return;
        }

        JsonArray pages = json.has("pages") && json.get("pages").isJsonArray() ? json.getAsJsonArray("pages") : null;
        if (pages != null && pages.size() > 1) {
            List<BiliVideoAdapter.BiliVideoItem> items = new ArrayList<>();
            long targetCid = -1;
            int targetItemIndex = -1;
            if (p > 0) {
                int idx = Math.max(0, Math.min(p - 1, pages.size() - 1));
                JsonObject pageObj = pages.get(idx).getAsJsonObject();
                targetCid = pageObj.has("cid") && !pageObj.get("cid").isJsonNull() ? pageObj.get("cid").getAsLong() : -1;
            }
            for (int i = 0; i < pages.size(); i++) {
                JsonObject page = pages.get(i).getAsJsonObject();
                String part = page.has("part") && !page.get("part").isJsonNull() ? page.get("part").getAsString() : "";
                long cid = page.has("cid") && !page.get("cid").isJsonNull() ? page.get("cid").getAsLong() : -1;
                int duration = page.has("duration") && !page.get("duration").isJsonNull() ? page.get("duration").getAsInt() : 0;
                if (cid <= 0) {
                    continue;
                }
                String uniqueId = bvid + "_" + cid;
                String title = currentVideoTitle != null ? currentVideoTitle : "视频";
                if (!part.isEmpty()) {
                    title = title + " - " + part;
                }
                BiliVideoAdapter.BiliVideoItem item = new BiliVideoAdapter.BiliVideoItem(uniqueId, title, upName != null ? upName : "UP主", duration);
                if (targetCid > 0 && cid == targetCid) {
                    item.setSelected(true);
                    adapter.setHighlightBvid(bvid + "_" + targetCid);
                    targetItemIndex = items.size();
                }
                items.add(item);
                coverByUniqueId.put(uniqueId, headerCoverUrl);
            }
            tabs.add(new CollectionTab("分P", items));
            final int finalTargetItemIndex = targetItemIndex;
            runOnUiThread(() -> {
                applyHeader(upName, upUid, upFace);
                setLoading(false);
                showParsedUi();
                setFilterTextSilently("");
                selectTab(0);
                if (finalTargetItemIndex >= 0) {
                    clearAllSelections();
                    if (finalTargetItemIndex < tabs.get(0).items.size()) {
                        tabs.get(0).items.get(finalTargetItemIndex).setSelected(true);
                    }
                    jumpToIndexInCurrentList(finalTargetItemIndex);
                }
                adapter.notifyDataSetChanged();
                updateSelectionUi();
            });
            return;
        }

        long cid = json.has("cid") && !json.get("cid").isJsonNull() ? json.get("cid").getAsLong() : -1;
        if (cid > 0) {
            List<BiliVideoAdapter.BiliVideoItem> items = new ArrayList<>();
            String uniqueId = bvid + "_" + cid;
            String title = currentVideoTitle != null ? currentVideoTitle : bvid;
            BiliVideoAdapter.BiliVideoItem item = new BiliVideoAdapter.BiliVideoItem(uniqueId, title, upName != null ? upName : "UP主", 0);
            item.setSelected(true);
            items.add(item);
            coverByUniqueId.put(uniqueId, headerCoverUrl);
            tabs.add(new CollectionTab("视频", items));
            runOnUiThread(() -> {
                applyHeader(upName, upUid, upFace);
                setLoading(false);
                showParsedUi();
                selectTab(0);
                updateSelectionUi();
            });
            return;
        }

        runOnUiThread(() -> {
            setLoading(false);
            showEmptyHint("解析失败: 缺少CID");
        });
    }

    private void fetchBangumiSeason(@Nullable Long epId, @Nullable Long seasonId) {
        String url = epId != null
                ? ("https://api.bilibili.com/pgc/view/web/season?ep_id=" + epId)
                : ("https://api.bilibili.com/pgc/view/web/season?season_id=" + seasonId);
        Request req = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build();
        httpClient.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showEmptyHint("请求失败: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showEmptyHint("请求失败: " + response.code());
                    });
                    return;
                }
                String jsonStr = response.body().string();
                JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
                int code = root.has("code") ? root.get("code").getAsInt() : -1;
                if (code != 0 || !root.has("result") || root.get("result").isJsonNull()) {
                    String msg = root.has("message") ? root.get("message").getAsString() : "未知错误";
                    runOnUiThread(() -> {
                        setLoading(false);
                        showEmptyHint("番剧解析失败: " + msg);
                    });
                    return;
                }

                JsonObject result = root.getAsJsonObject("result");
                tabs.clear();
                coverByUniqueId.clear();

                headerCoverUrl = result.has("cover") && !result.get("cover").isJsonNull() ? result.get("cover").getAsString() : null;
                currentVideoTitle = result.has("title") && !result.get("title").isJsonNull() ? result.get("title").getAsString() : "番剧";
                String evaluate = result.has("evaluate") && !result.get("evaluate").isJsonNull() ? result.get("evaluate").getAsString() : "";
                currentVideoInfoText = buildBangumiInfoText(currentVideoTitle, result, evaluate);

                List<JsonObject> sectionObjects = new ArrayList<>();
                if (result.has("sections") && result.get("sections").isJsonArray()) {
                    for (JsonElement el : result.getAsJsonArray("sections")) {
                        if (el != null && el.isJsonObject()) {
                            sectionObjects.add(el.getAsJsonObject());
                        }
                    }
                }
                if (result.has("section") && result.get("section").isJsonArray()) {
                    for (JsonElement el : result.getAsJsonArray("section")) {
                        if (el != null && el.isJsonObject()) {
                            sectionObjects.add(el.getAsJsonObject());
                        }
                    }
                }
                if (sectionObjects.isEmpty()) {
                    if (result.has("episodes") && result.get("episodes").isJsonArray()) {
                        JsonObject fake = new JsonObject();
                        fake.addProperty("title", "正片");
                        fake.add("episodes", result.getAsJsonArray("episodes"));
                        sectionObjects.add(fake);
                    }
                }

                for (JsonObject section : sectionObjects) {
                    String secTitle = section.has("title") && !section.get("title").isJsonNull() ? section.get("title").getAsString() : "合集";
                    JsonArray episodes = section.has("episodes") && section.get("episodes").isJsonArray() ? section.getAsJsonArray("episodes") : null;
                    List<BiliVideoAdapter.BiliVideoItem> items = new ArrayList<>();
                    if (episodes != null) {
                        for (JsonElement el : episodes) {
                            if (el == null || !el.isJsonObject()) {
                                continue;
                            }
                            JsonObject ep = el.getAsJsonObject();
                            String bvid = ep.has("bvid") && !ep.get("bvid").isJsonNull() ? ep.get("bvid").getAsString() : null;
                            long cid = ep.has("cid") && !ep.get("cid").isJsonNull() ? ep.get("cid").getAsLong() : -1;
                            long id = ep.has("id") && !ep.get("id").isJsonNull() ? ep.get("id").getAsLong() : -1;
                            String epTitle = ep.has("title") && !ep.get("title").isJsonNull() ? ep.get("title").getAsString() : "";
                            String epLong = ep.has("long_title") && !ep.get("long_title").isJsonNull() ? ep.get("long_title").getAsString() : "";
                            String epCover = ep.has("cover") && !ep.get("cover").isJsonNull() ? ep.get("cover").getAsString() : headerCoverUrl;
                            int duration = 0;
                            if (ep.has("duration") && !ep.get("duration").isJsonNull()) {
                                int d = ep.get("duration").getAsInt();
                                duration = d > 10000 ? Math.max(0, d / 1000) : Math.max(0, d);
                            }
                            if (bvid == null || cid <= 0) {
                                continue;
                            }
                            String uniqueId = bvid + "_" + cid + (id > 0 ? "_ep" + id : "");
                            String title = currentVideoTitle != null ? currentVideoTitle : "番剧";
                            if (!TextUtils.isEmpty(epTitle)) {
                                title = title + " - " + epTitle;
                            }
                            if (!TextUtils.isEmpty(epLong)) {
                                title = title + " " + epLong;
                            }
                            BiliVideoAdapter.BiliVideoItem item = new BiliVideoAdapter.BiliVideoItem(uniqueId, title, "番剧", duration);
                            if (epId != null && id == epId) {
                                item.setSelected(true);
                            }
                            items.add(item);
                            coverByUniqueId.put(uniqueId, epCover);
                        }
                    }
                    tabs.add(new CollectionTab(secTitle, items));
                }

                runOnUiThread(() -> {
                    applyHeader(null, null, null);
                    setLoading(false);
                    showParsedUi();
                    int defaultTab = 0;
                    if (epId != null) {
                        for (int i = 0; i < tabs.size(); i++) {
                            for (BiliVideoAdapter.BiliVideoItem it : tabs.get(i).items) {
                                if (it.isSelected()) {
                                    defaultTab = i;
                                    break;
                                }
                            }
                        }
                    }
                    selectTab(defaultTab);
                    updateSelectionUi();
                });
            }
        });
    }

    private void applyHeader(@Nullable String upName, @Nullable String upUid, @Nullable String upFace) {
        if (userInfoBar != null) {
            if (!TextUtils.isEmpty(upName) || !TextUtils.isEmpty(upUid) || !TextUtils.isEmpty(upFace)) {
                userInfoBar.setVisibility(View.VISIBLE);
            } else {
                userInfoBar.setVisibility(View.GONE);
            }
        }
        if (tvUserName != null && !TextUtils.isEmpty(upName)) {
            tvUserName.setText(upName);
        } else if (tvUserName != null) {
            tvUserName.setText("UP主");
        }
        if (tvUserUid != null) {
            tvUserUid.setText(!TextUtils.isEmpty(upUid) ? ("UID: " + upUid) : "UID: -");
        }
        if (ivUserAvatar != null && !TextUtils.isEmpty(upFace)) {
            MusicCoverUtils.loadCoverFromUrl(upFace, this, ivUserAvatar);
        }

        if (videoInfoCard != null) {
            videoInfoCard.setVisibility(View.VISIBLE);
        }
        if (ivVideoCover != null) {
            if (!TextUtils.isEmpty(headerCoverUrl)) {
                MusicCoverUtils.loadCoverFromUrl(headerCoverUrl, this, ivVideoCover);
            } else {
                ivVideoCover.setImageResource(R.drawable.default_playlist_cover);
            }
        }
        if (tvVideoInfo != null) {
            String text = currentVideoInfoText != null ? currentVideoInfoText : "";
            tvVideoInfo.setText(text);
        }
    }

    private void showParsedUi() {
        if (hsvTabs != null) {
            hsvTabs.setVisibility(View.VISIBLE);
        }
        if (selectionBar != null) {
            selectionBar.setVisibility(View.VISIBLE);
        }
        if (lvItems != null) {
            lvItems.setVisibility(View.VISIBLE);
        }
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.GONE);
        }

        renderTabs();
    }

    private void renderTabs() {
        if (tabsContainer == null) {
            return;
        }
        tabsContainer.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            int idx = i;
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(tabs.get(i).title);
            b.setTextSize(12f);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setPadding(18, 0, 18, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMarginEnd(10);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> selectTab(idx));
            tabsContainer.addView(b);
        }
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        selectedTabIndex = index;
        applyFilterAndReset();
        updateSelectionUi();
    }

    private void applyFilterAndReset() {
        if (selectedTabIndex < 0 || selectedTabIndex >= tabs.size()) {
            return;
        }
        CollectionTab tab = tabs.get(selectedTabIndex);
        String kw = etItemFilter != null ? etItemFilter.getText().toString().trim() : "";
        if (kw.isEmpty()) {
            filteredItems = tab.items;
        } else {
            String low = kw.toLowerCase(Locale.getDefault());
            List<BiliVideoAdapter.BiliVideoItem> out = new ArrayList<>();
            for (BiliVideoAdapter.BiliVideoItem it : tab.items) {
                if (it.getTitle() != null && it.getTitle().toLowerCase(Locale.getDefault()).contains(low)) {
                    out.add(it);
                }
            }
            filteredItems = out;
        }
        adapter.clearItems();
        displayedCount = 0;
        isLoadingMore = false;
        loadNextPage();
    }

    private void loadNextPage() {
        if (isLoadingMore) {
            return;
        }
        if (filteredItems == null) {
            return;
        }
        if (displayedCount >= filteredItems.size()) {
            return;
        }
        isLoadingMore = true;
        int start = displayedCount;
        int end = Math.min(filteredItems.size(), start + PAGE_SIZE);
        if (start < end) {
            adapter.addItems(filteredItems.subList(start, end));
            displayedCount = end;
        }
        isLoadingMore = false;
        updateSelectionUi();
    }

    private void setSelectAllForCurrentFilter(boolean select) {
        if (selectedTabIndex < 0 || selectedTabIndex >= tabs.size()) {
            return;
        }
        CollectionTab tab = tabs.get(selectedTabIndex);
        if (filteredItems == null) {
            return;
        }
        for (BiliVideoAdapter.BiliVideoItem it : tab.items) {
            if (filteredItems.contains(it)) {
                it.setSelected(select);
            }
        }
        adapter.notifyDataSetChanged();
        updateSelectionUi();
    }

    private boolean isCurrentFilterAllSelected() {
        if (selectedTabIndex < 0 || selectedTabIndex >= tabs.size()) {
            return false;
        }
        if (filteredItems == null || filteredItems.isEmpty()) {
            return false;
        }
        for (BiliVideoAdapter.BiliVideoItem it : filteredItems) {
            if (!it.isSelected()) {
                return false;
            }
        }
        return true;
    }

    private int getAllSelectedCount() {
        int cnt = 0;
        for (CollectionTab tab : tabs) {
            for (BiliVideoAdapter.BiliVideoItem it : tab.items) {
                if (it.isSelected()) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    private void updateSelectionUi() {
        int totalSelected = getAllSelectedCount();
        if (tvSelectedCount != null) {
            if (selectedTabIndex >= 0 && selectedTabIndex < tabs.size()) {
                int tabSelected = 0;
                for (BiliVideoAdapter.BiliVideoItem it : tabs.get(selectedTabIndex).items) {
                    if (it.isSelected()) {
                        tabSelected++;
                    }
                }
                tvSelectedCount.setText(String.format(Locale.getDefault(), "已选择: %d（当前合集 %d）", totalSelected, tabSelected));
            } else {
                tvSelectedCount.setText(String.format(Locale.getDefault(), "已选择: %d", totalSelected));
            }
        }
        if (cbSelectAll != null) {
            cbSelectAll.setOnCheckedChangeListener(null);
            cbSelectAll.setChecked(isCurrentFilterAllSelected());
            cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> setSelectAllForCurrentFilter(isChecked));
        }
        if (btnConfirm != null) {
            btnConfirm.setEnabled(totalSelected > 0);
        }
    }

    private SelectionPayload collectSelections() {
        SelectionPayload out = new SelectionPayload();
        for (CollectionTab tab : tabs) {
            for (BiliVideoAdapter.BiliVideoItem it : tab.items) {
                if (!it.isSelected()) {
                    continue;
                }
                String uniqueId = it.getBvid();
                out.ids.add(uniqueId);
                out.titles.add(it.getTitle());
                out.covers.add(coverByUniqueId.get(uniqueId));
                out.epIds.add(extractEpIdFromUniqueId(uniqueId));
            }
        }
        return out;
    }

    private long[] toLongArray(List<Long> list) {
        long[] out = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Long v = list.get(i);
            out[i] = v != null ? v : -1;
        }
        return out;
    }

    private long extractEpIdFromUniqueId(String uniqueId) {
        if (uniqueId == null) {
            return -1;
        }
        String[] parts = uniqueId.split("_");
        if (parts.length >= 3 && parts[2] != null && parts[2].startsWith("ep")) {
            return safeLong(parts[2].substring(2));
        }
        return -1;
    }

    private void preselectByPIndex(int p) {
        if (selectedTabIndex < 0 || selectedTabIndex >= tabs.size()) {
            return;
        }
        CollectionTab tab = tabs.get(selectedTabIndex);
        int idx = Math.max(0, Math.min(p - 1, tab.items.size() - 1));
        for (int i = 0; i < tab.items.size(); i++) {
            tab.items.get(i).setSelected(i == idx);
        }
        adapter.notifyDataSetChanged();
        jumpToIndexInCurrentList(idx);
        updateSelectionUi();
    }

    private void setFilterTextSilently(String text) {
        if (etItemFilter == null) {
            return;
        }
        suppressFilterApply = true;
        etItemFilter.setText(text != null ? text : "");
        suppressFilterApply = false;
    }

    private void clearAllSelections() {
        for (CollectionTab tab : tabs) {
            for (BiliVideoAdapter.BiliVideoItem it : tab.items) {
                it.setSelected(false);
            }
        }
    }

    private void jumpToIndexInCurrentList(int indexInTabItems) {
        if (selectedTabIndex < 0 || selectedTabIndex >= tabs.size()) {
            return;
        }
        if (filteredItems == null) {
            return;
        }
        int idx = Math.max(0, Math.min(indexInTabItems, filteredItems.size() - 1));
        ensureLoadedForIndex(idx);
        if (lvItems == null) {
            return;
        }
        lvItems.post(() -> {
            int max = Math.max(0, adapter.getCount() - 1);
            int pos = Math.max(0, Math.min(idx, max));
            int near = Math.max(0, pos - 3);
            lvItems.setSelection(near);
        });
    }

    private void ensureLoadedForIndex(int index) {
        if (filteredItems == null) {
            return;
        }
        int idx = Math.max(0, Math.min(index, filteredItems.size() - 1));
        int needEnd = Math.min(filteredItems.size(), ((idx / PAGE_SIZE) + 1) * PAGE_SIZE);
        if (displayedCount >= needEnd) {
            return;
        }
        if (isLoadingMore) {
            return;
        }
        isLoadingMore = true;
        int start = displayedCount;
        int end = needEnd;
        if (start < end) {
            adapter.addItems(filteredItems.subList(start, end));
            displayedCount = end;
        }
        isLoadingMore = false;
        updateSelectionUi();
    }

    private void clearAllUi() {
        tabs.clear();
        selectedTabIndex = -1;
        headerCoverUrl = null;
        currentVideoTitle = null;
        currentVideoInfoText = null;
        coverByUniqueId.clear();
        filteredItems = new ArrayList<>();
        adapter.clearItems();
        displayedCount = 0;
        setFilterTextSilently("");
        if (userInfoBar != null) {
            userInfoBar.setVisibility(View.GONE);
        }
        if (videoInfoCard != null) {
            videoInfoCard.setVisibility(View.GONE);
        }
        if (hsvTabs != null) {
            hsvTabs.setVisibility(View.GONE);
        }
        if (selectionBar != null) {
            selectionBar.setVisibility(View.GONE);
        }
        if (lvItems != null) {
            lvItems.setVisibility(View.GONE);
        }
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.GONE);
        }
        if (btnConfirm != null) {
            btnConfirm.setEnabled(false);
        }
    }

    private void showEmptyHint(String text) {
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(text);
        }
        if (lvItems != null) {
            lvItems.setVisibility(View.GONE);
        }
        if (hsvTabs != null) {
            hsvTabs.setVisibility(View.GONE);
        }
        if (selectionBar != null) {
            selectionBar.setVisibility(View.GONE);
        }
        if (videoInfoCard != null) {
            videoInfoCard.setVisibility(View.GONE);
        }
    }

    private void setLoading(boolean loading) {
        runOnUiThread(() -> {
            if (progress != null) {
                progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void resolveFinalUrl(String url, ResultCallback<String> callback) {
        OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build();
        resolveFinalUrlWithMethod(client, url, true, callback);
    }

    private void resolveFinalUrlWithMethod(OkHttpClient client, String url, boolean headFirst, ResultCallback<String> callback) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/");
        if (headFirst) {
            b.head();
        } else {
            b.get();
        }
        Request req = b.build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                callback.onResult(null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    if (headFirst) {
                        int code = response.code();
                        if (code == 403 || code == 405 || code == 400) {
                            response.close();
                            resolveFinalUrlWithMethod(client, url, false, callback);
                            return;
                        }
                    }
                    String finalUrl = response.request().url().toString();
                    callback.onResult(finalUrl);
                } catch (Exception e) {
                    callback.onResult(null);
                } finally {
                    try {
                        response.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private Long extractBangumiEpId(String path) {
        if (path == null) {
            return null;
        }
        Matcher m = Pattern.compile("/bangumi/play/ep(\\d+)").matcher(path);
        if (m.find()) {
            return safeLong(m.group(1));
        }
        return null;
    }

    private Long extractBangumiSeasonId(String path) {
        if (path == null) {
            return null;
        }
        Matcher m = Pattern.compile("/bangumi/play/ss(\\d+)").matcher(path);
        if (m.find()) {
            return safeLong(m.group(1));
        }
        return null;
    }

    private long safeLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String buildInfoText(String title, String tname, String pubdate, JsonObject json, String desc) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(title)) {
            sb.append(title).append("\n");
        }
        if (!TextUtils.isEmpty(tname)) {
            sb.append(tname);
        }
        if (!TextUtils.isEmpty(pubdate)) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(pubdate);
        }
        sb.append("\n");
        if (json != null && json.has("stat") && json.get("stat").isJsonObject()) {
            JsonObject stat = json.getAsJsonObject("stat");
            String view = stat.has("view") ? stat.get("view").getAsString() : null;
            String danmaku = stat.has("danmaku") ? stat.get("danmaku").getAsString() : null;
            String like = stat.has("like") ? stat.get("like").getAsString() : null;
            String coin = stat.has("coin") ? stat.get("coin").getAsString() : null;
            String fav = stat.has("favorite") ? stat.get("favorite").getAsString() : null;
            String share = stat.has("share") ? stat.get("share").getAsString() : null;
            String reply = stat.has("reply") ? stat.get("reply").getAsString() : null;
            if (view != null) {
                sb.append(view).append("播放");
            }
            if (danmaku != null) {
                sb.append(danmaku).append("弹幕");
            }
            if (like != null) {
                sb.append(like).append("点赞");
            }
            if (coin != null) {
                sb.append(coin).append("投币");
            }
            if (fav != null) {
                sb.append(fav).append("收藏");
            }
            if (share != null) {
                sb.append(share).append("分享");
            }
            if (reply != null) {
                sb.append(reply).append("评论");
            }
            sb.append("\n");
        }
        if (!TextUtils.isEmpty(desc)) {
            sb.append("\n").append(desc);
        }
        return sb.toString();
    }

    private String buildBangumiInfoText(String title, JsonObject result, String evaluate) {
        StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(title)) {
            sb.append(title).append("\n");
        }
        if (result != null) {
            if (result.has("new_ep") && result.get("new_ep").isJsonObject()) {
                JsonObject newEp = result.getAsJsonObject("new_ep");
                String idx = newEp.has("index_show") && !newEp.get("index_show").isJsonNull() ? newEp.get("index_show").getAsString() : null;
                if (!TextUtils.isEmpty(idx)) {
                    sb.append(idx).append("\n");
                }
            }
            if (result.has("stat") && result.get("stat").isJsonObject()) {
                JsonObject stat = result.getAsJsonObject("stat");
                String views = stat.has("views") && !stat.get("views").isJsonNull() ? stat.get("views").getAsString() : null;
                String danmaku = stat.has("danmakus") && !stat.get("danmakus").isJsonNull() ? stat.get("danmakus").getAsString() : null;
                if (!TextUtils.isEmpty(views)) {
                    sb.append(views).append("播放");
                }
                if (!TextUtils.isEmpty(danmaku)) {
                    sb.append(danmaku).append("弹幕");
                }
                sb.append("\n");
            }
        }
        if (!TextUtils.isEmpty(evaluate)) {
            sb.append("\n").append(evaluate);
        }
        return sb.toString();
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
                    }
                } else {
                    ivBgBlur.setImageDrawable(null);
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

    private static final class CollectionTab {
        final String title;
        final List<BiliVideoAdapter.BiliVideoItem> items;

        CollectionTab(String title, List<BiliVideoAdapter.BiliVideoItem> items) {
            this.title = title;
            this.items = items;
        }
    }

    private static final class SelectionPayload {
        final ArrayList<String> ids = new ArrayList<>();
        final ArrayList<String> titles = new ArrayList<>();
        final ArrayList<String> covers = new ArrayList<>();
        final ArrayList<Long> epIds = new ArrayList<>();
    }
}
