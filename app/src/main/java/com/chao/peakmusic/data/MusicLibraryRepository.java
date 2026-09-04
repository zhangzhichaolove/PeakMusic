package com.chao.peakmusic.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MusicLibraryRepository {
    private static volatile MusicLibraryRepository instance;
    private final MusicLibraryDao dao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private MusicLibraryRepository(Context context) {
        dao = MusicDatabase.get(context).libraryDao();
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
        databaseExecutor.execute(() -> dao.saveTrack(mergeMetadata(metadata)));
    }

    public void recordPlayback(String source, String name, String artist, boolean local) {
        if (source == null || source.isEmpty()) {
            return;
        }
        databaseExecutor.execute(() -> {
            MusicTrackEntity metadata = new MusicTrackEntity();
            metadata.source = source;
            metadata.name = name;
            metadata.artist = artist;
            metadata.local = local;
            MusicTrackEntity track = mergeMetadata(metadata);
            track.lastPlayedAt = System.currentTimeMillis();
            track.playCount++;
            dao.saveTrack(track);
        });
    }

    public void toggleFavorite(MusicTrackEntity metadata, ValueCallback<Boolean> callback) {
        if (metadata == null || metadata.source.isEmpty()) {
            post(callback, false);
            return;
        }
        databaseExecutor.execute(() -> {
            MusicTrackEntity track = mergeMetadata(metadata);
            track.favorite = !track.favorite;
            track.favoriteAt = track.favorite ? System.currentTimeMillis() : 0;
            dao.saveTrack(track);
            post(callback, track.favorite);
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

    public void clearHistory(Runnable callback) {
        databaseExecutor.execute(() -> {
            dao.deleteUnreferencedHistory();
            dao.clearReferencedHistory();
            post(callback);
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

    public void addToPlaylist(long playlistId, MusicTrackEntity metadata, Runnable callback) {
        if (metadata == null || metadata.source.isEmpty()) {
            post(callback);
            return;
        }
        databaseExecutor.execute(() -> {
            dao.saveTrack(mergeMetadata(metadata));
            PlaylistTrackEntity track = new PlaylistTrackEntity();
            track.playlistId = playlistId;
            track.source = metadata.source;
            track.addedAt = System.currentTimeMillis();
            dao.addPlaylistTrack(track);
            post(callback);
        });
    }

    public void removeFromPlaylist(long playlistId, String source, Runnable callback) {
        databaseExecutor.execute(() -> {
            dao.removePlaylistTrack(playlistId, source);
            post(callback);
        });
    }

    public void deletePlaylist(long playlistId, Runnable callback) {
        databaseExecutor.execute(() -> {
            dao.deletePlaylistTracks(playlistId);
            dao.deletePlaylist(playlistId);
            post(callback);
        });
    }

    private MusicTrackEntity mergeMetadata(MusicTrackEntity source) {
        MusicTrackEntity saved = dao.findTrack(source.source);
        if (saved == null) {
            return source;
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
