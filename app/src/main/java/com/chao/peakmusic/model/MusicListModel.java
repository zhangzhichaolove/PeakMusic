package com.chao.peakmusic.model;

import java.util.List;

/**
 * Created by Chao on 2018-09-23.
 */

public class MusicListModel {
    /**
     * "total": 16,
     * "size": 12,
     * "current": 1,
     * "orders": [],
     * "hitCount": false,
     * "searchCount": true,
     * "pages": 2
     */
    private int current;
    private int pages;
    private int size;
    private int total;
    private List<MusicModel> records;

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        this.current = current;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<MusicModel> getRecords() {
        return records;
    }

    public void setRecords(List<MusicModel> records) {
        this.records = records;
    }
}
