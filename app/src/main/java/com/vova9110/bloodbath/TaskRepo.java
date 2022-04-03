package com.vova9110.bloodbath;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.vova9110.bloodbath.Database.TaskDatabase;
import com.vova9110.bloodbath.Database.Tasks;
import com.vova9110.bloodbath.Database.TasksDao;

import java.util.List;

import javax.inject.Inject;

public class TaskRepo { // Репозиторий предоставляет абстрактный доступ к базе данных, то есть представлен в роли API (так они советуют делать)
    private TasksDao TasksDao; // Создаём поле, которое будет представлять переменную интерфейса Дао
    private LiveData<List<Tasks>> allTasks; // Это поле будет представлять список всех задач
    private List<Tasks> mList;



    TaskRepo(TasksDao Dao){//Можно и здесь добавить аннотацию Inject, чтобы Даггер обращался к этому конструктору для создания и сам передавал в него Дао
        TasksDao = Dao;
        allTasks = TasksDao.getAllTasks();
        Log.d("TAG", "Repo instance created");
    }

    LiveData<List<Tasks>> getAllTasks() {
        return allTasks;
    }

    void insert (Tasks task){
        TaskDatabase.databaseWriteExecutor.execute(() -> TasksDao.insert(task));//ExecutorService - это статичная константа внутри DB класса
    }

    void fill (){
        TaskDatabase.databaseWriteExecutor.execute(()->{
            for (int i = 0; i < 27; i++){
                if (i<10){ Tasks task = new Tasks("0" + i); TasksDao.insert(task);}
                else { Tasks task = new Tasks("" + i); TasksDao.insert(task);}
            }
        });
    }
    void delTask(int pos) {
        mList = allTasks.getValue();
        if (!mList.isEmpty()) TaskDatabase.databaseWriteExecutor.execute(() -> TasksDao.deleteOne(mList.get(pos).getTask()));
    }
    void clear () {
        TaskDatabase.databaseWriteExecutor.execute(() -> TasksDao.deleteAll());
    }
}

