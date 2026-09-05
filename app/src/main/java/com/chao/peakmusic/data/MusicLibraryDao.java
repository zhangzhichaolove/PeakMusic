package com.chao.peakmusic.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Upsert;

import java.util.List;

@Dao
public interface MusicLibraryDao {
    @Query("SELECT * FROM music_tracks WHERE source = :source LIMIT 1")
    MusicTrackEntity findTrack(String source);

    @Upsert
    void saveTrack(MusicTrackEntity track);

    @Query("SELECT * FROM music_tracks WHERE favorite = 1 ORDER BY favoriteAt DESC")
    List<MusicTrackEntity> favorites();

    @Query("SELECT * FROM music_tracks WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 200")
    List<MusicTrackEntity> history();

    @Query("DELETE FROM music_tracks WHERE lastPlayedAt > 0 AND favorite = 0 AND source NOT IN (SELECT source FROM playlist_tracks)")
    void deleteUnreferencedHistory();

    @Query("UPDATE music_tracks SET lastPlayedAt = 0, playCount = 0 WHERE lastPlayedAt > 0 AND (favorite = 1 OR source IN (SELECT source FROM playlist_tracks))")
    void clearReferencedHistory();

    @Insert
    long insertPlaylist(PlaylistEntity playlist);

    @Query("SELECT playlists.id, playlists.name, playlists.createdAt, COUNT(playlist_tracks.source) AS trackCount FROM playlists LEFT JOIN playlist_tracks ON playlists.id = playlist_tracks.playlistId GROUP BY playlists.id ORDER BY playlists.createdAt DESC")
    List<PlaylistSummary> playlists();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void addPlaylistTrack(PlaylistTrackEntity track);

    @Query("SELECT music_tracks.* FROM music_tracks INNER JOIN playlist_tracks ON music_tracks.source = playlist_tracks.source WHERE playlist_tracks.playlistId = :playlistId ORDER BY playlist_tracks.addedAt DESC")
    List<MusicTrackEntity> playlistTracks(long playlistId);

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND source = :source")
    void removePlaylistTrack(long playlistId, String source);

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    void deletePlaylistTracks(long playlistId);

    @Query("SELECT COUNT(*) FROM playlists WHERE id = :playlistId")
    int hasPlaylist(long playlistId);

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    int renamePlaylist(long playlistId, String name);

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    void deletePlaylist(long playlistId);
}
