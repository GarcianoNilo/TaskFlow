package com.example.taskflow.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.taskflow.model.Task;

@Database(entities = {Task.class}, version = 1, exportSchema = false)
@TypeConverters({DateConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    
    private static AppDatabase instance;
    
    public abstract TaskDao taskDao();
    
    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    "task_flow_database")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}