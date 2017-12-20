package com.chao.peakmusic.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

/**
 * Created by Chao on 2017-12-19.
 */

public class SongModel implements Serializable, Parcelable {

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

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(singer);
        dest.writeString(song);
        dest.writeString(album);
        dest.writeLong(albumId);
        dest.writeString(path);
        dest.writeInt(duration);
        dest.writeLong(size);
    }

    public static final Parcelable.Creator<SongModel> CREATOR = new Creator<SongModel>() {
        @Override
        public SongModel createFromParcel(Parcel source) {
            return new SongModel(source);
        }

        @Override
        public SongModel[] newArray(int size) {
            return new SongModel[size];
        }
    };

    private SongModel(Parcel in) {
        singer = in.readString();
        song = in.readString();
        album = in.readString();
        albumId = in.readLong();
        path = in.readString();
        duration = in.readInt();
        size = in.readLong();
    }

}
