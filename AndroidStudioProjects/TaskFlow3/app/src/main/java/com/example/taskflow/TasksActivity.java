package com.example.taskflow;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskflow.adapter.CalendarAdapter;
import com.example.taskflow.adapter.TaskAdapter;
import com.example.taskflow.adapter.TaskListAdapter;
import com.example.taskflow.db.FirebaseTaskRepository;
import com.example.taskflow.db.TaskDatabase;
import com.example.taskflow.model.Task;
import com.example.taskflow.service.TaskService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class TasksActivity extends AppCompatActivity implements TaskAdapter.TaskActionListener, CalendarAdapter.OnDateClickListener {

    // UI Components
    private RecyclerView taskRecyclerView;
    private RecyclerView calendarGrid;
    private TaskListAdapter taskAdapter;
    private CalendarAdapter calendarAdapter;
    private TabLayout tabLayout;
    private TextView monthYearText;
    private ImageButton prevMonthButton;
    private ImageButton nextMonthButton;
    private LinearLayout noTasksContainer;
    private TextView emptyViewText;
    private FloatingActionButton fabAddTask;
    
    // Data and Services
    private FirebaseTaskRepository taskRepository;
    private ExecutorService executorService;
    private TaskDatabase taskDatabase;
    private TaskService taskService;
    private String userEmail;
    private List<Task> taskList;
    private GoogleSignInClient mGoogleSignInClient;
    
    // Calendar state
    private Calendar currentDisplayedMonth;
    private Date selectedDate;
    
    // Refresh control
    private long lastRefreshTime = 0;
    private static final long MIN_REFRESH_INTERVAL = 30 * 1000; // 30 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);
        
        // Initialize repositories and services
        taskRepository = FirebaseTaskRepository.getInstance();
        executorService = Executors.newSingleThreadExecutor();
        taskDatabase = TaskDatabase.getInstance(this);
        
        // Configure Google sign-in
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        
        // Initialize views
        initializeViews();
        
        // Setup navigation
        setupBottomNavigation();
        
        // Get user email from intent or Google sign-in
        userEmail = getIntent().getStringExtra("USER_EMAIL");
        if (userEmail == null) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null) {
                userEmail = account.getEmail();
            }
        }
        
        // Initialize TaskService if we have a user email
        if (userEmail != null) {
            taskService = new TaskService(this, userEmail);
        }
        
        // Set up task list and adapters
        taskList = new ArrayList<>();
        
        // Initialize calendar
        initializeCalendar();
        
        // Initialize task list
        initializeTaskList();
        
        // Setup tab selection listener
        setupTabListener();
        
        // Setup calendar navigation buttons
        setupCalendarNavigation();
        
        // Setup add task button
        setupAddTaskButton();
        
        // Load tasks
        loadTasks();
    }
    
    private void initializeViews() {
        taskRecyclerView = findViewById(R.id.task_recycler_view);
        calendarGrid = findViewById(R.id.calendar_grid);
        tabLayout = findViewById(R.id.tabs);
        monthYearText = findViewById(R.id.month_year_text);
        prevMonthButton = findViewById(R.id.prev_month_button);
        nextMonthButton = findViewById(R.id.next_month_button);
        noTasksContainer = findViewById(R.id.no_tasks_container);
        emptyViewText = findViewById(R.id.empty_view_text);
        fabAddTask = findViewById(R.id.fab_add_task);
    }
    
    private void initializeCalendar() {
        // Set up current displayed month and selected date
        currentDisplayedMonth = Calendar.getInstance();
        selectedDate = new Date(); // Today by default
        
        // Set up calendar adapter
        calendarAdapter = new CalendarAdapter(this, currentDisplayedMonth, this);
        
        // Set up calendar grid with 7 columns (days of week)
        calendarGrid.setLayoutManager(new GridLayoutManager(this, 7));
        calendarGrid.setAdapter(calendarAdapter);
        
        // Update month-year text display
        updateMonthYearText();
    }
    
    private void initializeTaskList() {
        // Set up task adapter
        taskAdapter = new TaskListAdapter(this, this);
        
        // Set up task recycler view
        taskRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        taskRecyclerView.setAdapter(taskAdapter);
    }
    
    private void setupTabListener() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                int filterType;
                
                switch (position) {
                    case 0:
                        filterType = TaskListAdapter.FILTER_TODAY;
                        break;
                    case 1:
                        filterType = TaskListAdapter.FILTER_TOMORROW;
                        break;
                    case 2:
                        filterType = TaskListAdapter.FILTER_THIS_WEEK;
                        break;
                    default:
                        filterType = TaskListAdapter.FILTER_TODAY;
                        break;
                }
                
                taskAdapter.applyFilter(filterType);
                updateEmptyState(taskAdapter.getItemCount() == 0, filterType);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not needed
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Clear any date filters when reselecting a tab
                taskAdapter.clearDateFilter();
                updateEmptyState(taskAdapter.getItemCount() == 0, taskAdapter.getCurrentFilter());
            }
        });
    }
    
    private void setupCalendarNavigation() {
        // Previous month button
        prevMonthButton.setOnClickListener(v -> {
            currentDisplayedMonth.add(Calendar.MONTH, -1);
            updateMonthYearText();
            calendarAdapter.updateCalendar(currentDisplayedMonth);
        });
        
        // Next month button
        nextMonthButton.setOnClickListener(v -> {
            currentDisplayedMonth.add(Calendar.MONTH, 1);
            updateMonthYearText();
            calendarAdapter.updateCalendar(currentDisplayedMonth);
        });
    }
    
    private void updateMonthYearText() {
        monthYearText.setText(calendarAdapter.getMonthYearString());
    }
    
    private void setupAddTaskButton() {
        fabAddTask.setOnClickListener(v -> {
            // Launch task creation activity
            Intent intent = new Intent(TasksActivity.this, CreateTaskActivity.class);
            startActivity(intent);
        });
    }
    
    private void setupBottomNavigation() {
        // Find the BottomNavigationView
        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        
        // Set the tasks tab as selected
        bottomNavigation.setSelectedItemId(R.id.nav_tasks);
        
        // Optimize navigation by disabling animations when reselecting the same item
        bottomNavigation.setOnItemReselectedListener(item -> {
            // Do nothing when reselecting the same item to prevent unnecessary animations
        });
        
        // Set up item selection listener
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_home) {
                Intent intent = new Intent(TasksActivity.this, MainActivity.class);
                startActivity(intent);
                // Removed transition animation for more natural navigation
                finish();
                return true;
            } else if (itemId == R.id.nav_tasks) {
                // Already in TasksActivity
                return true;
            } else if (itemId == R.id.nav_settings) {
                Intent intent = new Intent(TasksActivity.this, SettingsActivity.class);
                startActivity(intent);
                // Removed transition animation for more natural navigation
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                Intent intent = new Intent(TasksActivity.this, ProfileActivity.class);
                startActivity(intent);
                // Removed transition animation for more natural navigation
                finish();
                return true;
            }
            
            return false;
        });
    }
    
    private void loadTasks() {
        if (userEmail == null) {
            showError("Please sign in to view your tasks");
            updateEmptyState(true, TaskListAdapter.FILTER_TODAY);
            return;
        }
        
        // Only use Google Tasks API
        if (taskService != null) {
            taskService.getAllTasks(null, new TaskService.TasksCallback() {
                @Override
                public void onSuccess(List<Task> tasks) {
                    if (tasks != null && !tasks.isEmpty()) {
                        // Update UI with retrieved tasks
                        runOnUiThread(() -> {
                            taskList.clear();
                            taskList.addAll(tasks);
                            taskAdapter.setTasks(tasks);
                            
                            // Update calendar with dates that have tasks
                            calendarAdapter.setDatesWithTasks(taskAdapter.getDatesWithTasks());
                            
                            // Apply current filter
                            int currentFilter = taskAdapter.getCurrentFilter();
                            updateEmptyState(taskAdapter.getItemCount() == 0, currentFilter);
                        });
                        
                        // Sync with local database in background for offline access
                        executorService.execute(() -> {
                            for (Task task : tasks) {
                                taskDatabase.taskDao().insertOrUpdateTask(task);
                            }
                        });
                    } else {
                        // No tasks found, show empty state
                        runOnUiThread(() -> {
                            taskList.clear();
                            taskAdapter.setTasks(new ArrayList<>());
                            updateEmptyState(true, TaskListAdapter.FILTER_TODAY);
                        });
                    }
                }
                
                @Override
                public void onFailure(Exception e) {
                    // Just show error and empty state - don't create tasks
                    runOnUiThread(() -> {
                        showError("Failed to load tasks from Google Tasks: " + e.getMessage());
                        updateEmptyState(true, TaskListAdapter.FILTER_TODAY);
                    });
                }
            });
        } else {
            showError("Google Tasks service unavailable");
            updateEmptyState(true, TaskListAdapter.FILTER_TODAY);
        }
    }
    
    private void updateEmptyState(boolean isEmpty, int filterType) {
        if (isEmpty) {
            taskRecyclerView.setVisibility(View.GONE);
            noTasksContainer.setVisibility(View.VISIBLE);
            
            // Set different messages based on filter
            switch (filterType) {
                case TaskListAdapter.FILTER_TODAY:
                    emptyViewText.setText("No tasks scheduled for today.");
                    break;
                case TaskListAdapter.FILTER_TOMORROW:
                    emptyViewText.setText("No tasks scheduled for tomorrow.");
                    break;
                case TaskListAdapter.FILTER_THIS_WEEK:
                    emptyViewText.setText("No tasks scheduled for this week.");
                    break;
            }
        } else {
            taskRecyclerView.setVisibility(View.VISIBLE);
            noTasksContainer.setVisibility(View.GONE);
        }
    }
    
    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Only reload tasks if enough time has passed since last refresh
        // or if we're coming back from creating/editing a task
        long currentTime = System.currentTimeMillis();
        boolean shouldRefresh = (currentTime - lastRefreshTime > MIN_REFRESH_INTERVAL) || 
                                getIntent().getBooleanExtra("TASK_CREATED", false) || 
                                getIntent().getBooleanExtra("TASK_EDITED", false);
        
        if (shouldRefresh) {
            loadTasks();
            lastRefreshTime = currentTime;
            
            // Reset flags
            if (getIntent().hasExtra("TASK_CREATED")) {
                getIntent().removeExtra("TASK_CREATED");
            }
            if (getIntent().hasExtra("TASK_EDITED")) {
                getIntent().removeExtra("TASK_EDITED");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
    
    // CalendarAdapter.OnDateClickListener implementation
    @Override
    public void onDateClick(Date date) {
        selectedDate = date;
        taskAdapter.filterByDate(date);
        updateEmptyState(taskAdapter.getItemCount() == 0, taskAdapter.getCurrentFilter());
    }
    
    // TaskActionListener Implementation
    @Override
    public void onTaskCompletionChanged(Task task, boolean isCompleted) {
        // Update task status
        String newStatus = isCompleted ? "COMPLETED" : "PENDING";
        
        // Save the task ID before any modifications
        String taskId = task.getId();
        String googleTaskId = task.getGoogleTaskId();
        
        // First, update the status in Firestore directly without creating a new document
        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("status", newStatus);
        
        // Update Firestore using just the status field update
        taskRepository.updateTaskFields(taskId, statusUpdate, new FirebaseTaskRepository.TaskCallback() {
            @Override
            public void onSuccess() {
                // Update the task in our local model
                task.setStatus(newStatus);
                
                // Update Google Tasks if available
                if (taskService != null && googleTaskId != null) {
                    executorService.execute(() -> {
                        try {
                            taskService.updateTaskStatus(task, isCompleted);
                        } catch (Exception e) {
                            android.util.Log.e("TasksActivity", "Failed to update task in Google Tasks: " + e.getMessage());
                        }
                    });
                }
                
                // Update local Room database (only update the status field)
                executorService.execute(() -> {
                    try {
                        // First, get the existing task to avoid duplicates
                        Task existingTask = taskDatabase.taskDao().getTaskById(taskId);
                        if (existingTask != null) {
                            existingTask.setStatus(newStatus);
                            taskDatabase.taskDao().updateTask(existingTask);
                        } else {
                            // Fallback if not found by ID
                            taskDatabase.taskDao().insertOrUpdate(task);
                        }
                        
                        // Now find and remove any duplicates
                        if (task.getTitle() != null && task.getDate() != null) {
                            List<Task> similarTasks = taskDatabase.taskDao().getSimilarTasks(task.getTitle(), task.getDate());
                            
                            if (similarTasks != null && similarTasks.size() > 1) {
                                // Keep only one task (the one we're updating) and delete others
                                for (Task similarTask : similarTasks) {
                                    if (!similarTask.getId().equals(taskId)) {
                                        taskDatabase.taskDao().deleteTask(similarTask);
                                        android.util.Log.d("TasksActivity", "Deleted duplicate task from Room: " + similarTask.getId());
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("TasksActivity", "Error updating task in Room: " + e.getMessage());
                    }
                });
                
                // Update the UI
                runOnUiThread(() -> {
                    // Update task in the list to match new status
                    for (int i = 0; i < taskList.size(); i++) {
                        Task t = taskList.get(i);
                        if ((t.getId() != null && t.getId().equals(taskId)) ||
                            (t.getGoogleTaskId() != null && googleTaskId != null &&
                             t.getGoogleTaskId().equals(googleTaskId))) {
                            t.setStatus(newStatus);
                            break;
                        }
                    }
                    
                    // Update UI without creating new tasks
                    taskAdapter.setTasks(taskList);
                    Toast.makeText(TasksActivity.this, 
                            isCompleted ? "Task marked as completed" : "Task marked as pending", 
                            Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                showError("Failed to update task status: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void onTaskDeleted(Task task) {
        // Delete task from Firestore
        taskRepository.deleteTask(task, new FirebaseTaskRepository.TaskCallback() {
            @Override
            public void onSuccess() {
                // Delete from Google Tasks if available
                if (taskService != null && task.getGoogleTaskId() != null) {
                    executorService.execute(() -> {
                        taskService.deleteTask(task);
                    });
                }
                
                // Delete from local Room database
                executorService.execute(() -> {
                    taskDatabase.taskDao().deleteTask(task);
                });
                
                // Update UI
                runOnUiThread(() -> {
                    taskList.remove(task);
                    
                    // Update the task adapter
                    taskAdapter.setTasks(taskList);
                    
                    // Update calendar with dates that have tasks
                    calendarAdapter.setDatesWithTasks(taskAdapter.getDatesWithTasks());
                    
                    // Check if we need to show empty state
                    updateEmptyState(taskAdapter.getItemCount() == 0, taskAdapter.getCurrentFilter());
                    
                    showError("Task deleted successfully");
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                showError("Failed to delete task: " + e.getMessage());
            }
        });
    }
    
    @Override
    public void onTaskEdit(Task task) {
        // Navigate to edit task screen
        Intent intent = new Intent(TasksActivity.this, CreateTaskActivity.class);
        intent.putExtra("TASK_ID", task.getId());
        intent.putExtra("EDIT_MODE", true);
        startActivity(intent);
    }
    
    @Override
    public void onTaskShare(Task task) {
        // Share task details via Intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        
        String shareText = "Task: " + task.getTitle() + "\n";
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            shareText += "Description: " + task.getDescription() + "\n";
        }
        
        if (task.getDate() != null) {
            java.text.SimpleDateFormat dateFormat = 
                    new java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault());
            shareText += "Date: " + dateFormat.format(task.getDate()) + "\n";
        }
        
        if (task.getStartTime() != null && task.getEndTime() != null) {
            shareText += "Time: " + task.getStartTime() + " - " + task.getEndTime() + "\n";
        }
        
        shareText += "Status: " + task.getStatus();
        
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "TaskFlow Task: " + task.getTitle());
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        
        startActivity(Intent.createChooser(shareIntent, "Share Task via"));
    }

    @Override
    public void startActivity(Intent intent) {
        // Disable all animations for navigation between main screens
        if (intent.getComponent() != null && 
            (intent.getComponent().getClassName().contains("MainActivity") || 
             intent.getComponent().getClassName().contains("ProfileActivity") || 
             intent.getComponent().getClassName().contains("SettingsActivity"))) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        }
        super.startActivity(intent);
    }
    
    @Override
    public void finish() {
        super.finish();
        // Disable closing animation
        overridePendingTransition(0, 0);
    }
}