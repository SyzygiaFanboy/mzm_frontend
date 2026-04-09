package com.example.myapplication.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.myapplication.R;

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
    public View getView(final int position, View convertView, ViewGroup parent) {
        // 边界检查
        if (data == null || position < 0 || position >= data.size()) {
            // 如果位置无效，返回一个空的视图或默认视图
            if (convertView == null) {
                convertView = super.getView(0, null, parent);
            }
            return convertView;
        }

        View view = super.getView(position, convertView, parent);
        CheckBox cbItem = view.findViewById(R.id.cbSelect);
        
        cbItem.setOnCheckedChangeListener(null);
        
        // 控制复选框可见性
        cbItem.setVisibility(isBatchMode ? View.VISIBLE : View.GONE);

        // 获取isSelected值并判null
        Boolean isSelected = (Boolean) data.get(position).get("isSelected");
        cbItem.setChecked(isSelected != null ? isSelected : false);
        
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
        
        // 设置监听器，并添加严格的边界检查
        cbItem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (data != null && position >= 0 && position < data.size()) {
                try {
                    data.get(position).put("isSelected", isChecked);
                    if (selectAllListener != null) {
                        selectAllListener.onSelectAllChanged(shouldSelectAll());
                    }
                } catch (IndexOutOfBoundsException e) {
                    android.util.Log.w("BatchModeAdapter", "Index out of bounds in checkbox listener: position=" + position + ", size=" + data.size());
                }
            }
        });
        
        return view;
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
}