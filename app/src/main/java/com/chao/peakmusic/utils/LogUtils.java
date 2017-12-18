package com.chao.peakmusic.utils;

import android.util.Log;

/**
 * 创建日期：2017/4/9 on 12:07
 * 描述:Log打印工具类
 * 作者:张智超 Chao
 */
public class LogUtils {
    private static final String TAG = "CLog";

    public static void println(Object object) {
        if (GeneralVar.deBug) {
            System.out.println(object);
        }
    }


    public synchronized static void showTagE(String tag, Object object) {
        if (GeneralVar.deBug) {
            Log.e(tag, object.toString());
        }
    }

    public synchronized static void showTagE(Object object) {
        if (GeneralVar.deBug) {
            Log.e(TAG, object.toString());
        }
    }


}
