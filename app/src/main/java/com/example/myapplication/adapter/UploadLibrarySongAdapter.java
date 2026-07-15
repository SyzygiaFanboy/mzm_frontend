package com.example.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UploadLibrarySongAdapter extends RecyclerView.Adapter<UploadLibrarySongAdapter.ViewHolder> {

    public interface OnSelectionChangedListener {
        void onItemSelectionChanged(LibrarySong item, boolean selected);
    }

    private final List<LibrarySong> items = new ArrayList<>();
    private final Map<String, Integer> positionByKey = new HashMap<>();
    private OnSelectionChangedListener selectionChangedListener;

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void submitList(List<LibrarySong> list) {
        items.clear();
        positionByKey.clear();
        if (list != null) {
            items.addAll(list);
        }
        for (int i = 0; i < items.size(); i++) {
            LibrarySong it = items.get(i);
            if (it.key != null) {
                positionByKey.put(it.key, i);
            }
        }
        notifyDataSetChanged();
    }

    public int getSelectedCount() {
        int count = 0;
        for (LibrarySong it : items) {
            if (it.selected) {
                count++;
            }
        }
        return count;
    }

    public boolean isAllSelected() {
        if (items.isEmpty()) {
            return false;
        }
        for (LibrarySong it : items) {
            if (!it.selected) {
                return false;
            }
        }
        return true;
    }

    public void selectAll(boolean select) {
        for (LibrarySong it : items) {
            it.selected = select;
            if (selectionChangedListener != null) {
                selectionChangedListener.onItemSelectionChanged(it, select);
            }
        }
        notifyDataSetChanged();
    }

    public void setSelectedByKey(String key, boolean selected) {
        Integer pos = positionByKey.get(key);
        if (pos == null) {
            return;
        }
        LibrarySong it = items.get(pos);
        if (it.selected == selected) {
            return;
        }
        it.selected = selected;
        notifyItemChanged(pos);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_upload_library_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LibrarySong item = items.get(position);
        holder.tvTitle.setText(item.title);
        holder.tvSubtitle.setText(item.subtitle);
        holder.tvDuration.setText(item.durationText);

        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(item.selected);
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> setSelected(position, isChecked));

        holder.itemView.setOnClickListener(v -> {
            boolean newState = !item.selected;
            holder.cbSelect.setChecked(newState);
            setSelected(position, newState);
        });
    }

    private void setSelected(int position, boolean selected) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        LibrarySong it = items.get(position);
        if (it.selected == selected) {
            return;
        }
        it.selected = selected;
        if (selectionChangedListener != null) {
            selectionChangedListener.onItemSelectionChanged(it, selected);
        }
        notifyItemChanged(position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        TextView tvTitle;
        TextView tvSubtitle;
        TextView tvDuration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cbSelect);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            tvDuration = itemView.findViewById(R.id.tvDuration);
        }
    }

    public static class LibrarySong {
        public final String key;
        public final String title;
        public final String subtitle;
        public final String durationText;
        public final String artistForUpload;
        public final int durationMsForUpload;
        public final String filePathForUpload;
        public final String uriStringForUpload;
        public final String coverUrlForPreview;
        public boolean selected;

        public LibrarySong(
                String key,
                String title,
                String subtitle,
                String durationText,
                String artistForUpload,
                int durationMsForUpload,
                String filePathForUpload,
                String uriStringForUpload,
                String coverUrlForPreview
        ) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.durationText = durationText;
            this.artistForUpload = artistForUpload;
            this.durationMsForUpload = durationMsForUpload;
            this.filePathForUpload = filePathForUpload;
            this.uriStringForUpload = uriStringForUpload;
            this.coverUrlForPreview = coverUrlForPreview;
            this.selected = false;
        }
    }
}
