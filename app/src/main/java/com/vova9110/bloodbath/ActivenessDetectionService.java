package com.vova9110.bloodbath;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class ActivenessDetectionService extends Service {
    public ActivenessDetectionService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}