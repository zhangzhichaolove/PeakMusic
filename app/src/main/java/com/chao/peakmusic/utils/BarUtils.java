package com.chao.peakmusic.utils;

import android.app.Activity;
import android.graphics.Color;
import android.view.Window;

import androidx.core.view.WindowCompat;

/** Window configuration shared by all activities. */
public final class BarUtils {
    private BarUtils() {
    }

    public static void setWindow(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
    }
}
