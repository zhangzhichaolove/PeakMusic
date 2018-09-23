package com.chao.peakmusic.listener;

/**
 * Created by Chao on 2017-12-21.
 */

public interface PlayMusicListener {

    public void playMusic(int position, String name, String artist);

    public void playMusic(String url, String name, String artist, String img);
}
