package com.chao.peakmusic.utils;

import android.app.Activity;

import java.util.HashMap;
import java.util.Iterator;

public class AppManager {
    private static final AppManager INSTANCE = new AppManager();
    private HashMap<String, Activity> mActivityMap = new HashMap<String, Activity>();

    private AppManager() {
        init();
    }

    /**
     * 初始化操作
     */
    private static void init() {

    }

    public HashMap<String, Activity> getActivitys() {
        return mActivityMap;
    }

    public static AppManager getInstance() {
        return INSTANCE;
    }

    /**
     * 添加Activity
     *
     * @param activity
     */
    public void addActivity(Activity activity) {
        mActivityMap.put(activity.getClass().getSimpleName(), activity);
    }

    /**
     * 移除Activity
     *
     * @param activity
     */
    public void removeActivity(Activity activity) {
        mActivityMap.remove(activity.getClass().getSimpleName());
    }

    /**
     * 清空ActivityMap容器
     */
    public void clearActivityMap() {
        mActivityMap.clear();
    }

    /**
     * 关闭所有Activity
     */
    public void finishAllActivity() {
        Iterator<Activity> iterator = mActivityMap.values().iterator();
        while (iterator.hasNext()) {
            iterator.next().finish();
        }
    }

    /**
     * 退出程序
     */
    public void exit() {
        Iterator<Activity> iterator = mActivityMap.values().iterator();
        while (iterator.hasNext()) {
            iterator.next().finish();
        }
        clearActivityMap();
        System.exit(0);
    }

}
