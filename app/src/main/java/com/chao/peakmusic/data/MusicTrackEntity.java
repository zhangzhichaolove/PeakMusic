package com.chao.peakmusic.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.model.SongModel;

import android.os.Parcel;
import android.os.Parcelable;

@Entity(tableName = "music_tracks")
public class MusicTrackEntity implements Parcelable {
    // Historical column name: source is the stable library key, NOT the playback URL.
    @PrimaryKey
    @NonNull
    public String source = "";
    @NonNull @ColumnInfo(defaultValue = "'legacy'")
    public String sourceId = MusicSource.LEGACY;
    public String sourceBaseUrl;
    public String mediaId;
    public String playbackUrl;
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
            result.source = keyOf(music);
            result.sourceId = music.getSourceId();
            result.sourceBaseUrl = music.getSourceBaseUrl();
            result.mediaId = music.getId();
            result.playbackUrl = music.getMp3();
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
            result.source = safe(song.getPath()); // Preserve the MediaStore URI key and existing local memberships.
            result.sourceId = MusicSource.LOCAL;
            result.playbackUrl = song.getPath();
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
        result.setMp3(getPlaybackUrl());
        result.setId(mediaId);
        result.restoreLibrarySource(sourceId, sourceBaseUrl, source);
        return result;
    }

    public String getPlaybackUrl() { return playbackUrl == null ? source : playbackUrl; }

    public static String keyOf(MusicModel music) {
        if (music.getStoredLibraryKey() != null) return music.getStoredLibraryKey();
        if (MusicSource.LEGACY.equals(music.getSourceId())) return safe(music.getMp3());
        String id = music.getId();
        return music.getSourceId() + (id == null || id.trim().isEmpty()
                ? ":url:" + safe(music.getMp3()) : ":id:" + id.trim());
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
        sourceId = in.readString(); sourceBaseUrl = in.readString();
        mediaId = in.readString(); playbackUrl = in.readString();
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
        dest.writeString(sourceId); dest.writeString(sourceBaseUrl);
        dest.writeString(mediaId); dest.writeString(playbackUrl);
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
