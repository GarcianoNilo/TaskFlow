package com.example.taskflow;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.taskflow.databinding.ActivityLoginBinding;
import com.example.taskflow.service.GmailService;
import com.example.taskflow.util.NotificationUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.gmail.GmailScopes;

import com.google.android.gms.tasks.Task;

import static com.google.api.services.tasks.TasksScopes.TASKS;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    private GoogleSignInClient mGoogleSignInClient;
    private boolean isLoggingIn = false;
    private GmailService gmailService;

    // Activity result launcher for Google Sign-In
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                try {
                    // Handle the result of the Google Sign-In
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleSignInResult(task);
                } catch (Exception e) {
                    Log.e(TAG, "Google Sign-In failed", e);
                    showLoginError("Google Sign-In failed. Please try again.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Log.d(TAG, "Configuring Google Sign-In with Gmail scopes");
        // Configure Google Sign-In with Tasks API and Gmail API scopes
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestScopes(new Scope(TASKS))
                .requestScopes(new Scope(GmailScopes.GMAIL_SEND))  // Add Gmail API scope
                .build();

        // Build a GoogleSignInClient with the options
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Set up the Google login button
        binding.buttonGoogleLogin.setOnClickListener(v -> {
            if (isLoggingIn) return;
            
            Log.d(TAG, "Google login button clicked, starting sign-in flow");
            // Show loading state
            isLoggingIn = true;
            binding.progressLogin.setVisibility(View.VISIBLE);
            binding.textViewError.setVisibility(View.GONE);

            // Start Google Sign-In
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already signed in
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            Log.d(TAG, "User already signed in: " + account.getEmail());
            
            // Check if we have Gmail scope
            if (!GoogleSignIn.hasPermissions(account, new Scope(GmailScopes.GMAIL_SEND))) {
                Log.d(TAG, "Gmail permissions not granted yet, requesting permission...");
                // Request the missing Gmail scope
                GoogleSignIn.requestPermissions(
                    this,
                    1001,  // Request code for Gmail permission
                    account,
                    new Scope(GmailScopes.GMAIL_SEND)
                );
            } else {
                Log.d(TAG, "User has all required permissions including Gmail scope");
                // User is already signed in with all required permissions, navigate to main activity
                updateUI(account);
            }
        } else {
            Log.d(TAG, "No signed-in user found on startup");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        
        // Handle the result of Gmail API permission request
        if (requestCode == 1001) {
            if (resultCode == RESULT_OK) {
                // User granted Gmail permission
                Log.d(TAG, "User granted Gmail permission");
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null) {
                    // Since the Gmail permission was just granted, let's explicitly send an email notification
                    // before navigating to the main activity
                    if (account.getEmail() != null) {
                        Log.d(TAG, "Sending Gmail notification after permission granted");
                        sendEmailNotification(account.getEmail());
                    }
                    updateUI(account);
                }
            } else {
                // User denied Gmail permission, continue with local notifications only
                Log.w(TAG, "User denied Gmail permission, will use local notifications only");
                Toast.makeText(this, "Email notifications won't be sent without Gmail permissions", 
                        Toast.LENGTH_LONG).show();
                
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null) {
                    updateUI(account);
                }
            }
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d(TAG, "Google Sign-In successful for: " + (account.getEmail() != null ? account.getEmail() : "unknown"));
            
            // Check if we have Gmail scope
            if (!GoogleSignIn.hasPermissions(account, new Scope(GmailScopes.GMAIL_SEND))) {
                Log.d(TAG, "Gmail permissions not granted during sign-in, requesting now");
                // Request the Gmail scope explicitly
                GoogleSignIn.requestPermissions(
                    this,
                    1001,  // Request code for Gmail permission
                    account,
                    new Scope(GmailScopes.GMAIL_SEND)
                );
                return; // Wait for onActivityResult to continue the flow
            }
            
            // If we already have Gmail permission, proceed with the sign-in flow
            updateUI(account);
        } catch (ApiException e) {
            // Sign-in failed
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode(), e);
            
            // Fix for deprecated getStatusMessage() method
            String errorMessage = "Sign-in failed with code: " + e.getStatusCode();
            showLoginError(errorMessage);
        } finally {
            isLoggingIn = false;
            binding.progressLogin.setVisibility(View.GONE);
        }
    }

    private void updateUI(GoogleSignInAccount account) {
        if (account != null) {
            // User successfully signed in
            String displayName = account.getDisplayName();
            String email = account.getEmail();
            
            Log.d(TAG, "Updating UI for signed-in user: " + email);
            
            // Check notification permission for Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Requesting POST_NOTIFICATIONS permission for Android 13+");
                    ActivityCompat.requestPermissions(this, 
                            new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
                } else {
                    Log.d(TAG, "POST_NOTIFICATIONS permission already granted");
                    sendLoginNotification(email);
                }
            } else {
                // For older Android versions
                Log.d(TAG, "Running on pre-Android 13, no need for notification permission");
                sendLoginNotification(email);
            }
            
            // Navigate to main activity with user info
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("user_name", displayName != null ? displayName : "User");
            intent.putExtra("user_email", email);
            if (account.getPhotoUrl() != null) {
                intent.putExtra("user_photo", account.getPhotoUrl().toString());
            }
            startActivity(intent);
            finish();  // This ensures LoginActivity is removed from back stack
        }
    }

    private void sendLoginNotification(String email) {
        if (email != null) {
            Log.d(TAG, "Sending login notification for: " + email);
            
            // Show local notification
            NotificationUtil.showLoginNotification(this, email);
            
            // Send Gmail email notification if we have permission
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null) {
                boolean hasPermission = GoogleSignIn.hasPermissions(account, new Scope(GmailScopes.GMAIL_SEND));
                Log.d(TAG, "Has Gmail send permission: " + hasPermission);
                
                if (hasPermission) {
                    sendEmailNotification(email);
                } else {
                    Log.w(TAG, "Gmail permissions not available, skipping email notification");
                }
            } else {
                Log.e(TAG, "Could not get signed in account for email notification");
            }
        } else {
            Log.e(TAG, "Cannot send notification: email is null");
        }
    }
    
    // Separate method for email notification to avoid code duplication
    private void sendEmailNotification(String email) {
        if (email == null) {
            Log.e(TAG, "Cannot send email notification: email is null");
            return;
        }
        
        // Initialize Gmail service
        Log.d(TAG, "Initializing Gmail service for: " + email);
        gmailService = new GmailService(this, email);
        
        // Send email notification
        Log.d(TAG, "Attempting to send email notification");
        gmailService.sendLoginNotificationEmail(email, new GmailService.EmailCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Login email notification sent successfully for: " + email);
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, 
                        "Login notification email sent", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to send login email notification", e);
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this, 
                        "Could not send email: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, send notification
                Log.d(TAG, "Notification permission granted");
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null && account.getEmail() != null) {
                    sendLoginNotification(account.getEmail());
                }
            }
        }
    }
    
    private void showLoginError(String errorMessage) {
        binding.textViewError.setText(errorMessage);
        binding.textViewError.setVisibility(View.VISIBLE);
        binding.progressLogin.setVisibility(View.GONE);
        isLoggingIn = false;
    }
}
