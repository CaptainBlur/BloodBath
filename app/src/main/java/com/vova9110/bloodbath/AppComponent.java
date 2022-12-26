package com.vova9110.bloodbath;

import com.vova9110.bloodbath.alarmScreenBackground.AlarmActivity;
import com.vova9110.bloodbath.alarmScreenBackground.AlarmExecutionDispatch;
import com.vova9110.bloodbath.alarmScreenBackground.AlarmRepo;
import com.vova9110.bloodbath.alarmScreenBackground.FiringControlService;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = DBModule.class)
public interface AppComponent {
    void inject(MainActivity MA);
    AlarmRepo getRepo();
}
