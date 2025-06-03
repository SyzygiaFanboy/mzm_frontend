package com.example.myapplication.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.BaseAdapter;

import com.example.myapplication.MusicCoverUtils;
import com.example.myapplication.model.Playlist;
import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;


public class PlaylistAdapter extends BaseAdapter {
    private Context context;
    private List<Playlist> playlists;
    private boolean inManageMode = false;
    private List<Boolean> selectedList; // 标记选中状态
    private Runnable onSelectionChanged;
    public PlaylistAdapter(Context context, List<Playlist> playlists) {
        this.context = context;
        this.playlists = playlists;
        this.selectedList = new ArrayList<>();
        for (int i = 0; i < playlists.size(); i++) {
            selectedList.add(false);
        }
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

    @Override
    public int getCount() {
        return playlists.size();
    }

    @Override
    public Object getItem(int position) {
        return playlists.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void addPlaylist(Playlist playlist) {
        playlists.add(playlist);
        selectedList.add(false);  // 确保同步增长
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = LayoutInflater.from(context).inflate(R.layout.item_playlist, parent, false);

        Playlist playlist = playlists.get(position);

        ImageView ivCover = convertView.findViewById(R.id.playlistCover);
        TextView tvName = convertView.findViewById(R.id.playlistName);
        TextView tvCount = convertView.findViewById(R.id.songCount);
        ImageView ivHandle = convertView.findViewById(R.id.imgHandle);
        CheckBox cbSelect = convertView.findViewById(R.id.cbSelect);

        String coverPath = playlist.getLatestCoverPath();
        if (coverPath != null && !coverPath.isEmpty()) {
            Bitmap coverBitmap = MusicCoverUtils.getCoverFromFile(coverPath, context);
            if (coverBitmap != null) {
                ivCover.setImageBitmap(coverBitmap);
            } else {
                ivCover.setImageResource(playlist.getCoverRes());
            }
        } else {
            ivCover.setImageResource(playlist.getCoverRes());
        }
        tvName.setText(playlist.getName());
        tvCount.setText("歌曲数：" + playlist.getSongCount());

        cbSelect.setVisibility(inManageMode ? View.VISIBLE : View.GONE);
        cbSelect.setChecked(selectedList.get(position));

        ivHandle.setVisibility(inManageMode ? View.VISIBLE : View.GONE);

        cbSelect.setOnClickListener(v -> toggleSelected(position));
        Log.d("PlaylistAdapter", "封面路径: " + playlist.getLatestCoverPath());
        return convertView;
    }


    // 可扩展拖动排序函数（后面再加）
}
