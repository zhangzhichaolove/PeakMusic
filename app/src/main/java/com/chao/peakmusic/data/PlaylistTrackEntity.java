package com.chao.peakmusic.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "playlist_tracks", primaryKeys = {"playlistId", "source"},
        foreignKeys = {
            @ForeignKey(entity = PlaylistEntity.class, parentColumns = "id", childColumns = "playlistId", onDelete = ForeignKey.CASCADE),
            @ForeignKey(entity = MusicTrackEntity.class, parentColumns = "source", childColumns = "source", onDelete = ForeignKey.CASCADE)
        }, indices = @Index("source"))
public class PlaylistTrackEntity {
    public long playlistId;
    @NonNull
    public String source = "";
    public long addedAt;
}
