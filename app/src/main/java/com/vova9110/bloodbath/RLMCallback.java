package com.vova9110.bloodbath;

public interface RLMCallback {
    void notifyBaseClick (int prefParentPos);
    void updateDatasetEvent (int position, int mode, int newHour, int newMinute);
}
