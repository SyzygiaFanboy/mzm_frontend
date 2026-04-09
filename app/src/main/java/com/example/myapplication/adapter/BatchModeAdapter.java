package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.myapplication.MusicCoverUtils;
import com.example.myapplication.R;

import java.util.List;
import java.util.Map;

//复选模式适配器
public class BatchModeAdapter extends android.widget.BaseAdapter {

    public interface OnSelectAllListener {
        void onSelectAllChanged(boolean shouldSelectAll);
    }

    private OnSelectAllListener selectAllListener;
    private List<Map<String, Object>> data;
    private boolean isBatchMode;
    private final LayoutInflater inflater;
    private final Context context;

    // 监听器的方法
    public void setOnSelectAllListener(OnSelectAllListener listener) {
        this.selectAllListener = listener;
    }

    public BatchModeAdapter(Context context, List<Map<String, Object>> data, int resource, String[] from, int[] to) {
        this.data = data;
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    public void setData(List<Map<String, Object>> newData) {
        this.data = newData;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return data != null ? data.size() : 0;
    }

    @Override
    public Object getItem(int i) {
        if (data == null || i < 0 || i >= data.size()) {
            return null;
        }
        return data.get(i);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (data == null || position < 0 || position >= data.size()) {
            if (convertView != null) {
                return convertView;
            }
            return inflater.inflate(R.layout.playlist_layout, parent, false);
        }

        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.playlist_layout, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Map<String, Object> item = data.get(position);

        String filePath = null;
        Object filePathObj = item.get("filePath");
        if (filePathObj != null) {
            filePath = String.valueOf(filePathObj);
        }

        String coverUrl = null;
        Object coverUrlObj = item.get("coverUrl");
        if (coverUrlObj != null) {
            coverUrl = String.valueOf(coverUrlObj);
        }

        if (filePath == null || filePath.isEmpty()) {
            holder.ivCover.setImageResource(R.drawable.default_cover);
        } else {
            MusicCoverUtils.loadCoverSmart(filePath, coverUrl, context, holder.ivCover);
        }

        Object nameObj = item.get("name");
        String fullName = nameObj != null ? String.valueOf(nameObj) : "";
        String songName = fullName;
        String artistName = "";
        int splitIndex = fullName.indexOf(" - ");
        if (splitIndex > 0) {
            artistName = fullName.substring(0, splitIndex).trim();
            songName = fullName.substring(splitIndex + 3).trim();
        }
        holder.tvName.setText(songName);
        holder.tvArtist.setText(artistName);

        Object durationObj = item.get("TimeDuration");
        holder.tvDuration.setText(durationObj != null ? String.valueOf(durationObj) : "");

        holder.cbItem.setOnCheckedChangeListener(null);
        holder.cbItem.setVisibility(isBatchMode ? View.VISIBLE : View.GONE);
        Boolean isSelected = (Boolean) item.get("isSelected");
        holder.cbItem.setChecked(isSelected != null && isSelected);

        if (isBatchMode) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.tvName.getLayoutParams();
            params.horizontalBias = 0f;
            holder.tvName.setLayoutParams(params);
        }

        holder.cbItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (data != null && position >= 0 && position < data.size()) {
                data.get(position).put("isSelected", isChecked);
                if (selectAllListener != null) {
                    selectAllListener.onSelectAllChanged(shouldSelectAll());
                }
            }
        });

        return convertView;
    }


    private boolean shouldSelectAll() {
        if (data == null || data.isEmpty()) {
            return false;
        }
        for (Map<String, Object> item : data) {
            Boolean isSelected = (Boolean) item.get("isSelected");
            if (isSelected == null || !isSelected) {
                return false;
            }
        }
        return true;
    }

    // 设置批量模式
    public void setBatchMode(boolean isBatchMode) {
        this.isBatchMode = isBatchMode;
        notifyDataSetChanged();
    }

    private static final class ViewHolder {
        final CheckBox cbItem;
        final ImageView ivCover;
        final TextView tvName;
        final TextView tvArtist;
        final TextView tvDuration;

        ViewHolder(View root) {
            cbItem = root.findViewById(R.id.cbSelect);
            ivCover = root.findViewById(R.id.songCover);
            tvName = root.findViewById(R.id.musicname);
            tvArtist = root.findViewById(R.id.artistName);
            tvDuration = root.findViewById(R.id.musiclength);
        }
    }
}
