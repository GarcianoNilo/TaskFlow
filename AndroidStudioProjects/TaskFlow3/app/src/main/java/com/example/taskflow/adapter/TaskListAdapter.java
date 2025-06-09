package com.example.taskflow.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskflow.R;
import com.example.taskflow.model.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskListAdapter extends RecyclerView.Adapter<TaskListAdapter.TaskViewHolder> {

    // Filter constants
    public static final int FILTER_TODAY = 0;
    public static final int FILTER_TOMORROW = 1;
    public static final int FILTER_THIS_WEEK = 2;

    private final Context context;
    private List<Task> allTasks;
    private List<Task> filteredTasks;
    private final TaskAdapter.TaskActionListener taskActionListener;
    private int currentFilter = FILTER_TODAY; // Default filter
    private Date selectedDate = null;

    public TaskListAdapter(Context context, TaskAdapter.TaskActionListener listener) {
        this.context = context;
        this.taskActionListener = listener;
        this.allTasks = new ArrayList<>();
        this.filteredTasks = new ArrayList<>();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = filteredTasks.get(position);

        // Set task title
        holder.titleText.setText(task.getTitle());

        // Set task description (if available)
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            holder.descriptionText.setVisibility(View.VISIBLE);
            holder.descriptionText.setText(task.getDescription());
        } else {
            holder.descriptionText.setVisibility(View.GONE);
        }

        // Format and set the date
        if (task.getDate() != null) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            holder.dateText.setText(dateFormat.format(task.getDate()));
            holder.dateContainer.setVisibility(View.VISIBLE);
        } else {
            holder.dateContainer.setVisibility(View.GONE);
        }

        // Format and set the time
        if (task.getStartTime() != null && task.getEndTime() != null) {
            holder.timeText.setText(String.format("%s - %s", task.getStartTime(), task.getEndTime()));
            holder.timeContainer.setVisibility(View.VISIBLE);
        } else {
            holder.timeContainer.setVisibility(View.GONE);
        }

        // Set task completion status
        boolean isCompleted = "COMPLETED".equalsIgnoreCase(task.getStatus());
        holder.checkBox.setChecked(isCompleted);
        if (isCompleted) {
            holder.titleText.setAlpha(0.5f);
        } else {
            holder.titleText.setAlpha(1.0f);
        }

        // Set up the actions (more button)
        holder.moreButton.setOnClickListener(v -> {
            // Show popup menu with actions
            android.widget.PopupMenu popup = new android.widget.PopupMenu(context, holder.moreButton);
            popup.inflate(R.menu.task_actions_menu);
            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();

                if (itemId == R.id.action_edit) {
                    taskActionListener.onTaskEdit(task);
                    return true;
                } else if (itemId == R.id.action_delete) {
                    taskActionListener.onTaskDeleted(task);
                    return true;
                } else if (itemId == R.id.action_share) {
                    taskActionListener.onTaskShare(task);
                    return true;
                }

                return false;
            });
            popup.show();
        });

        // Set checkbox listener
        holder.checkBox.setOnClickListener(v -> {
            boolean newState = holder.checkBox.isChecked();
            taskActionListener.onTaskCompletionChanged(task, newState);
            // Visual feedback is updated when task list is refreshed
        });
    }

    @Override
    public int getItemCount() {
        return filteredTasks.size();
    }

    /**
     * Set the full list of tasks and apply the current filter
     */
    public void setTasks(List<Task> tasks) {
        this.allTasks = new ArrayList<>(tasks);
        applyFilter(currentFilter);
    }

    /**
     * Apply a filter based on time period
     */
    public void applyFilter(int filterType) {
        currentFilter = filterType;
        
        // First apply date filter if selected
        List<Task> dateFiltered = selectedDate != null ? filterTasksByDate(allTasks, selectedDate) : new ArrayList<>(allTasks);
        
        // Then apply time period filter
        filteredTasks = filterTasksByTimePeriod(dateFiltered, filterType);
        notifyDataSetChanged();
    }

    /**
     * Filter tasks for a specific date
     */
    public void filterByDate(Date date) {
        this.selectedDate = date;
        applyFilter(currentFilter); // This will apply both date and period filter
    }

    /**
     * Clear date filter
     */
    public void clearDateFilter() {
        selectedDate = null;
        applyFilter(currentFilter);
    }

    /**
     * Get the current filter type
     */
    public int getCurrentFilter() {
        return currentFilter;
    }

    /**
     * Get dates that have tasks
     */
    public List<Date> getDatesWithTasks() {
        List<Date> dates = new ArrayList<>();
        
        for (Task task : allTasks) {
            if (task.getDate() != null) {
                dates.add(task.getDate());
            }
        }
        
        return dates;
    }

    /**
     * Filter tasks by date
     */
    private List<Task> filterTasksByDate(List<Task> tasks, Date date) {
        List<Task> filtered = new ArrayList<>();
        
        Calendar selectedCal = Calendar.getInstance();
        selectedCal.setTime(date);
        
        // Remove time component for comparison
        selectedCal.set(Calendar.HOUR_OF_DAY, 0);
        selectedCal.set(Calendar.MINUTE, 0);
        selectedCal.set(Calendar.SECOND, 0);
        selectedCal.set(Calendar.MILLISECOND, 0);
        
        for (Task task : tasks) {
            if (task.getDate() != null) {
                Calendar taskCal = Calendar.getInstance();
                taskCal.setTime(task.getDate());
                
                // Remove time component for comparison
                taskCal.set(Calendar.HOUR_OF_DAY, 0);
                taskCal.set(Calendar.MINUTE, 0);
                taskCal.set(Calendar.SECOND, 0);
                taskCal.set(Calendar.MILLISECOND, 0);
                
                if (taskCal.equals(selectedCal)) {
                    filtered.add(task);
                }
            }
        }
        
        return filtered;
    }

    /**
     * Filter tasks by time period (today, tomorrow, this week)
     */
    private List<Task> filterTasksByTimePeriod(List<Task> tasks, int filterType) {
        List<Task> filtered = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        
        // Remove time component for comparison
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);

        Calendar endOfWeek = (Calendar) today.clone();
        // Calculate the last day of the current week (Saturday)
        endOfWeek.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
        // If today is already Saturday or Sunday, jump to next week's Saturday
        if (today.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
            today.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            endOfWeek.add(Calendar.WEEK_OF_YEAR, 1);
        }

        switch (filterType) {
            case FILTER_TODAY:
                for (Task task : tasks) {
                    if (task.getDate() != null) {
                        Calendar taskCal = Calendar.getInstance();
                        taskCal.setTime(task.getDate());
                        
                        // Remove time component
                        taskCal.set(Calendar.HOUR_OF_DAY, 0);
                        taskCal.set(Calendar.MINUTE, 0);
                        taskCal.set(Calendar.SECOND, 0);
                        taskCal.set(Calendar.MILLISECOND, 0);
                        
                        if (taskCal.equals(today)) {
                            filtered.add(task);
                        }
                    }
                }
                break;
                
            case FILTER_TOMORROW:
                for (Task task : tasks) {
                    if (task.getDate() != null) {
                        Calendar taskCal = Calendar.getInstance();
                        taskCal.setTime(task.getDate());
                        
                        // Remove time component
                        taskCal.set(Calendar.HOUR_OF_DAY, 0);
                        taskCal.set(Calendar.MINUTE, 0);
                        taskCal.set(Calendar.SECOND, 0);
                        taskCal.set(Calendar.MILLISECOND, 0);
                        
                        if (taskCal.equals(tomorrow)) {
                            filtered.add(task);
                        }
                    }
                }
                break;
                
            case FILTER_THIS_WEEK:
                for (Task task : tasks) {
                    if (task.getDate() != null) {
                        Calendar taskCal = Calendar.getInstance();
                        taskCal.setTime(task.getDate());
                        
                        // Remove time component
                        taskCal.set(Calendar.HOUR_OF_DAY, 0);
                        taskCal.set(Calendar.MINUTE, 0);
                        taskCal.set(Calendar.SECOND, 0);
                        taskCal.set(Calendar.MILLISECOND, 0);
                        
                        // Check if task date is between today and end of week
                        if ((taskCal.equals(today) || taskCal.after(today)) && 
                            (taskCal.equals(endOfWeek) || taskCal.before(endOfWeek))) {
                            filtered.add(task);
                        }
                    }
                }
                break;
        }

        return filtered;
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView titleText;
        TextView descriptionText;
        TextView dateText;
        TextView timeText;
        View dateContainer;
        View timeContainer;
        View moreButton; // Changed from ImageButton to View to support ImageView

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.cb_task_complete);
            titleText = itemView.findViewById(R.id.tv_task_title);
            descriptionText = itemView.findViewById(R.id.tv_task_description_preview);
            dateText = itemView.findViewById(R.id.tv_task_date);
            timeText = itemView.findViewById(R.id.tv_task_time);
            dateContainer = itemView.findViewById(R.id.time_info_container);
            timeContainer = itemView.findViewById(R.id.time_info_container);
            moreButton = itemView.findViewById(R.id.iv_delete_task); // Using delete icon as more button
        }
    }
}