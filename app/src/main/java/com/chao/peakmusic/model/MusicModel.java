package com.chao.peakmusic.model;

import java.io.Serializable;

/**
 * Created by Chao on 2018-09-23.
 */

public class MusicModel implements Serializable {

    /**
     * {
     * "id": 8,
     * "name": "有些爱情放不下",
     * "singer": "唐伯虎Annie",
     * "img": "https://file.peakchao.com:196/有些爱情放不下-唐伯虎Annie.jpg",
     * "lrc": "https://file.peakchao.com:196/有些爱情放不下-唐伯虎Annie.lrc",
     * "mp3": "https://file.peakchao.com:196/有些爱情放不下-唐伯虎Annie.mp3"
     * },
     */

    private int id;
    private String name;
    private String singer;
    private String img;
    private String lrc;
    private String mp3;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSinger() {
        return singer;
    }

    public void setSinger(String singer) {
        this.singer = singer;
    }

    public String getImg() {
        return img;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getLrc() {
        return lrc;
    }

    public void setLrc(String lrc) {
        this.lrc = lrc;
    }

    public String getMp3() {
        return mp3;
    }

    public void setMp3(String mp3) {
        this.mp3 = mp3;
    }
}
