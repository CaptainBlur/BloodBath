package com.vova9110.bloodbath;

import android.util.Log;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import com.vova9110.bloodbath.Database.Tasks;

public class RawLayoutManager extends RecyclerView.LayoutManager {

    private List<Tasks> mList;
    private TaskViewModel mViewModel;

    public RawLayoutManager (TaskViewModel VM){
        mViewModel = VM;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT);
    }

    @Override
    public void onLayoutChildren (RecyclerView.Recycler recycler, RecyclerView.State state){
        mList = mViewModel.getAllTasks().getValue();
        if (false) {
            View view = recycler.getViewForPosition(0);
            addView(view);
            measureChildWithMargins(view, 0, 0);
            layoutDecorated(view, 0, 0, getWidth(), getHeight());
        }
        Log.d("TAG", "Time to layout!");
    }
}
