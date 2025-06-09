package com.example.taskflow;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.example.taskflow.util.EmailNotificationUtil;

import java.util.Calendar;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences sharedPreferences;
    private SwitchMaterial switchNotifications;
    private SwitchMaterial switchDailyTaskSummary;
    private SwitchMaterial switchDarkMode;
    private SwitchMaterial switchColorCode;
    private LinearLayout layoutReminderTime;
    private TextView textReminderTime;
    private SwitchMaterial switchDataSync;
    // Removed btnLogout declaration
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Configure Google Sign-In for logout
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Initialize the toolbar
        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false); // Changed to false to hide back button
        getSupportActionBar().setTitle("Settings");

        // Initialize SharedPreferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Initialize UI elements
        switchNotifications = findViewById(R.id.switch_notifications);
        switchDailyTaskSummary = findViewById(R.id.switch_daily_summary);
        layoutReminderTime = findViewById(R.id.layout_reminder_time);
        textReminderTime = findViewById(R.id.text_reminder_time);
        switchDarkMode = findViewById(R.id.switch_dark_mode);
        switchColorCode = findViewById(R.id.switch_color_coding);
        switchDataSync = findViewById(R.id.switch_data_sync);
        
        // Initialize Clear Cache and Export Tasks buttons
        Button btnClearCache = findViewById(R.id.btn_clear_cache);
        Button btnExport = findViewById(R.id.btn_export);
        
        // Initialize test notification button
        Button btnTestNotification = findViewById(R.id.btn_test_notification);

        // Set up initial states based on saved preferences
        loadSavedPreferences();

        // Set up click listeners
        setupClickListeners();
        
        // Set up Clear Cache button
        btnClearCache.setOnClickListener(v -> {
            showClearCacheConfirmationDialog();
        });
        
        // Set up Export Tasks button
        btnExport.setOnClickListener(v -> {
            exportTasks();
        });
        
        // Set up test notification button
        btnTestNotification.setOnClickListener(v -> {
            // Trigger a test notification immediately
            sendTestNotification();
            Toast.makeText(this, "Sending test notification...", Toast.LENGTH_SHORT).show();
        });
        
        // Setup bottom navigation
        setupNavigation();
    }

    private void loadSavedPreferences() {
        boolean notificationsEnabled = sharedPreferences.getBoolean("notifications_enabled", true);
        switchNotifications.setChecked(notificationsEnabled);
        layoutReminderTime.setEnabled(notificationsEnabled);

        boolean dailySummaryEnabled = sharedPreferences.getBoolean("daily_summary_enabled", true);
        switchDailyTaskSummary.setChecked(dailySummaryEnabled);

        String reminderTime = sharedPreferences.getString("reminder_time", "15 minutes before");
        textReminderTime.setText(reminderTime);

        boolean darkModeEnabled = sharedPreferences.getBoolean("dark_mode_enabled", false);
        switchDarkMode.setChecked(darkModeEnabled);

        boolean colorCodeEnabled = sharedPreferences.getBoolean("color_code_enabled", true);
        switchColorCode.setChecked(colorCodeEnabled);

        boolean dataSyncEnabled = sharedPreferences.getBoolean("data_sync_enabled", true);
        switchDataSync.setChecked(dataSyncEnabled);
    }

    private void setupClickListeners() {
        // Notification switch
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("notifications_enabled", isChecked);
            editor.apply();
            layoutReminderTime.setEnabled(isChecked);
            Toast.makeText(this, isChecked ? "Notifications enabled" : "Notifications disabled", Toast.LENGTH_SHORT).show();
        });

        // Daily task summary switch
        switchDailyTaskSummary.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("daily_summary_enabled", isChecked);
            editor.apply();
            
            if (isChecked) {
                scheduleDailySummaryNotification();
                Toast.makeText(this, "Daily task summary enabled", Toast.LENGTH_SHORT).show();
            } else {
                cancelDailySummaryNotification();
                Toast.makeText(this, "Daily task summary disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // Reminder time picker
        layoutReminderTime.setOnClickListener(v -> showTimePickerDialog());

        // Dark mode switch
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("dark_mode_enabled", isChecked);
            editor.apply();
            applyDarkMode(isChecked);
            Toast.makeText(this, isChecked ? "Dark mode enabled" : "Dark mode disabled", Toast.LENGTH_SHORT).show();
        });

        // Color coding switch
        switchColorCode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("color_code_enabled", isChecked);
            editor.apply();
            Toast.makeText(this, isChecked ? "Task color coding enabled" : "Task color coding disabled", Toast.LENGTH_SHORT).show();
        });

        // Data sync switch
        switchDataSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("data_sync_enabled", isChecked);
            editor.apply();
            Toast.makeText(this, isChecked ? "Data sync enabled" : "Data sync disabled", Toast.LENGTH_SHORT).show();
        });

        // Removed btnLogout click listener
    }

    private void scheduleDailySummaryNotification() {
        // Set up an alarm for 8 PM every day to show the notification summary
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, DailySummaryReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0, // Request code
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set the alarm to fire at approximately 8:00 p.m.
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, 20); // 8 PM
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        // If the time has already passed today, set it for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Schedule the alarm to repeat daily
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }

    private void cancelDailySummaryNotification() {
        // Cancel the daily summary notification alarm
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, DailySummaryReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        alarmManager.cancel(pendingIntent);
    }

    private void showTimePickerDialog() {
        // Get current reminder time
        String currentTime = textReminderTime.getText().toString();
        int minutes = 15; // Default value

        // Try to parse current value if it's in the format "X minutes before"
        try {
            String[] parts = currentTime.split(" ");
            minutes = Integer.parseInt(parts[0]);
        } catch (Exception e) {
            // Use default value if parsing fails
        }

        // Show a time picker or a dialog with options
        final CharSequence[] items = {"5 minutes before", "15 minutes before", "30 minutes before", "1 hour before", "1 day before"};
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Choose reminder time");
        builder.setItems(items, (dialog, which) -> {
            String selectedTime = (String) items[which];
            textReminderTime.setText(selectedTime);
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("reminder_time", selectedTime);
            editor.apply();
            
            Toast.makeText(this, "Reminder set to " + selectedTime, Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void applyDarkMode(boolean enableDarkMode) {
        if (enableDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }

    private void signOut() {
        mGoogleSignInClient.signOut()
            .addOnCompleteListener(this, task -> {
                // Clear any saved preferences related to user data if needed
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                
                // Navigate back to login screen
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
    }

    private void setupNavigation() {
        // Get the bottom navigation view
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        
        // Optimize navigation by disabling animations when reselecting the same item
        bottomNavigation.setOnItemReselectedListener(item -> {
            // Do nothing when reselecting the same item to prevent unnecessary animations
        });
        
        // Set the selected item
        bottomNavigation.setSelectedItemId(R.id.nav_settings);
        
        // Setup item selection listener
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
                // Get the current signed-in account to pass the email
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null) {
                    intent.putExtra("USER_EMAIL", account.getEmail());
                }
                startActivity(intent);
                // Removed transition animation for more natural navigation
                finish();
                return true;
            } else if (itemId == R.id.nav_profile) {
                // Navigate to Profile
                Intent intent = new Intent(this, ProfileActivity.class);
                startActivity(intent);
                // Removed transition animation for more natural navigation
                finish();
                return true;
            }
            
            return false;
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Sends a test notification by fetching real task data and sending both
     * a device notification and email notification to the user's Gmail account
     */
    private void sendTestNotification() {
        // Get the logged in user's email
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        
        if (account != null && account.getEmail() != null) {
            String userEmail = account.getEmail();
            
            // Show toast to indicate we're processing
            Toast.makeText(this, "Sending dynamic test notifications to " + userEmail, Toast.LENGTH_SHORT).show();
            
            // Log for debugging
            android.util.Log.d("SettingsActivity", "Sending dynamic test notifications to: " + userEmail);
            
            // Send device notification and email with real task data to the user's Gmail
            EmailNotificationUtil.sendDynamicTaskSummaryNotification(this, userEmail);
        } else {
            // Fallback if we can't get the email
            Intent intent = new Intent(this, DailySummaryReceiver.class);
            intent.putExtra("test_mode", true);
            sendBroadcast(intent);
            Toast.makeText(this, "Couldn't find user email. Sending local notification only.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void startActivity(Intent intent) {
        // Disable all animations for navigation between main screens
        if (intent.getComponent() != null && 
            (intent.getComponent().getClassName().contains("MainActivity") || 
             intent.getComponent().getClassName().contains("TasksActivity") || 
             intent.getComponent().getClassName().contains("ProfileActivity"))) {
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
     * Shows a confirmation dialog before clearing all task data
     */
    private void showClearCacheConfirmationDialog() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Clear All Task Data")
            .setMessage("This will delete all your tasks, including completed and pending tasks, from both cloud and local storage. This action cannot be undone. Are you sure?")
            .setPositiveButton("Clear All Data", (dialog, which) -> {
                clearAllTaskData();
            })
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    /**
     * Clears all task data from Firebase and Room database
     */
    private void clearAllTaskData() {
        // Show progress dialog
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Clearing all task data...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Get current user email
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null || account.getEmail() == null) {
            Toast.makeText(this, "Unable to identify user account", Toast.LENGTH_SHORT).show();
            progressDialog.dismiss();
            return;
        }
        
        String userEmail = account.getEmail();
        
        // Create an executor for background operations
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        
        executor.execute(() -> {
            boolean firebaseSuccess = true;
            boolean localSuccess = true;
            
            try {
                // Delete from Firebase
                com.example.taskflow.db.FirebaseTaskRepository repository = com.example.taskflow.db.FirebaseTaskRepository.getInstance();
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                final boolean[] success = {false};
                
                repository.deleteAllTasksForUser(userEmail, new com.example.taskflow.db.FirebaseTaskRepository.TaskCallback() {
                    @Override
                    public void onSuccess() {
                        success[0] = true;
                        latch.countDown();
                    }
                    
                    @Override
                    public void onFailure(Exception e) {
                        success[0] = false;
                        latch.countDown();
                    }
                });
                
                // Wait for Firebase deletion to complete
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                firebaseSuccess = success[0];
                
                // Delete from Room database - use the safer recreateDatabase method
                try {
                    // First try to delete just the user's data
                    com.example.taskflow.db.TaskDatabase database = com.example.taskflow.db.TaskDatabase.getInstance(this);
                    database.taskDao().deleteAllTasksForUser(userEmail);
                } catch (Exception e) {
                    android.util.Log.e("SettingsActivity", "Error deleting user tasks, will recreate database", e);
                    // If that fails, recreate the entire database safely
                    com.example.taskflow.db.TaskDatabase.recreateDatabase(this);
                }
                
                localSuccess = true;
                
                // Update UI on main thread
                final boolean finalFirebaseSuccess = firebaseSuccess;
                final boolean finalLocalSuccess = localSuccess;
                
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (finalFirebaseSuccess && finalLocalSuccess) {
                        Toast.makeText(this, "All task data cleared successfully", Toast.LENGTH_LONG).show();
                    } else if (finalFirebaseSuccess) {
                        Toast.makeText(this, "Cloud data cleared successfully, but there was an issue with local data", Toast.LENGTH_LONG).show();
                    } else if (finalLocalSuccess) {
                        Toast.makeText(this, "Local data cleared successfully, but there was an issue with cloud data", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "There was an issue clearing task data", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error clearing data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                executor.shutdown();
            }
        });
    }
    
    /**
     * Export tasks to a CSV file with app header and logo
     */
    private void exportTasks() {
        // Get user email
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null || account.getEmail() == null) {
            Toast.makeText(this, "Unable to identify user account", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String userEmail = account.getEmail();
        
        // Show progress dialog
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Preparing task export...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Create an executor for background operations
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        
        executor.execute(() -> {
            try {
                // Get all tasks for the current user
                com.example.taskflow.db.FirebaseTaskRepository repository = com.example.taskflow.db.FirebaseTaskRepository.getInstance();
                java.util.concurrent.atomic.AtomicReference<java.util.List<com.example.taskflow.model.Task>> tasksRef = 
                        new java.util.concurrent.atomic.AtomicReference<>();
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                
                repository.getTasksByUser(userEmail, new com.example.taskflow.db.FirebaseTaskRepository.TasksCallback() {
                    @Override
                    public void onSuccess(java.util.List<com.example.taskflow.model.Task> tasks) {
                        tasksRef.set(tasks);
                        latch.countDown();
                    }
                    
                    @Override
                    public void onFailure(Exception e) {
                        latch.countDown();
                    }
                });
                
                // Wait for tasks to be fetched
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
                
                java.util.List<com.example.taskflow.model.Task> tasks = tasksRef.get();
                
                // If no tasks found or error occurred, try fetching from local database
                if (tasks == null || tasks.isEmpty()) {
                    com.example.taskflow.db.TaskDatabase database = com.example.taskflow.db.TaskDatabase.getInstance(this);
                    tasks = database.taskDao().getAllTasksForUser(userEmail);
                }
                
                // Final check if we have tasks to export
                if (tasks == null || tasks.isEmpty()) {
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(this, "No tasks found to export", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // Create CSV content with app header and logo reference
                final java.util.List<com.example.taskflow.model.Task> finalTasks = tasks;
                String csvContent = generateCSVWithHeader(finalTasks);
                
                // Get Documents directory
                java.io.File documentsDir = new java.io.File(getExternalFilesDir(null), "TaskFlow");
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs();
                }
                
                // Create unique filename with timestamp
                String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
                java.io.File exportFile = new java.io.File(documentsDir, "TaskFlow_Export_" + timeStamp + ".csv");
                
                // Write CSV content to file
                java.io.FileWriter fileWriter = new java.io.FileWriter(exportFile);
                fileWriter.write(csvContent);
                fileWriter.close();
                
                // Create intent to share the file
                android.content.Intent shareIntent = new android.content.Intent();
                shareIntent.setAction(android.content.Intent.ACTION_SEND);
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "TaskFlow Task Export");
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Here's my exported tasks from TaskFlow app.");
                
                // Use FileProvider to create shareable URI
                android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                        this, 
                        getPackageName() + ".fileprovider", 
                        exportFile);
                
                shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, fileUri);
                shareIntent.setType("text/csv");
                shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                // Update UI on main thread
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    startActivity(android.content.Intent.createChooser(shareIntent, "Share Tasks Export"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Error exporting tasks: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } finally {
                executor.shutdown();
            }
        });
    }
    
    /**
     * Generates CSV content with app header including logo reference
     */
    private String generateCSVWithHeader(java.util.List<com.example.taskflow.model.Task> tasks) {
        StringBuilder csv = new StringBuilder();
        
        // Add app header with logo reference
        csv.append("TaskFlow - Your Task Management App\n");
        csv.append("Exported on: ").append(new java.text.SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", 
                java.util.Locale.getDefault()).format(new java.util.Date())).append("\n\n");
        
        // Add column headers
        csv.append("Title,Description,Date,Start Time,End Time,Status\n");
        
        // Add task data
        for (com.example.taskflow.model.Task task : tasks) {
            // Format task data with proper escaping for CSV
            String title = task.getTitle() != null ? "\"" + task.getTitle().replace("\"", "\"\"") + "\"" : "";
            String description = task.getDescription() != null ? "\"" + task.getDescription().replace("\"", "\"\"") + "\"" : "";
            
            String dateStr = "";
            if (task.getDate() != null) {
                dateStr = new java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.getDefault()).format(task.getDate());
            }
            
            String startTime = task.getStartTime() != null ? task.getStartTime() : "";
            String endTime = task.getEndTime() != null ? task.getEndTime() : "";
            String status = task.getStatus() != null ? task.getStatus() : "";
            
            csv.append(String.format("%s,%s,%s,%s,%s,%s\n", 
                    title, description, dateStr, startTime, endTime, status));
        }
        
        return csv.toString();
    }
}