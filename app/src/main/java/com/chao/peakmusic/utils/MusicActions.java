package com.chao.peakmusic.utils;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.chao.peakmusic.R;
import com.chao.peakmusic.data.MusicLibraryRepository;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.data.PlaylistSummary;

import java.util.ArrayList;
import java.util.List;

public final class MusicActions {
    private static final int REQUEST_WRITE_MUSIC = 7101;

    private MusicActions() {
    }

    public static void show(Activity activity, MusicTrackEntity track) {
        show(activity, track, null);
    }

    public static void show(Activity activity, MusicTrackEntity track, Runnable changed) {
        MusicLibraryRepository repository = MusicLibraryRepository.get(activity);
        repository.isFavorite(track, favorite -> {
            List<String> actions = new ArrayList<>();
            actions.add(activity.getString(R.string.music_details));
            actions.add(activity.getString(favorite
                    ? R.string.remove_favorite : R.string.add_favorite));
            actions.add(activity.getString(R.string.add_to_playlist));
            actions.add(activity.getString(R.string.copy_music_address));
            actions.add(activity.getString(R.string.share_music));
            if (track.local) {
                actions.add(activity.getString(R.string.open_music_file));
            } else {
                actions.add(activity.getString(R.string.download_music));
            }
            new AlertDialog.Builder(activity)
                    .setTitle(track.name)
                    .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                        if (which == 0) {
                            showDetails(activity, track);
                        } else if (which == 1) {
                            repository.toggleFavorite(track, saved -> {
                                ToastUtils.showToast(activity.getString(saved
                                        ? R.string.favorite_added : R.string.favorite_removed));
                                if (changed != null) changed.run();
                            });
                        } else if (which == 2) {
                            choosePlaylist(activity, repository, track);
                        } else if (which == 3) {
                            copyAddress(activity, track);
                        } else if (which == 4) {
                            share(activity, track);
                        } else if (track.local) {
                            openLocalFile(activity, track);
                        } else {
                            download(activity, track);
                        }
                    })
                    .show();
        });
    }

    private static void showDetails(Activity activity, MusicTrackEntity track) {
        String message = track.local
                ? activity.getString(R.string.local_music_details, safe(track.name),
                safe(track.artist), safe(track.album), formatTime(track.durationMs),
                android.text.format.Formatter.formatFileSize(activity, track.sizeBytes),
                safe(track.filePath))
                : activity.getString(R.string.online_music_details, safe(track.name),
                safe(track.artist), safe(track.imageUrl), safe(track.lyricsUrl),
                safe(track.source));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.music_details)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        TextView text = dialog.findViewById(android.R.id.message);
        if (text != null) text.setTextIsSelectable(true);
    }

    private static void choosePlaylist(Activity activity, MusicLibraryRepository repository,
                                       MusicTrackEntity track) {
        repository.loadPlaylists(playlists -> {
            if (playlists.isEmpty()) {
                ToastUtils.showToast(activity.getString(R.string.playlists_empty_create_first));
                return;
            }
            String[] names = new String[playlists.size()];
            for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name;
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.choose_playlist)
                    .setItems(names, (dialog, which) -> {
                        PlaylistSummary playlist = playlists.get(which);
                        repository.addToPlaylist(playlist.id, track, () ->
                                ToastUtils.showToast(activity.getString(
                                        R.string.added_to_playlist, playlist.name)));
                    })
                    .show();
        });
    }

    private static void copyAddress(Activity activity, MusicTrackEntity track) {
        String address = track.local && !TextUtils.isEmpty(track.filePath)
                ? track.filePath : track.source;
        ClipboardManager manager = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText(activity.getString(
                R.string.music_address), address));
        ToastUtils.showToast(activity.getString(R.string.copied));
    }

    private static void share(Activity activity, MusicTrackEntity track) {
        String address = track.local && !TextUtils.isEmpty(track.filePath)
                ? track.filePath : track.source;
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, track.name)
                .putExtra(Intent.EXTRA_TEXT, track.name + " - " + track.artist + "\n" + address);
        activity.startActivity(Intent.createChooser(intent,
                activity.getString(R.string.share_music)));
    }

    private static void openLocalFile(Activity activity, MusicTrackEntity track) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(track.source), "audio/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
        } catch (RuntimeException error) {
            ToastUtils.showToast(activity.getString(R.string.open_music_file_failed));
        }
    }

    private static void download(Activity activity, MusicTrackEntity track) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(activity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_MUSIC);
            ToastUtils.showToast(activity.getString(R.string.download_permission_required));
            return;
        }
        try {
            String fileName = sanitize(track.name + "-" + track.artist) + ".mp3";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(track.source))
                    .setTitle(track.name)
                    .setDescription(track.artist)
                    .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, fileName);
            DownloadManager manager = (DownloadManager)
                    activity.getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            ToastUtils.showToast(activity.getString(R.string.download_started));
        } catch (RuntimeException error) {
            ToastUtils.showToast(activity.getString(R.string.download_failed));
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String formatTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds) / 1000;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d",
                seconds / 60, seconds % 60);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
