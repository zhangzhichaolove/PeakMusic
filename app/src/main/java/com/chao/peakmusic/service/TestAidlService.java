package com.chao.peakmusic.service;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.chao.peakmusic.TestAidlInterface;
import com.chao.peakmusic.utils.LogUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by Chao on 2017-12-19.
 */

public class TestAidlService extends Service {
    private MediaPlayer mediaPlayer;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        File file = Environment.getExternalStorageDirectory();
        //traverseFolder(file);
        //playMusic("/storage/emulated/0/Download/What are words.mp3");
    }

    /**
     * 播放音乐的方法
     *
     * @param currentPath 音乐文件路径
     */
    public void playMusic(String currentPath) {

        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }
            if (mediaPlayer.isPlaying()) {//如果当前正在播放音乐，则先停止
                mediaPlayer.stop();
            }
            mediaPlayer.reset();//重置播放器z状态
            mediaPlayer.setDataSource(currentPath);//指定音频文件路径
            mediaPlayer.setLooping(true);//设置为循环播放
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    mediaPlayer.start();
                }
            });
            //mediaPlayer.prepare();//初始化播放器MediaPlayer
            mediaPlayer.prepareAsync();//异步初始化播放器MediaPlayer

            // updateSeekBar();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void traverseFolder(File file) {
        File[] listFiles = file.listFiles();
        for (int i = 0; listFiles != null && i < listFiles.length; i++) {
            if (listFiles[i].isDirectory()) {//目录
                traverseFolder(listFiles[i]);
            } else {
                if (listFiles[i].getName().endsWith(".mp3")) {
                    LogUtils.showTagE(listFiles[i].getPath());
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    private TestAidlInterface.Stub binder = new TestAidlInterface.Stub() {

        @Override
        public void openAudio(int position) throws RemoteException {
            playMusic("/storage/emulated/0/Download/What are words.mp3");
        }

        @Override
        public void play() throws RemoteException {

        }

        @Override
        public void pause() throws RemoteException {

        }

        @Override
        public void getMusicName() throws RemoteException {

        }

        @Override
        public void getDuration() throws RemoteException {

        }

        @Override
        public void getCurrentPosition() throws RemoteException {

        }

        @Override
        public void seekTo(int position) throws RemoteException {

        }

        @Override
        public void seekPlayMode(int mode) throws RemoteException {

        }

        @Override
        public void pre() throws RemoteException {

        }

        @Override
        public void next() throws RemoteException {

        }
    };

}
