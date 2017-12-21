
package com.chao.peakmusic.utils;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

public class SPUtils {
    public static final String SUSPENSIONX = "suspensionx";
    public static final String SUSPENSIONY = "suspensiony";

    public static String getPrefString(String key, final String defaultValue) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        return settings.getString(key, defaultValue);
    }

    public static void setPrefString(final String key, final String value) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        settings.edit().putString(key, value).commit();
    }

    public static boolean getPrefBoolean(final String key,
                                         final boolean defaultValue) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        return settings.getBoolean(key, defaultValue);
    }

    public static boolean hasKey(final String key) {
        return PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication()).contains(key);
    }

    public static void setPrefBoolean(final String key, final boolean value) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        settings.edit().putBoolean(key, value).commit();
    }

    public static void setPrefInt(final String key, final int value) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        settings.edit().putInt(key, value).commit();
    }

    public static int getPrefInt(final String key, final int defaultValue) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        return settings.getInt(key, defaultValue);
    }

    public static void setPrefFloat(final String key, final float value) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        settings.edit().putFloat(key, value).commit();
    }

    public static float getPrefFloat(final String key, final float defaultValue) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        return settings.getFloat(key, defaultValue);
    }

    public static void setSettingLong(final String key, final long value) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        settings.edit().putLong(key, value).commit();
    }

    public static long getPrefLong(final String key, final long defaultValue) {
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(GeneralVar.getApplication());
        return settings.getLong(key, defaultValue);
    }

    public static void clearPreference(final SharedPreferences p) {
        final Editor editor = p.edit();
        editor.clear();
        editor.commit();
    }
}
