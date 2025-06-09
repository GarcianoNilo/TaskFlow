package com.example.taskflow.adapter;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskflow.R;
import com.example.taskflow.model.Task;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private final List<Task> tasks;
    private TaskActionListener taskActionListener;
    
    // Map for category colors
    private final Map<String, Integer> categoryColors = new HashMap<>();

    public interface TaskActionListener {
        void onTaskCompletionChanged(Task task, boolean isCompleted);
        void onTaskDeleted(Task task);
        default void onTaskEdit(Task task) {}
        default void onTaskShare(Task task) {}
    }

    public TaskAdapter(List<Task> tasks, TaskActionListener listener) {
        this.tasks = tasks;
        this.taskActionListener = listener;
        
        // Initialize category colors
        categoryColors.put("WORK", Color.parseColor("#3498DB"));     // Blue
        categoryColors.put("PERSONAL", Color.parseColor("#9B59B6")); // Purple
        categoryColors.put("HEALTH", Color.parseColor("#2ECC71"));   // Green
        categoryColors.put("EDUCATION", Color.parseColor("#F1C40F")); // Yellow
        categoryColors.put("ERRANDS", Color.parseColor("#E67E22"));  // Orange
        categoryColors.put("FINANCE", Color.parseColor("#16A085"));  // Teal
        categoryColors.put("HOME", Color.parseColor("#E74C3C"));     // Red
        categoryColors.put("OTHER", Color.parseColor("#95A5A6"));    // Gray
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view, taskActionListener);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.bind(task);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void updateTasks(List<Task> newTasks) {
        tasks.clear();
        tasks.addAll(newTasks);
        notifyDataSetChanged();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final TextView descriptionTextView;
        private final TextView dateTextView;
        private final TextView timeTextView;
        private final TextView statusTextView;
        private final TextView categoryTextView;
        private final TextView createdDateTextView;
        private final CheckBox completeCheckBox;
        private final ImageView deleteButton;
        private final View categoryIndicator;
        private final ImageButton editButton;
        private final ImageButton shareButton;
        private final TaskActionListener listener;

        public TaskViewHolder(@NonNull View itemView, TaskActionListener listener) {
            super(itemView);
            this.listener = listener;
            titleTextView = itemView.findViewById(R.id.tv_task_title);
            descriptionTextView = itemView.findViewById(R.id.tv_task_description_preview);
            dateTextView = itemView.findViewById(R.id.tv_task_date);
            timeTextView = itemView.findViewById(R.id.tv_task_time);
            statusTextView = itemView.findViewById(R.id.tv_task_status);
            
            // Comment out or remove references to missing IDs
            // categoryTextView = itemView.findViewById(R.id.tv_task_category);
            // createdDateTextView = itemView.findViewById(R.id.tv_task_created_date);
            categoryTextView = null; // Initialize with null since it's not in the layout
            createdDateTextView = null; // Initialize with null since it's not in the layout
            
            completeCheckBox = itemView.findViewById(R.id.cb_task_complete);
            deleteButton = itemView.findViewById(R.id.iv_delete_task);
            
            // Comment out or remove references to missing IDs
            // categoryIndicator = itemView.findViewById(R.id.category_indicator);
            // editButton = itemView.findViewById(R.id.btn_edit_task);
            // shareButton = itemView.findViewById(R.id.btn_share_task);
            categoryIndicator = null; // Initialize with null since it's not in the layout
            editButton = null; // Initialize with null since it's not in the layout
            shareButton = null; // Initialize with null since it's not in the layout
        }

        public void bind(Task task) {
            // Set task title
            titleTextView.setText(task.getTitle());
            
            // Set task description preview (truncated if necessary)
            String description = task.getDescription();
            if (description != null && !description.isEmpty()) {
                descriptionTextView.setText(description);
                descriptionTextView.setVisibility(View.VISIBLE);
            } else {
                descriptionTextView.setVisibility(View.GONE);
            }
            
            // Set category (default to "OTHER" if not set) - only if categoryTextView exists
            String category = task.getCategory();
            if (category == null || category.isEmpty()) {
                category = "OTHER";
            }
            
            // Only set category text if view exists
            if (categoryTextView != null) {
                categoryTextView.setText(category);
                
                // Set category color if view exists
                Integer categoryColor = categoryColors.get(category);
                if (categoryColor == null) {
                    categoryColor = categoryColors.get("OTHER");
                }
                categoryTextView.getBackground().setTint(categoryColor);
            }
            
            // Only set category indicator color if view exists
            if (categoryIndicator != null) {
                Integer categoryColor = categoryColors.get(category);
                if (categoryColor == null) {
                    categoryColor = categoryColors.get("OTHER");
                }
                categoryIndicator.setBackgroundColor(categoryColor);
            }
            
            // Format and set the scheduled date
            if (task.getDate() != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.US);
                dateTextView.setText(dateFormat.format(task.getDate()));
            } else {
                dateTextView.setText("Not scheduled");
            }
            
            // Set time
            if (task.getStartTime() != null && task.getEndTime() != null) {
                String timeText = task.getStartTime() + " - " + task.getEndTime();
                timeTextView.setText(timeText);
            } else {
                timeTextView.setText("No time set");
            }
            
            // Format and set creation date if available and if view exists
            if (createdDateTextView != null) {
                Date createdDate = task.getCreatedAt();
                if (createdDate != null) {
                    SimpleDateFormat createdDateFormat = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US);
                    createdDateTextView.setText("Created: " + createdDateFormat.format(createdDate));
                    createdDateTextView.setVisibility(View.VISIBLE);
                } else {
                    // If we don't have created date, use current time as fallback
                    SimpleDateFormat createdDateFormat = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US);
                    createdDateTextView.setText("Created: " + createdDateFormat.format(new Date()));
                    createdDateTextView.setVisibility(View.VISIBLE);
                }
            }
            
            // Set completion status
            boolean isCompleted = "COMPLETED".equals(task.getStatus());
            completeCheckBox.setChecked(isCompleted);
            
            // Apply strikethrough effect if completed
            if (isCompleted) {
                titleTextView.setPaintFlags(titleTextView.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                deleteButton.setVisibility(View.VISIBLE);
                // Optional: gray out the text for completed tasks
                titleTextView.setTextColor(Color.parseColor("#888888"));
            } else {
                titleTextView.setPaintFlags(titleTextView.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                deleteButton.setVisibility(View.GONE);
                // Reset text color for non-completed tasks
                titleTextView.setTextColor(itemView.getContext().getColor(R.color.main_text_color));
            }
            
            // Set status text and color
            statusTextView.setText(task.getStatus());
            
            int statusColor;
            switch (task.getStatus()) {
                case "COMPLETED":
                    statusColor = Color.parseColor("#2ECC71"); // Green
                    break;
                case "IN_PROGRESS":
                    statusColor = Color.parseColor("#3498DB"); // Blue
                    break;
                case "OVERDUE":
                    statusColor = Color.parseColor("#E74C3C"); // Red
                    break;
                default:
                    statusColor = Color.parseColor("#F9A826"); // Orange for PENDING
                    break;
            }
            
            statusTextView.getBackground().setTint(statusColor);
            
            // Check if the task is overdue and update status if needed
            if (!isCompleted && task.getDate() != null) {
                Calendar taskDate = Calendar.getInstance();
                taskDate.setTime(task.getDate());
                
                Calendar now = Calendar.getInstance();
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.MINUTE, 0);
                now.set(Calendar.SECOND, 0);
                now.set(Calendar.MILLISECOND, 0);
                
                if (taskDate.before(now)) {
                    // Task is overdue
                    statusTextView.setText("OVERDUE");
                    statusTextView.getBackground().setTint(Color.parseColor("#E74C3C")); // Red
                }
            }
            
            // Set up checkbox listener
            completeCheckBox.setOnClickListener(v -> {
                boolean newCompletionState = completeCheckBox.isChecked();
                if (listener != null) {
                    listener.onTaskCompletionChanged(task, newCompletionState);
                }
            });
            
            // Set up delete button listener (only visible for completed tasks)
            deleteButton.setOnClickListener(v -> {
                if (listener != null && isCompleted) {
                    listener.onTaskDeleted(task);
                }
            });
            
            // Set up edit button listener if view exists
            if (editButton != null) {
                editButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onTaskEdit(task);
                    }
                });
            }
            
            // Set up share button listener if view exists
            if (shareButton != null) {
                shareButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onTaskShare(task);
                    }
                });
            }
        }
    }
}
