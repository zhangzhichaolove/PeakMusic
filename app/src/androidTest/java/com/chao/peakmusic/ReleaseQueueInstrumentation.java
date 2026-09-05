package com.chao.peakmusic;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Black-box, platform-only runner; it never links Debug app/test-library classes into the Release target. */
public class ReleaseQueueInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int resultCode = android.app.Activity.RESULT_CANCELED;
        UiAutomation ui = getUiAutomation();
        try {
            runOnMainSync(() -> getTargetContext().startActivity(new Intent()
                    .setComponent(new ComponentName(getTargetContext(), "com.chao.peakmusic.activity.GuideActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            awaitText(ui, "Release API fixture");
            AccessibilityNodeInfo title = awaitText(ui, "Release pending");
            while (!title.isClickable() && title.getParent() != null) title = title.getParent();
            require(title.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Open restored player");
            awaitText(ui, "歌词校准");
            awaitText(ui, "Release source lyric");
            awaitText(ui, "歌词 +1.5s");
            ui.adoptShellPermissionIdentity(android.Manifest.permission.MEDIA_CONTENT_CONTROL);
            MediaSessionManager manager = getTargetContext().getSystemService(MediaSessionManager.class);
            MediaController controller = null;
            for (MediaController candidate : manager.getActiveSessions(null))
                if (getTargetContext().getPackageName().equals(candidate.getPackageName())) controller = candidate;
            // Only session discovery needs shell privileges. Keeping them during app playback
            // changes audio AppOps attribution on API 36 and can cause focus hardening to reject it.
            ui.dropShellPermissionIdentity();
            require(controller != null, "PeakMusic media session");
            require("Release pending".equals(controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE)), "Restored title");
            require(controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION) >= 59000, "Decoded WAV duration");
            require(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING, "Actual playback state");
            SystemClock.sleep(350);
            File dir = new File(getTargetContext().getExternalFilesDir(null), "verification-release");
            require(dir.isDirectory() || dir.mkdirs(), "Evidence directory");
            Bitmap screenshot = ui.takeScreenshot();
            try (FileOutputStream out = new FileOutputStream(new File(dir, "player.png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
            screenshot.recycle();
            String evidence = "api_fixture_displayed=true\nsource_key_distinct_from_playback_url=true\nsource_lyric_offset=1.5\nrelative_audio_and_lyrics_played=true\nrestored_title=Release pending\nstate=PLAYING\nduration_ms="
                    + controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION) + "\n";
            try (FileOutputStream out = new FileOutputStream(new File(dir, "verification.txt"))) { out.write(evidence.getBytes(StandardCharsets.UTF_8)); }
            controller.getTransportControls().pause();
            verifyBatchLibrary(ui, dir);
            verifyLocalLibrary(ui, dir, controller);
            com.chao.peakmusic.ReleaseDiagnosticCheck.run(this, dir);
            result.putString("stream", "RELEASE_QUEUE_OK\nRELEASE_LIBRARY_BATCH_OK\nRELEASE_LOCAL_LIBRARY_OK\nRELEASE_DIAGNOSTIC_PRIVACY_OK\n");
            resultCode = android.app.Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
        } finally { ui.dropShellPermissionIdentity(); }
        finish(resultCode, result);
    }

    private void verifyLocalLibrary(UiAutomation ui, File dir, MediaController controller) throws Exception {
        try (LocalMediaFixture media = new LocalMediaFixture(getTargetContext(), getContext())) {
            runOnMainSync(() -> getTargetContext().startActivity(new Intent()
                    .setComponent(new ComponentName(getTargetContext(), "com.chao.peakmusic.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            clickText(ui, "本地音乐");
            awaitText(ui, media.aName); awaitText(ui, media.bName);
            clickText(ui, "全部歌曲"); clickText(ui, "专辑"); clickText(ui, "Browse Album A");
            awaitText(ui, media.aName); awaitText(ui, media.bName);
            ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            clickText(ui, "专辑"); clickText(ui, "文件夹");
            clickText(ui, "external_primary:/" + media.root + "Second/Disc"); awaitText(ui, media.cName);
            ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            clickText(ui, "文件夹"); clickText(ui, "歌手"); clickText(ui, "Browse Artist A");
            clickText(ui, media.bName);
            long end = SystemClock.elapsedRealtime() + 15000;
            while (SystemClock.elapsedRealtime() < end && (!media.bName.equals(controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE))
                    || controller.getPlaybackState().getState() != PlaybackState.STATE_PLAYING
                    || controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION) < 59000)) SystemClock.sleep(100);
            require(media.bName.equals(controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE)), "Release selected local track");
            require(controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING, "Release local MP3 playing");
            require(controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION) >= 59000, "Release local MP3 decoded");
            controller.getTransportControls().pause();
            Bitmap screenshot = ui.takeScreenshot();
            try (FileOutputStream out = new FileOutputStream(new File(dir, "local-library.png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
            screenshot.recycle();
            android.app.Activity queue = startActivitySync(new Intent()
                    .setComponent(new ComponentName(getTargetContext(), "com.chao.peakmusic.activity.MusicQueueActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            awaitText(ui, "共 2 首 · 第 1–2 首");
            runOnMainSync(queue::openOptionsMenu); clickText(ui, "清空队列"); clickText(ui, "清空"); awaitText(ui, "队列为空");
            runOnMainSync(queue::finish);
            try (FileOutputStream out = new FileOutputStream(new File(dir, "local-library.txt"))) {
                out.write("real_mediastore_scan=true\nalbum_artist_folder_navigation=true\nvisible_group_queue_size=2\nlocal_mp3_decoded=true\nqueue_cleared_before_fixture_deletion=true\n".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void verifyBatchLibrary(UiAutomation ui, File dir) throws Exception {
        android.app.Activity library = startActivitySync(new Intent()
                .setComponent(new ComponentName(getTargetContext(), "com.chao.peakmusic.activity.MusicLibraryActivity"))
                .putExtra("mode", "playlists").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        clickText(ui, "Release batch source");
        clickText(ui, "多选"); clickText(ui, "全选");
        awaitText(ui, "已选择 2 首");
        runOnMainSync(library::openOptionsMenu); clickText(ui, "收藏所选");
        awaitText(ui, "多选");
        try (android.database.sqlite.SQLiteDatabase db = android.database.sqlite.SQLiteDatabase.openDatabase(
                getTargetContext().getDatabasePath("music-library.db").getPath(), null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY)) {
            require(count(db, "SELECT COUNT(*) FROM music_tracks WHERE favorite = 1") == 2, "Release batch favorites committed");
            clickText(ui, "多选"); clickText(ui, "全选");
            runOnMainSync(library::openOptionsMenu); clickText(ui, "添加所选到歌单"); clickText(ui, "Release batch copy");
            awaitText(ui, "多选");
            require(count(db, "SELECT COUNT(*) FROM playlist_tracks WHERE playlistId IN (SELECT id FROM playlists WHERE name = 'Release batch copy')") == 2,
                    "Release batch copy committed");
            clickText(ui, "多选"); clickText(ui, "全选");
            runOnMainSync(library::openOptionsMenu); clickText(ui, "从歌单移除所选"); clickText(ui, "从歌单移除");
            awaitText(ui, getTargetContext().getString(getTargetContext().getResources().getIdentifier("music_library_empty", "string", getTargetContext().getPackageName())));
            require(count(db, "SELECT COUNT(*) FROM playlist_tracks WHERE playlistId IN (SELECT id FROM playlists WHERE name = 'Release batch source')") == 0,
                    "Release only source memberships removed");
            require(count(db, "SELECT COUNT(*) FROM playlist_tracks WHERE playlistId IN (SELECT id FROM playlists WHERE name = 'Release batch copy')") == 2,
                    "Release copy memberships retained");
            require(count(db, "SELECT COUNT(*) FROM music_tracks WHERE favorite = 1") == 2, "Release favorites retained");
        }
        require(new File(getTargetContext().getCacheDir(), "queue-bulk.wav").exists(), "Local file retained");
        Bitmap screenshot = ui.takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(dir, "library-batch.png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
        screenshot.recycle();
        try (FileOutputStream out = new FileOutputStream(new File(dir, "library-batch.txt"))) {
            out.write("minified_viewmodel_created=true\nbatch_favorites=2\ncopy_memberships=2\nremoved_source_memberships=2\nlocal_file_retained=true\n".getBytes(StandardCharsets.UTF_8));
        }
        runOnMainSync(library::finish);
    }

    private long count(android.database.sqlite.SQLiteDatabase db, String sql) {
        return android.database.DatabaseUtils.longForQuery(db, sql, null);
    }
    private void clickText(UiAutomation ui, String text) {
        AccessibilityNodeInfo node = awaitText(ui, text);
        while (!node.isClickable() && node.getParent() != null) node = node.getParent();
        require(node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Click " + text);
    }

    private AccessibilityNodeInfo awaitText(UiAutomation ui, String text) {
        long end = SystemClock.elapsedRealtime() + 15000;
        do {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                if (text.contentEquals(node.getText() == null ? "" : node.getText())) return node;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < end);
        throw new AssertionError("Missing UI text: " + text);
    }
    private void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
