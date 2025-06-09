package com.example.taskflow.util;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Utility class to help with safer view binding access
 */
public class ViewBindingUtil {
    
    /**
     * Safely set text on a TextView
     * @param view the parent view to find the TextView from
     * @param textViewId the resource ID of the TextView
     * @param text the text to set
     * @return true if the text was set, false otherwise
     */
    public static boolean setTextSafely(@NonNull View view, @IdRes int textViewId, @Nullable String text) {
        TextView textView = view.findViewById(textViewId);
        if (textView != null) {
            textView.setText(text != null ? text : "");
            return true;
        }
        return false;
    }
    
    /**
     * Safely set visibility on a View
     * @param view the parent view to find the target View from
     * @param viewId the resource ID of the target View
     * @param visibility the visibility constant (View.VISIBLE, View.INVISIBLE, or View.GONE)
     * @return true if the visibility was set, false otherwise
     */
    public static boolean setVisibilitySafely(@NonNull View view, @IdRes int viewId, int visibility) {
        View targetView = view.findViewById(viewId);
        if (targetView != null) {
            targetView.setVisibility(visibility);
            return true;
        }
        return false;
    }
}
