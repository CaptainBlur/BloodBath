package com.foxstoncold.youralarm.alarmScreenBackground.StftFilter;

import android.util.SparseIntArray;


public interface CompleteDataListener {
    void onDataComputed(int[] timeValues, int[] freqValues, SparseIntArray fftDataset, int magMin, int magMax);
}
