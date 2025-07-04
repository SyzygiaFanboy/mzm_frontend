package com.example.myapplication.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.MusicCoverUtils;
import com.example.myapplication.R;
import com.example.myapplication.model.Playlist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistRecyclerAdapter extends RecyclerView.Adapter<PlaylistRecyclerAdapter.ViewHolder> {
    private Context context;
    private List<Playlist> playlists;
    private boolean inManageMode = false;
    private List<Boolean> selectedList;
    private Runnable onSelectionChanged;
    private OnItemClickListener onItemClickListener;
    private OnStartDragListener onStartDragListener;
    // 添加正在播放歌单的标记
    private String currentPlayingPlaylist = null;

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnStartDragListener {
        void onStartDrag(ViewHolder holder);
    }

    public PlaylistRecyclerAdapter(Context context, List<Playlist> playlists, OnStartDragListener dragListener) {
        this.context = context;
        this.playlists = playlists;
        this.selectedList = new ArrayList<>();
        this.onStartDragListener = dragListener;
        for (int i = 0; i < playlists.size(); i++) {
            selectedList.add(false);
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setManageMode(boolean enabled) {
        inManageMode = enabled;
        notifyDataSetChanged();
    }

    public void toggleSelected(int position) {
        selectedList.set(position, !selectedList.get(position));
        if (onSelectionChanged != null) onSelectionChanged.run();
        notifyDataSetChanged();
    }

    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
    }

    public void selectAll() {
        for (int i = 0; i < selectedList.size(); i++) {
            selectedList.set(i, true);
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        for (int i = 0; i < selectedList.size(); i++) {
            selectedList.set(i, false);
        }
        notifyDataSetChanged();
    }

    public List<Integer> getSelectedIndices() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < selectedList.size(); i++) {
            if (selectedList.get(i)) {
                result.add(i);
            }
        }
        return result;
    }

    public boolean isManageMode() {
        return inManageMode;
    }

    public void removeAt(int index) {
        playlists.remove(index);
        selectedList.remove(index);
        notifyDataSetChanged();
    }

    public void addPlaylist(Playlist playlist) {
        playlists.add(playlist);
        selectedList.add(false);  // 确保同步增长
        notifyDataSetChanged();
    }

    // 交换位置的方法，用于拖拽排序
    public void swapItems(int fromPosition, int toPosition) {
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(playlists, i, i + 1);
                Collections.swap(selectedList, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(playlists, i, i - 1);
                Collections.swap(selectedList, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_playlist, parent, false);
        return new ViewHolder(view);
    }

    // 添加设置当前播放歌单的方法
    public void setCurrentPlayingPlaylist(String playlistName) {
        this.currentPlayingPlaylist = playlistName;
        notifyDataSetChanged();
    }

    // 获取当前播放歌单
    public String getCurrentPlayingPlaylist() {
        return currentPlayingPlaylist;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
    
        String coverPath = playlist.getLatestCoverPath();
        if (coverPath != null && !coverPath.isEmpty()) {
            Bitmap coverBitmap = MusicCoverUtils.getCoverFromFile(coverPath, context);
            if (coverBitmap != null) {
                holder.ivCover.setImageBitmap(coverBitmap);
            } else {
                holder.ivCover.setImageResource(playlist.getCoverRes());
            }
        } else {
            holder.ivCover.setImageResource(playlist.getCoverRes());
        }
        holder.tvName.setText(playlist.getName());
        holder.tvCount.setText("歌曲数：" + playlist.getSongCount());
    
        holder.cbSelect.setVisibility(inManageMode ? View.VISIBLE : View.GONE);
        holder.cbSelect.setChecked(selectedList.get(position));
    
        holder.ivHandle.setVisibility(inManageMode ? View.VISIBLE : View.GONE);
    
        // 添加正在播放歌单的高亮效果
        if (currentPlayingPlaylist != null && currentPlayingPlaylist.equals(playlist.getName())) {
            holder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#c69bc5"));
            holder.tvName.setTextColor(context.getResources().getColor(android.R.color.white, null));
            holder.tvCount.setTextColor(context.getResources().getColor(android.R.color.white, null));
            // 设置拖拽按钮为白色，避免高亮颜色覆盖
            if (inManageMode) {
                holder.ivHandle.setColorFilter(context.getResources().getColor(android.R.color.white, null));
            }
        } else {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true);
            holder.itemView.setBackgroundResource(typedValue.resourceId);
            
            // 使用正确的方式获取默认文字颜色
            android.util.TypedValue textColorValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, textColorValue, true);
            int primaryColor = context.getResources().getColor(textColorValue.resourceId, context.getTheme());
            holder.tvName.setTextColor(primaryColor);
            
            context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, textColorValue, true);
            int secondaryColor = context.getResources().getColor(textColorValue.resourceId, context.getTheme());
            holder.tvCount.setTextColor(secondaryColor);
            // 恢复拖拽按钮的默认颜色
            if (inManageMode) {
                holder.ivHandle.clearColorFilter();
            }
        }

        holder.cbSelect.setOnClickListener(v -> toggleSelected(position));

        // 设置拖拽监听
        holder.ivHandle.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && inManageMode) {
                onStartDragListener.onStartDrag(holder);
            }
            return false;
        });

        // 设置点击监听
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null && !inManageMode) {
                onItemClickListener.onItemClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvName;
        TextView tvCount;
        ImageView ivHandle;
        CheckBox cbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.playlistCover);
            tvName = itemView.findViewById(R.id.playlistName);
            tvCount = itemView.findViewById(R.id.songCount);
            ivHandle = itemView.findViewById(R.id.imgHandle);
            cbSelect = itemView.findViewById(R.id.cbSelect);
        }
    }
}