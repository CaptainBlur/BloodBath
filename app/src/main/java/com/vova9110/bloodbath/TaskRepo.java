package com.vova9110.bloodbath;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LiveData;

import com.vova9110.bloodbath.Database.TaskDatabase;
import com.vova9110.bloodbath.Database.Tasks;
import com.vova9110.bloodbath.Database.TasksDao;

import java.util.List;

public class TaskRepo { // Репозиторий предоставляет абстрактный доступ к базе данных, то есть представлен в роли API (так они советуют делать)
    private TasksDao TasksDao; // Создаём поле, которое будет представлять переменную интерфейса Дао
    private LiveData<List<Tasks>> allTasks; // Это поле будет представлять список всех задач

    TaskRepo(Application app) { //TODO фигануть инстанс Application через DI
        TaskDatabase db = TaskDatabase.getDatabase(app); // сразу же создаётся БД и передаётся в него,
        TasksDao = db.tasksDao(); // переменной сразу же присваивается интерфейс Дао,
        allTasks = TasksDao.getAllTasks(); // сразу же запрашиваются все задачи из БД и также присваиваются полю
    }

    // Room executes all queries on a separate thread.
    // Observed LiveData will notify the observer when the data has changed.
    LiveData<List<Tasks>> getAllTasks() {
        return allTasks;
    }

    // You must call this on a non-UI thread or your app will throw an exception. Room ensures
    // that you're not doing any long running operations on the main thread, blocking the UI.
    void insert (Tasks task){
        TaskDatabase.databaseWriteExecutor.execute(() -> {
            TasksDao.insert(task);
        });
    }

    void delTask(String task) {
        TaskDatabase.databaseWriteExecutor.execute(() -> TasksDao.deleteOne(task));
    }



    public interface DeleteClick{
        void deleteClick (int position);
    }
}

