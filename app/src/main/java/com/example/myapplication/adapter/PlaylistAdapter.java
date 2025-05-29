package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.BaseAdapter;

import com.example.myapplication.model.Playlist;
import com.example.myapplication.R;

import java.util.List;


public class PlaylistAdapter extends BaseAdapter {
    private Context context;
    private List<Playlist> playlists;

    public PlaylistAdapter(Context context, List<Playlist> playlists) {
        this.context = context;
        this.playlists = playlists;
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

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // 绑定自定义布局 item_playlist
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_playlist, parent, false);
        }

        Playlist playlist = playlists.get(position);

        ImageView ivCover = convertView.findViewById(R.id.playlistCover);
        TextView tvName = convertView.findViewById(R.id.playlistName);
        TextView tvSongCount = convertView.findViewById(R.id.songCount);

        ivCover.setImageResource(playlist.getCoverRes());
        tvName.setText(playlist.getName());
        tvSongCount.setText("歌曲数：" + playlist.getSongCount());

        return convertView;
    }
}