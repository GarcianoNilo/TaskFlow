package com.example.taskflow.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.example.taskflow.MainActivity;
import com.example.taskflow.R;
import com.example.taskflow.model.Task;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationUtil {
    private static final String TAG = "NotificationUtil";
    private static final String CHANNEL_ID = "taskflow_login_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static void showLoginNotification(Context context, String userEmail) {
        try {
            Log.d(TAG, "Showing login notification for: " + userEmail);
            createNotificationChannel(context);

            // Create an intent to open the app when notification is tapped
            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE);

            // Format current date and time
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault());
            String dateTime = sdf.format(new Date());
            
            // Create a "Mark as Read" action
            Intent markReadIntent = new Intent(context, MainActivity.class);
            markReadIntent.setAction(MainActivity.ACTION_MARK_AS_READ);
            PendingIntent markReadPendingIntent = PendingIntent.getActivity(
                    context, 1, markReadIntent, PendingIntent.FLAG_IMMUTABLE);
                    
            // Create a "View Tasks" action  
            Intent viewTasksIntent = new Intent(context, MainActivity.class);
            viewTasksIntent.setAction(MainActivity.ACTION_VIEW_TASKS);
            PendingIntent viewTasksPendingIntent = PendingIntent.getActivity(
                    context, 2, viewTasksIntent, PendingIntent.FLAG_IMMUTABLE);
            
            // Get app icon for the notification avatar
            Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);

            // Create a person for the message style (Gmail-like)
            Person sender = new Person.Builder()
                    .setName("TaskFlow")
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .build();

            // Build a Gmail-like notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_logo) // Updated to use ic_logo instead of ic_notification
                    .setContentTitle("Welcome to TaskFlow")
                    .setContentText("You've successfully logged in as " + userEmail)
                    .setLargeIcon(largeIcon)
                    .setStyle(new NotificationCompat.MessagingStyle(sender)
                            .addMessage("Welcome back to TaskFlow! You've successfully logged in.", 
                                    System.currentTimeMillis(), sender)
                            .addMessage("Login time: " + dateTime + 
                                    "\nAccount: " + userEmail, System.currentTimeMillis(), sender)
                            .setConversationTitle("TaskFlow Login"))
                    .setColor(context.getResources().getColor(R.color.button_color))
                    .setPriority(NotificationCompat.PRIORITY_HIGH) // Increased priority
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_logo, "Mark as Read", markReadPendingIntent)
                    .addAction(R.drawable.ic_logo, "View Tasks", viewTasksPendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

            // Show notification using NotificationManagerCompat
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            
            // Check for notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (notificationManager.areNotificationsEnabled()) {
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                    Log.d(TAG, "Login notification posted successfully");
                } else {
                    Log.e(TAG, "Notifications are not enabled for this app");
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                Log.d(TAG, "Login notification posted successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing login notification", e);
        }
    }

    /**
     * Shows a task summary notification with dynamic content from actual tasks
     */
    public static void showTaskSummaryNotification(Context context, List<Task> tasks, String displayDate) {
        createNotificationChannel(context);

        // Create an intent to open the app when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_logo) // Updated to use ic_logo instead of ic_notification
                .setContentTitle("Tasks for " + displayDate)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Create the notification content based on tasks
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle("Tasks for " + displayDate);

        if (tasks == null || tasks.isEmpty()) {
            // No tasks for tomorrow
            builder.setContentText("You have no tasks scheduled for tomorrow.");
            inboxStyle.addLine("You have no tasks scheduled for tomorrow.");
        } else {
            // Tasks found
            builder.setContentText("You have " + tasks.size() + " task(s) scheduled for tomorrow.");
            
            // Show up to 5 tasks in the expanded notification
            int taskCount = Math.min(5, tasks.size());
            for (int i = 0; i < taskCount; i++) {
                Task task = tasks.get(i);
                StringBuilder taskLine = new StringBuilder();
                taskLine.append("• ").append(task.getTitle());
                
                if (task.getStartTime() != null && !task.getStartTime().isEmpty()) {
                    taskLine.append(" (").append(task.getStartTime());
                    if (task.getEndTime() != null && !task.getEndTime().isEmpty()) {
                        taskLine.append(" - ").append(task.getEndTime());
                    }
                    taskLine.append(")");
                }
                
                inboxStyle.addLine(taskLine.toString());
            }
            
            if (tasks.size() > 5) {
                inboxStyle.addLine("...and " + (tasks.size() - 5) + " more task(s)");
            }
            
            inboxStyle.setSummaryText(tasks.size() + " task(s) for tomorrow");
        }
        
        builder.setStyle(inboxStyle);

        // Show notification
        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (notificationManager.areNotificationsEnabled()) {
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                }
            } else {
                notificationManager.notify(NOTIFICATION_ID, builder.build());
            }
        } catch (Exception e) {
            Log.e("NotificationUtil", "Error showing notification: " + e.getMessage());
        }
    }

    private static void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Login Notifications";
            String description = "Login activity notifications for TaskFlow";
            int importance = NotificationManager.IMPORTANCE_HIGH; // Upgraded importance for better visibility
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.enableLights(true);
            
            // Register the channel with the system
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Login notification channel created");
            } else {
                Log.e(TAG, "NotificationManager is null");
            }
        }
    }
}
