package com.example.taskflow.util;

import android.util.Log;

/**
 * Helper class to handle Transsion device specific compatibility issues.
 * This is used to prevent crashes and warnings when running on Transsion devices
 * (Tecno, Infinix, itel, etc.) that have custom system classes.
 */
public class TranssionCompatHelper {
    private static final String TAG = "TranssionCompat";
    
    /**
     * Initialize compatibility measures for Transsion devices
     */
    public static void init() {
        try {
            // Replace the class loader for Transsion classes
            setUpClassLoaderHook();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Transsion compatibility", e);
        }
    }
    
    /**
     * Set up a class loader hook to handle Transsion classes
     */
    private static void setUpClassLoaderHook() {
        // List of problematic Transsion packages to handle
        final String[] transsionPackages = {
            "com.transsion.hubcore.view",
            "com.transsion.hubcore.utils"
        };
        
        try {
            // Override the thread context class loader with our own class loader
            // that provides dummy implementations for Transsion classes
            Thread.currentThread().setContextClassLoader(new ClassLoader(Thread.currentThread().getContextClassLoader()) {
                @Override
                public Class<?> loadClass(String name) throws ClassNotFoundException {
                    // Check if this is a Transsion class
                    for (String pkg : transsionPackages) {
                        if (name.startsWith(pkg)) {
                            Log.d(TAG, "Attempting to load Transsion class: " + name);
                            
                            try {
                                // First try normal class loading
                                return super.loadClass(name);
                            } catch (ClassNotFoundException e) {
                                // If class not found, check if it's one of the known problematic classes
                                if (name.equals("com.transsion.hubcore.view.TranWindowManagerGlobalImpl") || 
                                    name.equals("com.transsion.hubcore.view.TranWindowLayoutImpl")) {
                                    
                                    Log.d(TAG, "Providing dummy implementation for: " + name);
                                    
                                    // Return Object as a dummy implementation
                                    // This is just to prevent ClassNotFoundException
                                    return Object.class;
                                }
                                throw e;
                            }
                        }
                    }
                    
                    // Not a Transsion class, use normal class loading
                    return super.loadClass(name);
                }
            });
            
            Log.i(TAG, "Transsion compatibility class loader hook installed");
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception setting up class loader hook", e);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set up class loader hook", e);
        }
    }
}