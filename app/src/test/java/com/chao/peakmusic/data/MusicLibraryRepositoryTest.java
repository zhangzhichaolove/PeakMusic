package com.chao.peakmusic.data;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import androidx.room.Room;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class MusicLibraryRepositoryTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private MusicDatabase database;
    private MusicLibraryRepository repository;
    private ExecutorService executor;
    @Before public void setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase.class).allowMainThreadQueries().build();
        repository = new MusicLibraryRepository(database);
        executor = ReflectionHelpers.getField(repository, "databaseExecutor");
    }
    @After public void tearDown() throws Exception { drain(); executor.shutdownNow(); database.close(); }
    private void drain() throws Exception {
        executor.submit(() -> {}).get(10, TimeUnit.SECONDS); Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private long playlist() throws Exception {
        AtomicReference<Long> id = new AtomicReference<>(); repository.createPlaylist("Original", id::set); drain(); return id.get();
    }
    private MusicTrackEntity track(String source) { MusicTrackEntity track = new MusicTrackEntity(); track.source = source; track.name = source; return track; }

    @Test public void metadataUpsertKeepsMembershipAndFavoriteHistory() throws Exception {
        long id = playlist(); MusicTrackEntity track = track("A");
        repository.addToPlaylist(id, track, null); repository.toggleFavorite(track, null);
        repository.recordPlayback("A", "Updated", "Artist", false); drain();
        assertEquals(1, database.libraryDao().playlistTracks(id).size());
        assertEquals(1, database.libraryDao().favorites().size());
        assertEquals("Updated", database.libraryDao().history().get(0).name);
    }

    @Test public void sourceScopedFavoritesMembershipAndHistorySurvivePlaybackUrlRotation() throws Exception {
        com.chao.peakmusic.model.MusicModel model = new com.chao.peakmusic.model.MusicModel();
        model.setId("42"); model.setMp3("https://cdn.example/shared.mp3?token=old");
        model.bindApiSource("https://example.com/a/");
        MusicTrackEntity a = MusicTrackEntity.from(model);
        com.chao.peakmusic.model.MusicModel other = new com.chao.peakmusic.model.MusicModel();
        other.setId("42"); other.setMp3(model.getMp3()); other.bindApiSource("https://example.com/b/");
        MusicTrackEntity b = MusicTrackEntity.from(other);
        long id = playlist();
        repository.toggleFavorite(a, null); repository.toggleFavorite(b, null);
        repository.addToPlaylist(id, a, null); repository.addToPlaylist(id, b, null);
        repository.recordPlayback(a.source, "A", "artist", false); drain();
        model.setMp3("https://cdn.example/shared.mp3?token=new");
        repository.saveMetadata(MusicTrackEntity.from(model)); drain();
        assertEquals(2, database.libraryDao().favorites().size());
        assertEquals(2, database.libraryDao().playlistTracks(id).size());
        MusicTrackEntity saved = database.libraryDao().findTrack(a.source);
        assertEquals(model.getMp3(), saved.getPlaybackUrl()); assertEquals(1, saved.playCount);
        assertEquals(0, database.libraryDao().findTrack(b.source).playCount);
        assertEquals(b.getPlaybackUrl(), database.libraryDao().findTrack(b.source).getPlaybackUrl());
        com.chao.peakmusic.lyrics.LyricOffsetStore offsets = new com.chao.peakmusic.lyrics.LyricOffsetStore(context);
        offsets.set(a.source, 1500); offsets.set(b.source, -500);
        assertEquals(1500, offsets.get(MusicTrackEntity.from(model).source));
        assertEquals(-500, offsets.get(b.source));
    }

    @Test public void unknownLegacyFavoriteIsNotMergedIntoANewApiByMatchingUrl() throws Exception {
        String url = "https://cdn.example/shared.mp3";
        repository.toggleFavorite(track(url), null); drain();
        com.chao.peakmusic.model.MusicModel music = new com.chao.peakmusic.model.MusicModel();
        music.setId(42); music.setMp3(url); music.bindApiSource("https://example.com/api/");
        repository.saveMetadata(MusicTrackEntity.from(music)); drain();
        assertFalse(database.libraryDao().findTrack(MusicTrackEntity.keyOf(music)).favorite);
        assertTrue(database.libraryDao().findTrack(url).favorite);
        assertEquals(MusicSource.LEGACY, database.libraryDao().findTrack(url).sourceId);
    }
    @Test public void historyClearRetainsFavoriteAndPlaylistTracksAndRemovesUnreferencedTracks() throws Exception {
        long id = playlist();
        repository.addToPlaylist(id, track("playlist"), null); repository.toggleFavorite(track("favorite"), null);
        for (String source : new String[]{"playlist", "favorite", "history-only"}) repository.recordPlayback(source, source, "", false);
        AtomicReference<Boolean> cleared = new AtomicReference<>(); repository.clearHistory(cleared::set); drain();
        assertEquals(Boolean.TRUE, cleared.get()); assertTrue(database.libraryDao().history().isEmpty());
        assertEquals(1, database.libraryDao().favorites().size()); assertEquals(1, database.libraryDao().playlistTracks(id).size());
        assertNull(database.libraryDao().findTrack("history-only"));
    }

    @Test public void batchFavoriteAndMembershipUseLatestRowsDeduplicateAndPreserveHistory() throws Exception {
        long id = playlist();
        MusicTrackEntity a = track("A"); a.playbackUrl = "https://example.com/old";
        repository.saveMetadata(a); repository.saveMetadata(track("B")); drain();
        a.playbackUrl = "https://example.com/new"; repository.saveMetadata(a);
        repository.recordPlayback("A", "A", "artist", false); drain();
        AtomicReference<Boolean> result = new AtomicReference<>();
        repository.addTracksToPlaylist(id, java.util.List.of("A", "B", "A"), result::set); drain();
        assertEquals(Boolean.TRUE, result.get()); assertEquals(2, database.libraryDao().playlistTracks(id).size());
        repository.setFavorites(java.util.List.of("A", "B"), true, result::set); drain();
        assertEquals(Boolean.TRUE, result.get()); assertEquals(2, database.libraryDao().favorites().size());
        assertEquals("https://example.com/new", database.libraryDao().findTrack("A").getPlaybackUrl());
        assertEquals(1, database.libraryDao().findTrack("A").playCount);
        repository.removeTracksFromPlaylist(id, java.util.List.of("A", "B"), result::set); drain();
        assertEquals(Boolean.TRUE, result.get()); assertTrue(database.libraryDao().playlistTracks(id).isEmpty());
        assertEquals(2, database.libraryDao().favorites().size());
        repository.setFavorites(java.util.List.of("A", "B"), false, result::set); drain();
        assertTrue(database.libraryDao().favorites().isEmpty());
        assertNotNull(database.libraryDao().findTrack("B")); assertEquals(1, database.libraryDao().findTrack("A").playCount);
    }

    @Test public void batchRejectsEmptyMissingKeysAndDeletedPlaylistBeforeAnyWrites() throws Exception {
        long id = playlist(); repository.saveMetadata(track("A")); drain();
        AtomicReference<Boolean> result = new AtomicReference<>();
        for (java.util.List<String> keys : java.util.List.of(java.util.List.<String>of(), java.util.List.of("A", "missing"))) {
            repository.setFavorites(keys, true, result::set); drain(); assertEquals(Boolean.FALSE, result.get());
            repository.addTracksToPlaylist(id, keys, result::set); drain(); assertEquals(Boolean.FALSE, result.get());
        }
        repository.addTracksToPlaylist(id + 100, java.util.List.of("A"), result::set); drain();
        assertEquals(Boolean.FALSE, result.get()); assertTrue(database.libraryDao().favorites().isEmpty());
        assertTrue(database.libraryDao().playlistTracks(id).isEmpty());
    }

    @Test public void batchFavoriteFailureRollsBackEarlierRowsAndMembershipSurvives() throws Exception {
        long id = playlist(); repository.saveMetadata(track("A")); repository.saveMetadata(track("B")); drain();
        repository.addTracksToPlaylist(id, java.util.List.of("A", "B"), null); drain();
        database.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER fail_batch_favorite BEFORE UPDATE ON music_tracks WHEN NEW.source = 'B' BEGIN SELECT RAISE(ABORT, 'injected'); END");
        AtomicReference<Boolean> result = new AtomicReference<>();
        repository.setFavorites(java.util.List.of("A", "B"), true, result::set); drain();
        assertEquals(Boolean.FALSE, result.get()); assertTrue(database.libraryDao().favorites().isEmpty());
        assertEquals(2, database.libraryDao().playlistTracks(id).size());
    }

    @Test public void batchMembershipFailureRollsBackPriorInsertsAndDeletes() throws Exception {
        long id = playlist(); repository.saveMetadata(track("A")); repository.saveMetadata(track("B")); drain();
        database.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER fail_batch_insert BEFORE INSERT ON playlist_tracks WHEN NEW.source = 'B' BEGIN SELECT RAISE(ABORT, 'injected'); END");
        AtomicReference<Boolean> result = new AtomicReference<>();
        repository.addTracksToPlaylist(id, java.util.List.of("A", "B"), result::set); drain();
        assertEquals(Boolean.FALSE, result.get()); assertTrue(database.libraryDao().playlistTracks(id).isEmpty());
        database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER fail_batch_insert");
        repository.addTracksToPlaylist(id, java.util.List.of("A", "B"), result::set); drain();
        database.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER fail_batch_delete BEFORE DELETE ON playlist_tracks WHEN OLD.source = 'B' BEGIN SELECT RAISE(ABORT, 'injected'); END");
        repository.removeTracksFromPlaylist(id, java.util.List.of("A", "B"), result::set); drain();
        assertEquals(Boolean.FALSE, result.get()); assertEquals(2, database.libraryDao().playlistTracks(id).size());
    }
    @Test public void failedMembershipInsertRollsBackMetadataAndReportsFailure() throws Exception {
        long id = playlist();
        database.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER fail_membership BEFORE INSERT ON playlist_tracks BEGIN SELECT RAISE(ABORT, 'injected'); END");
        AtomicReference<Boolean> result = new AtomicReference<>(); repository.addToPlaylist(id, track("A"), result::set); drain();
        assertEquals(Boolean.FALSE, result.get()); assertNull(database.libraryDao().findTrack("A"));
        assertTrue(database.libraryDao().playlistTracks(id).isEmpty());
    }
    @Test public void deleteAndRenameDoNotOrphanMembershipOrEraseTrackMetadata() throws Exception {
        long id = playlist(); repository.addToPlaylist(id, track("A"), null); drain();
        AtomicReference<Boolean> result = new AtomicReference<>();
        repository.renamePlaylist(id, " Renamed ", result::set); drain();
        assertEquals(Boolean.TRUE, result.get()); assertEquals("Renamed", database.libraryDao().playlists().get(0).name);
        repository.renamePlaylist(id, " ", result::set); drain(); assertEquals(Boolean.FALSE, result.get());
        repository.deletePlaylist(id, result::set); drain(); assertEquals(Boolean.TRUE, result.get());
        assertTrue(database.libraryDao().playlists().isEmpty()); assertTrue(database.libraryDao().playlistTracks(id).isEmpty());
        assertNotNull(database.libraryDao().findTrack("A"));
        repository.addToPlaylist(id, track("B"), result::set); drain(); assertEquals(Boolean.FALSE, result.get());
        assertNull(database.libraryDao().findTrack("B"));
    }
    @Test public void failedHistoryResetRollsBackEarlierUnreferencedDeletion() throws Exception {
        repository.toggleFavorite(track("favorite"), null);
        repository.recordPlayback("favorite", "favorite", "", false);
        repository.recordPlayback("history-only", "history-only", "", false);
        drain();
        database.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER fail_history_reset BEFORE UPDATE ON music_tracks BEGIN SELECT RAISE(ABORT, 'injected'); END");
        AtomicReference<Boolean> result = new AtomicReference<>(); repository.clearHistory(result::set); drain();
        assertEquals(Boolean.FALSE, result.get()); assertEquals(2, database.libraryDao().history().size());
        assertNotNull(database.libraryDao().findTrack("history-only"));
    }
    @Test public void failedPlaylistDeletionRollsBackEarlierMemberRemoval() throws Exception {
        long id = playlist(); repository.addToPlaylist(id, track("A"), null); drain();
        database.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER fail_playlist_delete BEFORE DELETE ON playlists BEGIN SELECT RAISE(ABORT, 'injected'); END");
        AtomicReference<Boolean> result = new AtomicReference<>(); repository.deletePlaylist(id, result::set); drain();
        assertEquals(Boolean.FALSE, result.get()); assertEquals(1, database.libraryDao().playlists().size());
        assertEquals(1, database.libraryDao().playlistTracks(id).size());
    }

    @Test public void versionOneMigrationPreservesDataAndRemovesOnlyOrphanMembership() throws Exception { migrateFrom(1); }
    @Test public void versionTwoMigrationRetainsLegacyAndLocalKeysWithForeignKeyMembership() throws Exception { migrateFrom(2); }

    private void migrateFrom(int version) throws Exception {
        String name = "music-migration-test.db"; context.deleteDatabase(name);
        try (SQLiteDatabase old = context.openOrCreateDatabase(name, 0, null);
             InputStreamReader reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(
                     "com.chao.peakmusic.data.MusicDatabase/" + version + ".json"), StandardCharsets.UTF_8)) {
            JsonObject schema = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("database");
            for (JsonElement element : schema.getAsJsonArray("entities")) {
                JsonObject entity = element.getAsJsonObject();
                old.execSQL(entity.get("createSql").getAsString().replace("${TABLE_NAME}", entity.get("tableName").getAsString()));
                if (entity.has("indices")) for (JsonElement index : entity.getAsJsonArray("indices")) old.execSQL(index.getAsJsonObject().get("createSql")
                        .getAsString().replace("${TABLE_NAME}", entity.get("tableName").getAsString()));
            }
            old.execSQL("INSERT INTO music_tracks (source,name,local,albumId,durationMs,sizeBytes,favorite,favoriteAt,lastPlayedAt,playCount) VALUES ('old','Old favorite',0,0,0,0,1,100,200,3)");
            old.execSQL("INSERT INTO playlists (id,name,createdAt) VALUES (1,'Saved list',100)");
            old.execSQL("INSERT INTO playlist_tracks (playlistId,source,addedAt) VALUES (1,'old',100)");
            if (version == 1) old.execSQL("INSERT INTO playlist_tracks (playlistId,source,addedAt) VALUES (999,'old',100),(1,'missing',100)");
            old.execSQL("INSERT INTO music_tracks (source,name,local,albumId,durationMs,sizeBytes,favorite,favoriteAt,lastPlayedAt,playCount) VALUES ('content://media/external/audio/media/42','Local',1,7,60000,1000,0,0,0,0)");
            old.setVersion(version);
        }
        MusicDatabase upgraded = Room.databaseBuilder(context, MusicDatabase.class, name)
                .addMigrations(MusicDatabase.MIGRATION_1_2, MusicDatabase.MIGRATION_2_3).allowMainThreadQueries().build();
        try {
            assertEquals("Old favorite", upgraded.libraryDao().favorites().get(0).name);
            assertEquals(3, upgraded.libraryDao().history().get(0).playCount);
            assertEquals(1, upgraded.libraryDao().playlistTracks(1).size());
            assertEquals("Saved list", upgraded.libraryDao().playlists().get(0).name);
            MusicTrackEntity legacy = upgraded.libraryDao().findTrack("old");
            assertEquals(MusicSource.LEGACY, legacy.sourceId); assertNull(legacy.sourceBaseUrl); assertNull(legacy.mediaId);
            assertEquals("old", legacy.getPlaybackUrl());
            MusicTrackEntity local = upgraded.libraryDao().findTrack("content://media/external/audio/media/42");
            assertEquals(MusicSource.LOCAL, local.sourceId); assertEquals(local.source, local.getPlaybackUrl());
            assertEquals(7, local.albumId); assertEquals(60000, local.durationMs);
            try (android.database.Cursor check = upgraded.getOpenHelper().getReadableDatabase().query("PRAGMA foreign_key_check")) {
                assertFalse(check.moveToFirst());
            }
            try (android.database.Cursor cursor = upgraded.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM playlist_tracks")) {
                assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0));
            }
        } finally { upgraded.close(); context.deleteDatabase(name); }
    }
}
