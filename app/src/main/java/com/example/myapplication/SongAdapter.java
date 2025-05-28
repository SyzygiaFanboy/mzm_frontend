package com.example.myapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.List;

public class SongAdapter extends BaseAdapter {
    private List<Songinf> list;
    private LayoutInflater inflater;

    public SongAdapter(Context ctx, List<Songinf> list) {
        this.list = list;
        this.inflater = LayoutInflater.from(ctx);
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Songinf getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.search_item, parent, false);
            holder = new ViewHolder();
            holder.tvName = convertView.findViewById(R.id.tv_songname);
            holder.tvMusician = convertView.findViewById(R.id.tv_musician);
            holder.tvPlayCount = convertView.findViewById(R.id.tv_playcount);
            holder.cbSelect = convertView.findViewById(R.id.cb_select);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Songinf song = getItem(pos);

        // 解绑旧的 listener，避免复用时触发错误回调
        holder.cbSelect.setOnCheckedChangeListener(null);

        // 根据当前数据模型设置 checkbox 状态
        holder.cbSelect.setChecked(song.isSelected());

        // 更新文本信息
        holder.tvName.setText(song.getSongname());
        holder.tvMusician.setText(song.getMusician());
        holder.tvPlayCount.setText("热度：" + song.getPlayCount());

        // 重新绑定 listener，修改数据模型
        holder.cbSelect.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                song.setSelected(isChecked);
            }
        });

        return convertView;
    }

    static class ViewHolder {
        TextView tvName;
        TextView tvMusician;
        TextView tvPlayCount;
        CheckBox cbSelect;
    }

    public void setData(List<Songinf> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }
}
