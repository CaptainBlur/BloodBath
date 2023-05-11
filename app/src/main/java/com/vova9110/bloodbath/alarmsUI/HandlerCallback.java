package com.vova9110.bloodbath.alarmsUI;

public interface HandlerCallback {
    void passPrefToAdapter(int parentPos, int prefPos);
    void removePref(boolean pullDataset);
    void removeNPassPrefToAdapter(int parentPos, int prefPos);
    void deleteItem (int pos);
    void addItem (int hour, int minute);
    void changeItem (int oldPos, int hour, int minute);
    void updateOneState (int parentPos, boolean isChecked);

    RLMCallbackOLD pullRLMCallback();
}
