package com.chao.peakmusic.utils;

import android.content.Context;

/**
 * Created by Chao on 2017-12-18.
 */

public class GeneralVar {

    private static Context context;
    public static boolean deBug = true;

    public static void setContext(Context context) {
        GeneralVar.context = context;
    }

    public static Context getContext() {
        return context;
    }
}
