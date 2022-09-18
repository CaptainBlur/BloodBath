package com.vova9110.bloodbath;

import static android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.room.Room;

import com.vova9110.bloodbath.Database.AlarmDao;
import com.vova9110.bloodbath.Database.AlarmDatabase;
import com.vova9110.bloodbath.Database.AlarmRepo;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

public class  AlarmViewModel extends AndroidViewModel {
    public final String TAG = "TAG_AVM";
    private final AppComponent component;
    private final Application app;
    @Inject
    public AlarmRepo repo;
    @Inject
    public Intent execIntent;


    public AlarmViewModel(Application app) { // Конструктор, в который принимаем параметры, необходимые для создания БД в репозитории
        super(app);
        component = DaggerAppComponent.builder().dBModule(new DBModule(app)).build();
        this.app = app;
        component.inject(this);
        Log.d (TAG, "Creating VM. Setting erased alarms if needed");

        checkPermission();
        app.getApplicationContext().startService(execIntent);
    }
    AppComponent getComponent(){ return component; }

    private void checkPermission (){
        Context context = app.getApplicationContext();
        Intent intent = new Intent(ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        if (!Settings.canDrawOverlays(context)) context.startActivity(intent);
    }
}
@Singleton
@Component(modules = DBModule.class)
interface AppComponent {
    void inject (MainActivity MA);
    void inject (AlarmViewModel VM);
    void inject (AlarmExec AE);
    void inject (AlarmActivity AA);
}

@Module
class DBModule{
    public final String TAG = "DBM";
    private Application app;
    private AlarmDatabase db;

    public DBModule (Application app){
        this.app = app;
        db = Room.databaseBuilder(app, AlarmDatabase.class, "alarms_database").build();//Особенность метода build такова, что каждый раз при входе с приложение, создаётся БД,
        //поэтому она и создаётся у нас один раз при инициализации Dagger (он создаёт все модули один раз)
    }

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
    FreeAlarmsHandler providesHandler(AlarmRepo repo, AlarmDao alarmDao, Intent execIntent){//Мы говорим Даггеру, что этот конструктор можно использовать для создания репозиторияю Даггер сам передаёт в него Дао для создания,
        return new FreeAlarmsHandler(repo, execIntent);//при этом создавая всего один экземпляр репозитория и передавая его куда надо
    }

    @Provides
    Intent providesIntent (AlarmRepo alarmRepo){
        Intent execIntent = new Intent(app.getApplicationContext(), AlarmExec.class);
        execIntent.putExtra("repo", alarmRepo);
        return execIntent;
    }
}

