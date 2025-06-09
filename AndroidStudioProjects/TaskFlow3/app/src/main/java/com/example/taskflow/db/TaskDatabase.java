package com.example.taskflow.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.taskflow.model.Task;
import com.example.taskflow.util.DateConverter;

@Database(entities = {Task.class}, version = 3, exportSchema = false)
@TypeConverters({DateConverter.class})
public abstract class TaskDatabase extends RoomDatabase {
    
    private static final String DATABASE_NAME = "tasks_db";
    private static TaskDatabase instance;
    
    public abstract TaskDao taskDao();
    
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Migration from version 1 to 2 (if you've made schema changes)
            // For example, if you added a field or changed a type
            // If no actual schema change, the implementation can be empty
        }
    };
    
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Migration from version 2 to 3
            // If needed, add SQL statements to update schema
        }
    };
    
    public static synchronized TaskDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    TaskDatabase.class,
                    DATABASE_NAME)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE) // Helps with some integrity issues
                    .build();
        }
        return instance;
    }
    
    /**
     * Recreates the database by closing and deleting the existing one.
     * This is a safer alternative than just trying to delete tables.
     */
    public static void recreateDatabase(Context context) {
        // Close the database if it's open
        if (instance != null) {
            if (instance.isOpen()) {
                instance.close();
            }
            instance = null;
        }
        
        // Delete the database file
        context.deleteDatabase(DATABASE_NAME);
    }
}
