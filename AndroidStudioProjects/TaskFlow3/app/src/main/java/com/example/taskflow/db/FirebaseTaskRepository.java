package com.example.taskflow.db;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.taskflow.model.Task;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Repository class for handling Firestore operations for Tasks
 */
public class FirebaseTaskRepository {
    private static final String TAG = "FirebaseTaskRepository";
    private static final String COLLECTION_TASKS = "tasks";
    
    private final FirebaseFirestore db;
    private static FirebaseTaskRepository instance;
    
    // Cache for tasks by user to reduce Firestore reads
    private Map<String, List<Task>> userTasksCache = new HashMap<>();
    private Map<String, Long> userTasksCacheTimestamp = new HashMap<>();
    private static final long CACHE_EXPIRATION_MS = 5 * 60 * 1000; // 5 minutes
    
    // Cache for date-specific tasks
    private Map<String, List<Task>> dateTasksCache = new HashMap<>();
    private Map<String, Long> dateTasksCacheTimestamp = new HashMap<>();

    // Task ID cache to prevent duplications during rapid status toggling
    private final Map<String, Long> recentlyUpdatedTasks = new HashMap<>();
    
    // Callbacks
    public interface TaskCallback {
        void onSuccess();
        void onFailure(Exception e);
    }
    
    public interface TasksCallback {
        void onSuccess(List<Task> tasks);
        void onFailure(Exception e);
    }
    
    public interface TaskCountCallback {
        void onSuccess(int count);
        void onFailure(Exception e);
    }
    
    public interface SingleTaskCallback {
        void onSuccess(Task task);
        void onFailure(Exception e);
    }
    
    private FirebaseTaskRepository() {
        this.db = FirebaseFirestore.getInstance();
    }
    
    public static synchronized FirebaseTaskRepository getInstance() {
        if (instance == null) {
            instance = new FirebaseTaskRepository();
        }
        return instance;
    }
    
    /**
     * Save a task to Firestore
     * This method is enhanced to prevent duplicates by checking the task's ID and Google Task ID
     */
    public void saveTask(Task task, TaskCallback callback) {
        if (task == null) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Task cannot be null"));
            }
            return;
        }

        // Debounce rapid task updates to prevent duplicates
        if (isRecentlyUpdated(task.getId())) {
            Log.d(TAG, "Task was recently updated, debouncing to prevent duplication: " + task.getId());
            if (callback != null) {
                callback.onSuccess();
            }
            return;
        }
        
        // Mark the task as recently updated
        markTaskAsUpdated(task.getId());
        
        // First check if we're saving a task with a Google Task ID
        if (task.getGoogleTaskId() != null && !task.getGoogleTaskId().isEmpty()) {
            // Check for existing tasks with this Google Task ID to prevent duplication
            db.collection(COLLECTION_TASKS)
                .whereEqualTo("googleTaskId", task.getGoogleTaskId())
                .whereEqualTo("userEmail", task.getUserEmail())
                .get()
                .addOnCompleteListener(queryTask -> {
                    if (queryTask.isSuccessful()) {
                        List<DocumentSnapshot> matchingDocs = queryTask.getResult().getDocuments();
                        
                        if (!matchingDocs.isEmpty()) {
                            // Task with this Google Task ID already exists
                            DocumentSnapshot existingDoc = matchingDocs.get(0);
                            
                            // If we have multiple matches, we need to merge and clean up
                            if (matchingDocs.size() > 1) {
                                Log.w(TAG, "Multiple tasks found with the same Google Task ID: " + task.getGoogleTaskId());
                                // Keep the earliest created document
                                DocumentSnapshot keepDoc = existingDoc;
                                
                                // Delete all duplicates except the one we're keeping
                                for (DocumentSnapshot doc : matchingDocs) {
                                    if (!doc.getId().equals(keepDoc.getId())) {
                                        db.collection(COLLECTION_TASKS).document(doc.getId()).delete();
                                        Log.d(TAG, "Deleted duplicate task with ID: " + doc.getId());
                                    }
                                }
                            }
                            
                            // Update the existing task rather than creating a new one
                            String existingId = existingDoc.getId();
                            
                            // Only set the ID if it's different (to avoid unnecessary changes)
                            if (!existingId.equals(task.getId())) {
                                task.setId(existingId);
                                Log.d(TAG, "Updated task ID to match existing Firestore document: " + existingId);
                            }
                            
                            // Now save with the existing document ID
                            db.collection(COLLECTION_TASKS)
                                .document(task.getId())
                                .set(task.toMap())
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Task updated successfully: " + task.getId());
                                    if (callback != null) {
                                        callback.onSuccess();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error updating task", e);
                                    if (callback != null) {
                                        callback.onFailure(e);
                                    }
                                });
                        } else {
                            // No existing task with this Google Task ID, check if there's a similar task by title/date
                            checkForSimilarTaskAndSave(task, callback);
                        }
                    } else {
                        // Query failed, fall back to direct save
                        Log.w(TAG, "Query for existing Google Task ID failed, saving directly", queryTask.getException());
                        saveTaskDirectly(task, callback);
                    }
                });
        } else {
            // No Google Task ID, check if there's a similar task by title/date
            checkForSimilarTaskAndSave(task, callback);
        }
    }
    
    /**
     * Check if a task was recently updated (debounce mechanism)
     */
    private boolean isRecentlyUpdated(String taskId) {
        if (!recentlyUpdatedTasks.containsKey(taskId)) {
            return false;
        }
        
        long lastUpdateTime = recentlyUpdatedTasks.get(taskId);
        long currentTime = System.currentTimeMillis();
        
        // 1000 ms debounce period
        return (currentTime - lastUpdateTime) < 1000;
    }
    
    /**
     * Mark a task as recently updated
     */
    private void markTaskAsUpdated(String taskId) {
        recentlyUpdatedTasks.put(taskId, System.currentTimeMillis());
        
        // Cleanup old entries every 50 operations to prevent memory leaks
        if (recentlyUpdatedTasks.size() > 50) {
            long currentTime = System.currentTimeMillis();
            ArrayList<String> keysToRemove = new ArrayList<>();
            
            for (Map.Entry<String, Long> entry : recentlyUpdatedTasks.entrySet()) {
                // Remove entries older than 10 seconds
                if (currentTime - entry.getValue() > 10000) {
                    keysToRemove.add(entry.getKey());
                }
            }
            
            for (String key : keysToRemove) {
                recentlyUpdatedTasks.remove(key);
            }
        }
    }
    
    /**
     * Check for similar tasks by title and date to prevent duplication
     */
    private void checkForSimilarTaskAndSave(Task task, TaskCallback callback) {
        // Only perform similarity check if we have enough information
        if (task.getTitle() != null && task.getUserEmail() != null) {
            db.collection(COLLECTION_TASKS)
                .whereEqualTo("userEmail", task.getUserEmail())
                .whereEqualTo("title", task.getTitle())
                .get()
                .addOnCompleteListener(queryTask -> {
                    if (queryTask.isSuccessful()) {
                        List<DocumentSnapshot> docs = queryTask.getResult().getDocuments();
                        boolean foundSimilar = false;
                        
                        if (docs.size() > 1) {
                            // Multiple tasks with the same title, check for duplicates
                            Task bestMatchTask = null;
                            DocumentSnapshot bestMatchDoc = null;
                            
                            for (DocumentSnapshot doc : docs) {
                                Task existingTask = doc.toObject(Task.class);
                                if (existingTask != null) {
                                    boolean sameDate = (task.getDate() != null && existingTask.getDate() != null &&
                                                       Math.abs(task.getDate().getTime() - existingTask.getDate().getTime()) < 86400000); // 1 day tolerance
                                    
                                    // If this task has the same ID as our task, it's the one we want to update
                                    if (existingTask.getId().equals(task.getId())) {
                                        bestMatchTask = existingTask;
                                        bestMatchDoc = doc;
                                        foundSimilar = true;
                                        break;
                                    }
                                    
                                    // If this task has the same Google Task ID, it's the one we want to update
                                    if (existingTask.getGoogleTaskId() != null && task.getGoogleTaskId() != null && 
                                        existingTask.getGoogleTaskId().equals(task.getGoogleTaskId())) {
                                        bestMatchTask = existingTask;
                                        bestMatchDoc = doc;
                                        foundSimilar = true;
                                        break;
                                    }
                                    
                                    // If this task has the same date as our task, it's likely the one we want to update
                                    if (sameDate && (bestMatchTask == null || bestMatchDoc == null)) {
                                        bestMatchTask = existingTask;
                                        bestMatchDoc = doc;
                                        foundSimilar = true;
                                    }
                                }
                            }
                            
                            // If we found a similar task, use its ID
                            if (foundSimilar && bestMatchTask != null) {
                                task.setId(bestMatchTask.getId());
                                
                                // Also update Google Task ID if it's missing in our task but present in the existing one
                                if (task.getGoogleTaskId() == null && bestMatchTask.getGoogleTaskId() != null) {
                                    task.setGoogleTaskId(bestMatchTask.getGoogleTaskId());
                                }
                                
                                // Delete any other duplicates
                                for (DocumentSnapshot doc : docs) {
                                    if (!doc.getId().equals(bestMatchTask.getId())) {
                                        db.collection(COLLECTION_TASKS).document(doc.getId()).delete();
                                        Log.d(TAG, "Deleted duplicate task with ID: " + doc.getId());
                                    }
                                }
                            }
                        } else if (docs.size() == 1) {
                            // Single match, check if it's the same task
                            DocumentSnapshot doc = docs.get(0);
                            Task existingTask = doc.toObject(Task.class);
                            
                            if (existingTask != null) {
                                boolean sameDate = (task.getDate() != null && existingTask.getDate() != null &&
                                                   Math.abs(task.getDate().getTime() - existingTask.getDate().getTime()) < 86400000);
                                
                                if (sameDate) {
                                    // Same task, just update the status
                                    task.setId(existingTask.getId());
                                    foundSimilar = true;
                                    
                                    // Also update Google Task ID if needed
                                    if (task.getGoogleTaskId() == null && existingTask.getGoogleTaskId() != null) {
                                        task.setGoogleTaskId(existingTask.getGoogleTaskId());
                                    }
                                }
                            }
                        }
                        
                        // Now save the task, either with existing ID or as new
                        saveTaskDirectly(task, callback);
                        
                    } else {
                        // Query failed, fall back to direct save
                        saveTaskDirectly(task, callback);
                    }
                });
        } else {
            // Not enough info to check similarity, just save directly
            saveTaskDirectly(task, callback);
        }
    }
    
    /**
     * Helper method to directly save a task to Firestore
     */
    private void saveTaskDirectly(Task task, TaskCallback callback) {
        Map<String, Object> taskMap = task.toMap();
        
        // Get the document reference
        DocumentReference docRef;
        if (task.getId() != null && !task.getId().isEmpty()) {
            // Use task's ID as the document ID
            docRef = db.collection(COLLECTION_TASKS).document(task.getId());
        } else {
            // Create a new document with auto-generated ID
            docRef = db.collection(COLLECTION_TASKS).document();
            task.setId(docRef.getId());
            taskMap = task.toMap(); // Update map with the new ID
        }
        
        docRef.set(taskMap)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Task saved successfully: " + task.getId());
                if (callback != null) {
                    callback.onSuccess();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving task", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            });
    }
    
    /**
     * Update only specific fields of a task without creating duplicates
     * This is especially useful for status updates
     */
    public void updateTaskFields(String taskId, Map<String, Object> fields, TaskCallback callback) {
        if (taskId == null || fields == null || fields.isEmpty()) {
            if (callback != null) {
                callback.onFailure(new IllegalArgumentException("Task ID and fields cannot be null or empty"));
            }
            return;
        }

        // Debounce rapid task updates to prevent duplicates
        if (isRecentlyUpdated(taskId)) {
            Log.d(TAG, "Task was recently updated, debouncing field update: " + taskId);
            if (callback != null) {
                callback.onSuccess();
            }
            return;
        }
        
        markTaskAsUpdated(taskId);
        
        // First check if document exists
        db.collection(COLLECTION_TASKS)
            .document(taskId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    
                    if (document.exists()) {
                        // Document exists, update it
                        db.collection(COLLECTION_TASKS)
                            .document(taskId)
                            .update(fields)
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Task fields updated successfully: " + taskId);
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error updating task fields", e);
                                if (callback != null) {
                                    callback.onFailure(e);
                                }
                            });
                    } else {
                        // Document does not exist, create it with these fields
                        Log.d(TAG, "Task document does not exist, creating new one: " + taskId);
                        
                        // Create a new task with the required fields
                        Task newTask = new Task();
                        newTask.setId(taskId);
                        
                        // Add all fields from the map to the new task
                        Map<String, Object> taskMap = newTask.toMap();
                        taskMap.putAll(fields);
                        
                        // Add some defaults if they're not in the fields
                        if (!fields.containsKey("userEmail") && fields.containsKey("googleTaskId")) {
                            // Try to find the user email from another task with the same Google Task ID
                            String googleTaskId = (String) fields.get("googleTaskId");
                            if (googleTaskId != null) {
                                findUserEmailByGoogleTaskId(googleTaskId, (userEmail) -> {
                                    if (userEmail != null) {
                                        taskMap.put("userEmail", userEmail);
                                    }
                                    saveNewTaskWithFields(taskId, taskMap, callback);
                                });
                                return; // Exit early as we're handling the callback in the findUserEmailByGoogleTaskId method
                            }
                        }
                        
                        // Save the task with the fields
                        saveNewTaskWithFields(taskId, taskMap, callback);
                    }
                } else {
                    // Error checking if document exists
                    Log.e(TAG, "Error checking if task document exists", task.getException());
                    if (callback != null) {
                        callback.onFailure(task.getException());
                    }
                }
            });
    }
    
    /**
     * Helper method to save a new task with the provided fields
     */
    private void saveNewTaskWithFields(String taskId, Map<String, Object> taskMap, TaskCallback callback) {
        db.collection(COLLECTION_TASKS)
            .document(taskId)
            .set(taskMap)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "New task created with fields: " + taskId);
                if (callback != null) {
                    callback.onSuccess();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error creating new task with fields", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            });
    }
    
    /**
     * Try to find a user email from another task with the same Google Task ID
     */
    private void findUserEmailByGoogleTaskId(String googleTaskId, UserEmailCallback callback) {
        db.collection(COLLECTION_TASKS)
            .whereEqualTo("googleTaskId", googleTaskId)
            .limit(1)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && !task.getResult().isEmpty()) {
                    DocumentSnapshot document = task.getResult().getDocuments().get(0);
                    String userEmail = document.getString("userEmail");
                    callback.onUserEmailFound(userEmail);
                } else {
                    callback.onUserEmailFound(null);
                }
            });
    }
    
    /**
     * Callback interface for finding user email
     */
    private interface UserEmailCallback {
        void onUserEmailFound(String userEmail);
    }

    /**
     * Update just the status of a task - optimized method to prevent duplication
     */
    public void updateTaskStatus(String taskId, String status, TaskCallback callback) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("status", status);
        
        updateTaskFields(taskId, fields, callback);
    }
    
    /**
     * Get all tasks for a specific user
     */
    public void getTasksByUser(String userEmail, TasksCallback callback) {
        // Check cache first
        if (userTasksCache.containsKey(userEmail) && userTasksCacheTimestamp.containsKey(userEmail)) {
            long lastCacheTime = userTasksCacheTimestamp.get(userEmail);
            if (System.currentTimeMillis() - lastCacheTime < CACHE_EXPIRATION_MS) {
                Log.d(TAG, "Returning tasks from cache for user: " + userEmail);
                callback.onSuccess(userTasksCache.get(userEmail));
                return;
            }
        }

        // Use a simpler query that doesn't require complex compound index
        db.collection(COLLECTION_TASKS)
            .whereEqualTo("userEmail", userEmail)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && callback != null) {
                    List<Task> tasks = new ArrayList<>();
                    Map<String, Task> uniqueTasks = new HashMap<>();
                    
                    for (DocumentSnapshot document : task.getResult()) {
                        Task taskObj = document.toObject(Task.class);
                        if (taskObj != null) {
                            // Use a composite key of title+date to identify potential duplicates
                            String key = taskObj.getTitle() + "_" + (taskObj.getDate() != null ? taskObj.getDate().getTime() : "null");
                            
                            // If we already have this task, keep the one with more complete information
                            if (uniqueTasks.containsKey(key)) {
                                Task existingTask = uniqueTasks.get(key);
                                
                                // Prefer tasks with Google Task ID if available
                                if (existingTask.getGoogleTaskId() == null && taskObj.getGoogleTaskId() != null) {
                                    uniqueTasks.put(key, taskObj);
                                    // Delete the duplicate from Firestore
                                    db.collection(COLLECTION_TASKS).document(existingTask.getId()).delete();
                                    Log.d(TAG, "Deleted duplicate task during query: " + existingTask.getId());
                                } else if (existingTask.getGoogleTaskId() != null && taskObj.getGoogleTaskId() == null) {
                                    // Keep existing, delete the duplicate
                                    db.collection(COLLECTION_TASKS).document(taskObj.getId()).delete();
                                    Log.d(TAG, "Deleted duplicate task during query: " + taskObj.getId());
                                } 
                                // If both have Google Task ID but they're different, keep both
                                else if (existingTask.getGoogleTaskId() != null && taskObj.getGoogleTaskId() != null && 
                                        !existingTask.getGoogleTaskId().equals(taskObj.getGoogleTaskId())) {
                                    // Different Google tasks, generate a different key
                                    uniqueTasks.put(key + "_" + taskObj.getGoogleTaskId(), taskObj);
                                }
                                // Otherwise keep what we have
                            } else {
                                // New unique task
                                uniqueTasks.put(key, taskObj);
                            }
                        }
                    }
                    
                    // Convert map to list
                    tasks.addAll(uniqueTasks.values());
                    
                    // Sort in memory
                    if (!tasks.isEmpty()) {
                        tasks.sort((task1, task2) -> {
                            // First compare by date
                            int dateCompare = 0;
                            if (task1.getDate() != null && task2.getDate() != null) {
                                dateCompare = task1.getDate().compareTo(task2.getDate());
                            } else if (task1.getDate() == null && task2.getDate() != null) {
                                return -1;
                            } else if (task1.getDate() != null && task2.getDate() == null) {
                                return 1;
                            }
                            
                            // If dates are equal, compare by startTime
                            if (dateCompare == 0) {
                                if (task1.getStartTime() != null && task2.getStartTime() != null) {
                                    return task1.getStartTime().compareTo(task2.getStartTime());
                                } else if (task1.getStartTime() == null && task2.getStartTime() != null) {
                                    return -1;
                                } else if (task1.getStartTime() != null && task2.getStartTime() == null) {
                                    return 1;
                                }
                            }
                            return dateCompare;
                        });
                    }
                    
                    // Cache the result
                    userTasksCache.put(userEmail, tasks);
                    userTasksCacheTimestamp.put(userEmail, System.currentTimeMillis());

                    callback.onSuccess(tasks);
                } else if (callback != null) {
                    callback.onFailure(task.getException());
                }
            });
    }
    
    /**
     * Delete a task from Firestore
     */
    public void deleteTask(Task task, TaskCallback callback) {
        db.collection(COLLECTION_TASKS)
            .document(task.getId())
            .delete()
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Task deleted successfully: " + task.getId());
                if (callback != null) {
                    callback.onSuccess();
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error deleting task", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            });
    }
    
    /**
     * Count tasks by user and status
     */
    public void getTaskCountByStatusAndUser(String status, String userEmail, TaskCountCallback callback) {
        db.collection(COLLECTION_TASKS)
            .whereEqualTo("userEmail", userEmail)
            .whereEqualTo("status", status)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && callback != null) {
                    callback.onSuccess(task.getResult().size());
                } else if (callback != null) {
                    callback.onFailure(task.getException());
                }
            });
    }
    
    /**
     * Count pending tasks for a user
     */
    public void getPendingTasksCountByUser(String userEmail, TaskCountCallback callback) {
        getTaskCountByStatusAndUser("PENDING", userEmail, callback);
    }
    
    /**
     * Count completed tasks for a user
     */
    public void getCompletedTasksCountByUser(String userEmail, TaskCountCallback callback) {
        getTaskCountByStatusAndUser("COMPLETED", userEmail, callback);
    }
    
    /**
     * Count all tasks for a user
     */
    public void getTaskCountByUser(String userEmail, TaskCountCallback callback) {
        db.collection(COLLECTION_TASKS)
            .whereEqualTo("userEmail", userEmail)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && callback != null) {
                    callback.onSuccess(task.getResult().size());
                } else if (callback != null) {
                    callback.onFailure(task.getException());
                }
            });
    }
    
    /**
     * Get a specific task by ID
     */
    public void getTaskById(String taskId, SingleTaskCallback callback) {
        db.collection(COLLECTION_TASKS)
            .document(taskId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && callback != null) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        Task taskObj = document.toObject(Task.class);
                        callback.onSuccess(taskObj);
                    } else {
                        callback.onSuccess(null);
                    }
                } else if (callback != null) {
                    callback.onFailure(task.getException());
                }
            });
    }
    
    /**
     * Get tasks for a specific date and user
     */
    public void getTasksForDateByUser(Date date, String userEmail, TasksCallback callback) {
        String cacheKey = userEmail + "_" + date.getTime();
        
        // Check cache first
        if (dateTasksCache.containsKey(cacheKey) && dateTasksCacheTimestamp.containsKey(cacheKey)) {
            long lastCacheTime = dateTasksCacheTimestamp.get(cacheKey);
            if (System.currentTimeMillis() - lastCacheTime < CACHE_EXPIRATION_MS) {
                Log.d(TAG, "Returning tasks from cache for date: " + date + " and user: " + userEmail);
                callback.onSuccess(dateTasksCache.get(cacheKey));
                return;
            }
        }

        db.collection(COLLECTION_TASKS)
            .whereEqualTo("userEmail", userEmail)
            .whereEqualTo("date", date)
            .orderBy("startTime", Query.Direction.ASCENDING)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && callback != null) {
                    List<Task> tasks = new ArrayList<>();
                    for (DocumentSnapshot document : task.getResult()) {
                        Task taskObj = document.toObject(Task.class);
                        tasks.add(taskObj);
                    }
                    
                    // Cache the result
                    dateTasksCache.put(cacheKey, tasks);
                    dateTasksCacheTimestamp.put(cacheKey, System.currentTimeMillis());

                    callback.onSuccess(tasks);
                } else if (callback != null) {
                    callback.onFailure(task.getException());
                }
            });
    }
    
    /**
     * Convert a Firestore document to a Task object
     */
    private Task documentToTask(DocumentSnapshot document) {
        if (document == null) {
            return null;
        }
        
        Task task = new Task();
        task.setId(document.getId());
        
        // Get basic task properties
        task.setTitle(document.getString("title"));
        task.setDescription(document.getString("description"));
        task.setStatus(document.getString("status"));
        task.setCategory(document.getString("category"));
        task.setStartTime(document.getString("startTime"));
        task.setEndTime(document.getString("endTime"));
        task.setUserEmail(document.getString("userEmail"));
        task.setGoogleTaskId(document.getString("googleTaskId"));
        task.setAttachmentUri(document.getString("attachmentUri"));
        task.setAttachmentName(document.getString("attachmentName"));
        task.setDriveFileId(document.getString("driveFileId"));
        
        // Get the date and handle null
        Date date = document.getDate("date");
        if (date != null) {
            task.setDate(date);
        }
        
        return task;
    }

    /**
     * Delete all tasks for a specific user
     */
    public void deleteAllTasksForUser(String userEmail, TaskCallback callback) {
        Log.d(TAG, "Attempting to delete all tasks for user: " + userEmail);
        
        db.collection(COLLECTION_TASKS)
            .whereEqualTo("userEmail", userEmail)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Create a batched write to efficiently delete multiple documents
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    
                    // Add each document to the batch for deletion
                    for (DocumentSnapshot document : task.getResult()) {
                        batch.delete(document.getReference());
                    }
                    
                    // Execute batch delete
                    batch.commit()
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Successfully deleted all tasks for user: " + userEmail);
                            if (callback != null) {
                                callback.onSuccess();
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Error deleting tasks in batch", e);
                            if (callback != null) {
                                callback.onFailure(e);
                            }
                        });
                } else {
                    Log.e(TAG, "Error getting tasks to delete", task.getException());
                    if (callback != null) {
                        callback.onFailure(task.getException());
                    }
                }
            });
    }
}