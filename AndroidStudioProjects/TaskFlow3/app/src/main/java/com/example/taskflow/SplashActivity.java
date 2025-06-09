package com.example.taskflow;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.example.taskflow.databinding.ActivitySplashBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private boolean isNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if user is already logged in
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            // User is already logged in, go directly to MainActivity
            navigateToMain(account);
            return;
        }
        
        // If not logged in, show splash screen
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply animations
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up);
        
        binding.imageView.startAnimation(fadeIn);
        binding.textViewTitle.startAnimation(slideUp);
        binding.textViewSubtitle.startAnimation(slideUp);
        binding.textViewSubtitleEmphasis.startAnimation(slideUp);
        binding.buttonGetStarted.startAnimation(AnimationUtils.loadAnimation(this, R.anim.button_scale));

        // Set up button click listener to navigate to login activity
        binding.buttonGetStarted.setOnClickListener(v -> {
            if (isNavigating) return;
            
            isNavigating = true;
            binding.progressIndicator.setVisibility(View.VISIBLE);
            
            // Add a short delay for better UX
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                navigateToLogin();
            }, 800);
        });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // Close the splash activity so user can't go back to it
    }
    
    private void navigateToMain(GoogleSignInAccount account) {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        
        // Pass user info to MainActivity
        String displayName = account.getDisplayName();
        String email = account.getEmail();
        intent.putExtra("user_name", displayName != null ? displayName : "User");
        intent.putExtra("user_email", email);
        if (account.getPhotoUrl() != null) {
            intent.putExtra("user_photo", account.getPhotoUrl().toString());
        }
        
        startActivity(intent);
        finish(); // Close the splash activity
    }
}
