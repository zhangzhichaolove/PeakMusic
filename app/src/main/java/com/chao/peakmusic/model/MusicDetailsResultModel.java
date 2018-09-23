package com.chao.peakmusic.model;

import java.util.List;

/**
 * Created by Chao on 2018-09-23.
 */

public class MusicDetailsResultModel {
    private String xcode;
    private List<MusicDetailsModel> songList;

    public String getXcode() {
        return xcode;
    }

    public void setXcode(String xcode) {
        this.xcode = xcode;
    }

    public List<MusicDetailsModel> getSongList() {
        return songList;
    }

    public void setSongList(List<MusicDetailsModel> songList) {
        this.songList = songList;
    }
}
