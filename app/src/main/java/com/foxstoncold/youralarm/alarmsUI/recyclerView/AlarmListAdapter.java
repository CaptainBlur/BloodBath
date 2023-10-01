package com.foxstoncold.youralarm.alarmsUI.recyclerView;

import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.asynclayoutinflater.view.AsyncLayoutInflater;
import androidx.recyclerview.widget.RecyclerView;

import com.foxstoncold.youralarm.R;
import com.foxstoncold.youralarm.SplitLoggerUI;
import com.foxstoncold.youralarm.alarmsUI.FAHCallback;
import com.foxstoncold.youralarm.database.Alarm;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

public class AlarmListAdapter extends RecyclerView.Adapter<ChildViewHolder>{
    private final FAHCallback hCallback;
    private final List<Alarm> mList = new LinkedList<>();
    private final SplitLoggerUI slU = new SplitLoggerUI();

    private boolean firstShot = true;
    private final AsyncLayoutInflater asyncInflatter;
    private final Stack<ChildViewHolder> vhCache = new Stack();
    private final int CACHE_SIZE = 9;

    public static final int TYPE_TIME = 717;
    public static final int TYPE_PREF = 60;
    public static final int TYPE_ADD = 805;

    public AlarmListAdapter(FAHCallback hCallback, List<Alarm> initialList) {
        this.hCallback = hCallback;
        this.mList.addAll(initialList);
        asyncInflatter = new AsyncLayoutInflater(hCallback.getContextForInflater());
    }

    private void inflateAsync(ViewGroup parent, FAHCallback hCallback){
        asyncInflatter.inflate(R.layout.recycler_view_item, parent, (view, resid, parent1) -> {
            Rect rect = new Rect(parent.getLeft(), parent.getTop(), parent.getRight(), parent.getBottom());
            vhCache.push(new ChildViewHolder(view, hCallback, rect));
        });
    }

    @NonNull
    @Override
    public ChildViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
//        slU.i("onCreate");
        if (vhCache.isEmpty()){
            Rect rect = new Rect(parent.getLeft(), parent.getTop(), parent.getRight(), parent.getBottom());
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_view_item, parent, false);

            if (firstShot) {
                for (int i = 0; i < CACHE_SIZE; i++) inflateAsync(parent, hCallback);
                firstShot = false;
            }
            return new ChildViewHolder(view, hCallback, rect);
        }
        else{
            inflateAsync(parent, hCallback);
            return vhCache.pop();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ChildViewHolder holder, int position) {
        Alarm current = mList.get(position);
        holder.bind(current);
    }

    @Override
    public int getItemViewType(int position) {
        Alarm current = mList.get(position);

        if (current.getAddFlag()) return TYPE_ADD;
        else if (current.getPrefFlag()) return TYPE_PREF;
        else return TYPE_TIME;
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    public void submitList(List<Alarm> list){
        mList.clear();
        mList.addAll(list);
    }
    public List<Alarm> getCurrentList () {
        return mList;
    }
}
