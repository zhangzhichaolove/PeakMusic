package com.chao.peakmusic.utils;

import com.chao.peakmusic.model.MusicModel;

import java.util.List;

public class MusicDataUtils {
    private static MusicDataUtils instance;
    private List<MusicModel> musicList;
    private int currentPosition = -1;

    private MusicDataUtils() {
    }

    public static MusicDataUtils getInstance() {
        if (instance == null) {
            synchronized (MusicDataUtils.class) {
                if (instance == null) {
                    instance = new MusicDataUtils();
                }
            }
        }
        return instance;
    }

    public List<MusicModel> getMusicList() {
        return musicList;
    }

    public void setMusicList(List<MusicModel> musicLis) {
        this.musicList = musicLis;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }
}
