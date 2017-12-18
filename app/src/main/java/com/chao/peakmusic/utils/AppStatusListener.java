package com.chao.peakmusic.utils;

import android.app.Activity;
import android.app.Application.ActivityLifecycleCallbacks;
import android.os.Bundle;


/**
 * App生命周期监听类
 *
 * @author Cc
 */
public class AppStatusListener {
    private static AppStatusListener instance;
    private AppLifecycleListener appLifecycleListener;
    public AppLifecycle appLifecycle;
    private static int count = 0;
    private static int stop = 0;
    // APP是否在前台运行
    private static boolean isBackstage = true;

    public static AppStatusListener getInstance() {
        if (instance == null) {
            synchronized (AppStatusListener.class) {
                if (instance == null) {
                    instance = new AppStatusListener();
                }
            }
        }
        return instance;
    }

    private AppStatusListener() {
        appLifecycleListener = new AppLifecycleListener();
    }

    private class AppLifecycleListener implements ActivityLifecycleCallbacks {

        @Override
        public void onActivityStopped(Activity activity) {
            count--;
            if (count == 0) {
                LogUtils.showTagE(">>>>>>>>>>>>>>>>>>>切到后台 <<<<<<<<<<<<<<<<");
                isBackstage = false;
                if (appLifecycle != null) {
                    appLifecycle.AppBackstage(isBackstage);
                }
            }
        }

        @Override
        public void onActivityStarted(Activity activity) {
            AppManager.getInstance().addActivity(activity);
            if (count == 0) {
                LogUtils.showTagE(">>>>>>>>>>>>>>>>>>>切到前台 <<<<<<<<<<<<<<<<");
                isBackstage = true;
                if (appLifecycle != null) {
                    appLifecycle.AppBackstage(isBackstage);
                }
            }
            count++;
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity,
                                                Bundle outState) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
            //Cpublic.setActivity(activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            AppManager.getInstance().removeActivity(activity);
            stop--;
            if (stop == 0) {
                LogUtils.showTagE("程序退出。");
            }
        }

        @Override
        public void onActivityCreated(Activity activity,
                                      Bundle savedInstanceState) {
            stop++;
        }
    }

    public int getCount() {
        return count;
    }

    public boolean isBackstage() {
        return isBackstage;
    }


    public void setAppLifecycle(AppLifecycle appLifecycle) {
        this.appLifecycle = appLifecycle;
    }

    public ActivityLifecycleCallbacks getAppLifecycleListener() {
        return appLifecycleListener;
    }

    public interface AppLifecycle {

        public void AppBackstage(boolean isBackstage);

    }

}
