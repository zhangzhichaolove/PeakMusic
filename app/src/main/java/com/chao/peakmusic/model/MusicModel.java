package com.chao.peakmusic.model;

import com.chao.peakmusic.data.MusicSource;

import android.os.Parcel;
import android.os.Parcelable;

import okhttp3.HttpUrl;

/**
 * Created by Chao on 2018-09-23.
 */

public class MusicModel implements Parcelable {

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

    private String id;
    private transient String sourceId;
    private transient String sourceBaseUrl;
    private transient String storedLibraryKey;
    private String name;
    private String singer;
    private String img;
    private String lrc;
    private String mp3;

    public String getId() {
        return id;
    }

    public void setId(int id) {
        this.id = String.valueOf(id);
    }

    public void setId(String id) { this.id = id; }

    /** Capture the issuing API once. JSON cannot write these client-owned transient fields. */
    public void bindApiSource(String baseUrl) {
        if (sourceId != null) return;
        sourceBaseUrl = MusicSource.normalize(baseUrl);
        sourceId = MusicSource.apiId(sourceBaseUrl);
    }

    public String getSourceId() { return sourceId == null ? MusicSource.LEGACY : sourceId; }
    public String getSourceBaseUrl() { return sourceBaseUrl; }
    public String getStoredLibraryKey() { return storedLibraryKey; }

    public void restoreLibrarySource(String sourceId, String baseUrl, String key) {
        this.sourceId = sourceId == null ? MusicSource.LEGACY : sourceId;
        sourceBaseUrl = baseUrl;
        storedLibraryKey = key;
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
        return resolveUrl(img);
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getLrc() {
        return resolveUrl(lrc);
    }

    public void setLrc(String lrc) {
        this.lrc = lrc;
    }

    public String getMp3() {
        return resolveUrl(mp3);
    }

    public void setMp3(String mp3) {
        this.mp3 = mp3;
    }

    private String resolveUrl(String value) {
        HttpUrl baseUrl = sourceBaseUrl == null ? null : HttpUrl.parse(sourceBaseUrl);
        HttpUrl resolvedUrl = baseUrl == null || value == null ? null : baseUrl.resolve(value);
        return resolvedUrl == null ? value : resolvedUrl.toString();
    }

    public MusicModel() {
    }

    private MusicModel(Parcel in) {
        id = in.readString();
        name = in.readString();
        singer = in.readString();
        img = in.readString();
        lrc = in.readString();
        mp3 = in.readString();
        sourceId = in.readString();
        sourceBaseUrl = in.readString();
        storedLibraryKey = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(singer);
        dest.writeString(img);
        dest.writeString(lrc);
        dest.writeString(mp3);
        dest.writeString(sourceId);
        dest.writeString(sourceBaseUrl);
        dest.writeString(storedLibraryKey);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MusicModel> CREATOR = new Creator<MusicModel>() {
        @Override
        public MusicModel createFromParcel(Parcel in) {
            return new MusicModel(in);
        }

        @Override
        public MusicModel[] newArray(int size) {
            return new MusicModel[size];
        }
    };
}
