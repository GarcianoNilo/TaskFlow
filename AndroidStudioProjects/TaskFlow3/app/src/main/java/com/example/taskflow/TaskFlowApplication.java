package com.example.taskflow;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.example.taskflow.util.TranssionCompatHelper;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.security.ProviderInstaller;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

/**
 * Application class for TaskFlow to initialize Firebase and other app-wide configurations
 */
public class TaskFlowApplication extends Application {
    private static final String TAG = "TaskFlowApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Update Android Security Provider to avoid SSL issues
        updateAndroidSecurityProvider();
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized successfully");
            
            // Configure Firestore settings for better offline support
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();
            
            FirebaseFirestore.getInstance().setFirestoreSettings(settings);
            Log.d(TAG, "Firestore settings configured for offline use");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase", e);
        }
        
        // Initialize any app-wide resources here
        
        // Handle Transsion device compatibility
        handleTranssionCompatibility();
    }
    
    /**
     * Update Android Security Provider to fix SSL and connection issues
     * with Google Play Services on older devices
     */
    private void updateAndroidSecurityProvider() {
        try {
            ProviderInstaller.installIfNeeded(this);
            Log.d(TAG, "Security provider installed successfully");
        } catch (GooglePlayServicesRepairableException e) {
            // Google Play Services is available but needs update
            // Show dialog to update Google Play Services
            Log.w(TAG, "Google Play Services needs update", e);
            GoogleApiAvailability.getInstance()
                .showErrorNotification(this, e.getConnectionStatusCode());
        } catch (GooglePlayServicesNotAvailableException e) {
            // Google Play Services not available
            Log.e(TAG, "Google Play Services not available", e);
        } catch (Exception e) {
            // Handle any other exceptions
            Log.e(TAG, "Error updating security provider", e);
        }
    }
    
    /**
     * Handle compatibility issues with Transsion devices (Tecno, Infinix, itel)
     * by setting up a custom class loader that will ignore certain system exceptions
     */
    private void handleTranssionCompatibility() {
        try {
            // Initialize our specialized Transsion compatibility helper
            TranssionCompatHelper.init();
            
            // Check if this is a Transsion device by trying to load a known Transsion class
            try {
                Class.forName("com.transsion.hubcore.utils.TranClassInfo");
                Log.i(TAG, "Transsion device detected, applying compatibility patches");
                
                // This is a Transsion device, set up error handling
                Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable throwable) {
                        // Check if this is a Transsion-related exception
                        if (isTranssionException(throwable)) {
                            Log.w(TAG, "Ignored Transsion-specific exception: " + throwable.getMessage());
                        } else {
                            // Forward to default handler for non-Transsion exceptions
                            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(thread, throwable);
                        }
                    }
                });
            } catch (ClassNotFoundException e) {
                // Not a Transsion device, no action needed
                Log.d(TAG, "Not a Transsion device, skipping compatibility patches");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Transsion compatibility", e);
            // Continue app initialization even if this fails
        }
    }
    
    /**
     * Check if an exception is related to Transsion-specific classes
     */
    private boolean isTranssionException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        
        // Check the exception message and stacktrace
        String message = throwable.getMessage();
        if (message != null && 
            (message.contains("com.transsion.hubcore") || 
             message.contains("TranWindowManager"))) {
            return true;
        }
        
        // Check cause
        if (throwable.getCause() != null) {
            return isTranssionException(throwable.getCause());
        }
        
        return false;
    }
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // You can add MultiDex initialization here if needed
    }
}