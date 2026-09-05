package com.chao.peakmusic.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

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

    /** Keeps toolbar content below the status bar while retaining edge-to-edge drawing. */
    public static void applyTopInset(View toolbar) {
        int initialPaddingTop = toolbar.getPaddingTop();
        int initialHeight = toolbar.getLayoutParams().height;
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            view.setPadding(view.getPaddingLeft(), initialPaddingTop + topInset,
                    view.getPaddingRight(), view.getPaddingBottom());
            if (initialHeight >= 0) {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.height = initialHeight + topInset;
                view.setLayoutParams(params);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(toolbar);
    }

    public static void applyPageInsets(View root, View toolbar) {
        applyTopInset(toolbar);
        applyBottomInsets(root);
    }

    public static void applyBottomInsets(View root) {
        int left = root.getPaddingLeft(), right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(left + safe.left, view.getPaddingTop(), right + safe.right, bottom + safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @SuppressWarnings("deprecation")
    private static void setLegacyTransparentStatusBar(Window window) {
        window.setStatusBarColor(Color.TRANSPARENT);
    }
}
