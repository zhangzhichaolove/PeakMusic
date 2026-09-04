package com.chao.peakmusic.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "playlists")
public class PlaylistEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name;
    public long createdAt;
}
