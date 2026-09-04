package com.chao.peakmusic.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.Window;

import androidx.core.view.WindowCompat;

/** Window configuration shared by all activities. */
public final class BarUtils {
    private BarUtils() {
    }

    public static void setWindow(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setLegacyTransparentStatusBar(window);
        }
    }

    @SuppressWarnings("deprecation")
    private static void setLegacyTransparentStatusBar(Window window) {
        window.setStatusBarColor(Color.TRANSPARENT);
    }
}
