package com.foxstoncold.youralarm;

import com.foxstoncold.youralarm.alarmScreenBackground.AlarmRepo;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = DBModule.class)
public interface AppComponent {
    void inject(MainActivity MA);
    AlarmRepo getRepo();
}
