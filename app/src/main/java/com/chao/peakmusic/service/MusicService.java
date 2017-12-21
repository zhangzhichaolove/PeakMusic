package com.chao.peakmusic.service;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.listener.ControlsClickListener;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.utils.LogUtils;
import com.chao.peakmusic.utils.ScreenUtils;
import com.cleveroad.audiowidget.AudioWidget;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


/**
 * 音乐后台服务
 * Created by Chao on 2017-12-19.
 */

public class MusicService extends Service {

    public static final String EXTRAS_MUSIC = "extras_music";
    private static final String TAG = "MusicService";
    private static final long UPDATE_INTERVAL = 1000;
    private Handler mHandler;
    private Timer timer;

    private AudioWidget audioWidget;
    private MediaPlayer mediaPlayer;
    private ControlsClickListener controlsClickListener;
    private ArrayList<SongModel> music;
    private SongModel currentMusic;
    private int currentPosition;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());
        audioWidget = new AudioWidget.Builder(this).build();
        controlsClickListener = new ControlsClickListener(stub, this);
        audioWidget.controller().onControlsClickListener(controlsClickListener);
        LogUtils.showTagE("服务创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.showTagE("服务启动");
        music = (ArrayList<SongModel>) intent.getSerializableExtra(EXTRAS_MUSIC);
        startTrackingPosition();
        return super.onStartCommand(intent, flags, startId);

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        LogUtils.showTagE("服务绑定");
        return stub;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        LogUtils.showTagE("服务解除绑定");
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTrackingPosition();
        LogUtils.showTagE("服务销毁");
    }

    /**
     * 播放音乐的方法
     *
     * @param currentPath 音乐文件路径
     */
    private void playMusic(String currentPath) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        mediaPlayer.start();
                        audioWidget.controller().duration(mediaPlayer.getDuration());
                    }
                });
                mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        try {
                            stub.next();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
            if (mediaPlayer.isPlaying()) {//如果当前正在播放音乐，则先停止
                mediaPlayer.stop();
            }
            mediaPlayer.reset();//重置播放器z状态
            mediaPlayer.setDataSource(currentPath);//指定音频文件路径
            mediaPlayer.setLooping(false);//设置为循环播放
            //mediaPlayer.prepare();//初始化播放器MediaPlayer
            mediaPlayer.prepareAsync();//异步初始化播放器MediaPlayer
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

    private void startTrackingPosition() {
        stopTrackingPosition();
        timer = new Timer(TAG);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                AudioWidget widget = audioWidget;
                MediaPlayer player = mediaPlayer;
                if (mediaPlayer != null && mediaPlayer.isPlaying() && widget != null) {
                    widget.controller().position(player.getCurrentPosition());
                }
            }
        }, UPDATE_INTERVAL, UPDATE_INTERVAL);
    }

    private void stopTrackingPosition() {
        if (timer == null)
            return;
        timer.cancel();
        timer.purge();
        timer = null;
    }

    private MusicAidlInterface.Stub stub = new MusicAidlInterface.Stub() {

        @Override
        public void openAudio(int position) throws RemoteException {
            currentMusic = music.get(position);
            currentPosition = position;
            playMusic(music.get(position).getPath()/*"/storage/emulated/0/Download/What are words.mp3"*/);
            if (!controlsClickListener.isPlaying()) {
                audioWidget.controller().start();
            }
        }

        @Override
        public void play() throws RemoteException {
            if (mediaPlayer != null && currentMusic != null) {
                mediaPlayer.start();
            } else if (mediaPlayer == null) {
                openAudio(0);
            }
            if (!controlsClickListener.isPlaying()) {
                audioWidget.controller().start();
            }
        }

        @Override
        public void pause() throws RemoteException {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
            }
            if (controlsClickListener.isPlaying()) {
                audioWidget.controller().pause();
            }
        }

        @Override
        public String getMusicName() throws RemoteException {
            return currentMusic.getSong();
        }

        @Override
        public boolean isPlay() throws RemoteException {
            return mediaPlayer != null && mediaPlayer.isPlaying();
        }

        @Override
        public long getDuration() throws RemoteException {
            return currentMusic.getDuration();
        }

        @Override
        public int getCurrentPosition() throws RemoteException {
            return currentPosition;
        }

        @Override
        public void seekTo(int position) throws RemoteException {
            mediaPlayer.seekTo(position);
        }

        @Override
        public void seekPlayMode(int mode) throws RemoteException {

        }

        @Override
        public void pre() throws RemoteException {
            currentPosition--;
            if (currentPosition >= 0) {
                openAudio(currentPosition);
            } else {
                openAudio(music.size() - 1);
            }
        }

        @Override
        public void next() throws RemoteException {
            currentPosition++;
            if (currentPosition <= music.size()) {
                openAudio(currentPosition);
            } else {
                openAudio(0);
            }
        }

        @Override
        public void show() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    audioWidget.show(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight() / 2);
                }
            });
        }

        @Override
        public void hide() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    audioWidget.collapse();
                    audioWidget.hide();
                }
            });
        }
    };

}
