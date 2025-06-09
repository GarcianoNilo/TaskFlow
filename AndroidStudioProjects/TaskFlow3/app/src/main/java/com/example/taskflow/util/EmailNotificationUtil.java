package com.example.taskflow.util;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.example.taskflow.model.Task;
import com.example.taskflow.service.TaskService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Utility class for sending email notifications and testing dynamic notifications
 */
public class EmailNotificationUtil {

    private static final String TAG = "EmailNotificationUtil";
    private static final String EMAIL_USERNAME = "2201105612@student.buksu.edu.ph"; // Student email
    private static final String EMAIL_PASSWORD = "lekl hknv zyvi jqnb"; // App password
    
    /**
     * Send a task summary notification to the user's Gmail
     */
    public static void sendTaskSummaryEmail(Context context, String userEmail, String subject, String messageBody) {
        new SendMailAsyncTask(userEmail, subject, messageBody).execute();
    }
    
    /**
     * Generate a task summary notification with real data from tomorrow's tasks
     */
    public static void sendDynamicTaskSummaryNotification(Context context, String userEmail) {
        // First, get tomorrow's date
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, 1); // Tomorrow
        Date tomorrow = calendar.getTime();
        
        // Format the date for display and database query
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String tomorrowDateStr = dateFormat.format(tomorrow);
        SimpleDateFormat displayFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
        String displayDate = displayFormat.format(tomorrow);
        
        // Fetch actual tasks for tomorrow using TaskService
        TaskService taskService = new TaskService(context, userEmail);
        taskService.getAllTasks(null, new TaskService.TasksCallback() {
            @Override
            public void onSuccess(List<Task> allTasks) {
                // Filter for tomorrow's tasks
                List<Task> tomorrowTasks = filterTasksForDate(allTasks, tomorrowDateStr);
                
                // Create an email with the tasks
                String subject = "TaskFlow: Your Tasks for " + displayDate;
                
                StringBuilder emailBodyBuilder = new StringBuilder();
                emailBodyBuilder.append("<html><body>");
                emailBodyBuilder.append("<h2 style='color:#08244D;'>Task Summary for ").append(displayDate).append("</h2>");
                
                if (tomorrowTasks.isEmpty()) {
                    emailBodyBuilder.append("<p>You have no tasks scheduled for tomorrow.</p>");
                } else {
                    emailBodyBuilder.append("<p>You have <b>").append(tomorrowTasks.size()).append("</b> task(s) scheduled:</p>");
                    emailBodyBuilder.append("<table style='border-collapse: collapse; width: 100%;'>");
                    emailBodyBuilder.append("<tr style='background-color: #AFC1D0;'>");
                    emailBodyBuilder.append("<th style='padding: 8px; text-align: left; border: 1px solid #ddd;'>Task</th>");
                    emailBodyBuilder.append("<th style='padding: 8px; text-align: left; border: 1px solid #ddd;'>Time</th>");
                    emailBodyBuilder.append("<th style='padding: 8px; text-align: left; border: 1px solid #ddd;'>Status</th>");
                    emailBodyBuilder.append("</tr>");
                    
                    for (Task task : tomorrowTasks) {
                        emailBodyBuilder.append("<tr>");
                        emailBodyBuilder.append("<td style='padding: 8px; text-align: left; border: 1px solid #ddd;'>")
                                .append(task.getTitle()).append("</td>");
                        
                        String timeInfo = "";
                        if (task.getStartTime() != null && !task.getStartTime().isEmpty()) {
                            timeInfo = task.getStartTime();
                            if (task.getEndTime() != null && !task.getEndTime().isEmpty()) {
                                timeInfo += " - " + task.getEndTime();
                            }
                        }
                        
                        emailBodyBuilder.append("<td style='padding: 8px; text-align: left; border: 1px solid #ddd;'>")
                                .append(timeInfo).append("</td>");
                        
                        String statusStyle = "color: #F9A826;"; // Pending color
                        if ("COMPLETED".equals(task.getStatus())) {
                            statusStyle = "color: #2ECC71;"; // Completed color
                        } else if ("IN_PROGRESS".equals(task.getStatus())) {
                            statusStyle = "color: #3498DB;"; // In Progress color
                        }
                        
                        emailBodyBuilder.append("<td style='padding: 8px; text-align: left; border: 1px solid #ddd;'>")
                                .append("<span style='").append(statusStyle).append("'>")
                                .append(task.getStatus()).append("</span></td>");
                        emailBodyBuilder.append("</tr>");
                    }
                    emailBodyBuilder.append("</table>");
                }
                
                emailBodyBuilder.append("<p style='margin-top: 20px;'>Open your TaskFlow app to view details or add new tasks.</p>");
                emailBodyBuilder.append("<p style='color:#666; font-size: 12px;'>This is an automated message from TaskFlow. Please do not reply to this email.</p>");
                emailBodyBuilder.append("</body></html>");
                
                // Send the email to the user
                sendTaskSummaryEmail(context, userEmail, subject, emailBodyBuilder.toString());
                
                // Also send a notification on the device
                NotificationUtil.showTaskSummaryNotification(context, tomorrowTasks, displayDate);
            }
            
            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to fetch tasks for email notification", e);
                // Send a generic notification as fallback
                String subject = "TaskFlow: Your Tasks for " + displayDate;
                String message = "<html><body>" +
                        "<h2 style='color:#08244D;'>Task Summary</h2>" +
                        "<p>We couldn't retrieve your tasks for tomorrow.</p>" +
                        "<p>Please open the TaskFlow app to see your scheduled tasks.</p>" +
                        "<p style='color:#666; font-size: 12px;'>This is an automated message from TaskFlow. Please do not reply to this email.</p>" +
                        "</body></html>";
                
                sendTaskSummaryEmail(context, userEmail, subject, message);
                
                // Also show a local notification
                NotificationUtil.showTaskSummaryNotification(context, null, displayDate);
            }
        });
    }

    /**
     * Filter tasks for a specific date
     */
    private static List<Task> filterTasksForDate(List<Task> allTasks, String dateStr) {
        List<Task> filteredTasks = new java.util.ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        
        for (Task task : allTasks) {
            if (task.getDate() != null) {
                String taskDateStr = dateFormat.format(task.getDate());
                if (dateStr.equals(taskDateStr)) {
                    filteredTasks.add(task);
                }
            }
        }
        
        return filteredTasks;
    }

    /**
     * AsyncTask for sending emails without blocking the main thread
     */
    private static class SendMailAsyncTask extends AsyncTask<Void, Void, Boolean> {
        private String recipientEmail;
        private String subject;
        private String messageBody;
        private Exception exception = null;

        public SendMailAsyncTask(String recipientEmail, String subject, String messageBody) {
            this.recipientEmail = recipientEmail;
            this.subject = subject;
            this.messageBody = messageBody;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.port", "465");

                Session session = Session.getInstance(props,
                    new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(EMAIL_USERNAME, EMAIL_PASSWORD);
                        }
                    });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_USERNAME));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                message.setSubject(subject);
                message.setContent(messageBody, "text/html; charset=utf-8");

                Transport.send(message);
                
                Log.d(TAG, "Email sent successfully to " + recipientEmail);
                return true;
            } catch (MessagingException e) {
                this.exception = e;
                Log.e(TAG, "Failed to send email: " + e.getMessage(), e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (!success) {
                Log.e(TAG, "Email delivery failed", exception);
            }
        }
    }
}