package com.example.taskflow;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.taskflow.adapter.TaskAdapter;
import com.example.taskflow.databinding.ActivityHomeBinding;
import com.example.taskflow.db.FirebaseTaskRepository;
import com.example.taskflow.db.TaskDatabase;
import com.example.taskflow.model.Task;
import com.example.taskflow.service.TaskService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements TaskAdapter.TaskActionListener {
    
    private ActivityHomeBinding binding;
    private GoogleSignInClient mGoogleSignInClient;
    private TaskAdapter taskAdapter;
    private List<Task> taskList;
    private TaskService taskService;
    private FirebaseTaskRepository taskRepository;
    private ExecutorService executorService;
    private TaskDatabase taskDatabase;
    
    // Action constants for notification actions
    public static final String ACTION_MARK_AS_READ = "ACTION_MARK_AS_READ";
    public static final String ACTION_VIEW_TASKS = "ACTION_VIEW_TASKS";
    
    private boolean tasksLoaded = false;
    private String currentUserEmail = null;
    
    // Refresh control
    private long lastRefreshTime = 0;
    private static final long MIN_REFRESH_INTERVAL = 30 * 1000; // 30 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize Firebase task repository and executor service
        taskRepository = FirebaseTaskRepository.getInstance();
        executorService = Executors.newSingleThreadExecutor();
        
        // Initialize Room database
        taskDatabase = TaskDatabase.getInstance(this);
        
        // Request notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != 
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, 100);
            }
        }

        // Initialize and configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
                
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        
        // Initialize task list and adapter
        taskList = new ArrayList<>();
        taskAdapter = new TaskAdapter(taskList, this);
        
        // Initialize the empty state and task list
        setupUI();
        
        // Set up navigation
        setupNavigation();
        
        // Handle notification actions if the activity was started from a notification
        handleNotificationActions(getIntent());
        
        // Load tasks from TaskService
        loadTasks();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    // TaskActionListener implementation methods
    @Override
    public void onTaskCompletionChanged(Task task, boolean isCompleted) {
        // Update task status
        String newStatus = isCompleted ? "COMPLETED" : "PENDING";
        
        // Save the task ID before any modifications
        String taskId = task.getId();
        String googleTaskId = task.getGoogleTaskId();
        
        // Invalidate the profile statistics cache since task status changed
        ProfileActivity.invalidateTaskStatisticsCache();
        
        // First, update the status in Firestore directly without creating a new document
        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("status", newStatus);
        
        // If this task has a Google Task ID, update in Google Tasks API first for speed
        if (taskService != null && googleTaskId != null) {
            executorService.execute(() -> {
                try {
                    // Update in Google Tasks first (faster response)
                    task.setStatus(newStatus); // Make sure task status is updated before sending
                    taskService.updateTaskStatus(task, isCompleted);
                    
                    // Then update in Firestore with a field update to prevent duplication
                    runOnUiThread(() -> {
                        taskRepository.updateTaskFields(taskId, statusUpdate, new FirebaseTaskRepository.TaskCallback() {
                            @Override
                            public void onSuccess() {
                                // Update local database
                                executorService.execute(() -> {
                                    try {
                                        // First, get the existing task to avoid duplicates
                                        Task existingTask = taskDatabase.taskDao().getTaskById(taskId);
                                        if (existingTask != null) {
                                            existingTask.setStatus(newStatus);
                                            taskDatabase.taskDao().updateTask(existingTask);
                                        } else {
                                            // Fallback if not found by ID
                                            task.setStatus(newStatus);
                                            taskDatabase.taskDao().insertOrUpdate(task);
                                        }
                                        
                                        // Find and remove any duplicates
                                        if (task.getTitle() != null && task.getDate() != null) {
                                            List<Task> similarTasks = taskDatabase.taskDao().getSimilarTasks(task.getTitle(), task.getDate());
                                            
                                            if (similarTasks != null && similarTasks.size() > 1) {
                                                // Keep only one task (the one we're updating) and delete others
                                                for (Task similarTask : similarTasks) {
                                                    if (!similarTask.getId().equals(taskId)) {
                                                        taskDatabase.taskDao().deleteTask(similarTask);
                                                        Log.d("MainActivity", "Deleted duplicate task from Room: " + similarTask.getId());
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.e("MainActivity", "Error updating task in Room: " + e.getMessage());
                                    }
                                });
                                
                                // Update UI
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
                                    
                                    taskAdapter.notifyDataSetChanged();
                                    Toast.makeText(MainActivity.this, 
                                            isCompleted ? "Task marked as completed" : "Task marked as pending", 
                                            Toast.LENGTH_SHORT).show();
                                });
                            }
                            
                            @Override
                            public void onFailure(Exception e) {
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, 
                                            "Failed to update task in Firestore: " + e.getMessage(), 
                                            Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    });
                } catch (Exception e) {
                    // If Google Tasks update fails, fall back to only Firestore update
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                                "Failed to update in Google Tasks. Updating in Firestore only.", 
                                Toast.LENGTH_SHORT).show();
                                
                        taskRepository.updateTaskFields(taskId, statusUpdate, new FirebaseTaskRepository.TaskCallback() {
                            @Override
                            public void onSuccess() {
                                updateTaskInLocalUI(task, newStatus);
                            }
                            
                            @Override
                            public void onFailure(Exception e) {
                                Toast.makeText(MainActivity.this, 
                                        "Failed to update task: " + e.getMessage(), 
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                }
            });
        } else {
            // No Google Task ID, update in Firestore only
            taskRepository.updateTaskFields(taskId, statusUpdate, new FirebaseTaskRepository.TaskCallback() {
                @Override
                public void onSuccess() {
                    updateTaskInLocalUI(task, newStatus);
                }
                
                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                                "Failed to update task: " + e.getMessage(), 
                                Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }
    
    // Helper method to update the task in the local UI after status change
    private void updateTaskInLocalUI(Task task, String newStatus) {
        task.setStatus(newStatus);
        
        // Update the local database
        executorService.execute(() -> {
            try {
                // Get the existing task
                Task existingTask = taskDatabase.taskDao().getTaskById(task.getId());
                if (existingTask != null) {
                    existingTask.setStatus(newStatus);
                    taskDatabase.taskDao().updateTask(existingTask);
                } else {
                    taskDatabase.taskDao().insertOrUpdate(task);
                }
                
                // Find and remove any duplicates
                if (task.getTitle() != null && task.getDate() != null) {
                    List<Task> similarTasks = taskDatabase.taskDao().getSimilarTasks(task.getTitle(), task.getDate());
                    
                    if (similarTasks != null && similarTasks.size() > 1) {
                        // Keep only one task (the one we're updating) and delete others
                        for (Task similarTask : similarTasks) {
                            if (!similarTask.getId().equals(task.getId())) {
                                taskDatabase.taskDao().deleteTask(similarTask);
                                Log.d("MainActivity", "Deleted duplicate task from Room DB: " + similarTask.getId());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error updating task in Room DB: " + e.getMessage());
            }
        });
        
        // Update the UI
        runOnUiThread(() -> {
            // Update the task in the list
            for (int i = 0; i < taskList.size(); i++) {
                Task t = taskList.get(i);
                if ((t.getId() != null && t.getId().equals(task.getId())) ||
                    (t.getGoogleTaskId() != null && task.getGoogleTaskId() != null && 
                     t.getGoogleTaskId().equals(task.getGoogleTaskId()))) {
                    taskList.set(i, task);
                    break;
                }
            }
            
            taskAdapter.notifyDataSetChanged();
            Toast.makeText(MainActivity.this, 
                    "COMPLETED".equals(newStatus) ? "Task marked as completed" : "Task marked as pending", 
                    Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onTaskDeleted(Task task) {
        // Only allow deletion of completed tasks
        if (!"COMPLETED".equals(task.getStatus())) {
            Toast.makeText(this, "Only completed tasks can be deleted", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show confirmation dialog
        new MaterialAlertDialogBuilder(this)
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to delete this completed task?")
            .setPositiveButton("Delete", (dialog, which) -> {
                // Invalidate the profile statistics cache since a task is being deleted
                ProfileActivity.invalidateTaskStatisticsCache();
                
                // Delete the task from Firestore
                taskRepository.deleteTask(task, new FirebaseTaskRepository.TaskCallback() {
                    @Override
                    public void onSuccess() {
                        // If using Google Tasks API, delete there too
                        if (taskService != null && task.getGoogleTaskId() != null) {
                            executorService.execute(() -> {
                                taskService.deleteTask(task);
                            });
                        }
                        
                        // Update UI
                        runOnUiThread(() -> {
                            // Remove the task from the list
                            taskList.remove(task);
                            taskAdapter.notifyDataSetChanged();
                            showEmptyState(taskList.isEmpty());
                            
                            Toast.makeText(MainActivity.this, "Task deleted", Toast.LENGTH_SHORT).show();
                        });
                    }
                    
                    @Override
                    public void onFailure(Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, 
                                    "Failed to delete task: " + e.getMessage(), 
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Handle notification actions if the activity was reopened from a notification
        handleNotificationActions(intent);
    }
    
    private void handleNotificationActions(Intent intent) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            
            switch (action) {
                case ACTION_MARK_AS_READ:
                    // Handle mark as read action
                    break;
                    
                case ACTION_VIEW_TASKS:
                    // Handle view tasks action
                    break;
            }
        }
        
        // Check if tasks need to be loaded (moved outside of the switch statement)
        if (!tasksLoaded || getIntent().getBooleanExtra("TASK_CREATED", false)) {
            loadTasks();
            tasksLoaded = true;
            
            // Reset the flag
            if (getIntent().getBooleanExtra("TASK_CREATED", false)) {
                getIntent().removeExtra("TASK_CREATED");
            }
        }
    }
    
    private void setupUI() {
        // Set up refresh listener
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            loadTasks();
        });
        
        // Set user information from Google Sign-In
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        String userName = null;
        String userEmail = null;
        String photoUrl = null;
        
        if (account != null) {
            userName = account.getDisplayName();
            userEmail = account.getEmail();
            
            // Get user's profile photo URL
            if (account.getPhotoUrl() != null) {
                photoUrl = account.getPhotoUrl().toString();
            }
            
            // Initialize TaskService with user account
            if (userEmail != null) {
                taskService = new TaskService(this, userEmail);
                currentUserEmail = userEmail;
            } else {
                Toast.makeText(this, "Failed to get user email. Some features may not work.", Toast.LENGTH_SHORT).show();
            }
        } else {
            userName = getIntent().getStringExtra("user_name");
            userEmail = getIntent().getStringExtra("user_email");
            photoUrl = getIntent().getStringExtra("user_photo");
            
            // Try to initialize TaskService even if Google Sign-In isn't available
            if (userEmail != null) {
                taskService = new TaskService(this, userEmail);
                currentUserEmail = userEmail;
            }
        }
        
        // Direct access to views using findViewById for more control
        TextView userNameTextView = findViewById(R.id.user_name);
        if (userNameTextView != null) {
            userNameTextView.setText(userName != null ? userName : "Guest User");
        }
        
        // Load user's profile image dynamically
        android.widget.ImageView profileImageView = findViewById(R.id.profile_image);
        if (profileImageView != null) {
            if (photoUrl != null && !photoUrl.isEmpty()) {
                // Use Glide to load the profile image
                com.bumptech.glide.Glide.with(this)
                    .load(android.net.Uri.parse(photoUrl))
                    .placeholder(R.drawable.profile_placeholder)
                    .error(R.drawable.profile_placeholder)
                    .circleCrop()
                    .into(profileImageView);
            } else {
                // Use placeholder if no image available
                profileImageView.setImageResource(R.drawable.profile_placeholder);
            }
        }
        
        // Setup RecyclerView
        binding.taskRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.taskRecyclerView.setAdapter(taskAdapter);
        
        // Set up FAB for adding tasks
        binding.fabAddTask.setOnClickListener(v -> {
            // Navigate to create task activity
            Intent intent = new Intent(MainActivity.this, CreateTaskActivity.class);
            if (currentUserEmail != null) {
                intent.putExtra("USER_EMAIL", currentUserEmail);
            }
            startActivity(intent);
        });
    }
    
    private void loadTasks() {
        // Show loading indicator
        binding.swipeRefreshLayout.setRefreshing(true);
        
        // Clear previous tasks
        taskList.clear();
        
        // Get current user email
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null) {
            currentUserEmail = account.getEmail();
            
            // Make sure TaskService is initialized
            if (taskService == null && currentUserEmail != null) {
                taskService = new TaskService(this, currentUserEmail);
            }
            
            if (taskService != null) {
                // Primary source: Fetch tasks from Google Tasks API for speed
                loadTasksFromGoogleTasks(0); // Start with 0 retries
            } else {
                // If no task service could be initialized, use Firestore tasks
                loadFirestoreTasks();
            }
        } else {
            // Not signed in, can't load user-specific tasks
            Toast.makeText(this, "Please sign in to access your tasks", Toast.LENGTH_SHORT).show();
            binding.swipeRefreshLayout.setRefreshing(false);
            showEmptyState(true);
        }
    }
    
    // Modified method to only use Google Tasks API without falling back to Firestore
    private void loadTasksFromGoogleTasks(int retryCount) {
        final int MAX_RETRIES = 2;
        
        taskService.getAllTasks(
            null, // We don't need the task lists here
            new TaskService.TasksCallback() {
                @Override
                public void onSuccess(List<Task> tasks) {
                    // We got tasks from Google Tasks API
                    if (tasks != null && !tasks.isEmpty()) {
                        // Update UI with retrieved tasks immediately for fast display
                        runOnUiThread(() -> {
                            taskList.clear(); // Clear list in case we're retrying
                            taskList.addAll(tasks);
                            taskAdapter.notifyDataSetChanged();
                            showEmptyState(false);
                            binding.swipeRefreshLayout.setRefreshing(false);
                            tasksLoaded = true;
                            // Update refresh time
                            lastRefreshTime = System.currentTimeMillis();
                        });
                        
                        // Sync with local database in background for offline access
                        executorService.execute(() -> {
                            for (Task task : tasks) {
                                taskDatabase.taskDao().insertOrUpdateTask(task);
                            }
                        });
                    } else {
                        // Google Tasks API returned empty data - show empty state instead of falling back
                        runOnUiThread(() -> {
                            taskList.clear();
                            taskAdapter.notifyDataSetChanged();
                            showEmptyState(true);
                            binding.swipeRefreshLayout.setRefreshing(false);
                            lastRefreshTime = System.currentTimeMillis();
                        });
                    }
                }
                
                @Override
                public void onFailure(Exception e) {
                    // Show error message and empty state instead of falling back to Firestore
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Failed to load tasks: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        binding.swipeRefreshLayout.setRefreshing(false);
                        showEmptyState(true);
                        lastRefreshTime = System.currentTimeMillis();
                    });
                }
            }
        );
    }
    
    private void loadFirestoreTasks() {
        if (currentUserEmail == null) {
            binding.swipeRefreshLayout.setRefreshing(false);
            showEmptyState(true);
            return;
        }
        
        // Fetch tasks from Firestore for the current user
        taskRepository.getTasksByUser(currentUserEmail, new FirebaseTaskRepository.TasksCallback() {
            @Override
            public void onSuccess(List<Task> tasks) {
                if (tasks != null && !tasks.isEmpty()) {
                    // Update UI with retrieved tasks from Firestore
                    runOnUiThread(() -> {
                        taskList.addAll(tasks);
                        taskAdapter.notifyDataSetChanged();
                        showEmptyState(false);
                        binding.swipeRefreshLayout.setRefreshing(false);
                        tasksLoaded = true; // Mark tasks as loaded only when successful
                        
                        Toast.makeText(MainActivity.this, 
                                "Using Firestore tasks data (Google Tasks API unavailable)", 
                                Toast.LENGTH_SHORT).show();
                    });
                } else {
                    // No tasks found in Firestore
                    runOnUiThread(() -> {
                        showEmptyState(true);
                        binding.swipeRefreshLayout.setRefreshing(false);
                    });
                }
            }
            
            @Override
            public void onFailure(Exception e) {
                // Firestore failed, try local database
                loadTasksFromLocalDatabase();
            }
        });
    }
    
    private void loadTasksFromLocalDatabase() {
        // Try to load tasks from Room database when Firebase fails
        executorService.execute(() -> {
            try {
                List<Task> localTasks = taskDatabase.taskDao().getAllTasks();
                
                runOnUiThread(() -> {
                    if (localTasks != null && !localTasks.isEmpty()) {
                        // Found tasks in local database
                        taskList.addAll(localTasks);
                        taskAdapter.notifyDataSetChanged();
                        showEmptyState(false);
                        tasksLoaded = true;
                    } else {
                        // No tasks in local database either, show empty state
                        showEmptyState(true);
                    }
                    binding.swipeRefreshLayout.setRefreshing(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, 
                            "Failed to load tasks from local database: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    binding.swipeRefreshLayout.setRefreshing(false);
                    showEmptyState(true);
                });
            }
        });
    }
    
    private void showEmptyState(boolean show) {
        if (show) {
            binding.emptyStateContainer.setVisibility(View.VISIBLE);
            binding.taskRecyclerView.setVisibility(View.GONE);
            binding.emptyStateText.setText("No tasks available.");
        } else {
            binding.emptyStateContainer.setVisibility(View.GONE);
            binding.taskRecyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    private void setupNavigation() {
        // Find the BottomNavigationView using findViewById instead of binding
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        
        // Check if bottom_navigation exists in the layout
        if (bottomNavigation != null) {
            // Optimize navigation by disabling animations when reselecting the same item
            bottomNavigation.setOnItemReselectedListener(item -> {
                // Do nothing when reselecting the same item to prevent unnecessary animations
            });

            bottomNavigation.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                
                if (itemId == R.id.nav_home) {
                    // Already at home
                    return true;
                } else if (itemId == R.id.nav_tasks) {
                    // Navigate to TasksActivity
                    Intent intent = new Intent(MainActivity.this, TasksActivity.class);
                    // Pass the user email to the TasksActivity
                    intent.putExtra("USER_EMAIL", currentUserEmail);
                    startActivity(intent);
                    // Removed transition animation for more natural navigation
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    // Launch the SettingsActivity with no transition
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                    // Removed transition animation for more natural navigation
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    // Navigate to ProfileActivity with no transition
                    Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
                    startActivity(intent);
                    // Removed transition animation for more natural navigation
                    return true;
                }
                
                return false;
            });
            
            // Set home as selected initially
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
    }
    
    @Override
    public void startActivity(Intent intent) {
        // Disable all animations
        if (intent.getComponent() != null && 
            (intent.getComponent().getClassName().contains("TasksActivity") || 
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
    
    private void signOut() {
        mGoogleSignInClient.signOut()
            .addOnCompleteListener(this, task -> {
                // Navigate back to login screen
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            });
    }
}