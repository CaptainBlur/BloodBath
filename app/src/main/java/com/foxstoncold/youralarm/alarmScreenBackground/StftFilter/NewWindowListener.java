package com.foxstoncold.youralarm.alarmScreenBackground.StftFilter;

import android.util.SparseIntArray;


public interface NewWindowListener {
    /**
     * @param timestamp time (in ms) of the snapshot in the bounds of entire input array
     * @param fftSnapshot key represents a frequency (need to be divided by 100 and written to the output as float)
     * and val is val in normalized dB, or absolute values.
     */
    public void onWindowComputed(SparseIntArray fftSnapshot);
    public void onIdlePassed();
}
