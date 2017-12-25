package com.chao.peakmusic.utils;

import android.content.Context;

/**
 * Created by Chao on 2017-12-18.
 */

public class GeneralVar {

    private static Context context;
    private static int screenWidth;
    private static int screenHeight;
    private static int statusHeight;
    private static int navigationBarHeight;
    public static boolean deBug = true;

    public static void setContext(Context context) {
        GeneralVar.context = context;
        GeneralVar.screenWidth = ScreenUtils.getScreenWidth();
        GeneralVar.screenHeight = ScreenUtils.getScreenHeight();
        GeneralVar.statusHeight = ScreenUtils.getStatusHeight();
        GeneralVar.navigationBarHeight = ScreenUtils.getNavBarHeight();
    }

    public static Context getApplication() {
        return context;
    }

    public static Context getContext() {
        return context;
    }


    public static int getScreenWidth() {
        return screenWidth;
    }

    public static void setScreenWidth(int screenWidth) {
        GeneralVar.screenWidth = screenWidth;
    }

    public static int getScreenHeight() {
        return screenHeight;
    }

    public static void setScreenHeight(int screenHeight) {
        GeneralVar.screenHeight = screenHeight;
    }

    public static int getStatusHeight() {
        return statusHeight;
    }
}
