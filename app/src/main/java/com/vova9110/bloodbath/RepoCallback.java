package com.vova9110.bloodbath;

public interface RepoCallback {
    void passPrefToAdapter(int parentPos, int prefPos);
    void removePref();
    void removeNPassPrefToAdapter(int parentPos, int prefPos);
}
