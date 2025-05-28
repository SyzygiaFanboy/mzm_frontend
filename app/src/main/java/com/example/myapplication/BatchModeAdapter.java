package com.example.myapplication;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.List;
import java.util.Map;
//复选模式适配器
public class BatchModeAdapter extends SimpleAdapter {

    public interface OnSelectAllListener {
        void onSelectAllChanged(boolean shouldSelectAll);
    }
    private OnSelectAllListener selectAllListener;
    private List<Map<String, Object>> data;
    private boolean isBatchMode;
    // 监听器的方法
    public void setOnSelectAllListener(OnSelectAllListener listener) {
        this.selectAllListener = listener;
    }

    public BatchModeAdapter(Context context, List<Map<String, Object>> data, int resource, String[] from, int[] to) {
        super(context, data, resource, from, to);
        this.data = data;
    }
    public void setData(List<Map<String, Object>> newData) {
        this.data = newData;
//        notifyDataSetChanged();
    }
    @Override public int getCount() { return data.size(); }
    @Override public Object getItem(int i) { return data.get(i); }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
//        CheckBox cbSelect = view.findViewById(R.id.cbSelect);

        CheckBox cbItem = view.findViewById(R.id.cbSelect);
        // 控制复选框可见性，歌单同理
        cbItem.setVisibility(isBatchMode ? View.VISIBLE : View.GONE);

        // 获取isSelected值并判null
        Boolean isSelected = (Boolean) data.get(position).get("isSelected");
        cbItem.setChecked(isSelected != null ? isSelected : false); // 空值保护
        TextView tvName = view.findViewById(R.id.musicname);
        // 批量模式时调整左边距
        if (isBatchMode) {
//            ObjectAnimator slideIn = ObjectAnimator.ofFloat(cbItem, "translationX", -50f, 0f);
//            slideIn.setInterpolator(new OvershootInterpolator());
//            slideIn.start();
//            cbItem.setVisibility(View.VISIBLE);
            //动画效果，但是略丑

            // 保持名称与序号间距不变
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) tvName.getLayoutParams();
            params.horizontalBias = 0f; // 左对齐
            tvName.setLayoutParams(params);
        } else {
            cbItem.setVisibility(View.GONE);
        }
        // 单项选中状态变化监听
        cbItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            data.get(position).put("isSelected", isChecked);
            if (selectAllListener != null) {
                selectAllListener.onSelectAllChanged(shouldSelectAll());
            }
        });
        return view;
    }
    // 判断是否应该触发全选
    private boolean shouldSelectAll() {
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
}