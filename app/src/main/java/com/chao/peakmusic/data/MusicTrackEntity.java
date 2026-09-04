package com.chao.peakmusic.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.model.SongModel;

import android.os.Parcel;
import android.os.Parcelable;

@Entity(tableName = "music_tracks")
public class MusicTrackEntity implements Parcelable {
    @PrimaryKey
    @NonNull
    public String source = "";
    public String name;
    public String artist;
    public String imageUrl;
    public String lyricsUrl;
    public boolean local;
    public String album;
    public String filePath;
    public long albumId;
    public int durationMs;
    public long sizeBytes;
    public boolean favorite;
    public long favoriteAt;
    public long lastPlayedAt;
    public int playCount;

    public static MusicTrackEntity from(MusicModel music) {
        MusicTrackEntity result = new MusicTrackEntity();
        if (music != null) {
            result.source = safe(music.getMp3());
            result.name = music.getName();
            result.artist = music.getSinger();
            result.imageUrl = music.getImg();
            result.lyricsUrl = music.getLrc();
        }
        return result;
    }

    public static MusicTrackEntity from(SongModel song) {
        MusicTrackEntity result = new MusicTrackEntity();
        if (song != null) {
            result.source = safe(song.getPath());
            result.name = song.getSong();
            result.artist = song.getSinger();
            result.local = true;
            result.album = song.getAlbum();
            result.filePath = song.getFilePath();
            result.albumId = song.getAlbumId();
            result.durationMs = song.getDuration();
            result.sizeBytes = song.getSize();
            if (song.getAlbumId() > 0) {
                result.imageUrl = "content://media/external/audio/albumart/" + song.getAlbumId();
            }
        }
        return result;
    }

    public MusicModel toOnlineMusic() {
        MusicModel result = new MusicModel();
        result.setName(name);
        result.setSinger(artist);
        result.setImg(imageUrl);
        result.setLrc(lyricsUrl);
        result.setMp3(source);
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public MusicTrackEntity() {
    }

    private MusicTrackEntity(Parcel in) {
        source = in.readString();
        name = in.readString();
        artist = in.readString();
        imageUrl = in.readString();
        lyricsUrl = in.readString();
        local = in.readByte() != 0;
        album = in.readString();
        filePath = in.readString();
        albumId = in.readLong();
        durationMs = in.readInt();
        sizeBytes = in.readLong();
        favorite = in.readByte() != 0;
        favoriteAt = in.readLong();
        lastPlayedAt = in.readLong();
        playCount = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(source);
        dest.writeString(name);
        dest.writeString(artist);
        dest.writeString(imageUrl);
        dest.writeString(lyricsUrl);
        dest.writeByte((byte) (local ? 1 : 0));
        dest.writeString(album);
        dest.writeString(filePath);
        dest.writeLong(albumId);
        dest.writeInt(durationMs);
        dest.writeLong(sizeBytes);
        dest.writeByte((byte) (favorite ? 1 : 0));
        dest.writeLong(favoriteAt);
        dest.writeLong(lastPlayedAt);
        dest.writeInt(playCount);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MusicTrackEntity> CREATOR = new Creator<MusicTrackEntity>() {
        @Override
        public MusicTrackEntity createFromParcel(Parcel in) {
            return new MusicTrackEntity(in);
        }

        @Override
        public MusicTrackEntity[] newArray(int size) {
            return new MusicTrackEntity[size];
        }
    };
}
