package com.example.taskflow.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.taskflow.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying and handling calendar grid in TaskActivity
 */
public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder> {

    private final Context context;
    private Calendar currentMonth;
    private final List<Date> days;
    private final OnDateClickListener dateClickListener;
    private final Calendar today;
    private Date selectedDate;
    private List<Date> datesWithTasks;

    // Calendar start date (usually start of month)
    private Calendar startDate;
    
    // Calendar end date (usually end of month)
    private Calendar endDate;

    public interface OnDateClickListener {
        void onDateClick(Date date);
    }

    public CalendarAdapter(Context context, Calendar currentMonth, OnDateClickListener listener) {
        this.context = context;
        this.currentMonth = (Calendar) currentMonth.clone();
        this.dateClickListener = listener;
        this.days = new ArrayList<>();
        this.today = Calendar.getInstance();
        this.selectedDate = today.getTime();
        this.datesWithTasks = new ArrayList<>();
        
        // Generate days for current month view
        calculateDays();
    }

    @NonNull
    @Override
    public CalendarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_calendar_day, parent, false);
        return new CalendarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarViewHolder holder, int position) {
        Date date = days.get(position);
        
        if (date == null) {
            // Empty day cell
            holder.dayText.setText("");
            holder.dayText.setBackground(null);
            holder.taskDot.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(null);
            return;
        }
        
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        
        // Set day number
        String dayNumber = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));
        holder.dayText.setText(dayNumber);
        
        // Check if day is in current month
        boolean isCurrentMonth = calendar.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH) &&
                                calendar.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR);
        
        // Check if this is today
        boolean isToday = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                         calendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                         calendar.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH);
        
        // Check if this day is selected
        Calendar selectedCal = Calendar.getInstance();
        if (selectedDate != null) {
            selectedCal.setTime(selectedDate);
        }
        
        boolean isSelected = selectedDate != null &&
                           calendar.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                           calendar.get(Calendar.MONTH) == selectedCal.get(Calendar.MONTH) &&
                           calendar.get(Calendar.DAY_OF_MONTH) == selectedCal.get(Calendar.DAY_OF_MONTH);
        
        // Check if this day has tasks
        boolean hasTask = false;
        for (Date taskDate : datesWithTasks) {
            if (taskDate == null) continue;
            
            Calendar taskCal = Calendar.getInstance();
            taskCal.setTime(taskDate);
            
            if (calendar.get(Calendar.YEAR) == taskCal.get(Calendar.YEAR) &&
                calendar.get(Calendar.MONTH) == taskCal.get(Calendar.MONTH) &&
                calendar.get(Calendar.DAY_OF_MONTH) == taskCal.get(Calendar.DAY_OF_MONTH)) {
                hasTask = true;
                break;
            }
        }
        
        // Reset text and background first
        holder.dayText.setBackground(null);
        
        // Style the calendar day based on its state
        if (isCurrentMonth) {
            // Current month days - make text black and more visible
            holder.dayText.setTextColor(ContextCompat.getColor(context, R.color.black));
            
            if (isToday) {
                // Today - blue circle with white text
                holder.dayText.setBackgroundResource(R.drawable.today_background);
                holder.dayText.setTextColor(ContextCompat.getColor(context, R.color.white));
            } else if (isSelected) {
                // Selected date - purple circle with white text
                holder.dayText.setBackgroundResource(R.drawable.selected_day_background);
                holder.dayText.setTextColor(ContextCompat.getColor(context, R.color.white));
            } else {
                // Regular day in current month - transparent background with black text
                holder.dayText.setBackgroundResource(R.drawable.calendar_day_background);
            }
        } else {
            // Days from other months - gray text to indicate they're not part of current month
            holder.dayText.setTextColor(ContextCompat.getColor(context, R.color.calendar_gray));
            holder.dayText.setBackgroundResource(R.drawable.calendar_day_background);
        }
        
        // Show task indicator dot if the day has tasks - orange dot
        if (hasTask && isCurrentMonth) {
            holder.taskDot.setVisibility(View.VISIBLE);
        } else {
            holder.taskDot.setVisibility(View.GONE);
        }
        
        // Set click listener for the day
        if (isCurrentMonth) {
            holder.itemView.setOnClickListener(v -> {
                selectedDate = date;
                notifyDataSetChanged(); // Refresh to update selected state
                if (dateClickListener != null) {
                    dateClickListener.onDateClick(date);
                }
            });
        } else {
            holder.itemView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    /**
     * Updates the calendar to display a new month
     */
    public void updateCalendar(Calendar calendar) {
        currentMonth = (Calendar) calendar.clone();
        calculateDays();
        notifyDataSetChanged();
    }
    
    /**
     * Set the list of dates that have tasks
     */
    public void setDatesWithTasks(List<Date> dates) {
        this.datesWithTasks = dates;
        notifyDataSetChanged();
    }
    
    /**
     * Get formatted month and year string
     */
    public String getMonthYearString() {
        SimpleDateFormat format = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        return format.format(currentMonth.getTime());
    }
    
    /**
     * Calculates all the days to be displayed in the calendar grid
     */
    private void calculateDays() {
        days.clear();
        
        // Clone current month calendar
        Calendar calendar = (Calendar) currentMonth.clone();
        
        // Set to the first day of the month
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        
        // Determine the day of week for the first day of month
        int firstDayOfMonth = calendar.get(Calendar.DAY_OF_WEEK);
        
        // Adjust the calendar to start from the first visible day
        // (which might be from the previous month)
        calendar.add(Calendar.DAY_OF_MONTH, -(firstDayOfMonth - 1));
        
        // Store start date
        startDate = (Calendar) calendar.clone();
        
        // Add 6 weeks of days (42 days) to cover all possible month views
        for (int i = 0; i < 42; i++) {
            days.add(calendar.getTime());
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        // Store end date
        endDate = (Calendar) calendar.clone();
        endDate.add(Calendar.DAY_OF_MONTH, -1); // Subtract one day to get the actual end date
    }
    
    /**
     * View holder for calendar day items
     */
    static class CalendarViewHolder extends RecyclerView.ViewHolder {
        TextView dayText;
        View taskDot;

        public CalendarViewHolder(@NonNull View itemView) {
            super(itemView);
            dayText = itemView.findViewById(R.id.day_text);
            taskDot = itemView.findViewById(R.id.task_dot);
        }
    }
}