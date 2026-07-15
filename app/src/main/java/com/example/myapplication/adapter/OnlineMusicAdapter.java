package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MusicCoverUtils;
import com.example.myapplication.R;
import com.example.myapplication.model.Songinf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OnlineMusicAdapter extends RecyclerView.Adapter<OnlineMusicAdapter.ViewHolder> {

    public interface Listener {
        void onClick(int position);

        void onLongPress(int position);

        void onSelectionChanged(int selectedCount);
    }

    public interface SongUrlProvider {
        String getSongUrl(Songinf song);
    }

    private final Context context;
    private final Listener listener;
    private final List<Songinf> items = new ArrayList<>();
    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();
    private SongUrlProvider urlProvider;
    private String currentPlayingSongId;
    private String currentPlayingSongUrl;
    private String currentQueuePlaylist;

    public OnlineMusicAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setSongUrlProvider(SongUrlProvider provider) {
        this.urlProvider = provider;
    }

    public void setCurrentPlaying(String queuePlaylist, String songId, String songUrl) {
        this.currentQueuePlaylist = queuePlaylist;
        this.currentPlayingSongId = songId;
        this.currentPlayingSongUrl = songUrl;
        notifyDataSetChanged();
    }

    public void submitList(List<Songinf> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        syncSelectedState();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void appendList(List<Songinf> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        int start = items.size();
        items.addAll(list);
        syncSelectedState();
        notifyItemRangeInserted(start, list.size());
    }

    public List<Songinf> getItems() {
        return new ArrayList<>(items);
    }

    public void setSelectionMode(boolean enable) {
        if (selectionMode == enable) {
            return;
        }
        selectionMode = enable;
        if (!selectionMode) {
            selectedIds.clear();
        }
        syncSelectedState();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void toggleSelected(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        Songinf it = items.get(position);
        String id = it.getSongid();
        if (id == null) {
            return;
        }
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
            it.setSelected(false);
        } else {
            selectedIds.add(id);
            it.setSelected(true);
        }
        notifyItemChanged(position);
        notifySelectionChanged();
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public List<Songinf> getSelectedItems() {
        List<Songinf> out = new ArrayList<>();
        for (Songinf it : items) {
            if (it.isSelected()) {
                out.add(it);
            }
        }
        return out;
    }

    public void selectAll(boolean select) {
        if (!selectionMode) {
            return;
        }
        selectedIds.clear();
        for (Songinf it : items) {
            it.setSelected(select);
            if (select && it.getSongid() != null) {
                selectedIds.add(it.getSongid());
            }
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public boolean isAllSelected() {
        if (!selectionMode || items.isEmpty()) {
            return false;
        }
        for (Songinf it : items) {
            if (!it.isSelected()) {
                return false;
            }
        }
        return true;
    }

    private void syncSelectedState() {
        for (Songinf it : items) {
            String id = it.getSongid();
            it.setSelected(id != null && selectedIds.contains(id));
        }
    }

    private void notifySelectionChanged() {
        if (listener != null) {
            listener.onSelectionChanged(getSelectedCount());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_online_music, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Songinf song = items.get(position);
        holder.tvTitle.setText(song.getSongname());
        holder.tvSubtitle.setText(song.getMusician());
        holder.tvDuration.setText(formatDuration(song.getSongduration()));
        holder.tvPlayCount.setText("热度：" + song.getPlayCount());

        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.cbSelect.setChecked(song.isSelected());
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!selectionMode) {
                return;
            }
            toggleSelected(holder.getBindingAdapterPosition());
        });

        String songUrl = null;
        if (urlProvider != null) {
            songUrl = urlProvider.getSongUrl(song);
        }
        MusicCoverUtils.loadCoverSmart(songUrl, song.getCoverUrl(), context, holder.ivCover);

        boolean isOnlineQueue = "在线音乐".equals(currentQueuePlaylist);
        boolean isCurrent = false;
        if (isOnlineQueue) {
            if (song.getSongid() != null && song.getSongid().equals(currentPlayingSongId)) {
                isCurrent = true;
            } else if (songUrl != null && songUrl.equals(currentPlayingSongUrl)) {
                isCurrent = true;
            }
        }
        holder.itemView.setBackgroundColor(isCurrent ? 0x66c69bc5 : 0x22000000);
        holder.tvTitle.setTextColor(isCurrent ? 0xFFFFFFFF : 0xFFFFFFFF);
        holder.tvSubtitle.setTextColor(isCurrent ? 0xFFFFFFFF : 0xE6FFFFFF);

        holder.itemView.setOnClickListener(v -> {
            if (listener == null) {
                return;
            }
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            if (selectionMode) {
                toggleSelected(pos);
            } else {
                listener.onClick(pos);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener == null) {
                return false;
            }
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) {
                return false;
            }
            listener.onLongPress(pos);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatDuration(int seconds) {
        int m = Math.max(0, seconds) / 60;
        int s = Math.max(0, seconds) % 60;
        return String.format("%d:%02d", m, s);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        ImageView ivCover;
        TextView tvTitle;
        TextView tvSubtitle;
        TextView tvDuration;
        TextView tvPlayCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvPlayCount = itemView.findViewById(R.id.tvPlayCount);
        }
    }
}
