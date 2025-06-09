package com.example.taskflow;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.taskflow.databinding.ActivityCreateTaskBinding;
import com.example.taskflow.db.FirebaseTaskRepository;
import com.example.taskflow.model.Task;
import com.example.taskflow.service.DriveService;
import com.example.taskflow.service.TaskService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class CreateTaskActivity extends AppCompatActivity {

    private static final String TAG = "CreateTaskActivity";
    
    private ActivityCreateTaskBinding binding;
    private Calendar selectedDate = Calendar.getInstance();
    private String startTime = "";
    private String endTime = "";
    private Uri attachmentUri = null;
    private String attachmentName = "";
    private String attachmentMimeType = "";
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
    private final SimpleDateFormat timeFormatter = new SimpleDateFormat("hh:mm a", Locale.US);
    private TaskService taskService;
    private DriveService driveService;
    private FirebaseTaskRepository taskRepository;
    private String currentUserEmail = null;

    // Activity result launcher for file picker
    private final ActivityResultLauncher<String> getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            result -> {
                if (result != null) {
                    attachmentUri = result;
                    extractFileInfo(result);
                    displayAttachmentName(result);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize Firebase repository
        taskRepository = FirebaseTaskRepository.getInstance();

        // Initialize services with the current Google account
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && account.getEmail() != null) {
            currentUserEmail = account.getEmail();
            // Check if we have the required Drive scope
            if (!GoogleSignIn.hasPermissions(account, new Scope(DriveScopes.DRIVE_FILE))) {
                requestDrivePermission();
            } else {
                initializeServices(account);
            }
        }

        setupUI();
    }
    
    /**
     * Request additional Drive scope permission
     */
    private void requestDrivePermission() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            GoogleSignIn.requestPermissions(
                this,
                1000,  // Request code
                account,
                new Scope(DriveScopes.DRIVE_FILE)
            );
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // Handle Google Sign-in permission result
        if (requestCode == 1000) {
            if (resultCode == RESULT_OK) {
                // User granted permission
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null) {
                    initializeServices(account);
                }
            } else {
                // User denied permission
                Toast.makeText(this, "Drive permissions are required to save attachments", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    /**
     * Initialize Google services
     */
    private void initializeServices(GoogleSignInAccount account) {
        taskService = new TaskService(this, account.getEmail());
        driveService = new DriveService(this, account.getEmail());
    }

    private void setupUI() {
        // Setup back button
        binding.btnBack.setOnClickListener(v -> finish());

        // Setup date picker
        binding.etDate.setOnClickListener(v -> showDatePicker());

        // Setup time pickers
        binding.etStartTime.setOnClickListener(v -> showTimePicker(true));
        binding.etEndTime.setOnClickListener(v -> showTimePicker(false));

        // Setup attachment button
        binding.btnAttachFile.setOnClickListener(v -> openFilePicker());

        // Delete button initially hidden for new tasks
        binding.btnDelete.setVisibility(View.GONE);

        // Setup save button
        binding.btnSaveTask.setOnClickListener(v -> validateAndSaveTask());
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    binding.etDate.setText(dateFormatter.format(selectedDate.getTime()));
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void showTimePicker(boolean isStartTime) {
        Calendar currentTime = Calendar.getInstance();
        
        // For end time, default to start time + 1 hour if start time is already set
        if (!isStartTime && !startTime.isEmpty()) {
            try {
                // Parse the start time
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
                Date startTimeDate = timeFormat.parse(startTime);
                
                if (startTimeDate != null) {
                    // Set current time to start time + 1 hour for better UX
                    currentTime.setTime(startTimeDate);
                    currentTime.add(Calendar.HOUR_OF_DAY, 1);
                }
            } catch (ParseException e) {
                Log.e(TAG, "Error parsing start time", e);
                // Use current time as fallback
            }
        }
        
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    String formattedTime = timeFormatter.format(calendar.getTime());
                    
                    if (isStartTime) {
                        startTime = formattedTime;
                        binding.etStartTime.setText(formattedTime);
                        
                        // If end time is empty or earlier than new start time, automatically set it to start time + 1 hour
                        try {
                            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
                            Date startTimeDate = timeFormat.parse(formattedTime);
                            
                            // Check if end time needs updating
                            boolean shouldUpdateEndTime = endTime.isEmpty();
                            
                            if (!endTime.isEmpty() && startTimeDate != null) {
                                Date currentEndTime = timeFormat.parse(endTime);
                                if (currentEndTime != null && (currentEndTime.before(startTimeDate) || currentEndTime.equals(startTimeDate))) {
                                    shouldUpdateEndTime = true;
                                }
                            }
                            
                            // Update end time if needed
                            if (shouldUpdateEndTime && startTimeDate != null) {
                                Calendar endCal = Calendar.getInstance();
                                endCal.setTime(startTimeDate);
                                endCal.add(Calendar.HOUR_OF_DAY, 1);
                                
                                endTime = timeFormatter.format(endCal.getTime());
                                binding.etEndTime.setText(endTime);
                            }
                        } catch (ParseException e) {
                            Log.e(TAG, "Error updating end time", e);
                        }
                    } else {
                        endTime = formattedTime;
                        binding.etEndTime.setText(formattedTime);
                    }
                },
                currentTime.get(Calendar.HOUR_OF_DAY),
                currentTime.get(Calendar.MINUTE),
                false
        );
        timePickerDialog.show();
    }

    private void openFilePicker() {
        getContent.launch("*/*");
    }
    
    /**
     * Extract file information from the Uri
     */
    private void extractFileInfo(Uri uri) {
        try {
            ContentResolver contentResolver = getContentResolver();
            
            // Get file name
            String displayName = null;
            android.database.Cursor cursor = contentResolver.query(
                    uri, null, null, null, null);
                    
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    displayName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
            
            // If couldn't get name from cursor, use last path segment
            if (displayName == null) {
                displayName = uri.getLastPathSegment();
            }
            
            // Get MIME type
            String mimeType = contentResolver.getType(uri);
            if (mimeType == null) {
                // Try to guess MIME type from file extension
                String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
                if (fileExtension != null) {
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
                }
                // Default to binary if we still can't determine
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }
            }
            
            this.attachmentName = displayName;
            this.attachmentMimeType = mimeType;
            
            Log.d(TAG, "Selected file: " + displayName + " (" + mimeType + ")");
        } catch (Exception e) {
            Log.e(TAG, "Error extracting file info", e);
            Toast.makeText(this, "Error processing file", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayAttachmentName(Uri uri) {
        try {
            String displayName = attachmentName;
            if (displayName == null || displayName.isEmpty()) {
                displayName = uri.getLastPathSegment();
            }
            
            if (displayName != null) {
                binding.tvFileName.setText(displayName);
                binding.tvFileName.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying attachment name", e);
        }
    }

    private void validateAndSaveTask() {
        String title = binding.etTitle.getText().toString().trim();
        String description = binding.etDescription.getText().toString().trim();
        
        // Validate inputs
        if (title.isEmpty()) {
            binding.tilTitle.setError(getString(R.string.error_title_empty));
            return;
        }
        
        if (binding.etDate.getText().toString().isEmpty()) {
            binding.tilDate.setError(getString(R.string.error_date_required));
            return;
        }
        
        if (startTime.isEmpty() || endTime.isEmpty()) {
            Toast.makeText(this, R.string.error_time_required, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Validate that the task is not in the past
        if (!validateFutureDateTime()) {
            return;
        }
        
        // Validate that end time is after start time
        if (!validateEndTimeAfterStartTime()) {
            return;
        }
        
        // Clear any errors
        binding.tilTitle.setError(null);
        binding.tilDate.setError(null);
        
        // Create task object - add null or empty string for the category parameter
        Task task = new Task(title, description, selectedDate.getTime(), startTime, endTime, "");
        
        // Set the user email for the task
        if (currentUserEmail != null) {
            task.setUserEmail(currentUserEmail);
        }
        
        // Show progress indicator
        binding.progressIndicator.setVisibility(View.VISIBLE);
        
        // Check if Google account is available
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null || taskService == null) {
            saveTaskToFirestore(task);
            return;
        }
        
        // Check for time conflicts before saving
        taskService.checkForTimeConflict(task, new TaskService.TimeConflictCallback() {
            @Override
            public void onConflictCheckComplete(boolean hasConflict) {
                runOnUiThread(() -> {
                    if (hasConflict) {
                        // Time conflict detected
                        binding.progressIndicator.setVisibility(View.GONE);
                        showTimeConflictDialog();
                    } else {
                        // No conflict, proceed with saving
                        saveTaskWithAttachment(task);
                    }
                });
            }
        });
    }

    private void saveTaskWithAttachment(Task task) {
        // First save the task to Google Tasks
        taskService.createTask(task, new TaskService.TaskCallback() {
            @Override
            public void onSuccess(String taskId) {
                // Task saved successfully, now upload attachment if any
                task.setGoogleTaskId(taskId);
                
                if (attachmentUri != null && driveService != null) {
                    uploadAttachment(task);
                } else {
                    // No attachment to upload, save directly to Firestore
                    saveTaskToFirestore(task);
                }
            }

            @Override
            public void onFailure(Exception e) {
                // Handle error but still save to Firestore
                Log.e(TAG, "Failed to save task to Google Tasks", e);
                saveTaskToFirestore(task);
            }
        });
    }
    
    /**
     * Upload the attachment to Google Drive
     */
    private void uploadAttachment(Task task) {
        if (attachmentUri == null || attachmentName == null || attachmentName.isEmpty()) {
            saveTaskToFirestore(task);
            return;
        }
        
        // Save attachment URI for local storage
        task.setAttachmentUri(attachmentUri.toString());
        task.setAttachmentName(attachmentName);
        
        // Upload to Drive
        driveService.uploadFile(
            attachmentUri, 
            task.getId(), 
            attachmentName, 
            attachmentMimeType,
            new DriveService.DriveCallback<String>() {
                @Override
                public void onSuccess(String driveFileId) {
                    // Store Drive file ID with task
                    task.setDriveFileId(driveFileId);
                    
                    runOnUiThread(() -> {
                        Toast.makeText(CreateTaskActivity.this, 
                            "Attachment saved to Google Drive", 
                            Toast.LENGTH_SHORT).show();
                    });
                    saveTaskToFirestore(task);
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Failed to upload attachment to Drive", e);
                    runOnUiThread(() -> {
                        Toast.makeText(CreateTaskActivity.this, 
                            "Error saving attachment to Drive", 
                            Toast.LENGTH_SHORT).show();
                    });
                    saveTaskToFirestore(task);
                }
            });
    }
    
    /**
     * Validates that the selected date and time are not in the past
     * @return true if the date and time are in the future, false otherwise
     */
    private boolean validateFutureDateTime() {
        Calendar now = Calendar.getInstance();
        Calendar taskDateTime = (Calendar) selectedDate.clone();
        
        try {
            // Parse the time
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
            Date startTimeDate = timeFormat.parse(startTime);
            
            if (startTimeDate != null) {
                // Set the time part of the selected date
                Calendar timeCal = Calendar.getInstance();
                timeCal.setTime(startTimeDate);
                
                taskDateTime.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                taskDateTime.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                taskDateTime.set(Calendar.SECOND, 0);
                
                // Compare with current time
                if (taskDateTime.before(now)) {
                    showPastDateTimeDialog();
                    return false;
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        
        return true;
    }
    
    /**
     * Validates that the end time is after the start time
     * @return true if end time is after start time, false otherwise
     */
    private boolean validateEndTimeAfterStartTime() {
        try {
            // Parse the time strings into Date objects for comparison
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.US);
            Date startTimeDate = timeFormat.parse(startTime);
            Date endTimeDate = timeFormat.parse(endTime);
            
            if (startTimeDate != null && endTimeDate != null) {
                // If end time is before or equal to start time
                if (endTimeDate.before(startTimeDate) || endTimeDate.equals(startTimeDate)) {
                    showInvalidEndTimeDialog();
                    return false;
                }
            }
        } catch (ParseException e) {
            Log.e(TAG, "Error parsing time for validation", e);
            return false;
        }
        
        return true;
    }
    
    private void showPastDateTimeDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Invalid Date/Time")
            .setMessage("You cannot create a task in the past. Please select a future date and time.")
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    private void showInvalidEndTimeDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Invalid End Time")
            .setMessage("End time must be after start time. Please adjust your time selection.")
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    private void showTimeConflictDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Time Conflict Detected")
            .setMessage("This task overlaps with an existing task at the selected time. Please choose a different time.")
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    private void saveTaskToFirestore(Task task) {
        // Save the task to Firestore
        taskRepository.saveTask(task, new FirebaseTaskRepository.TaskCallback() {
            @Override
            public void onSuccess() {
                // Invalidate the profile statistics cache since a new task was created
                ProfileActivity.invalidateTaskStatisticsCache();
                
                runOnUiThread(() -> {
                    binding.progressIndicator.setVisibility(View.GONE);
                    Toast.makeText(CreateTaskActivity.this, 
                        "Task created successfully!", Toast.LENGTH_SHORT).show();
                    
                    // Set flag to refresh task list in MainActivity
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("TASK_CREATED", true);
                    setResult(RESULT_OK, returnIntent);
                    
                    finish();
                });
            }
            
            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    binding.progressIndicator.setVisibility(View.GONE);
                    Toast.makeText(CreateTaskActivity.this, 
                        "Failed to create task: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void finishTaskCreation(Task task, Exception error) {
        // This method is deprecated, use saveTaskToFirestore instead
        binding.progressIndicator.setVisibility(View.GONE);
        
        if (error != null) {
            Toast.makeText(this, "Error syncing with Google services.", Toast.LENGTH_SHORT).show();
        }
        
        // Save to Firestore
        saveTaskToFirestore(task);
    }
}
