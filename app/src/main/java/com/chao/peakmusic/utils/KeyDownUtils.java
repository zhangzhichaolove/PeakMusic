package com.chao.peakmusic.utils;

import android.app.Activity;
import android.view.KeyEvent;


public class KeyDownUtils {
    private static long exitTime;

    /**
     * 返回键退出监听
     */
    public static boolean Black(Activity context, int keyCode, String content,
                                int time) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (System.currentTimeMillis() - exitTime > time) {
                ToastUtils.showToast(content);
                exitTime = System.currentTimeMillis();
            } else {
                context.finish();
            }
            return true;
        }

        return false;
    }

    /**
     * 返回键退出到后台 1 通过调用moveTaskToBack（） true/false的方式
     */
    public static boolean BlackBackstage(Activity context, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // nonRoot=false→
            // 仅当activity为task根（即首个activity例如启动activity之类的）时才生效
            // nonRoot=true→ 忽略上面的限制
            // 这个方法不会改变task中的activity中的顺序，效果基本等同于home键
            context.moveTaskToBack(true);
            return true;
        }

        return false;
    }

}
