package com.chao.peakmusic.service;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.chao.peakmusic.ActivityCall;
import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.listener.ControlsClickListener;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.utils.LogUtils;
import com.chao.peakmusic.utils.MusicDataUtils;
import com.chao.peakmusic.utils.SPUtils;
import com.chao.peakmusic.utils.ScreenUtils;
import com.chao.peakmusic.utils.ToastUtils;
import com.cleveroad.audiowidget.AudioWidget;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * 音乐后台服务
 * Created by Chao on 2017-12-19.
 */

public class MusicService extends Service implements AudioWidget.OnWidgetStateChangedListener {

    public static final String EXTRAS_MUSIC = "extras_music";
    private static final String TAG = "MusicService";
    private static final long UPDATE_INTERVAL = 1000;
    private Handler mHandler;
    private Timer timer;
    private ActivityCall activityCall;

    private AudioWidget audioWidget;
    private MediaPlayer mediaPlayer = null;
    private ControlsClickListener controlsClickListener;
    private ArrayList<SongModel> music;
    private SongModel currentMusic;
    private List<MusicModel> musicList;
    private boolean isLocal;
    private int currentPosition = -1;
    private boolean isPlaying = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler(Looper.getMainLooper());
        audioWidget = new AudioWidget.Builder(this).build();
        controlsClickListener = new ControlsClickListener(stub, this);
        audioWidget.controller().onControlsClickListener(controlsClickListener);
        audioWidget.controller().onWidgetStateChangedListener(this);
        //audioWidget.controller().stop();
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
        stopTrackingPosition();
        audioWidget.controller().onControlsClickListener(null);
        audioWidget.controller().onWidgetStateChangedListener(null);
        audioWidget.hide();
        audioWidget = null;
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopTrackingPosition();
        super.onDestroy();
        LogUtils.showTagE("服务销毁");
    }

    /**
     * 播放音乐的方法
     *
     * @param currentPath 音乐文件路径
     */
    private void playMusic(String currentPath) {
        //currentPath = "http://data.apiopen.top/%E8%A2%81%E7%BB%B4%E5%A8%85-%E8%AF%B4%E6%95%A3%E5%B0%B1%E6%95%A3.mp3";
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    isPlaying = false;
                    //audioWidget.controller().stop();
                    return false;
                });
                mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        mediaPlayer.start();
                        //audioWidget.controller().start();
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
            isPlaying = true;
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
            isLocal = true;
            currentMusic = music.get(position);
            currentPosition = position;
            playMusic(music.get(position).getPath()/*"/storage/emulated/0/Download/What are words.mp3"*/);
        }

        @Override
        public void playAudio(String url) throws RemoteException {
            isLocal = false;
            musicList = MusicDataUtils.getInstance().getMusicList();
            if (musicList != null) {
                for (int i = 0; i < musicList.size(); i++) {
                    if (musicList.get(i).getMp3().equals(url)) {
                        currentPosition = i;
                        break;
                    }
                }
            }
            playMusic(url);
        }

        @Override
        public void play() throws RemoteException {
            if (mediaPlayer != null/* && currentMusic != null*/) {
                mediaPlayer.start();
                isPlaying = true;
            } else if (mediaPlayer == null) {
               // openAudio(0);
                if (activityCall != null) {
                    activityCall.defaultPlay();//直接点击播放按钮，触发默认歌曲。
                }
            }
            //悬浮按钮点击播放触发此方法，同步状态到外部UI。
            if (activityCall != null) {
                activityCall.call(isPlaying);
            }
        }

        @Override
        public void pause() throws RemoteException {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
                isPlaying = false;
            }
            //悬浮按钮点击暂停触发此方法，同步状态到外部UI。
            if (activityCall != null) {
                activityCall.call(isPlaying);
            }
        }

        /**
         * 反射设置控件当前状态
         * @param state
         */
        void setPlayState(int state) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    //    static final int STATE_STOPPED = 0;
                    //    static final int STATE_PLAYING = 1;
                    //    static final int STATE_PAUSED = 2;
                    Field playbackState = audioWidget.getClass().getDeclaredField("playbackState");
                    playbackState.setAccessible(true);
                    Class<?> aClass = Class.forName("com.cleveroad.audiowidget.PlaybackState");
                    Field state1 = aClass.getDeclaredField("state");
                    state1.setAccessible(true);
                    state1.set(playbackState.get(audioWidget), state);
                    state1.setAccessible(false);
                    playbackState.setAccessible(false);
                } catch (NoSuchFieldException | IllegalAccessException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public String getMusicName() throws RemoteException {
            return currentMusic.getSong();
        }

        @Override
        public boolean isPlay() throws RemoteException {
            return mediaPlayer != null && mediaPlayer.isPlaying();//isPlaying;
        }

        @Override
        public long getDuration() throws RemoteException {
            return mediaPlayer.getDuration();
        }

        @Override
        public int getCurrentIndex() throws RemoteException {
            return currentPosition;
        }

        @Override
        public int getCurrentPosition() throws RemoteException {
            return mediaPlayer == null || !mediaPlayer.isPlaying() ? 0 : mediaPlayer.getCurrentPosition();
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
            if (activityCall != null) {
                activityCall.pre();
            }
//            if (isLocal) {//本地音乐
//                if (music != null && music.size() > 0) {
//                    currentPosition--;
//                    if (currentPosition >= 0) {
//                        openAudio(currentPosition);
//                    } else {
//                        openAudio(music.size() - 1);
//                    }
//                } else {
//                    ToastUtils.showToast("没有更多歌曲了~");
//                }
//            } else {//线上音乐
//                musicList = MusicDataUtils.getInstance().getMusicList();
//                if (musicList != null && musicList.size() > 0) {
//                    currentPosition--;
//                    if (currentPosition >= 0) {
//                        playAudio(musicList.get(currentPosition).getMp3());
//                    } else {
//                        playAudio(musicList.get(musicList.size() - 1).getMp3());
//                    }
//                } else {
//                    ToastUtils.showToast("没有更多歌曲了~");
//                }
//            }
        }

        @Override
        public void next() throws RemoteException {
            if (activityCall != null) {
                activityCall.next();
            }
//            if (isLocal) {
//                if (music != null && music.size() > 0) {
//                    currentPosition++;
//                    if (currentPosition <= music.size() - 1) {
//                        openAudio(currentPosition);
//                    } else {
//                        openAudio(0);
//                    }
//                } else {
//                    ToastUtils.showToast("没有更多歌曲了~");
//                }
//            } else {
//                musicList = MusicDataUtils.getInstance().getMusicList();
//                if (musicList != null && musicList.size() > 0) {
//                    currentPosition++;
//                    if (currentPosition <= musicList.size() - 1) {
//                        playAudio(musicList.get(currentPosition).getMp3());
//                    } else {
//                        playAudio(musicList.get(0).getMp3());
//                    }
//                } else {
//                    ToastUtils.showToast("没有更多歌曲了~");
//                }
//            }
        }

        @Override
        public void show() throws RemoteException {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    audioWidget.show(SPUtils.getPrefInt(SPUtils.SUSPENSIONX, ScreenUtils.getScreenWidth()), SPUtils.getPrefInt(SPUtils.SUSPENSIONY, ScreenUtils.getScreenHeight() / 2));
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

        @Override
        public void registerCallback(ActivityCall call) throws RemoteException {
            MusicService.this.activityCall = call;
        }

        /**
         *点击外部按钮控制播放状态时，对悬浮按钮状态进行同步。
         */
        @Override
        public void clickButton(boolean isPlay) throws RemoteException {
            if (isPlay) {
                audioWidget.controller().start();
            } else {
                audioWidget.controller().pause();
            }
        }
/**
 * 通过反射调用PlayPauseButton的onClick方法
 */
//        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
//        @Override
//        public void clickButton() throws RemoteException {
//            try {
//                Field playPauseButton = audioWidget.getClass().getDeclaredField("playPauseButton");
//                playPauseButton.setAccessible(true);
//                Class<?> aClass = Class.forName("com.cleveroad.audiowidget.PlayPauseButton");
//                Method method = aClass.getDeclaredMethod("onClick");
//                method.invoke(playPauseButton.get(audioWidget));
//                playPauseButton.setAccessible(false);
//            } catch (NoSuchFieldException | ClassNotFoundException | NoSuchMethodException e) {
//                e.printStackTrace();
//            } catch (IllegalAccessException e) {
//                e.printStackTrace();
//            } catch (InvocationTargetException e) {
//                e.printStackTrace();
//            }
//        }
    };

    @Override
    public void onWidgetStateChanged(@NonNull AudioWidget.State state) {
        LogUtils.showTagE(state);
    }

    @Override
    public void onWidgetPositionChanged(int cx, int cy) {
        SPUtils.setPrefInt(SPUtils.SUSPENSIONX, cx);
        SPUtils.setPrefInt(SPUtils.SUSPENSIONY, cy);
    }
}
