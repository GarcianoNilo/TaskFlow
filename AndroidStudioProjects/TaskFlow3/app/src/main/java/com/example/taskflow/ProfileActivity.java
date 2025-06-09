package com.example.taskflow;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.taskflow.databinding.ActivityProfileBinding;
import com.example.taskflow.db.FirebaseTaskRepository;
import com.example.taskflow.model.Task;
import com.example.taskflow.service.TaskService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private ActivityProfileBinding binding;
    private GoogleSignInClient mGoogleSignInClient;
    private ExecutorService executorService;
    private FirebaseTaskRepository taskRepository;
    private TaskService taskService; // Added TaskService for Google Tasks integration
    private String userEmail;
    
    // Add static cache for task statistics to prevent incremental counting
    private static int cachedPendingCount = -1;
    private static int cachedCompletedCount = -1;
    private static long lastStatisticsUpdateTime = 0;
    private static final long STATISTICS_CACHE_TIMEOUT = 60000; // 60 seconds cache timeout
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize Firebase repository and executor service
        taskRepository = FirebaseTaskRepository.getInstance();
        executorService = Executors.newSingleThreadExecutor();
        
        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        
        setupUI();
        setupNavigation();
        loadTaskStatistics();
    }
    
    private void setupUI() {
        // Get user information from Google account or from intent extras
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        String displayName = null;
        String email = null;
        String photoUrl = null;
        
        if (account != null) {
            displayName = account.getDisplayName();
            email = account.getEmail();
            if (account.getPhotoUrl() != null) {
                photoUrl = account.getPhotoUrl().toString();
            }
        } else {
            // Fallback to intent extras if Google account is not available
            displayName = getIntent().getStringExtra("user_name");
            email = getIntent().getStringExtra("user_email");
            photoUrl = getIntent().getStringExtra("user_photo");
        }
        
        // Store the email for use in other methods
        this.userEmail = email;
        
        // Initialize TaskService if we have a user email
        if (userEmail != null) {
            taskService = new TaskService(this, userEmail);
        }
        
        // Set user information in UI
        binding.userName.setText(displayName != null ? displayName : "User");
        binding.userEmail.setText(email != null ? email : "");
        
        // Show loading indicators for statistics
        binding.pendingTasksText.setText("Loading pending tasks...");
        binding.completedTasksText.setText("Loading completed tasks...");
        binding.totalTasksCount.setText("...");
        ((android.widget.TextView) binding.getRoot().findViewById(R.id.completion_rate_text)).setText("...");
        
        // Load profile image using Glide if available
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this)
                .load(Uri.parse(photoUrl))
                .placeholder(R.drawable.profile_placeholder)
                .error(R.drawable.profile_placeholder)
                .circleCrop()
                .into(binding.profileImage);
        }
        
        // Setup logout button
        binding.btnLogout.setOnClickListener(v -> signOut());
    }
    
    private void setupNavigation() {
        // Find the BottomNavigationView inside the included layout
        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        
        // Optimize navigation by disabling animations when reselecting the same item
        bottomNavigation.setOnItemReselectedListener(item -> {
            // Do nothing when reselecting the same item to prevent unnecessary animations
        });
        
        bottomNavigation.setSelectedItemId(R.id.nav_profile);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_home) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                // Removed transition animation for more natural navigation
                finish();
                return true;
            } else if (itemId == R.id.nav_tasks) {
                // Navigate to TasksActivity
                Intent intent = new Intent(this, TasksActivity.class);
                // Pass the user email to the TasksActivity
                intent.putExtra("USER_EMAIL", userEmail);
                startActivity(intent);
                // Removed transition animation for more natural navigation
                finish();
                return true;
            } else if (itemId == R.id.nav_settings) {
                // Navigate to Settings
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                // Removed transition animation for more natural navigation
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                // Already at profile
                return true;
            }
            return false;
        });
    }
    
    private void loadTaskStatistics() {
        // Get current user's email
        if (userEmail == null) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            userEmail = account != null ? account.getEmail() : null;
        }
        
        if (userEmail == null) {
            // Can't load stats without an email
            handleStatisticsError(new Exception("User email not available"));
            return;
        }
        
        // Check if cached statistics are still valid
        long currentTime = System.currentTimeMillis();
        if (cachedPendingCount >= 0 && cachedCompletedCount >= 0 && 
            currentTime - lastStatisticsUpdateTime < STATISTICS_CACHE_TIMEOUT) {
            // Use cached values
            updateStatisticsUI(cachedPendingCount, cachedCompletedCount);
            return;
        }
        
        // First try to get statistics from Google Tasks API
        if (taskService != null) {
            loadTaskStatisticsFromGoogleTasks();
        } else {
            // Fall back to Firestore if TaskService is not available
            loadTaskStatisticsFromFirestore();
        }
    }
    
    /**
     * Load task statistics from Google Tasks API (primary source)
     */
    private void loadTaskStatisticsFromGoogleTasks() {
        // Show loading indicator
        runOnUiThread(() -> {
            binding.pendingTasksText.setText("Loading pending tasks...");
            binding.completedTasksText.setText("Loading completed tasks...");
        });
        
        // Get all tasks from Google Tasks API
        taskService.getAllTasks(null, new TaskService.TasksCallback() {
            @Override
            public void onSuccess(List<Task> tasks) {
                if (tasks != null) {
                    // Count pending and completed tasks
                    int pendingCount = 0;
                    int completedCount = 0;
                    
                    for (Task task : tasks) {
                        if ("COMPLETED".equals(task.getStatus())) {
                            completedCount++;
                        } else {
                            pendingCount++;
                        }
                    }
                    
                    // Update cache
                    cachedPendingCount = pendingCount;
                    cachedCompletedCount = completedCount;
                    lastStatisticsUpdateTime = System.currentTimeMillis();
                    
                    // Update UI
                    updateStatisticsUI(pendingCount, completedCount);
                    
                    // Log success
                    Log.d(TAG, "Successfully loaded task statistics from Google Tasks API");
                } else {
                    // Fall back to Firestore if Google Tasks API returns null
                    Log.w(TAG, "Google Tasks API returned null, falling back to Firestore");
                    loadTaskStatisticsFromFirestore();
                }
            }
            
            @Override
            public void onFailure(Exception e) {
                // Fall back to Firestore on API failure
                Log.e(TAG, "Failed to load task statistics from Google Tasks API: " + e.getMessage());
                loadTaskStatisticsFromFirestore();
            }
        });
    }
    
    /**
     * Load task statistics from Firestore (fallback source)
     */
    private void loadTaskStatisticsFromFirestore() {
        runOnUiThread(() -> {
            binding.pendingTasksText.setText("Loading pending tasks...");
            binding.completedTasksText.setText("Loading completed tasks...");
        });
        
        // Get pending tasks count
        taskRepository.getPendingTasksCountByUser(userEmail, new FirebaseTaskRepository.TaskCountCallback() {
            @Override
            public void onSuccess(int pendingCount) {
                // Get completed tasks count
                taskRepository.getCompletedTasksCountByUser(userEmail, new FirebaseTaskRepository.TaskCountCallback() {
                    @Override
                    public void onSuccess(int completedCount) {
                        // Update cache
                        cachedPendingCount = pendingCount;
                        cachedCompletedCount = completedCount;
                        lastStatisticsUpdateTime = System.currentTimeMillis();
                        
                        // Update UI on main thread with both counts
                        updateStatisticsUI(pendingCount, completedCount);
                        
                        // Log success with fallback source
                        Log.d(TAG, "Successfully loaded task statistics from Firestore (fallback)");
                    }
                    
                    @Override
                    public void onFailure(Exception e) {
                        handleStatisticsError(e);
                    }
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                handleStatisticsError(e);
            }
        });
    }
    
    private void updateStatisticsUI(int pendingCount, int completedCount) {
        runOnUiThread(() -> {
            // Update card text with actual counts
            binding.pendingTasksText.setText("You have " + pendingCount + " pending task" + 
                (pendingCount != 1 ? "s" : "") + "!");
            binding.completedTasksText.setText("You have completed " + completedCount + " task" + 
                (completedCount != 1 ? "s" : "") + "!");
            
            // Calculate total tasks and update stats
            int totalTasks = pendingCount + completedCount;
            binding.totalTasksCount.setText(String.valueOf(totalTasks));
            
            // Calculate and display completion rate
            int completionRate = totalTasks > 0 ? (completedCount * 100) / totalTasks : 0;
            // Cast the View to TextView before calling setText
            ((android.widget.TextView) binding.getRoot().findViewById(R.id.completion_rate_text)).setText(completionRate + "%");
        });
    }
    
    private void handleStatisticsError(Exception e) {
        Log.e(TAG, "Error loading task statistics: " + e.getMessage());
        runOnUiThread(() -> {
            Toast.makeText(ProfileActivity.this, 
                    "Failed to load task statistics: " + e.getMessage(), 
                    Toast.LENGTH_SHORT).show();
            
            // Set default values
            binding.pendingTasksText.setText("You have 0 pending tasks!");
            binding.completedTasksText.setText("You have completed 0 tasks!");
            binding.totalTasksCount.setText("0");
            ((android.widget.TextView) binding.getRoot().findViewById(R.id.completion_rate_text)).setText("0%");
        });
    }
    
    private void signOut() {
        mGoogleSignInClient.signOut()
            .addOnCompleteListener(this, task -> {
                // Navigate back to login screen
                Intent intent = new Intent(this, LoginActivity.class);
                startActivity(intent);
                finish();
            });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    @Override
    public void startActivity(Intent intent) {
        // Disable all animations for navigation between main screens
        if (intent.getComponent() != null && 
            (intent.getComponent().getClassName().contains("MainActivity") || 
             intent.getComponent().getClassName().contains("TasksActivity") || 
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

    /**
     * Static method to invalidate the task statistics cache.
     * This should be called whenever tasks are created, updated, or deleted.
     */
    public static void invalidateTaskStatisticsCache() {
        cachedPendingCount = -1;
        cachedCompletedCount = -1;
        lastStatisticsUpdateTime = 0;
    }
}
