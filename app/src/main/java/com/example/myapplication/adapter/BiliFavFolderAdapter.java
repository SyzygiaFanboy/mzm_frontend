package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MusicCoverUtils;
import com.example.myapplication.R;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BiliFavFolderAdapter extends RecyclerView.Adapter<BiliFavFolderAdapter.VH> {

    public interface OnFolderClickListener {
        void onFolderClick(FavFolder folder);
    }

    public static class FavFolder {
        public final String mediaId;
        public final String title;
        public final int mediaCount;
        public String coverUrl;
        public String upName;
        public String upUid;
        public String upFace;
        public boolean detailFetched;
        public boolean fetching;

        public FavFolder(String mediaId, String title, int mediaCount) {
            this.mediaId = mediaId;
            this.title = title;
            this.mediaCount = mediaCount;
        }
    }

    private final Context context;
    private final OnFolderClickListener listener;
    private final OkHttpClient client = new OkHttpClient();
    private final List<FavFolder> folders = new ArrayList<>();

    public BiliFavFolderAdapter(Context context, OnFolderClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void submitList(List<FavFolder> list) {
        folders.clear();
        if (list != null) {
            folders.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_bili_fav_folder, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FavFolder f = folders.get(position);
        holder.tvTitle.setText(f.title);
        holder.tvCount.setText(f.mediaCount >= 0 ? ("包含视频: " + f.mediaCount) : "包含视频: -");
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onFolderClick(f);
            }
        });

        if (f.coverUrl != null && !f.coverUrl.isEmpty()) {
            MusicCoverUtils.loadCoverFromUrl(f.coverUrl, context, holder.ivCover);
        } else {
            holder.ivCover.setImageResource(R.drawable.default_playlist_cover);
            if (!f.detailFetched && !f.fetching) {
                fetchFolderInfo(f, holder.getBindingAdapterPosition());
            }
        }
    }

    @Override
    public int getItemCount() {
        return folders.size();
    }

    private void fetchFolderInfo(FavFolder folder, int position) {
        if (position < 0) {
            return;
        }
        folder.fetching = true;
        Request req = new Request.Builder()
                .url("https://api.bilibili.com/x/v3/fav/folder/info?media_id=" + folder.mediaId)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .addHeader("Accept", "application/json")
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                folder.fetching = false;
                folder.detailFetched = true;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                folder.fetching = false;
                folder.detailFetched = true;
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                String json = response.body().string();
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                int code = obj.has("code") ? obj.get("code").getAsInt() : -1;
                if (code != 0 || !obj.has("data") || obj.get("data").isJsonNull()) {
                    return;
                }
                JsonObject data = obj.getAsJsonObject("data");
                String cover = data.has("cover") && !data.get("cover").isJsonNull() ? data.get("cover").getAsString() : null;
                folder.coverUrl = cover;
                if (data.has("upper") && !data.get("upper").isJsonNull()) {
                    JsonObject upper = data.getAsJsonObject("upper");
                    folder.upName = upper.has("name") && !upper.get("name").isJsonNull() ? upper.get("name").getAsString() : null;
                    folder.upUid = upper.has("mid") && !upper.get("mid").isJsonNull() ? upper.get("mid").getAsString() : null;
                    folder.upFace = upper.has("face") && !upper.get("face").isJsonNull() ? upper.get("face").getAsString() : null;
                }
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> notifyItemChanged(position));
                }
            }
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView ivCover;
        final TextView tvTitle;
        final TextView tvCount;

        VH(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivFolderCover);
            tvTitle = itemView.findViewById(R.id.tvFolderTitle);
            tvCount = itemView.findViewById(R.id.tvFolderCount);
        }
    }
}

