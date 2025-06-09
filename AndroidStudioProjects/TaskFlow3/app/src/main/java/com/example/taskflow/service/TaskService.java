package com.example.taskflow.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.taskflow.db.TaskDatabase;
import com.example.taskflow.model.Task;
import com.example.taskflow.model.TaskList;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.tasks.Tasks;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.TaskLists;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Calendar;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

public class TaskService {
    private static final String TAG = "TaskService";
    private static final String APPLICATION_NAME = "TaskFlow";
    
    private final Context context;
    private final String accountName;
    private final Executor executor;
    private GoogleAccountCredential credential;
    private Tasks service;
    private final Tasks mService;
    private final ExecutorService executorService;
    private final String userEmail;
    
    // Cache task lists to avoid repeated API calls
    private List<TaskList> cachedTaskLists = null;
    
    // Cache tasks to reduce API calls (expires after 5 minutes)
    private List<Task> cachedTasks = null;
    private long cachedTasksTimestamp = 0;
    private static final long CACHE_EXPIRATION_MS = 5 * 60 * 1000; // 5 minutes
    
    // Cache for task conflicts check
    private Map<String, List<Task>> dateTasksCache = new HashMap<>();
    private long dateTasksCacheTimestamp = 0;

    public interface TaskCallback {
        void onSuccess(String taskId);
        void onFailure(Exception e);
    }
    
    public interface TaskListsCallback {
        void onSuccess(List<TaskList> taskLists);
        void onFailure(Exception e);
    }

    public TaskService(Context context, String accountName) {
        this.context = context;
        this.accountName = accountName;
        this.executor = Executors.newSingleThreadExecutor();
        setupCredential();
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context,
                Collections.singleton(TasksScopes.TASKS));
                
        credential.setSelectedAccountName(accountName);
        userEmail = accountName;

        NetHttpTransport transport = new NetHttpTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        
        mService = new Tasks.Builder(transport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        
        executorService = Executors.newCachedThreadPool();
    }

    private void setupCredential() {
        // Initialize credentials and service object
        credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singletonList(TasksScopes.TASKS));
        credential.setSelectedAccountName(accountName);

        // Use NetHttpTransport instead of AndroidHttp
        HttpTransport transport = new NetHttpTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        
        service = new Tasks.Builder(transport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void createTask(Task localTask, TaskCallback callback) {
        executor.execute(() -> {
            try {
                // First, find or create a default task list
                String taskListId = getDefaultTaskListId();
                
                // Create a Google Tasks API task
                com.google.api.services.tasks.model.Task googleTask = convertToGoogleTask(localTask);
                
                // Insert the task
                com.google.api.services.tasks.model.Task createdTask = 
                    service.tasks().insert(taskListId, googleTask).execute();
                
                // Return the created task ID
                if (callback != null) {
                    callback.onSuccess(createdTask.getId());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating task", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            }
        });
    }

    private String getDefaultTaskListId() throws IOException {
        // Get the first available task list or create one if none exists
        TaskLists result = service.tasklists().list().execute();
        
        if (result.getItems() != null && !result.getItems().isEmpty()) {
            // Return the first task list ID
            return result.getItems().get(0).getId();
        } else {
            // Create a new task list
            com.google.api.services.tasks.model.TaskList taskList = new com.google.api.services.tasks.model.TaskList();
            taskList.setTitle("TaskFlow");
            com.google.api.services.tasks.model.TaskList createdList = service.tasklists().insert(taskList).execute();
            return createdList.getId();
        }
    }

    private com.google.api.services.tasks.model.Task convertToGoogleTask(Task localTask) {
        com.google.api.services.tasks.model.Task googleTask = new com.google.api.services.tasks.model.Task();
        
        // Set title and notes
        String title = localTask.getTitle();
        String description = localTask.getDescription() != null ? localTask.getDescription() : "";
        
        // Add time information to title since Google Tasks doesn't display time in UI
        String startTime = localTask.getStartTime();
        String endTime = localTask.getEndTime();
        
        // Always include time in title if available (even if just start time)
        if (startTime != null && !startTime.isEmpty()) {
            if (endTime != null && !endTime.isEmpty()) {
                // Both start and end times available
                title = title + " (" + startTime + " - " + endTime + ")";
            } else {
                // Only start time available
                title = title + " (" + startTime + ")";
            }
        }
        
        googleTask.setTitle(title);
        
        // Add time information to notes as well
        StringBuilder timeInfoBuilder = new StringBuilder();
        if (startTime != null && !startTime.isEmpty()) {
            timeInfoBuilder.append("Start Time: ").append(startTime);
            
            if (endTime != null && !endTime.isEmpty()) {
                timeInfoBuilder.append("\nEnd Time: ").append(endTime);
            }
            
            // Add the time information to the beginning of the description
            if (timeInfoBuilder.length() > 0) {
                description = timeInfoBuilder.toString() + "\n\n" + description;
            }
        }
        
        googleTask.setNotes(description);
        
        // Set due date with proper RFC3339 format
        try {
            Date taskDate = localTask.getDate();
            if (taskDate != null) {
                // Format the date in RFC3339 format without milliseconds
                SimpleDateFormat rfc3339Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                rfc3339Format.setTimeZone(TimeZone.getTimeZone("UTC"));
                
                // Prioritize setting start time as the due time if available
                // This is a key change - using start time instead of end time
                String timeToUse = startTime != null && !startTime.isEmpty() ? startTime : endTime;
                
                if (timeToUse != null && !timeToUse.isEmpty()) {
                    try {
                        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
                        Date timeDate = timeFormat.parse(timeToUse);
                        
                        if (timeDate != null) {
                            Calendar dateCal = Calendar.getInstance();
                            dateCal.setTime(taskDate);
                            
                            Calendar timeCal = Calendar.getInstance();
                            timeCal.setTime(timeDate);
                            
                            // Combine date and time
                            dateCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                            dateCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                            dateCal.set(Calendar.SECOND, 0);
                            
                            taskDate = dateCal.getTime();
                        }
                    } catch (ParseException e) {
                        Log.e(TAG, "Error parsing time: " + e.getMessage());
                    }
                }
                
                // Format the combined date time
                String dueDate = rfc3339Format.format(taskDate);
                Log.d(TAG, "Setting due date with time: " + dueDate);
                googleTask.setDue(dueDate);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting due date: " + e.getMessage());
        }
        
        return googleTask;
    }
    
    /**
     * Formats a date according to RFC 3339 specification required by Google Tasks API
     */
    private String formatDateTimeForGoogleTasks(Date date) {
        // Google Tasks API requires RFC 3339 format
        TimeZone tz = TimeZone.getDefault();
        SimpleDateFormat rfc3339Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        rfc3339Format.setTimeZone(tz);
        return rfc3339Format.format(date);
    }

    public interface TaskListCallback {
        void onSuccess(List<com.google.api.services.tasks.model.TaskList> taskLists);
        void onFailure(Exception e);
    }

    /**
     * Retrieves all tasks from the user's Google Tasks account
     * and synchronizes with local database
     */
    public void getAllTasks(TaskListCallback listCallback, TasksCallback tasksCallback) {
        // Check if we have a valid cache
        long currentTime = System.currentTimeMillis();
        if (cachedTasks != null && (currentTime - cachedTasksTimestamp) < CACHE_EXPIRATION_MS) {
            // Use cached tasks if not expired
            if (tasksCallback != null) {
                tasksCallback.onSuccess(cachedTasks);
            }
            return;
        }
        
        executor.execute(() -> {
            try {
                // First get available task lists
                TaskLists taskLists = service.tasklists().list().execute();
                
                if (listCallback != null) {
                    listCallback.onSuccess(taskLists.getItems());
                }
                
                List<Task> allTasks = new ArrayList<>();
                
                // For each task list, get all tasks
                if (taskLists.getItems() != null) {
                    for (com.google.api.services.tasks.model.TaskList taskList : taskLists.getItems()) {
                        com.google.api.services.tasks.model.Tasks tasks = 
                                service.tasks().list(taskList.getId()).execute();
                        
                        if (tasks.getItems() != null) {
                            for (com.google.api.services.tasks.model.Task googleTask : tasks.getItems()) {
                                Task localTask = convertFromGoogleTask(googleTask);
                                allTasks.add(localTask);
                                
                                // Save or update each task in the local database
                                TaskDatabase.getInstance(context)
                                    .taskDao()
                                    .insertTask(localTask);
                            }
                        }
                    }
                }
                
                // After syncing with Google, merge with any locally-created tasks that haven't been synced
                List<Task> localOnlyTasks = TaskDatabase.getInstance(context)
                    .taskDao()
                    .getLocalOnlyTasks();
                    
                if (localOnlyTasks != null && !localOnlyTasks.isEmpty()) {
                    allTasks.addAll(localOnlyTasks);
                }
                
                // Update cache
                cachedTasks = allTasks;
                cachedTasksTimestamp = System.currentTimeMillis();
                
                if (tasksCallback != null) {
                    tasksCallback.onSuccess(allTasks);
                }
                
            } catch (IOException e) {
                Log.e(TAG, "Error fetching tasks", e);
                
                // On error, return all local tasks
                List<Task> localTasks = TaskDatabase.getInstance(context)
                    .taskDao()
                    .getAllTasks();
                    
                if (tasksCallback != null) {
                    if (localTasks != null && !localTasks.isEmpty()) {
                        // Update cache with local tasks
                        cachedTasks = localTasks;
                        cachedTasksTimestamp = System.currentTimeMillis();
                        tasksCallback.onSuccess(localTasks);
                    } else {
                        tasksCallback.onFailure(e);
                    }
                }
            }
        });
    }

    /**
     * Converts a Google Task to our local Task model
     */
    private Task convertFromGoogleTask(com.google.api.services.tasks.model.Task googleTask) {
        Task localTask = new Task();
        
        // Set basic properties
        localTask.setId(UUID.randomUUID().toString());
        localTask.setGoogleTaskId(googleTask.getId());
        
        String title = googleTask.getTitle();
        String notes = googleTask.getNotes();
        
        // Parse title to extract possible time information
        String extractedTitle = title;
        String startTime = "";
        String endTime = "";
        
        // Extract time if format is "Task Title (10:00 AM - 11:00 AM)"
        Pattern fullTimePattern = Pattern.compile("(.*) \\((\\d{1,2}:\\d{2} [AP]M) - (\\d{1,2}:\\d{2} [AP]M)\\)");
        Matcher fullTimeMatcher = fullTimePattern.matcher(title);
        
        // Extract time if format is "Task Title (10:00 AM)" - just start time
        Pattern singleTimePattern = Pattern.compile("(.*) \\((\\d{1,2}:\\d{2} [AP]M)\\)");
        Matcher singleTimeMatcher = singleTimePattern.matcher(title);
        
        if (fullTimeMatcher.find()) {
            // Full time range found
            extractedTitle = fullTimeMatcher.group(1);
            startTime = fullTimeMatcher.group(2);
            endTime = fullTimeMatcher.group(3);
        } else if (singleTimeMatcher.find()) {
            // Just start time found
            extractedTitle = singleTimeMatcher.group(1);
            startTime = singleTimeMatcher.group(2);
            // We'll try to calculate a default end time later
        }
        
        localTask.setTitle(extractedTitle);
        localTask.setStartTime(startTime);
        localTask.setEndTime(endTime);
        
        // Extract time information from notes if available
        if (notes != null) {
            // Check for "Start Time:" and "End Time:" format
            Pattern startTimePattern = Pattern.compile("Start Time: (\\d{1,2}:\\d{2} [AP]M)");
            Pattern endTimePattern = Pattern.compile("End Time: (\\d{1,2}:\\d{2} [AP]M)");
            
            Matcher startTimeMatcher = startTimePattern.matcher(notes);
            if (startTimeMatcher.find() && startTime.isEmpty()) {
                startTime = startTimeMatcher.group(1);
                localTask.setStartTime(startTime);
            }
            
            Matcher endTimeMatcher = endTimePattern.matcher(notes);
            if (endTimeMatcher.find() && endTime.isEmpty()) {
                endTime = endTimeMatcher.group(1);
                localTask.setEndTime(endTime);
            }
            
            // Extract description - skip time information at the beginning
            int descriptionStart = notes.indexOf("\n\n");
            if (descriptionStart != -1 && descriptionStart + 2 < notes.length()) {
                localTask.setDescription(notes.substring(descriptionStart + 2));
            } else {
                // Use the whole note as description if we can't find the separator
                localTask.setDescription(notes);
            }
        } else {
            localTask.setDescription("");
        }
        
        // Parse due date with time - this is the Google Task's due date
        if (googleTask.getDue() != null) {
            try {
                // Try both formats with and without milliseconds
                Date dueDate = null;
                try {
                    // RFC 3339 format with milliseconds: 2023-07-15T10:00:00.000Z
                    SimpleDateFormat rfc3339FormatWithMS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                    rfc3339FormatWithMS.setTimeZone(TimeZone.getTimeZone("UTC"));
                    dueDate = rfc3339FormatWithMS.parse(googleTask.getDue());
                } catch (ParseException e1) {
                    try {
                        // Try without milliseconds: 2023-07-15T10:00:00Z
                        SimpleDateFormat rfc3339FormatNoMS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                        rfc3339FormatNoMS.setTimeZone(TimeZone.getTimeZone("UTC"));
                        dueDate = rfc3339FormatNoMS.parse(googleTask.getDue());
                    } catch (ParseException e2) {
                        Log.e(TAG, "Error parsing due date: " + e2.getMessage());
                    }
                }
                
                if (dueDate != null) {
                    localTask.setDate(dueDate);
                    
                    // If we don't have time info from title or notes, extract it from the due date
                    SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
                    String timeFromDue = timeFormat.format(dueDate);
                    
                    // If we have no time information at all, use due date time as start time
                    if (startTime.isEmpty() && endTime.isEmpty()) {
                        startTime = timeFromDue;
                        localTask.setStartTime(startTime);
                        
                        // Calculate an end time 1 hour after start time
                        try {
                            Date startTimeDate = timeFormat.parse(startTime);
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(startTimeDate);
                            cal.add(Calendar.HOUR, 1);
                            endTime = timeFormat.format(cal.getTime());
                            localTask.setEndTime(endTime);
                        } catch (ParseException e) {
                            Log.e(TAG, "Error calculating end time: " + e.getMessage());
                        }
                    } 
                    // If we only have start time but no end time
                    else if (!startTime.isEmpty() && endTime.isEmpty()) {
                        // Calculate an end time 1 hour after start time
                        try {
                            Date startTimeDate = timeFormat.parse(startTime);
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(startTimeDate);
                            cal.add(Calendar.HOUR, 1);
                            endTime = timeFormat.format(cal.getTime());
                            localTask.setEndTime(endTime);
                        } catch (ParseException e) {
                            Log.e(TAG, "Error calculating end time: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling due date: " + e.getMessage());
                localTask.setDate(new Date());  // Default to current date
            }
        } else {
            localTask.setDate(new Date());  // Default to current date
        }
        
        // Set status (completed or pending)
        String status = "PENDING";
        if (googleTask.getCompleted() != null) {
            status = "COMPLETED";
        }
        localTask.setStatus(status);
        
        return localTask;
    }

    /**
     * Callback for retrieving tasks
     */
    public interface TasksCallback {
        void onSuccess(List<Task> tasks);
        void onFailure(Exception e);
    }

    /**
     * Checks if a task's time conflicts with any existing tasks on the same date
     * 
     * @param newTask The task to check for conflicts
     * @param callback Callback with boolean result (true if conflict exists)
     */
    public void checkForTimeConflict(Task newTask, TimeConflictCallback callback) {
        executor.execute(() -> {
            try {
                // Get all tasks first
                List<Task> allTasks = new ArrayList<>();
                
                // Get the user's task lists
                TaskLists taskLists = service.tasklists().list().execute();
                
                if (taskLists.getItems() != null) {
                    for (com.google.api.services.tasks.model.TaskList taskList : taskLists.getItems()) {
                        com.google.api.services.tasks.model.Tasks tasks = 
                                service.tasks().list(taskList.getId()).execute();
                        
                        if (tasks.getItems() != null) {
                            for (com.google.api.services.tasks.model.Task googleTask : tasks.getItems()) {
                                Task existingTask = convertFromGoogleTask(googleTask);
                                allTasks.add(existingTask);
                            }
                        }
                    }
                }
                
                // Check for conflicts
                boolean hasConflict = hasTimeConflict(newTask, allTasks);
                
                if (callback != null) {
                    callback.onConflictCheckComplete(hasConflict);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error checking for time conflicts", e);
                if (callback != null) {
                    callback.onConflictCheckComplete(false); // Default to no conflict on error
                }
            }
        });
    }
    
    /**
     * Helper method to check if a task conflicts with any in the provided list
     */
    private boolean hasTimeConflict(Task newTask, List<Task> existingTasks) {
        // Skip if no time info in new task
        if (newTask.getStartTime() == null || newTask.getEndTime() == null || 
            newTask.getStartTime().isEmpty() || newTask.getEndTime().isEmpty() ||
            newTask.getDate() == null) {
            return false;
        }
        
        // Get date-only part of the new task
        Calendar newTaskCal = Calendar.getInstance();
        newTaskCal.setTime(newTask.getDate());
        int newTaskYear = newTaskCal.get(Calendar.YEAR);
        int newTaskMonth = newTaskCal.get(Calendar.MONTH);
        int newTaskDay = newTaskCal.get(Calendar.DAY_OF_MONTH);
        
        // Parse the new task's start and end times
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
        
        try {
            Date newTaskStartTime = timeFormat.parse(newTask.getStartTime());
            Date newTaskEndTime = timeFormat.parse(newTask.getEndTime());
            
            if (newTaskStartTime == null || newTaskEndTime == null) {
                return false;
            }
            
            // For each existing task
            for (Task existingTask : existingTasks) {
                // Skip self-comparison if this is an edit operation
                if (newTask.getId().equals(existingTask.getId())) {
                    continue;
                }
                
                // Skip tasks without proper time info
                if (existingTask.getStartTime() == null || existingTask.getEndTime() == null || 
                    existingTask.getStartTime().isEmpty() || existingTask.getEndTime().isEmpty() ||
                    existingTask.getDate() == null) {
                    continue;
                }
                
                // Check if the date matches
                Calendar existingTaskCal = Calendar.getInstance();
                existingTaskCal.setTime(existingTask.getDate());
                
                // Only check tasks on the same date
                if (existingTaskCal.get(Calendar.YEAR) == newTaskYear &&
                    existingTaskCal.get(Calendar.MONTH) == newTaskMonth &&
                    existingTaskCal.get(Calendar.DAY_OF_MONTH) == newTaskDay) {
                    
                    // Parse existing task time
                    Date existingTaskStartTime = timeFormat.parse(existingTask.getStartTime());
                    Date existingTaskEndTime = timeFormat.parse(existingTask.getEndTime());
                    
                    if (existingTaskStartTime == null || existingTaskEndTime == null) {
                        continue;
                    }
                    
                    // Check for time overlap
                    // Conflict exists if:
                    // - New task starts during existing task (newStart >= existingStart AND newStart < existingEnd)
                    // - New task ends during existing task (newEnd > existingStart AND newEnd <= existingEnd)
                    // - New task completely contains existing task (newStart <= existingStart AND newEnd >= existingEnd)
                    
                    boolean newStartsDuringExisting = !newTaskStartTime.before(existingTaskStartTime) && 
                                                       newTaskStartTime.before(existingTaskEndTime);
                                                       
                    boolean newEndsDuringExisting = newTaskEndTime.after(existingTaskStartTime) && 
                                                   !newTaskEndTime.after(existingTaskEndTime);
                                                   
                    boolean newContainsExisting = !newTaskStartTime.after(existingTaskStartTime) && 
                                                  !newTaskEndTime.before(existingTaskEndTime);
                                                  
                    if (newStartsDuringExisting || newEndsDuringExisting || newContainsExisting) {
                        Log.d(TAG, "Time conflict found with task: " + existingTask.getTitle());
                        return true; // Conflict found
                    }
                }
            }
            
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing task times", e);
            return false; // On error, assume no conflict
        }
        
        return false; // No conflicts found
    }
    
    /**
     * Callback interface for time conflict check
     */
    public interface TimeConflictCallback {
        void onConflictCheckComplete(boolean hasConflict);
    }

    /**
     * Updates the completion status of a task in Google Tasks
     * 
     * @param task The task to update
     * @param isCompleted Whether the task should be marked as completed
     */
    public void updateTaskStatus(Task task, boolean isCompleted) {
        executor.execute(() -> {
            try {
                if (task.getGoogleTaskId() != null) {
                    // First, we need to find which task list contains this task
                    TaskLists taskLists = service.tasklists().list().execute();
                    
                    if (taskLists.getItems() != null) {
                        for (com.google.api.services.tasks.model.TaskList taskList : taskLists.getItems()) {
                            try {
                                // Get the task from Google Tasks
                                com.google.api.services.tasks.model.Task googleTask = 
                                    service.tasks().get(taskList.getId(), task.getGoogleTaskId()).execute();
                                
                                // Update completion status
                                if (isCompleted) {
                                    // Set completed timestamp to now (RFC 3339 format)
                                    SimpleDateFormat rfc3339Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                                    rfc3339Format.setTimeZone(TimeZone.getTimeZone("UTC"));
                                    String completedTime = rfc3339Format.format(new Date());
                                    googleTask.setCompleted(completedTime);
                                    googleTask.setStatus("completed");
                                } else {
                                    // Clear completed timestamp
                                    googleTask.setCompleted(null);
                                    googleTask.setStatus("needsAction");
                                }
                                
                                // Update the task
                                service.tasks().update(taskList.getId(), task.getGoogleTaskId(), googleTask).execute();
                                
                                // Success, no need to continue searching other task lists
                                Log.d(TAG, "Task status updated successfully for: " + task.getTitle());
                                return;
                            } catch (IOException e) {
                                // Task not found in this list, continue to next list
                                continue;
                            }
                        }
                    }
                    
                    Log.e(TAG, "Task not found in any list: " + task.getGoogleTaskId());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating task status", e);
            }
        });
    }
    
    /**
     * Deletes a task from Google Tasks
     * 
     * @param task The task to delete
     */
    public void deleteTask(Task task) {
        executor.execute(() -> {
            try {
                if (task.getGoogleTaskId() != null) {
                    // First, we need to find which task list contains this task
                    TaskLists taskLists = service.tasklists().list().execute();
                    
                    if (taskLists.getItems() != null) {
                        for (com.google.api.services.tasks.model.TaskList taskList : taskLists.getItems()) {
                            try {
                                // Try to delete the task from this list
                                service.tasks().delete(taskList.getId(), task.getGoogleTaskId()).execute();
                                
                                // Success, no need to continue searching other task lists
                                Log.d(TAG, "Task deleted successfully: " + task.getTitle());
                                return;
                            } catch (IOException e) {
                                // Task not found in this list, continue to next list
                                continue;
                            }
                        }
                    }
                    
                    Log.e(TAG, "Task not found in any list: " + task.getGoogleTaskId());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting task", e);
            }
        });
    }
    
    // Get all task lists for the user
    public void getTaskLists(@NonNull TaskListsCallback callback) {
        // Use cached task lists if available to reduce API calls
        if (cachedTaskLists != null) {
            callback.onSuccess(cachedTaskLists);
            return;
        }
        
        executorService.execute(() -> {
            try {
                TaskLists result = mService.tasklists().list().execute();
                List<com.google.api.services.tasks.model.TaskList> items = result.getItems();
                
                List<TaskList> taskLists = new ArrayList<>();
                if (items != null) {
                    for (com.google.api.services.tasks.model.TaskList item : items) {
                        TaskList taskList = new TaskList();
                        taskList.setId(item.getId());
                        taskList.setTitle(item.getTitle());
                        taskLists.add(taskList);
                    }
                }
                
                // Cache the task lists for future use
                cachedTaskLists = taskLists;
                callback.onSuccess(taskLists);
            } catch (IOException e) {
                callback.onFailure(e);
            }
        });
    }
    
    // Get all tasks from a specific task list
    private void getTasksFromList(String taskListId, @NonNull TasksCallback callback) {
        executorService.execute(() -> {
            try {
                com.google.api.services.tasks.model.Tasks result = mService
                        .tasks()
                        .list(taskListId)
                        .execute();
                
                List<com.google.api.services.tasks.model.Task> items = result.getItems();
                List<Task> tasks = new ArrayList<>();
                
                if (items != null) {
                    for (com.google.api.services.tasks.model.Task item : items) {
                        Task task = new Task();
                        task.setId(item.getId());  // Use the Google Task ID as the ID
                        task.setGoogleTaskId(item.getId());  // Also store as Google Task ID for reference
                        task.setTitle(item.getTitle());
                        task.setDescription(item.getNotes());
                        task.setUserEmail(userEmail);
                        
                        // Set task status
                        task.setStatus(item.getCompleted() != null ? "COMPLETED" : "PENDING");
                        
                        // Try to parse due date
                        if (item.getDue() != null) {
                            try {
                                // Parse DateTime directly
                                SimpleDateFormat rfc3339Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                                rfc3339Format.setTimeZone(TimeZone.getTimeZone("UTC"));
                                Date dueDate = rfc3339Format.parse(item.getDue());
                                
                                task.setDate(dueDate);
                                
                                // Extract time information if available
                                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                                SimpleDateFormat endTimeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                                
                                task.setStartTime(timeFormat.format(dueDate));
                                
                                // Add one hour for end time if not specified
                                Date endDate = new Date(dueDate.getTime() + 3600000);
                                task.setEndTime(endTimeFormat.format(endDate));
                            } catch (Exception e) {
                                // Handle date parsing errors
                                task.setDate(new Date());
                            }
                        } else {
                            task.setDate(new Date());
                        }
                        
                        tasks.add(task);
                    }
                }
                
                callback.onSuccess(tasks);
            } catch (IOException e) {
                callback.onFailure(e);
            }
        });
    }
    
    // Find which task list contains a specific task
    private String getTaskListForTask(String taskId) throws IOException {
        // Get all task lists
        TaskLists result = mService.tasklists().list().execute();
        List<com.google.api.services.tasks.model.TaskList> items = result.getItems();
        
        if (items != null) {
            for (com.google.api.services.tasks.model.TaskList taskList : items) {
                try {
                    // Try to get the task from this task list
                    mService.tasks().get(taskList.getId(), taskId).execute();
                    // If no exception, task found in this list
                    return taskList.getId();
                } catch (IOException e) {
                    // Task not found in this list, try next one
                }
            }
        }
        
        // Default to first task list if task not found
        return items != null && !items.isEmpty() ? items.get(0).getId() : null;
    }
    
    public void shutdown() {
        executorService.shutdown();
    }
}
