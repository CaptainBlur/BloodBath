package com.vova9110.bloodbath;

import android.app.Application;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.vova9110.bloodbath.Database.Tasks;

import java.util.List;

public class TaskViewModel extends ViewModel {
    private TaskRepo repo; // Создаём переменную, представляющую репозиторий
    private final LiveData<List<Tasks>> allTasks; // Уже во время инициализации у нас есть список всех задач

    public TaskViewModel(Application application) { // Конструктор, в который принимаем параметры, необходимые для создания БД в репозитории
        repo = new TaskRepo(application);
        allTasks = repo.getAllTasks();
    }
    LiveData<List<Tasks>> getAllTasks() {return allTasks;}// Методы, котоыре доступны при обращении к классу TaskViewModel
    public void insert (Tasks task) {repo.insert(task);}
}
