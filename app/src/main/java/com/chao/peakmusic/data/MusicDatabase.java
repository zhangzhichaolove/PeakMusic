package com.chao.peakmusic.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {MusicTrackEntity.class, PlaylistEntity.class,
        PlaylistTrackEntity.class}, version = 3, exportSchema = true)
public abstract class MusicDatabase extends RoomDatabase {
    public static final androidx.room.migration.Migration MIGRATION_1_2 = new androidx.room.migration.Migration(1, 2) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE playlist_tracks_new (playlistId INTEGER NOT NULL, source TEXT NOT NULL, addedAt INTEGER NOT NULL, "
                    + "PRIMARY KEY(playlistId, source), FOREIGN KEY(playlistId) REFERENCES playlists(id) ON UPDATE NO ACTION ON DELETE CASCADE, "
                    + "FOREIGN KEY(source) REFERENCES music_tracks(source) ON UPDATE NO ACTION ON DELETE CASCADE)");
            // Retain valid memberships; old versions could leave orphan rows after interrupted writes.
            db.execSQL("INSERT INTO playlist_tracks_new SELECT pt.playlistId, pt.source, pt.addedAt FROM playlist_tracks pt "
                    + "INNER JOIN playlists p ON p.id = pt.playlistId INNER JOIN music_tracks t ON t.source = pt.source");
            db.execSQL("DROP TABLE playlist_tracks");
            db.execSQL("ALTER TABLE playlist_tracks_new RENAME TO playlist_tracks");
            db.execSQL("CREATE INDEX index_playlist_tracks_source ON playlist_tracks(source)");
        }
    };
    public static final androidx.room.migration.Migration MIGRATION_2_3 = new androidx.room.migration.Migration(2, 3) {
        @Override public void migrate(androidx.sqlite.db.SupportSQLiteDatabase db) {
            // Old online rows have no recorded API source. Retain every key and membership.
            db.execSQL("ALTER TABLE music_tracks ADD COLUMN sourceId TEXT NOT NULL DEFAULT 'legacy'");
            db.execSQL("ALTER TABLE music_tracks ADD COLUMN sourceBaseUrl TEXT");
            db.execSQL("ALTER TABLE music_tracks ADD COLUMN mediaId TEXT");
            db.execSQL("ALTER TABLE music_tracks ADD COLUMN playbackUrl TEXT");
            db.execSQL("UPDATE music_tracks SET playbackUrl = source, sourceId = 'local' WHERE local = 1");
            db.execSQL("UPDATE music_tracks SET playbackUrl = source WHERE local = 0");
        }
    };
    private static volatile MusicDatabase instance;

    public abstract MusicLibraryDao libraryDao();

    public static MusicDatabase get(Context context) {
        if (instance == null) {
            synchronized (MusicDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    MusicDatabase.class, "music-library.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return instance;
    }
}
