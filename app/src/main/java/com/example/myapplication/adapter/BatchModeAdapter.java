package com.example.myapplication.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

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

    // 监听器的方法
    public void setOnSelectAllListener(OnSelectAllListener listener) {
        this.selectAllListener = listener;
    }

    public BatchModeAdapter(Context context, List<Map<String, Object>> data, int resource, String[] from, int[] to) {
        this.data = data;
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

        Object indexObj = item.get("index");
        holder.tvSeq.setText(indexObj != null ? String.valueOf(indexObj) : String.valueOf(position + 1));

        Object nameObj = item.get("name");
        holder.tvName.setText(nameObj != null ? String.valueOf(nameObj) : "");

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
        final TextView tvSeq;
        final TextView tvName;
        final TextView tvDuration;

        ViewHolder(View root) {
            cbItem = root.findViewById(R.id.cbSelect);
            tvSeq = root.findViewById(R.id.seq);
            tvName = root.findViewById(R.id.musicname);
            tvDuration = root.findViewById(R.id.musiclength);
        }
    }
}
