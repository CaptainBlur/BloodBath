package com.vova9110.bloodbath;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.room.Room;

import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmDao;
import com.vova9110.bloodbath.Database.AlarmDatabase;
import com.vova9110.bloodbath.Database.AlarmRepo;

import java.util.Date;
import java.util.List;

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

    public AlarmViewModel(Application app) { // Конструктор, в который принимаем параметры, необходимые для создания БД в репозитории
        super(app);
        component = DaggerAppComponent.builder().dBModule(new DBModule(app)).build();
        this.app = app;
        component.inject(this);
        Log.d (TAG, "Creating VM. Setting erased alarms if needed");

        refreshActives();
    }
    AppComponent getComponent(){ return component; }

    private void refreshActives(){
        AlarmManager AM = ((android.app.AlarmManager) app.getApplicationContext().getSystemService(Context.ALARM_SERVICE));
        Intent broadcastI = new Intent(app.getApplicationContext(), AlarmReceiver.class);
        List<Alarm> actives = repo.getActives();

        for (Alarm active : actives){
            int ID = Integer.parseInt(String.valueOf(active.getHour()).concat(String.valueOf(active.getMinute())));
            Log.d (TAG, "setting: " + ID);

            PendingIntent PI = PendingIntent.getBroadcast(app.getApplicationContext(), ID, broadcastI, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(active.getInitialTime().getTime(), PI);
            AM.setAlarmClock(info, PI);
        }
        Log.d (TAG, "NEXT ALARM AT: " + new Date(AM.getNextAlarmClock().getTriggerTime()));
    }
}
@Singleton
@Component(modules = DBModule.class)
interface AppComponent {
    void inject (MainActivity MA);
    void inject (AlarmViewModel VM);
    void inject (AlarmExec AE);
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
        Log.d (TAG, "Providing DAO");
        return db.alarmDao();//А мы извлекаем из него Дао, которое используется в конструкторе репозитория
    }

    @Singleton
    @Provides
    UIHandler providesHandler(AlarmRepo repo, AlarmDao alarmDao, Intent execIntent){//Мы говорим Даггеру, что этот конструктор можно использовать для создания репозиторияю Даггер сам передаёт в него Дао для создания,
        return new UIHandler(repo, execIntent);//при этом создавая всего один экземпляр репозитория и передавая его куда надо
    }

    @Provides
    Intent providesIntent (AlarmRepo alarmRepo){
        Intent execIntent = new Intent(app.getApplicationContext(), AlarmExec.class);
        execIntent.putExtra("repo", alarmRepo);
        return execIntent;
    }
}

