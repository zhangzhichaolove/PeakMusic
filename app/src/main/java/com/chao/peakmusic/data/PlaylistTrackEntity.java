package com.chao.peakmusic.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "playlist_tracks", primaryKeys = {"playlistId", "source"})
public class PlaylistTrackEntity {
    public long playlistId;
    @NonNull
    public String source = "";
    public long addedAt;
}
