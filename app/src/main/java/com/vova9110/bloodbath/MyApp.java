package com.vova9110.bloodbath;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Room;

import com.vova9110.bloodbath.alarmScreenBackground.AlarmExecutionDispatch;
import com.vova9110.bloodbath.alarmScreenBackground.FiringControlService;
import com.vova9110.bloodbath.alarmsUI.FreeAlarmsHandler;
import com.vova9110.bloodbath.database.AlarmDao;
import com.vova9110.bloodbath.database.AlarmDatabase;
import com.vova9110.bloodbath.alarmScreenBackground.AlarmRepo;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

public class MyApp extends Application {
    public AppComponent component;

    @Override
    public void onCreate() {
        super.onCreate();
        //Basically, we have just one thing that needs to be available globally, and that's Repo.
        //And I see no problems placing it there
        component = DaggerAppComponent.builder().dBModule(new DBModule(this)).build();
        SplitLogger.initialize(this);
        SplitLogger.i("Application initialized! Hello everyone!");
    }
}

@Module
class DBModule{
    private final Application app;
    private final AlarmDatabase db;

    public DBModule (Application app){
        this.app = app;
        db = Room.databaseBuilder(app.getApplicationContext().createDeviceProtectedStorageContext(), AlarmDatabase.class, "alarms_database").build();//Особенность метода build такова, что каждый раз при входе с приложение, создаётся БД,
        //поэтому она и создаётся у нас один раз при инициализации Dagger (он создаёт все модули один раз)
    }

    /**
     * Repo's constructor is marked by Inject and all of these three methods are only supplemental
     * @return
     */
    @Singleton
    @Provides
    AlarmDatabase providesDB(){
        return db;//Бестлоку возвращать экзеспляр AlarmDatabase, класс вообще абстрактный. Нужно создавать экземпляр через билдер
    }

    @Singleton
    @Provides
    AlarmDao providesDao(@NonNull AlarmDatabase db){//Так выходит, что предыдущий метод предоставляет сюда экземпляр БД
        return db.alarmDao();//А мы извлекаем из него Дао, которое используется в конструкторе репозитория
    }

    @Singleton
    @Provides
    Context providesContext(){//Так выходит, что предыдущий метод предоставляет сюда экземпляр БД
        return app.getApplicationContext();//А мы извлекаем из него Дао, которое используется в конструкторе репозитория
    }

}