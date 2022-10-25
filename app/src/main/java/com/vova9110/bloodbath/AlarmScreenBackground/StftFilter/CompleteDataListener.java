package com.vova9110.bloodbath.AlarmScreenBackground.StftFilter;

import android.util.SparseIntArray;


public interface CompleteDataListener {
    void onDataComputed(int[] timeValues, int[] freqValues, SparseIntArray fftDataset, int magMin, int magMax);
}
