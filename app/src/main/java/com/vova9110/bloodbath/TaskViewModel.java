package com.vova9110.bloodbath;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.room.Room;

import com.vova9110.bloodbath.Database.TaskDatabase;
import com.vova9110.bloodbath.Database.Tasks;
import com.vova9110.bloodbath.Database.TasksDao;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

public class  TaskViewModel extends AndroidViewModel {
    @Inject
    public TaskRepo repo;
    private final LiveData<List<Tasks>> allTasks; // Уже во время инициализации у нас есть список всех задач
    private final AppComponent component;

    public TaskViewModel(Application app) { // Конструктор, в который принимаем параметры, необходимые для создания БД в репозитории
        super(app);
        component = DaggerAppComponent.builder().dBModule(new DBModule(app)).build();
        component.inject(this);
        allTasks = repo.getAllTasks();
    }
    LiveData<List<Tasks>> getAllTasks() { return allTasks;}// Методы, котоыре доступны при обращении к классу TaskViewModel
    AppComponent getComponent(){ return component; }
    void insert (Tasks task) { repo.insert(task); }
    void fill () { repo.fill(); }
    void clear () { repo.clear(); }

}
@Singleton
@Component(modules = DBModule.class)
interface AppComponent {
    void inject (MainActivity MA);
    void inject (TaskViewModel VM);
    void inject (AlarmViewHolder VH);
}

@Module
class DBModule{
    private TaskDatabase db;

    public DBModule (Application app){
        db = Room.databaseBuilder(app, TaskDatabase.class, "tasks_database").build();//Особенность метода build такова, что каждый раз при входе с приложение, создаётся БД,
        //поэтому она и создаётся у нас один раз при инициализации Dagger (он создаёт все модули один раз)
    }

    @Singleton
    @Provides
    TaskDatabase providesDB(){
        return db;//Бестлоку возвращать экзеспляр TaskDatabase, класс вообще абстрактный. Нужно создавать экземпляр через билдер
    }

    @Singleton
    @Provides
    TasksDao providesDao(@NonNull TaskDatabase db){//Так выходит, что предыдущий метод предоставляет сюда экземпляр БД
        return db.tasksDao();//А мы извлекаем из него Дао, которое используется в конструкторе репозитория
    }

    @Singleton
    @Provides
    TaskRepo taskRepo(TasksDao tasksDao){//Мы говорим Даггеру, что этот конструктор можно использовать для создания репозиторияю Даггер сам передаёт в него Дао для создания,
        return new TaskRepo(tasksDao);//при этом создавая всего один экземпляр репозитория и передавая его куда надо
    }
}

