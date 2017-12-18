package com.chao.peakmusic;

import android.app.Application;

import com.chao.peakmusic.utils.AppStatusListener;
import com.chao.peakmusic.utils.GeneralVar;

/**
 * Created by Chao on 2017-12-18.
 */

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        GeneralVar.setContext(this);
        registerActivityLifecycleCallbacks(AppStatusListener.getInstance().getAppLifecycleListener());
    }
}
