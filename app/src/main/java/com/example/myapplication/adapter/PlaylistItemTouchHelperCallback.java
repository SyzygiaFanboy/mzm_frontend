package com.example.myapplication.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class PlaylistItemTouchHelperCallback extends ItemTouchHelper.Callback {

    private final PlaylistRecyclerAdapter adapter;
    private final Runnable onMoveFinished;

    public PlaylistItemTouchHelperCallback(PlaylistRecyclerAdapter adapter, Runnable onMoveFinished) {
        this.adapter = adapter;
        this.onMoveFinished = onMoveFinished;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        // 禁用长按拖拽，我们将使用拖拽手柄
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        // 禁用滑动删除
        return false;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        // 只允许上下拖动
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        int swipeFlags = 0;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source,
                          @NonNull RecyclerView.ViewHolder target) {
        // 当项目被拖动时，更新适配器中的数据
        int fromPosition = source.getAdapterPosition();
        int toPosition = target.getAdapterPosition();
        adapter.swapItems(fromPosition, toPosition);
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // 不实现，因为我们禁用了滑动
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        // 拖拽结束后调用回调，用于保存更新后的顺序
        if (onMoveFinished != null) {
            onMoveFinished.run();
        }
    }
}