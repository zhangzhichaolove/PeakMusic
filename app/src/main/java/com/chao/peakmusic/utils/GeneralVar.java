package com.chao.peakmusic.utils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

/**
 * Created by Chao on 2017-12-18.
 */

public class GeneralVar {

    @SuppressLint("StaticFieldLeak")
    private static Application application;
    public static boolean deBug = true;

    public static void setContext(Context context) {
        GeneralVar.application = (Application) context.getApplicationContext();
    }

    public static Context getApplication() {
        return application;
    }

    public static Context getContext() {
        return application;
    }

}
