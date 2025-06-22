package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;
import com.example.myapplication.model.SelectedSong;

import java.util.List;

public class SelectedSongAdapter extends RecyclerView.Adapter<SelectedSongAdapter.ViewHolder> {
    private Context context;
    private List<SelectedSong> songs;
    private OnRemoveClickListener onRemoveClickListener;
    
    public interface OnRemoveClickListener {
        void onRemoveClick(int position);
    }
    
    public SelectedSongAdapter(Context context, List<SelectedSong> songs, OnRemoveClickListener listener) {
        this.context = context;
        this.songs = songs;
        this.onRemoveClickListener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_selected_song, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SelectedSong song = songs.get(position);
        
        holder.tvSongName.setText(song.getSongName());
        holder.tvArtist.setText(song.getArtist());
        
        holder.btnRemove.setOnClickListener(v -> {
            if (onRemoveClickListener != null) {
                onRemoveClickListener.onRemoveClick(position);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return songs.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivSongIcon;
        TextView tvSongName;
        TextView tvArtist;
        ImageButton btnRemove;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSongIcon = itemView.findViewById(R.id.ivSongIcon);
            tvSongName = itemView.findViewById(R.id.tvSongName);
            tvArtist = itemView.findViewById(R.id.tvArtist);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}