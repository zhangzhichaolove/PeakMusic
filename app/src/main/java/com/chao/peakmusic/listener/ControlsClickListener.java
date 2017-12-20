package com.chao.peakmusic.listener;

import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;

import com.chao.peakmusic.MainActivity;
import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.utils.LogUtils;
import com.cleveroad.audiowidget.AudioWidget;

/**
 * Created by Chao on 2017-12-19.
 */

public class ControlsClickListener implements AudioWidget.OnControlsClickListener {
    MusicAidlInterface.Stub stub;
    Context context;
    boolean isPlaying = false;//默认暂停

    public ControlsClickListener(MusicAidlInterface.Stub stub, Context context) {
        this.stub = stub;
        this.context = context;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * 播放列表
     */
    @Override
    public boolean onPlaylistClicked() {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        LogUtils.showTagE("onPlaylistClicked");
        return false;
    }

    @Override
    public void onPlaylistLongClicked() {
        LogUtils.showTagE("onPlaylistLongClicked");
    }

    @Override
    public void onPreviousClicked() {
        LogUtils.showTagE("onPreviousClicked");
    }

    @Override
    public void onPreviousLongClicked() {
        LogUtils.showTagE("onPreviousLongClicked");
    }

    @Override
    public boolean onPlayPauseClicked(boolean isPlay) {
        isPlaying = isPlay;
        try {
            if (isPlay) {
                stub.play();
            } else {
                stub.pause();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        LogUtils.showTagE("TAG:" + isPlay);
        return false;
    }


//    @Override
//    public boolean onPlayPauseClicked() {
////        boolean fail = true;
////        try {
////            if (isPlaying) {
////                stub.pause();
////                fail = stub.isPlay();
////            } else {
////                stub.play();
////                fail = !stub.isPlay();
////            }
////        } catch (RemoteException e) {
////            e.printStackTrace();
////        }
////        isPlaying = fail ? isPlaying : !isPlaying;
////        LogUtils.showTagE("onPlayPauseClicked");
//        try {
//            stub.isPlay();
//        } catch (RemoteException e) {
//            e.printStackTrace();
//        }
//        return false;
//    }

    @Override
    public void onPlayPauseLongClicked() {
        LogUtils.showTagE("onPlayPauseLongClicked");
    }

    @Override
    public void onNextClicked() {
        LogUtils.showTagE("onNextClicked");
    }

    @Override
    public void onNextLongClicked() {
        LogUtils.showTagE("onNextLongClicked");
    }

    @Override
    public void onAlbumClicked() {
        LogUtils.showTagE("onAlbumClicked");
    }

    @Override
    public void onAlbumLongClicked() {
        LogUtils.showTagE("onAlbumLongClicked");
    }
}
