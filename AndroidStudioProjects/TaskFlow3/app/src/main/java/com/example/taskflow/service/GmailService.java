package com.example.taskflow.service;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Service class for Gmail API operations
 * Used to send emails via the user's Gmail account
 */
public class GmailService {
    private static final String TAG = "GmailService";
    private static final String APPLICATION_NAME = "TaskFlow";
    
    private final Context context;
    private final String accountName;
    private Gmail service;
    private GoogleAccountCredential credential;
    private final Executor executor;
    
    public GmailService(Context context, String accountName) {
        this.context = context;
        this.accountName = accountName;
        this.executor = Executors.newSingleThreadExecutor();
        setupCredential();
    }
    
    private void setupCredential() {
        try {
            // Initialize credentials with Gmail API scope
            Log.d(TAG, "Setting up Gmail credential for account: " + accountName);
            credential = GoogleAccountCredential.usingOAuth2(
                    context, Collections.singletonList(GmailScopes.GMAIL_SEND));
            credential.setSelectedAccountName(accountName);

            // Build the Gmail service
            NetHttpTransport transport = new NetHttpTransport();
            GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            
            service = new Gmail.Builder(transport, jsonFactory, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
            
            if (service != null) {
                Log.d(TAG, "Gmail service initialized successfully");
            } else {
                Log.e(TAG, "Failed to initialize Gmail service");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Gmail credential", e);
        }
    }
    
    /**
     * Send a login notification email to the user
     * @param recipientEmail Email address to receive the notification
     * @param callback Callback to handle success/failure of email sending
     */
    public void sendLoginNotificationEmail(String recipientEmail, EmailCallback callback) {
        if (service == null) {
            Log.e(TAG, "Cannot send email: Gmail service is not initialized");
            callback.onFailure(new Exception("Gmail service is not initialized"));
            return;
        }
        
        Log.d(TAG, "Attempting to send login notification email to: " + recipientEmail);
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Creating email message...");
                MimeMessage mimeMessage = createLoginNotificationEmail(recipientEmail);
                Log.d(TAG, "Converting to Gmail API message format...");
                Message message = createMessageFromEmail(mimeMessage);
                
                Log.d(TAG, "Sending email via Gmail API...");
                // Send the message
                Message sentMessage = service.users().messages().send("me", message).execute();
                
                if (sentMessage != null && sentMessage.getId() != null) {
                    Log.d(TAG, "Email sent successfully, message ID: " + sentMessage.getId());
                    // Execute callback on success
                    callback.onSuccess();
                } else {
                    Log.e(TAG, "Email sending failed: No message ID returned");
                    callback.onFailure(new Exception("Failed to send email: No message ID returned"));
                }
                
            } catch (GoogleJsonResponseException e) {
                Log.e(TAG, "GoogleJsonResponseException in sending email. Status code: " + e.getStatusCode(), e);
                if (e.getStatusCode() == 403 || e.getStatusCode() == 401) {
                    Log.e(TAG, "Permission denied. Check OAuth scopes and account permissions.");
                    callback.onFailure(new Exception("Permission denied: Requires Gmail permission. Status code: " + e.getStatusCode()));
                } else {
                    Log.e(TAG, "Other API error: " + e.getDetails().getMessage());
                    callback.onFailure(e);
                }
            } catch (MessagingException e) {
                Log.e(TAG, "MessagingException while creating email", e);
                callback.onFailure(e);
            } catch (Exception e) {
                Log.e(TAG, "Exception in sending email", e);
                callback.onFailure(e);
            }
        });
    }
    
    /**
     * Create an email message with login notification content
     * @param to Recipient email address
     * @return MimeMessage object representing the email
     */
    private MimeMessage createLoginNotificationEmail(String to) throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);
        
        MimeMessage email = new MimeMessage(session);
        
        email.setFrom(new InternetAddress(accountName));
        email.addRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject("TaskFlow Login Notification");
        
        // Get device model information for more detailed notification
        String deviceModel = Build.MANUFACTURER + " " + Build.MODEL;
        
        // Create a more detailed HTML message body
        String htmlBody = "<div style='font-family: Arial, sans-serif; padding: 20px; max-width: 600px;'>"
                + "<h2 style='color: #4285f4;'>TaskFlow Login Alert</h2>"
                + "<p>Hello,</p>"
                + "<p>Your TaskFlow account was just accessed.</p>"
                + "<div style='background-color: #f1f1f1; padding: 15px; border-radius: 5px;'>"
                + "<p><strong>Date & Time:</strong> " + new java.util.Date() + "</p>"
                + "<p><strong>Account:</strong> " + to + "</p>"
                + "<p><strong>Device:</strong> " + deviceModel + "</p>"
                + "</div>"
                + "<p>If this was you, no action is needed.</p>"
                + "<p>If you didn't sign in, please secure your account immediately by changing your password.</p>"
                + "<p style='color: #777; font-size: 12px; margin-top: 30px;'>This is an automated message from TaskFlow. Please do not reply to this email.</p>"
                + "</div>";
                
        email.setContent(htmlBody, "text/html; charset=utf-8");
        return email;
    }
    
    /**
     * Convert MimeMessage to Gmail API Message format
     */
    private Message createMessageFromEmail(MimeMessage emailContent) throws MessagingException, java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }
    
    /**
     * Callback interface for email operations
     */
    public interface EmailCallback {
        void onSuccess();
        void onFailure(Exception e);
    }
}