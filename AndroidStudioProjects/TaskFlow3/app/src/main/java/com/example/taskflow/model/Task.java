package com.example.taskflow.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.taskflow.util.DateConverter;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity(tableName = "tasks")
@TypeConverters({DateConverter.class})
public class Task implements Serializable {
    @PrimaryKey
    @NonNull
    private String id;
    private String title;
    private String description;
    private Date date;
    private String startTime;
    private String endTime;
    private String status;
    private String category;
    private String googleTaskId;
    private String userEmail;
    private String attachmentUri;
    private String attachmentName;
    private String driveFileId;
    private Date createdAt; // New field to track when the task was created

    public Task() {
        this.id = UUID.randomUUID().toString();
        this.status = "PENDING"; // Default status
        this.createdAt = new Date(); // Set creation time to now
    }

    @Ignore
    public Task(String title, String description, Date date, 
               String startTime, String endTime, String category) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = "PENDING";
        this.category = category;
        this.createdAt = new Date(); // Set creation time to now
    }

    @Ignore
    public Task(String title, String description, Date date, String startTime, String endTime) {
        this(title, description, date, startTime, endTime, ""); // Call the existing constructor with empty category
    }

    public Task(Task sourceTask) {
        this.id = sourceTask.id;
        this.title = sourceTask.title;
        this.description = sourceTask.description;
        this.date = sourceTask.date;
        this.startTime = sourceTask.startTime;
        this.endTime = sourceTask.endTime;
        this.status = sourceTask.status;
        this.category = sourceTask.category;
        this.googleTaskId = sourceTask.googleTaskId;
        this.userEmail = sourceTask.userEmail;
        this.attachmentUri = sourceTask.attachmentUri;
        this.attachmentName = sourceTask.attachmentName;
        this.driveFileId = sourceTask.driveFileId;
        this.createdAt = sourceTask.createdAt;
    }
    
    // Convert Task to Firestore document
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("title", title);
        map.put("description", description);
        map.put("date", date);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        map.put("status", status);
        map.put("category", category);
        map.put("googleTaskId", googleTaskId);
        map.put("userEmail", userEmail);
        map.put("attachmentUri", attachmentUri);
        map.put("attachmentName", attachmentName);
        map.put("driveFileId", driveFileId);
        map.put("createdAt", createdAt); // Include creation time in Firestore
        return map;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getGoogleTaskId() {
        return googleTaskId;
    }

    public void setGoogleTaskId(String googleTaskId) {
        this.googleTaskId = googleTaskId;
    }
    
    public String getAttachmentUri() {
        return attachmentUri;
    }
    
    public void setAttachmentUri(String attachmentUri) {
        this.attachmentUri = attachmentUri;
    }
    
    public String getAttachmentName() {
        return attachmentName;
    }
    
    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }
    
    public String getDriveFileId() {
        return driveFileId;
    }
    
    public void setDriveFileId(String driveFileId) {
        this.driveFileId = driveFileId;
    }
    
    public Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
