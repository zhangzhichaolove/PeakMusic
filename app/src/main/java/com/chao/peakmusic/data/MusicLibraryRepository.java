package com.chao.peakmusic.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MusicLibraryRepository {
    private static volatile MusicLibraryRepository instance;
    private final MusicDatabase database;
    private final MusicLibraryDao dao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MusicLibraryRepository(Context context) {
        this(MusicDatabase.get(context));
    }

    MusicLibraryRepository(MusicDatabase database) {
        this.database = database;
        dao = database.libraryDao();
    }

    public static MusicLibraryRepository get(Context context) {
        if (instance == null) {
            synchronized (MusicLibraryRepository.class) {
                if (instance == null) {
                    instance = new MusicLibraryRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void saveMetadata(MusicTrackEntity metadata) {
        if (metadata == null || metadata.source.isEmpty()) {
            return;
        }
        databaseExecutor.execute(() -> database.runInTransaction(() -> dao.saveTrack(mergeMetadata(metadata))));
    }

    public void recordPlayback(String source, String name, String artist, boolean local) {
        if (source == null || source.isEmpty()) {
            return;
        }
        databaseExecutor.execute(() -> database.runInTransaction(() -> {
            MusicTrackEntity metadata = new MusicTrackEntity();
            metadata.source = source;
            metadata.name = name;
            metadata.artist = artist;
            metadata.local = local;
            MusicTrackEntity track = mergeMetadata(metadata);
            track.lastPlayedAt = System.currentTimeMillis();
            track.playCount++;
            dao.saveTrack(track);
        }));
    }

    public void toggleFavorite(MusicTrackEntity metadata, ValueCallback<Boolean> callback) {
        if (metadata == null || metadata.source.isEmpty()) {
            post(callback, false);
            return;
        }
        databaseExecutor.execute(() -> {
            boolean favorite = database.runInTransaction(() -> {
                MusicTrackEntity track = mergeMetadata(metadata);
                track.favorite = !track.favorite;
                track.favoriteAt = track.favorite ? System.currentTimeMillis() : 0;
                dao.saveTrack(track);
                return track.favorite;
            });
            post(callback, favorite);
        });
    }

    public void isFavorite(MusicTrackEntity track, ValueCallback<Boolean> callback) {
        databaseExecutor.execute(() -> {
            MusicTrackEntity saved = track == null ? null : dao.findTrack(track.source);
            post(callback, saved != null && saved.favorite);
        });
    }

    public void loadFavorites(ValueCallback<List<MusicTrackEntity>> callback) {
        databaseExecutor.execute(() -> post(callback, dao.favorites()));
    }

    public void loadHistory(ValueCallback<List<MusicTrackEntity>> callback) {
        databaseExecutor.execute(() -> post(callback, dao.history()));
    }

    public void loadTrack(String source, ValueCallback<MusicTrackEntity> callback) {
        databaseExecutor.execute(() -> post(callback,
                source == null ? null : dao.findTrack(source)));
    }

    public void clearHistory(ValueCallback<Boolean> callback) {
        databaseExecutor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    dao.deleteUnreferencedHistory();
                    dao.clearReferencedHistory();
                });
                post(callback, true);
            } catch (android.database.sqlite.SQLiteException error) { post(callback, false); }
        });
    }

    public void createPlaylist(String name, ValueCallback<Long> callback) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) {
            post(callback, -1L);
            return;
        }
        databaseExecutor.execute(() -> {
            PlaylistEntity playlist = new PlaylistEntity();
            playlist.name = normalized;
            playlist.createdAt = System.currentTimeMillis();
            post(callback, dao.insertPlaylist(playlist));
        });
    }

    public void loadPlaylists(ValueCallback<List<PlaylistSummary>> callback) {
        databaseExecutor.execute(() -> post(callback, dao.playlists()));
    }

    public void loadPlaylistTracks(long playlistId,
                                   ValueCallback<List<MusicTrackEntity>> callback) {
        databaseExecutor.execute(() -> post(callback, dao.playlistTracks(playlistId)));
    }

    public void addToPlaylist(long playlistId, MusicTrackEntity metadata, ValueCallback<Boolean> callback) {
        if (metadata == null || metadata.source.isEmpty()) {
            post(callback, false);
            return;
        }
        databaseExecutor.execute(() -> {
            try {
                boolean added = database.runInTransaction(() -> {
                    if (dao.hasPlaylist(playlistId) == 0) return false;
                    dao.saveTrack(mergeMetadata(metadata));
                    PlaylistTrackEntity track = new PlaylistTrackEntity();
                    track.playlistId = playlistId;
                    track.source = metadata.source;
                    track.addedAt = System.currentTimeMillis();
                    dao.addPlaylistTrack(track);
                    return true;
                });
                post(callback, added);
            } catch (android.database.sqlite.SQLiteException error) { post(callback, false); }
        });
    }

    public void renamePlaylist(long playlistId, String name, ValueCallback<Boolean> callback) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty()) { post(callback, false); return; }
        databaseExecutor.execute(() -> {
            try { post(callback, dao.renamePlaylist(playlistId, normalized) == 1); }
            catch (android.database.sqlite.SQLiteException error) { post(callback, false); }
        });
    }

    public void removeFromPlaylist(long playlistId, String source, Runnable callback) {
        databaseExecutor.execute(() -> {
            dao.removePlaylistTrack(playlistId, source);
            post(callback);
        });
    }

    /** Batch actions target established keys, never stale UI metadata or playback URLs. */
    public void setFavorites(List<String> keys, boolean favorite, ValueCallback<Boolean> callback) {
        batch(keys, tracks -> {
            long now = System.currentTimeMillis();
            for (MusicTrackEntity track : tracks) {
                track.favorite = favorite;
                track.favoriteAt = favorite ? now : 0;
                dao.saveTrack(track); // Upsert preserves every playlist association.
            }
            return true;
        }, callback);
    }

    public void addTracksToPlaylist(long playlistId, List<String> keys, ValueCallback<Boolean> callback) {
        batch(keys, tracks -> {
            if (dao.hasPlaylist(playlistId) == 0) return false;
            long now = System.currentTimeMillis();
            for (MusicTrackEntity track : tracks) {
                PlaylistTrackEntity member = new PlaylistTrackEntity();
                member.playlistId = playlistId; member.source = track.source; member.addedAt = now;
                dao.addPlaylistTrack(member);
            }
            return true;
        }, callback);
    }

    public void removeTracksFromPlaylist(long playlistId, List<String> keys, ValueCallback<Boolean> callback) {
        batch(keys, tracks -> {
            if (dao.hasPlaylist(playlistId) == 0) return false;
            for (MusicTrackEntity track : tracks) dao.removePlaylistTrack(playlistId, track.source);
            return true; // Only membership is removed, not files, favorites or playback history.
        }, callback);
    }

    private void batch(List<String> keys, BatchAction action, ValueCallback<Boolean> callback) {
        java.util.Set<String> snapshot = keys == null ? java.util.Collections.emptySet() : new java.util.LinkedHashSet<>(keys);
        if (snapshot.isEmpty() || snapshot.contains(null) || snapshot.contains("")) { post(callback, false); return; }
        databaseExecutor.execute(() -> {
            try {
                boolean success = database.runInTransaction(() -> {
                    java.util.ArrayList<MusicTrackEntity> tracks = new java.util.ArrayList<>();
                    for (String key : snapshot) {
                        MusicTrackEntity track = dao.findTrack(key);
                        if (track == null) return false; // Validate the complete snapshot before any writes.
                        tracks.add(track);
                    }
                    return action.apply(tracks);
                });
                post(callback, success);
            } catch (android.database.sqlite.SQLiteException error) { post(callback, false); }
        });
    }

    private interface BatchAction { boolean apply(List<MusicTrackEntity> tracks); }

    public void deletePlaylist(long playlistId, ValueCallback<Boolean> callback) {
        databaseExecutor.execute(() -> {
            try {
                database.runInTransaction(() -> {
                    dao.deletePlaylistTracks(playlistId);
                    dao.deletePlaylist(playlistId);
                });
                post(callback, true);
            } catch (android.database.sqlite.SQLiteException error) { post(callback, false); }
        });
    }

    private MusicTrackEntity mergeMetadata(MusicTrackEntity source) {
        MusicTrackEntity saved = dao.findTrack(source.source);
        if (saved == null) {
            return source;
        }
        if (source.playbackUrl != null) saved.playbackUrl = source.playbackUrl;
        if (!MusicSource.LEGACY.equals(source.sourceId)) {
            saved.sourceId = source.sourceId; saved.sourceBaseUrl = source.sourceBaseUrl; saved.mediaId = source.mediaId;
        }
        saved.name = prefer(source.name, saved.name);
        saved.artist = prefer(source.artist, saved.artist);
        saved.imageUrl = prefer(source.imageUrl, saved.imageUrl);
        saved.lyricsUrl = prefer(source.lyricsUrl, saved.lyricsUrl);
        saved.album = prefer(source.album, saved.album);
        saved.filePath = prefer(source.filePath, saved.filePath);
        saved.local = source.local || saved.local;
        if (source.albumId > 0) saved.albumId = source.albumId;
        if (source.durationMs > 0) saved.durationMs = source.durationMs;
        if (source.sizeBytes > 0) saved.sizeBytes = source.sizeBytes;
        return saved;
    }

    private static String prefer(String first, String fallback) {
        return first == null || first.isEmpty() ? fallback : first;
    }

    private <T> void post(ValueCallback<T> callback, T value) {
        if (callback != null) {
            mainHandler.post(() -> callback.onResult(value));
        }
    }

    private void post(Runnable callback) {
        if (callback != null) {
            mainHandler.post(callback);
        }
    }

    public interface ValueCallback<T> {
        void onResult(T value);
    }
}
