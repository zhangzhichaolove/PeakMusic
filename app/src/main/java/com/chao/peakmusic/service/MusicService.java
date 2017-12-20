package com.chao.peakmusic.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.utils.LogUtils;


/**
 * 音乐后台服务
 * Created by Chao on 2017-12-19.
 */

public class MusicService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.showTagE("onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.showTagE("onStartCommand");
        return super.onStartCommand(intent, flags, startId);

    }

    private MusicAidlInterface.Stub stub = new MusicAidlInterface.Stub() {

        @Override
        public void playMusic() throws RemoteException {
            LogUtils.showTagE("playMusic");
        }

        @Override
        public void suspendMusic() throws RemoteException {
            LogUtils.showTagE("suspendMusic");
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        LogUtils.showTagE("onBind");
        return stub;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        LogUtils.showTagE("onUnbind");
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtils.showTagE("onDestroy");
    }

}
