package com.vova9110.bloodbath.alarmsUI;

public interface RLMCallbackOLD {
    void notifyBaseClick (int prefParentPos);
    void updateDatasetEvent (int position, int mode, int newHour, int newMinute);
    void hideOnResume ();
    void prepareUpdateDataset();
    void setNotifyFlag(int flag);
}
