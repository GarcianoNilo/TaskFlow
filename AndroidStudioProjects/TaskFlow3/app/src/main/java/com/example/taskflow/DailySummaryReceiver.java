package com.example.taskflow;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.taskflow.db.TaskDao;
import com.example.taskflow.db.TaskDatabase;
import com.example.taskflow.model.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DailySummaryReceiver extends BroadcastReceiver {
    private static final String TAG = "DailySummaryReceiver";
    private static final String CHANNEL_ID = "daily_summary_channel";
    private static final int NOTIFICATION_ID = 2000;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: Starting notification process");
        createNotificationChannel(context);
        
        // Get tomorrow's date
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1); // Tomorrow
        Date tomorrow = calendar.getTime();
        
        // Format the date for display and database query
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String tomorrowDateStr = dateFormat.format(tomorrow);
        SimpleDateFormat displayFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        String displayDate = displayFormat.format(tomorrow);
        
        // For testing, we'll use both local database and create a test notification
        if (intent.getBooleanExtra("test_mode", false)) {
            Log.d(TAG, "onReceive: Creating test notification");
            showTestNotification(context, displayDate);
            return;
        }
        
        // Get tasks for tomorrow
        TaskDao taskDao = TaskDatabase.getInstance(context).taskDao();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        executor.execute(() -> {
            try {
                // Query database for tomorrow's tasks
                List<Task> tomorrowTasks = taskDao.getTasksForDate(tomorrowDateStr);
                Log.d(TAG, "Found " + (tomorrowTasks != null ? tomorrowTasks.size() : 0) + " tasks for tomorrow");
                
                // Create notification content based on tomorrow's tasks
                String notificationTitle = "Tasks for " + displayDate;
                String notificationContent;
                
                if (tomorrowTasks == null || tomorrowTasks.isEmpty()) {
                    notificationContent = "You have no tasks scheduled for tomorrow.";
                } else {
                    StringBuilder contentBuilder = new StringBuilder();
                    contentBuilder.append("You have ").append(tomorrowTasks.size()).append(" task(s) scheduled tomorrow:\n");
                    
                    int taskCount = Math.min(3, tomorrowTasks.size()); // Display up to 3 tasks
                    for (int i = 0; i < taskCount; i++) {
                        Task task = tomorrowTasks.get(i);
                        contentBuilder.append("\n• ").append(task.getTitle());
                        if (task.getStartTime() != null && !task.getStartTime().isEmpty()) {
                            contentBuilder.append(" (").append(task.getStartTime()).append(")");
                        }
                    }
                    
                    if (tomorrowTasks.size() > 3) {
                        contentBuilder.append("\n\n...and ").append(tomorrowTasks.size() - 3).append(" more task(s)");
                    }
                    
                    notificationContent = contentBuilder.toString();
                }
                
                // Show the notification
                showNotification(context, notificationTitle, notificationContent);
            } catch (Exception e) {
                Log.e(TAG, "Error creating notification", e);
                // Show a fallback notification in case of error
                showNotification(context, "Task Summary", 
                        "An error occurred while preparing your task summary. Please open the app to view your tasks.");
            }
        });
    }

    private void showTestNotification(Context context, String displayDate) {
        // Create a test notification with sample tasks
        String title = "Test: Tasks for " + displayDate;
        StringBuilder content = new StringBuilder();
        content.append("This is a test notification showing how your daily summary will appear.\n\n");
        content.append("You have 3 sample tasks scheduled tomorrow:\n");
        content.append("\n• Complete project presentation (09:00 AM)");
        content.append("\n• Team meeting with marketing (02:30 PM)");
        content.append("\n• Send weekly report (04:00 PM)");
        
        showNotification(context, title, content.toString());
    }

    private void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ (Android 8.0 and higher)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Daily Task Summary";
            String description = "Provides a summary of tomorrow's tasks";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            // Register the channel with the system
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created");
            } else {
                Log.e(TAG, "NotificationManager is null");
            }
        }
    }

    private void showNotification(Context context, String title, String content) {
        try {
            // Create an intent that opens the MainActivity when clicked
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Build the notification with a proper icon
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_logo) // Updated to use ic_logo instead of ic_notification
                    .setContentTitle(title)
                    .setContentText(content)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_HIGH) // Increased priority
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true);
                    
            // Show the notification
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            
            // Check for notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (notificationManager.areNotificationsEnabled()) {
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                    Log.d(TAG, "Notification posted successfully");
                } else {
                    Log.e(TAG, "Notifications are not enabled for this app");
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                Log.d(TAG, "Notification posted successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }
}