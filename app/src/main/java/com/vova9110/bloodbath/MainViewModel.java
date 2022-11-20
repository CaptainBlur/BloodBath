package com.vova9110.bloodbath;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.room.Room;

import com.vova9110.bloodbath.AlarmScreenBackground.AlarmExec;
import com.vova9110.bloodbath.Database.AlarmDao;
import com.vova9110.bloodbath.Database.AlarmDatabase;
import com.vova9110.bloodbath.Database.AlarmRepo;

import org.apache.commons.math3.geometry.spherical.oned.ArcsSet;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

public class MainViewModel extends AndroidViewModel {
    public final static String PREFERENCES_NAME = "prefs";
    private final AppComponent component;
    private final Application app;
    private SplitLogger.SLCompanion sl;
    @Inject
    public AlarmRepo repo;
    @Inject
    public Intent execIntent;


    public MainViewModel(Application app) { // Конструктор, в который принимаем параметры, необходимые для создания БД в репозитории
        super(app);
        SplitLogger.initialize(app,false);
        sl = new SplitLogger.SLCompanion(false, this.getClass().getName(), false);

        component = DaggerAppComponent.builder().dBModule(new DBModule(app)).build();
        this.app = app;
        component.inject(this);
        sl.f("Creating VM. Setting erased alarms if needed");

        checkLaunchPreferences();
        app.getApplicationContext().startService(execIntent);
    }
    AppComponent getComponent(){ return component; }

    private void checkLaunchPreferences (){
        SharedPreferences prefs = app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (!prefs.getBoolean("notificationChannelsSet", false)) {
            NotificationManager manager = (NotificationManager) app.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel channel1 = new NotificationChannel("firing", app.getString(R.string.firing_notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
            //todo set dummy notification sound and no vibration
//                    .setSound();
            channel1.enableVibration(false);
            manager.createNotificationChannel(channel1);

            NotificationChannel channel2 = new NotificationChannel("activeness", app.getString(R.string.activeness_detection_notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
            //todo set required sound and vibration, this is for start and end
//                    .setSound();
            channel2.enableVibration(true);
            manager.createNotificationChannel(channel2);

            NotificationChannel channel3 = new NotificationChannel("warning", app.getString(R.string.warning_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            //todo set dummy sound
//                    .setSound();
            channel3.enableVibration(true);
            manager.createNotificationChannel(channel3);

            sl.fp("Setting notification channels");
            editor.putBoolean("notificationChannelsSet", true);
        }

        if (!prefs.getBoolean("appExitedProperly", false) && !prefs.getBoolean("firstLaunch", true)){
            sl.ip("urgent app exit detected!");
        }
        editor.putBoolean("appExitedProperly", false);
        editor.putBoolean("firstLaunch", false);
        editor.apply();
    }
}
@Singleton
@Component(modules = DBModule.class)
interface AppComponent {
    void inject(MainActivity MA);
    void inject(MainViewModel VM);
    void inject(AlarmDeployReceiver ADR);
    void inject(ExecReceiver ER);
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
    Context providesContext(@NonNull Application app){//Так выходит, что предыдущий метод предоставляет сюда экземпляр БД
        return app.getApplicationContext();//А мы извлекаем из него Дао, которое используется в конструкторе репозитория
    }

    @Singleton
    @Provides
    FreeAlarmsHandler providesHandler(AlarmRepo repo, Intent execIntent){//Мы говорим Даггеру, что этот конструктор можно использовать для создания репозиторияю Даггер сам передаёт в него Дао для создания,
        return new FreeAlarmsHandler(repo, execIntent);//при этом создавая всего один экземпляр репозитория и передавая его куда надо
    }

    @Provides
    Intent providesIntent (AlarmRepo alarmRepo){
        Intent execIntent = new Intent(app.getApplicationContext(), AlarmExec.class);
        execIntent.putExtra("repo", alarmRepo);
        return execIntent;
    }
}

