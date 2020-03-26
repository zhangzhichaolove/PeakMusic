package com.chao.peakmusic.utils;

import androidx.annotation.StringRes;
import android.widget.Toast;

/**
 * 封装自定义方法
 *
 * @author Chao
 * @datetime: 2015年11月26日 下午9:06:30 Chao
 */
public class ToastUtils {
    private static Toast toast;// 系统纯文本Toast

    /**
     * 系统默认Toast
     */
    public static void showDefaultsToast(Object msg) {
        if (toast == null) {
            toast = Toast.makeText(GeneralVar.getContext(), "",
                    Toast.LENGTH_LONG);
        }
        toast.setText(msg.toString());
        toast.show();
    }


    /**
     * 显示全局配置Toast
     */
    public static void showToast(@StringRes int resId) {
        showToast(GeneralVar.getContext().getString(resId));
    }

    /**
     * 显示全局配置Toast
     */
    public static void showToast(Object msg) {
        if (toast == null) {
            toast = Toast.makeText(GeneralVar.getContext(), "",
                    Toast.LENGTH_LONG);
        }
        toast.setText(msg.toString());
        toast.show();
    }


}
