package com.chao.peakmusic.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import androidx.annotation.Nullable;

import com.chao.peakmusic.utils.LogUtils;

/**
 * Created by Chao on 2017-12-19.
 */

public class TestService extends Service {

    private final IBinder binder = new AudioBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    //为了和Activity交互，我们需要定义一个Binder对象
    public class AudioBinder extends Binder {
        //返回Service对象
        public TestService getService() {
            return TestService.this;
        }
    }

    public void playMusic() {
        LogUtils.showTagE("playMusic");
    }


    /**
     * 打开一个音频文件
     */
    public void openAudio(int position) {

    }

    /**
     * 播放
     */
    public void play() {

    }

    /**
     * 暂停
     */
    public void pause() {
    }

    /**
     * 获取歌曲名称
     */
    public void getMusicName() {
    }

    /**
     * 获取歌曲时长
     */
    public void getDuration() {
    }

    /**
     * 获取歌曲当前播放位置
     */
    public void getCurrentPosition() {
    }

    /**
     * 播放指定进度
     */
    public void seekTo(int position) {
    }

    /**
     * 设置播放模式
     */
    public void seekPlayMode(int mode) {
    }

    /**
     * 上一首
     */
    public void pre() {
    }

    /**
     * 下一首
     */
    public void next() {
    }
}
