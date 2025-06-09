package com.example.taskflow.model;

/**
 * Model class representing a Google Task List.
 */
public class TaskList {
    private String id;
    private String title;
    
    public TaskList() {
        // Default constructor
    }
    
    public TaskList(String id, String title) {
        this.id = id;
        this.title = title;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
}