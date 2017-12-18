package com.chao.peakmusic.utils;

import android.content.Context;
import android.content.Intent;

import com.chao.peakmusic.MainActivity;

/**
 * Created by Chao on 2017-12-18.
 */

public class NavigationManager {


    public static void gotoHome(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        context.startActivity(intent);
    }

}
