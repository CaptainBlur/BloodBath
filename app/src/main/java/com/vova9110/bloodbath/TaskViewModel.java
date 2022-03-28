package com.vova9110.bloodbath;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.vova9110.bloodbath.Database.Tasks;

import java.util.List;

public class TaskViewModel extends AndroidViewModel {
    private TaskRepo repo; //TODO фигануть весь репозиторий через DI
    private final LiveData<List<Tasks>> allTasks; // Уже во время инициализации у нас есть список всех задач

    public TaskViewModel(Application app) { // Конструктор, в который принимаем параметры, необходимые для создания БД в репозитории
        super(app);
        repo = new TaskRepo(app);
        allTasks = repo.getAllTasks();
    }
    LiveData<List<Tasks>> getAllTasks() { return allTasks;}// Методы, котоыре доступны при обращении к классу TaskViewModel
    void insert (Tasks task) { repo.insert(task); }
    void fill () { repo.fill(); }
    void clear () { repo.clear(); }
}
