package com.chao.peakmusic.model;

import java.util.List;

/**
 * Created by Chao on 2018-09-23.
 */

public class MusicListModel {

    private String channel;
    private List<SongList> songlist;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public List<SongList> getSonglist() {
        return songlist;
    }

    public void setSonglist(List<SongList> songlist) {
        this.songlist = songlist;
    }

    public class SongList{
        private int songid;
        private String title;
        private String artist;
        private String thumb;

        public int getSongid() {
            return songid;
        }

        public void setSongid(int songid) {
            this.songid = songid;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getThumb() {
            return thumb;
        }

        public void setThumb(String thumb) {
            this.thumb = thumb;
        }
    }

}
