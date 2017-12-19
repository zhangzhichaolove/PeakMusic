package com.chao.peakmusic.model;

/**
 * Created by Chao on 2017-12-19.
 */

public class SongModel {

    /**
     * 歌手
     */
    private String singer;
    /**
     * 歌曲名
     */
    private String song;
    /**
     * 专辑
     */
    private String album;
    /**
     * 专辑ID
     */
    private long albumId;
    /**
     * 歌曲的地址
     */
    private String path;
    /**
     * 歌曲长度
     */
    private int duration;
    /**
     * 歌曲的大小
     */
    private long size;

    public SongModel(String singer, String song, String album, long albumId, String path, int duration, long size) {
        this.singer = singer;
        this.song = song;
        this.album = album;
        this.albumId = albumId;
        this.path = path;
        this.duration = duration;
        this.size = size;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public long getAlbumId() {
        return albumId;
    }

    public void setAlbumId(long albumId) {
        this.albumId = albumId;
    }

    public String getSinger() {
        return singer;
    }

    public void setSinger(String singer) {
        this.singer = singer;
    }

    public String getSong() {
        return song;
    }

    public void setSong(String song) {
        this.song = song;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
