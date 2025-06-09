package com.example.taskflow.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.taskflow.model.Task;

import java.util.Date;
import java.util.List;

@Dao
public interface TaskDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTask(Task task);
    
    @Update
    void updateTask(Task task);
    
    @Delete
    void deleteTask(Task task);
    
    @Query("SELECT * FROM tasks ORDER BY date ASC")
    List<Task> getAllTasks();
    
    @Query("SELECT * FROM tasks WHERE userEmail = :userEmail ORDER BY date ASC")
    List<Task> getTasksByUser(String userEmail);
    
    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'PENDING'")
    int getPendingTasksCount();
    
    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'COMPLETED'")
    int getCompletedTasksCount();
    
    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'PENDING' AND userEmail = :userEmail")
    int getPendingTasksCountByUser(String userEmail);
    
    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'COMPLETED' AND userEmail = :userEmail")
    int getCompletedTasksCountByUser(String userEmail);
    
    @Query("SELECT COUNT(*) FROM tasks")
    long getTaskCount();
    
    @Query("SELECT COUNT(*) FROM tasks WHERE userEmail = :userEmail")
    long getTaskCountByUser(String userEmail);
    
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    Task getTaskById(String taskId);
    
    @Query("SELECT * FROM tasks WHERE date = :date")
    List<Task> getTasksForDate(String date);
    
    @Query("SELECT * FROM tasks WHERE date = :date AND userEmail = :userEmail")
    List<Task> getTasksForDateByUser(String date, String userEmail);
    
    @Query("SELECT * FROM tasks WHERE googleTaskId IS NULL")
    List<Task> getLocalOnlyTasks();
    
    @Query("SELECT * FROM tasks WHERE googleTaskId IS NULL AND userEmail = :userEmail")
    List<Task> getLocalOnlyTasksByUser(String userEmail);

    @Query("DELETE FROM tasks WHERE userEmail = :userEmail")
    void deleteAllTasksForUser(String userEmail);
    
    @Query("SELECT * FROM tasks WHERE userEmail = :userEmail ORDER BY date ASC, startTime ASC")
    List<Task> getAllTasksForUser(String userEmail);

    // Added method for insertOrUpdateTask
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateTask(Task task);
    
    // Added method for insertOrUpdate as an alias for consistency
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdate(Task task);
    
    // Added method to delete task by ID
    @Query("DELETE FROM tasks WHERE id = :taskId")
    void deleteTaskById(String taskId);
    
    // Added method to get similar tasks by title and date
    @Query("SELECT * FROM tasks WHERE title = :title AND date = :date")
    List<Task> getSimilarTasks(String title, Date date);
}
