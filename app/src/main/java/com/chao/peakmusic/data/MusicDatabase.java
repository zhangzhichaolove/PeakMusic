package com.chao.peakmusic.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {MusicTrackEntity.class, PlaylistEntity.class,
        PlaylistTrackEntity.class}, version = 1, exportSchema = false)
public abstract class MusicDatabase extends RoomDatabase {
    private static volatile MusicDatabase instance;

    public abstract MusicLibraryDao libraryDao();

    public static MusicDatabase get(Context context) {
        if (instance == null) {
            synchronized (MusicDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    MusicDatabase.class, "music-library.db")
                            .build();
                }
            }
        }
        return instance;
    }
}
