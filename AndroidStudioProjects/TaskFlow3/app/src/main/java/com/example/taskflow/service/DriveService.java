package com.example.taskflow.service;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Service class for Google Drive operations.
 * Handles authentication, file uploads, and folder management.
 */
public class DriveService {
    private static final String TAG = "DriveService";
    private static final String APPLICATION_NAME = "TaskFlow";
    private static final String ROOT_FOLDER_NAME = "TaskFlow Attachments";
    
    private final Context context;
    private final String accountName;
    private final Executor executor;
    private GoogleAccountCredential credential;
    private Drive service;

    public interface DriveCallback<T> {
        void onSuccess(T result);
        void onFailure(Exception e);
    }

    public DriveService(Context context, String accountName) {
        this.context = context;
        this.accountName = accountName;
        this.executor = Executors.newSingleThreadExecutor();
        setupCredential();
    }

    private void setupCredential() {
        try {
            // Initialize credentials and service object
            credential = GoogleAccountCredential.usingOAuth2(
                    context, Collections.singletonList(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccountName(accountName);

            // Create Drive service with NetHttpTransport
            NetHttpTransport transport = new NetHttpTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            
            service = new Drive.Builder(transport, jsonFactory, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up Drive credential", e);
        }
    }
    
    /**
     * Uploads a file to Google Drive in the TaskFlow folder.
     * Creates the root folder if it doesn't exist.
     * 
     * @param uri The URI of the file to upload
     * @param taskId The ID of the task this file is associated with
     * @param fileName The name to give the file in Google Drive
     * @param mimeType The MIME type of the file
     * @param callback Callback to handle the result
     */
    public void uploadFile(Uri uri, String taskId, String fileName, String mimeType, DriveCallback<String> callback) {
        executor.execute(() -> {
            try {
                // Create a temporary local file from the Uri
                java.io.File tempFile = createTempFileFromUri(uri, fileName);
                if (tempFile == null) {
                    throw new IOException("Failed to create temp file from Uri");
                }
                
                // First, ensure root folder exists
                String rootFolderId = getOrCreateRootFolder();
                
                // Then, ensure task folder exists
                String taskFolderName = "Task_" + taskId;
                String taskFolderId = getOrCreateFolder(taskFolderName, rootFolderId);
                
                // Finally, upload the file to the task folder
                String fileId = uploadFileToFolder(tempFile, fileName, mimeType, taskFolderId);
                
                // Clean up the temp file
                if (!tempFile.delete()) {
                    Log.w(TAG, "Could not delete temp file: " + tempFile.getPath());
                }
                
                if (callback != null) {
                    callback.onSuccess(fileId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error uploading file to Drive", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            }
        });
    }
    
    /**
     * Creates a temporary file from a content URI
     */
    private java.io.File createTempFileFromUri(Uri uri, String fileName) throws IOException {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            return null;
        }
        
        java.io.File tempFile = new java.io.File(context.getCacheDir(), fileName);
        OutputStream outputStream = new FileOutputStream(tempFile);
        
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        
        outputStream.close();
        inputStream.close();
        
        return tempFile;
    }
    
    /**
     * Gets or creates the root TaskFlow folder in Google Drive
     */
    private String getOrCreateRootFolder() throws IOException {
        // Check if folder already exists
        String folderId = findFolderIdByName(ROOT_FOLDER_NAME, null);
        
        // If not found, create it
        if (folderId == null) {
            folderId = createFolder(ROOT_FOLDER_NAME, null);
        }
        
        return folderId;
    }
    
    /**
     * Gets or creates a folder with the given name in the specified parent folder
     */
    private String getOrCreateFolder(String folderName, String parentFolderId) throws IOException {
        // Check if folder already exists
        String folderId = findFolderIdByName(folderName, parentFolderId);
        
        // If not found, create it
        if (folderId == null) {
            folderId = createFolder(folderName, parentFolderId);
        }
        
        return folderId;
    }
    
    /**
     * Finds a folder ID by name and optional parent folder ID
     */
    private String findFolderIdByName(String folderName, String parentFolderId) throws IOException {
        String query = "mimeType='application/vnd.google-apps.folder' and name='" + folderName + "' and trashed=false";
        
        if (parentFolderId != null) {
            query += " and '" + parentFolderId + "' in parents";
        }
        
        FileList result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute();
                
        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }
        
        return null;
    }
    
    /**
     * Creates a folder in Google Drive
     */
    private String createFolder(String folderName, String parentFolderId) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(folderName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        
        if (parentFolderId != null) {
            fileMetadata.setParents(Collections.singletonList(parentFolderId));
        }
        
        File folder = service.files().create(fileMetadata)
                .setFields("id")
                .execute();
                
        return folder.getId();
    }
    
    /**
     * Uploads a file to a specific folder in Google Drive
     */
    private String uploadFileToFolder(java.io.File file, String fileName, String mimeType, String folderId) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        
        if (folderId != null) {
            fileMetadata.setParents(Collections.singletonList(folderId));
        }
        
        // Create file content
        FileContent mediaContent = new FileContent(mimeType, file);
        
        // Upload file
        File uploadedFile = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();
                
        return uploadedFile.getId();
    }
}