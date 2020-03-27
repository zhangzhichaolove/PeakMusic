package com.chao.peakmusic;

import android.app.Application;
import android.content.Context;

import androidx.multidex.MultiDex;

import com.blankj.utilcode.util.Utils;
import com.chao.peakmusic.utils.AppStatusListener;
import com.chao.peakmusic.utils.GeneralVar;
import com.chao.peakmusic.utils.ScanningUtils;

/**
 * Created by Chao on 2017-12-18.
 */

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.init(this);
        GeneralVar.setContext(this);
        ScanningUtils.getInstance(this).scanMusic();
        registerActivityLifecycleCallbacks(AppStatusListener.getInstance().getAppLifecycleListener());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}
