//package com.example.myapplication;
//
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.CheckBox;
//import android.widget.ImageView;
//import android.widget.TextView;
//
//import androidx.recyclerview.widget.RecyclerView;
//
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//
//public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {
//    public interface OnItemClick {
//        void onClick(int pos);
//    }
//
//    private List<String> data;
//    private boolean manageMode = false;
//    private Set<Integer> selected = new HashSet<>();
//    private OnItemClick click;
//
//    public PlaylistAdapter(List<String> data, OnItemClick click) {
//        this.data = data;
//        this.click = click;
//    }
//
//    /*显示多选框和拖拽句柄,但版本好像有点问题*/
//    public void setManageMode(boolean m) {
//        manageMode = m;
//        if (!m) selected.clear();
//        notifyDataSetChanged();
//    }
//
//    public boolean inManageMode() {
//        return manageMode;
//    }
//
//    public Set<Integer> getSelectedPositions() {
//        return selected;
//    }
//
//    @Override
//    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
//        View v = LayoutInflater.from(parent.getContext())
//                .inflate(R.layout.item_playlist, parent, false);
//        return new VH(v);
//    }
//
//    @Override
//    public void onBindViewHolder(VH holder, int pos) {
//        holder.tvName.setText(data.get(pos));
//        holder.cb.setVisibility(manageMode ? View.VISIBLE : View.GONE);
//        holder.handle.setVisibility(manageMode ? View.VISIBLE : View.GONE);
//        holder.cb.setChecked(selected.contains(pos));
//
//        holder.itemView.setOnClickListener(v -> {
//            if (manageMode) {
//                if (selected.contains(pos)) selected.remove(pos);
//                else selected.add(pos);
//                notifyItemChanged(pos);
//            } else {
//                click.onClick(pos);
//            }
//        });
//    }
//
//    @Override
//    public int getItemCount() {
//        return data.size();
//    }
//
//    static class VH extends RecyclerView.ViewHolder {
//        CheckBox cb;
//        TextView tvName;
//        ImageView handle;
//        public VH(View item) {
//            super(item);
//            cb = item.findViewById(R.id.cbSelect);
//            tvName = item.findViewById(R.id.tvName);
//            handle = item.findViewById(R.id.imgHandle);
//        }
//    }
//}
