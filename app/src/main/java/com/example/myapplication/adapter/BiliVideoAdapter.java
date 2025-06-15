package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.example.myapplication.R;

import java.util.ArrayList;
import java.util.List;

public class BiliVideoAdapter extends BaseAdapter {

    public interface OnSelectAllListener {
        void onSelectAllChanged(boolean shouldSelectAll);
    }

    public interface OnItemSelectListener {
        void onItemSelectChanged(int selectedCount);
    }

    private final Context context;
    private final List<BiliVideoItem> videoItems;
    private OnSelectAllListener selectAllListener;
    private OnItemSelectListener itemSelectListener;

    public BiliVideoAdapter(Context context) {
        this.context = context;
        this.videoItems = new ArrayList<>();
    }

    public void setOnSelectAllListener(OnSelectAllListener listener) {
        this.selectAllListener = listener;
    }

    public void setOnItemSelectListener(OnItemSelectListener listener) {
        this.itemSelectListener = listener;
    }

    public void addItems(List<BiliVideoItem> items) {
        videoItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clearItems() {
        videoItems.clear();
        notifyDataSetChanged();
    }

    public List<BiliVideoItem> getSelectedItems() {
        List<BiliVideoItem> selectedItems = new ArrayList<>();
        for (BiliVideoItem item : videoItems) {
            if (item.isSelected()) {
                selectedItems.add(item);
            }
        }
        return selectedItems;
    }

    public void selectAll(boolean select) {
        for (BiliVideoItem item : videoItems) {
            item.setSelected(select);
        }
        notifyDataSetChanged();
        if (itemSelectListener != null) {
            itemSelectListener.onItemSelectChanged(select ? videoItems.size() : 0);
        }
    }

    @Override
    public int getCount() {
        return videoItems.size();
    }

    @Override
    public Object getItem(int position) {
        return videoItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.bili_video_item, parent, false);
            holder = new ViewHolder();
            holder.cbSelect = convertView.findViewById(R.id.cbSelect);
            holder.tvTitle = convertView.findViewById(R.id.tvVideoTitle);
            holder.tvUploader = convertView.findViewById(R.id.tvUploader);
            holder.tvDuration = convertView.findViewById(R.id.tvDuration);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        BiliVideoItem item = videoItems.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvUploader.setText(item.getUploader());
        holder.tvDuration.setText(formatDuration(item.getDuration()));

        // 避免CheckBox状态错乱
        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(item.isSelected());
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.setSelected(isChecked);
            if (selectAllListener != null) {
                selectAllListener.onSelectAllChanged(shouldSelectAll());
            }
            if (itemSelectListener != null) {
                itemSelectListener.onItemSelectChanged(getSelectedCount());
            }
        });

        // 添加整行点击事件
        convertView.setOnClickListener(v -> {
            // 切换选中状态
            boolean newState = !item.isSelected();
            item.setSelected(newState);
            holder.cbSelect.setChecked(newState);

            // 通知监听器
            if (selectAllListener != null) {
                selectAllListener.onSelectAllChanged(shouldSelectAll());
            }
            if (itemSelectListener != null) {
                itemSelectListener.onItemSelectChanged(getSelectedCount());
            }
        });

        return convertView;
    }

    private int getSelectedCount() {
        int count = 0;
        for (BiliVideoItem item : videoItems) {
            if (item.isSelected()) {
                count++;
            }
        }
        return count;
    }

    private boolean shouldSelectAll() {
        for (BiliVideoItem item : videoItems) {
            if (!item.isSelected()) {
                return false;
            }
        }
        return !videoItems.isEmpty();
    }

    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    static class ViewHolder {
        CheckBox cbSelect;
        TextView tvTitle;
        TextView tvUploader;
        TextView tvDuration;
    }

    public static class BiliVideoItem {
        private final String bvid;
        private final String title;
        private final String uploader;
        private final int duration;
        private boolean selected;

        public BiliVideoItem(String bvid, String title, String uploader, int duration) {
            this.bvid = bvid;
            this.title = title;
            this.uploader = uploader;
            this.duration = duration;
            this.selected = false;
        }

        public String getBvid() {
            return bvid;
        }

        public String getTitle() {
            return title;
        }

        public String getUploader() {
            return uploader;
        }

        public int getDuration() {
            return duration;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }
    }
}