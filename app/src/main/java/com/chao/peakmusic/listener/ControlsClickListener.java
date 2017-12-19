package com.chao.peakmusic.listener;

import com.chao.peakmusic.utils.LogUtils;
import com.cleveroad.audiowidget.AudioWidget;

/**
 * Created by Chao on 2017-12-19.
 */

public class ControlsClickListener implements AudioWidget.OnControlsClickListener {


    @Override
    public boolean onPlaylistClicked() {
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
    public boolean onPlayPauseClicked() {
        LogUtils.showTagE("onPlayPauseClicked");
        return false;
    }

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
