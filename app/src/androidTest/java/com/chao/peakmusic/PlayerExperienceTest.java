package com.chao.peakmusic;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.chao.peakmusic.activity.MusicPlayActivity;
import com.chao.peakmusic.data.MusicLibraryRepository;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.lyrics.LyricOffsetStore;
import com.chao.peakmusic.service.MusicService;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Real Activity, MediaPlayer (synthetic silence), lyric HTTP errors and Room writes. Disposable emulator only. */
@RunWith(AndroidJUnit4.class)
public class PlayerExperienceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void lyricsRetryCalibrationAndFavoriteFollowActualCurrentTrack() throws Exception {
        File a = silentWave("player-a-" + System.nanoTime() + ".wav"), b = silentWave("player-b-" + System.nanoTime() + ".wav");
        try (LyricServer server = new LyricServer(); ActivityScenario<MusicPlayActivity> player = ActivityScenario.launch(MusicPlayActivity.class)) {
            MusicTrackEntity first = track(a, "Track A", server.url("a.lrc"));
            MusicTrackEntity second = track(b, "Track B", server.url("b.lrc"));
            LyricOffsetStore offsets = new LyricOffsetStore(context);
            offsets.set(first.source, 0); offsets.set(second.source, 0);
            ArrayList<MusicTrackEntity> queue = new ArrayList<>(); queue.add(first); queue.add(second);
            player.onActivity(activity -> com.chao.peakmusic.service.PlaybackStorage.get(activity).play(queue, 0));
            await(() -> text(player, R.id.tv_music_name).equals("Track A"));
            await(() -> text(player, R.id.tv_lyric_line).equals(context.getString(R.string.lyrics_failed)));
            server.fail = false;
            player.onActivity(activity -> activity.findViewById(R.id.lyrics_retry).performClick());
            await(() -> text(player, R.id.tv_lyric_line).equals("Fixture line A"));
            await(() -> enabled(player, R.id.playback_favorite));
            player.onActivity(activity -> activity.findViewById(R.id.playback_favorite).performClick());
            await(() -> text(player, R.id.playback_favorite).equals(context.getString(R.string.remove_favorite)));
            AtomicReference<Boolean> favorite = new AtomicReference<>();
            MusicLibraryRepository.get(context).isFavorite(first, favorite::set);
            await(() -> Boolean.TRUE.equals(favorite.get()));
            calibrate(player, R.string.lyric_offset_plus); calibrate(player, R.string.lyric_offset_plus);
            assertEquals(1000, offsets.get(first.source));
            player.onActivity(activity -> activity.findViewById(R.id.playback_next).performClick());
            await(() -> text(player, R.id.tv_music_name).equals("Track B"));
            await(() -> text(player, R.id.tv_lyric_line).equals(context.getString(R.string.lyrics_empty)));
            assertEquals(0, offsets.get(second.source));
            calibrate(player, R.string.lyric_offset_minus);
            assertEquals(-500, offsets.get(second.source));
            player.onActivity(activity -> activity.findViewById(R.id.playback_previous).performClick());
            await(() -> text(player, R.id.tv_music_name).equals("Track A"));
            assertEquals(1000, offsets.get(first.source));
            assertTrue(text(player, R.id.lyric_offset_value).contains("1.0"));
            player.onActivity(activity -> activity.findViewById(R.id.playback_mode).performClick());
            onView(withText(context.getResources().getStringArray(R.array.play_modes)[2]))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            player.onActivity(activity -> {
                try {
                    java.lang.reflect.Field field = MusicPlayActivity.class.getDeclaredField("musicService"); field.setAccessible(true);
                    MusicAidlInterface service = (MusicAidlInterface) field.get(activity);
                    assertEquals(2, service.getPlayMode());
                    assertTrue("Native WAV duration", service.getDuration() >= 59000);
                    service.pause();
                } catch (Exception error) { throw new AssertionError(error); }
            });
            player.recreate();
            await(() -> text(player, R.id.tv_music_name).equals("Track A"));
            await(() -> text(player, R.id.playback_favorite).equals(context.getString(R.string.remove_favorite)));
            assertTrue(text(player, R.id.lyric_offset_value).contains("1.0"));
            await(() -> text(player, R.id.tv_lyric_line).equals("Fixture line A"));
            onView(withText("Fixture line A")).check(androidx.test.espresso.assertion.ViewAssertions.matches(
                    androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed()));
            capture("player-calibration-favorite");
        } finally {
            context.stopService(new Intent(context, MusicService.class));
            a.delete(); b.delete();
        }
    }

    @Test public void landscapeLargeTextKeepsTransportAboveGesturesAndReachable() throws Exception {
        float scale = Settings.System.getFloat(context.getContentResolver(), Settings.System.FONT_SCALE, 1f);
        try {
            shell("settings put system font_scale 2.0"); SystemClock.sleep(700);
            try (ActivityScenario<MusicPlayActivity> player = ActivityScenario.launch(MusicPlayActivity.class)) {
                player.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                AtomicReference<Boolean> landscape = new AtomicReference<>(false);
                await(() -> {
                    player.onActivity(activity -> landscape.set(activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE));
                    return landscape.get();
                });
                SystemClock.sleep(500);
                player.onActivity(activity -> {
                    assertTrue(activity.getResources().getConfiguration().fontScale >= 1.9f);
                    View decor = activity.getWindow().getDecorView();
                    androidx.core.graphics.Insets safe = androidx.core.view.ViewCompat.getRootWindowInsets(decor)
                            .getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    for (int id : new int[]{R.id.playback_toggle, R.id.playback_previous, R.id.playback_next}) {
                        View button = activity.findViewById(id); int[] pos = new int[2]; button.getLocationOnScreen(pos);
                        assertTrue(pos[1] >= safe.top);
                        assertTrue(pos[1] + button.getHeight() <= decor.getHeight() - safe.bottom);
                        assertTrue(pos[0] >= safe.left);
                        assertTrue(pos[0] + button.getWidth() <= decor.getWidth() - safe.right);
                        assertTrue(button.getHeight() >= 48 * activity.getResources().getDisplayMetrics().density);
                    }
                });
                capture("player-landscape-font-200");
                player.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            }
        } finally { shell("settings put system font_scale " + scale); }
    }

    @Test public void playlistRenameKeepsMembersAndSelectionAfterRecreation() throws Exception {
        MusicLibraryRepository repository = MusicLibraryRepository.get(context);
        String original = "Playlist " + System.nanoTime(), renamed = "Renamed playlist";
        AtomicReference<Long> id = new AtomicReference<>();
        repository.createPlaylist(original, id::set); await(() -> id.get() != null);
        MusicTrackEntity member = track(new File(context.getCacheDir(), "playlist-native-test.wav"), "Saved member", null);
        AtomicReference<Boolean> added = new AtomicReference<>();
        repository.addToPlaylist(id.get(), member, added::set); await(() -> Boolean.TRUE.equals(added.get()));
        Intent intent = com.chao.peakmusic.activity.MusicLibraryActivity.intent(context,
                com.chao.peakmusic.activity.MusicLibraryActivity.MODE_PLAYLISTS);
        try (ActivityScenario<com.chao.peakmusic.activity.MusicLibraryActivity> library = ActivityScenario.launch(intent)) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            await(() -> {
                library.onActivity(activity -> {
                    androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.library_list);
                    ready.set(list.getAdapter() != null && list.getAdapter().getItemCount() > 0);
                });
                return ready.get();
            });
            onView(withText(original)).perform(click());
            androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(context);
            onView(withText(R.string.rename_playlist)).perform(click());
            onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.EditText.class))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                    .perform(androidx.test.espresso.action.ViewActions.replaceText(renamed));
            onView(withText(R.string.save)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            await(() -> {
                library.onActivity(activity -> ready.set(renamed.contentEquals(activity.getTitle())));
                return ready.get();
            });
            library.recreate();
            await(() -> {
                library.onActivity(activity -> {
                    androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.library_list);
                    ready.set(renamed.contentEquals(activity.getTitle())
                            && list.getAdapter() instanceof com.chao.peakmusic.adapter.MusicLibraryAdapter
                            && list.getAdapter().getItemCount() == 1);
                });
                return ready.get();
            });
            capture("playlist-renamed");
        } finally { repository.deletePlaylist(id.get(), null); }
    }

    @Test public void editableQueuePreservesPausedTrackAndClearsWithoutReturningAudio() throws Exception {
        File a = silentWave("queue-a.wav"), b = silentWave("queue-b.wav"), c = silentWave("queue-c.wav");
        try (ActivityScenario<MusicPlayActivity> player = ActivityScenario.launch(MusicPlayActivity.class)) {
            ArrayList<MusicTrackEntity> tracks = new ArrayList<>();
            tracks.add(track(a, "Queue A", null)); tracks.add(track(b, "Queue B", null)); tracks.add(track(c, "Queue C", null));
            player.onActivity(activity -> com.chao.peakmusic.service.PlaybackStorage.get(activity).play(tracks, 1));
            await(() -> text(player, R.id.tv_music_name).equals("Queue B"));
            player.onActivity(activity -> {
                try {
                    java.lang.reflect.Field field = MusicPlayActivity.class.getDeclaredField("musicService"); field.setAccessible(true);
                    ((MusicAidlInterface) field.get(activity)).pause();
                } catch (Exception error) { throw new AssertionError(error); }
            });
            // The player exposes the queue in its toolbar, not only through a test-only intent.
            onView(withText(R.string.playback_queue)).perform(click());
            androidx.test.espresso.Espresso.pressBack();
            try (ActivityScenario<com.chao.peakmusic.activity.MusicQueueActivity> queue =
                         ActivityScenario.launch(com.chao.peakmusic.activity.MusicQueueActivity.class)) {
                await(() -> queueNames(queue).size() == 3);
                queueAction("Queue B", R.string.queue_move_down);
                await(() -> queueNames(queue).equals(java.util.List.of("Queue A", "Queue C", "Queue B")));
                queueAction("Queue A", R.string.queue_play_next);
                await(() -> queueNames(queue).equals(java.util.List.of("Queue C", "Queue B", "Queue A")));
                queueAction("Queue B", R.string.queue_remove);
                await(() -> queueNames(queue).equals(java.util.List.of("Queue C", "Queue A")));
                queue.recreate();
                await(() -> queueNames(queue).equals(java.util.List.of("Queue C", "Queue A")));
                queue.onActivity(activity -> {
                    try {
                        MusicAidlInterface service = queueService(activity);
                        assertEquals(1, service.getCurrentIndex()); assertFalse(service.isPlay());
                        assertEquals("Queue A", service.getMusicName());
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                capture("editable-queue");
                androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(context);
                onView(withText(R.string.queue_clear)).perform(click());
                onView(withText(R.string.cancel)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                assertEquals(2, queueNames(queue).size());
                androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(context);
                onView(withText(R.string.queue_clear)).perform(click());
                onView(withText(R.string.clear)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                await(() -> queueNames(queue).isEmpty());
                queue.onActivity(activity -> {
                    try {
                        MusicAidlInterface service = queueService(activity);
                        assertEquals(-1, service.getCurrentIndex()); assertFalse(service.isPlay());
                        assertEquals(0, service.getDuration());
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                capture("queue-cleared");
            }
        } finally {
            context.stopService(new Intent(context, MusicService.class));
            a.delete(); b.delete(); c.delete();
        }
    }

    private void queueAction(String name, int action) {
        onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(
                context.getString(R.string.queue_item_actions_for, name))).perform(click());
        onView(withText(action)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
    }

    private MusicAidlInterface queueService(com.chao.peakmusic.activity.MusicQueueActivity activity) throws Exception {
        java.lang.reflect.Field field = activity.getClass().getDeclaredField("service"); field.setAccessible(true);
        return (MusicAidlInterface) field.get(activity);
    }

    private java.util.List<String> queueNames(ActivityScenario<com.chao.peakmusic.activity.MusicQueueActivity> queue) {
        AtomicReference<java.util.List<String>> names = new AtomicReference<>(java.util.Collections.emptyList());
        queue.onActivity(activity -> {
            try {
                MusicAidlInterface service = queueService(activity);
                if (service != null) names.set(service.getQueuePage(0).getStringArrayList("names"));
            } catch (Exception error) { throw new AssertionError(error); }
        });
        return names.get();
    }

    private void calibrate(ActivityScenario<MusicPlayActivity> player, int action) {
        player.onActivity(activity -> activity.findViewById(R.id.lyrics_calibrate).performClick());
        onView(withText(action)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
        androidx.test.espresso.Espresso.pressBack();
    }
    private String text(ActivityScenario<MusicPlayActivity> player, int id) {
        AtomicReference<String> text = new AtomicReference<>("");
        player.onActivity(activity -> { TextView view = activity.findViewById(id); if (view != null) text.set(view.getText().toString()); });
        return text.get();
    }
    private boolean enabled(ActivityScenario<MusicPlayActivity> player, int id) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        player.onActivity(activity -> result.set(activity.findViewById(id).isEnabled())); return result.get();
    }
    private void await(Check check) {
        long end = SystemClock.elapsedRealtime() + 10000;
        while (!check.done() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50);
        assertTrue("Expected UI state before timeout", check.done());
    }
    private interface Check { boolean done(); }
    private MusicTrackEntity track(File file, String name, String lyric) {
        MusicTrackEntity track = new MusicTrackEntity(); track.source = android.net.Uri.fromFile(file).toString();
        track.name = name; track.artist = "Generated silence"; track.lyricsUrl = lyric; track.local = true; return track;
    }
    private File silentWave(String name) throws Exception {
        File file = new File(context.getCacheDir(), name);
        int bytes = 60 * 8000 * 2;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(bytes + 36).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(header.array()); out.write(new byte[bytes]); }
        return file;
    }
    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // UI hierarchy assertions can finish before SurfaceFlinger presents the updated frame.
        SystemClock.sleep(350);
        File dir = new File(context.getExternalFilesDir(null), "verification"); assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); }
        finally { bitmap.recycle(); }
    }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            byte[] bytes = new byte[1024]; while (input.read(bytes) != -1) { }
        }
    }
    private static class LyricServer implements Closeable {
        private final ServerSocket server; volatile boolean fail = true;
        LyricServer() throws Exception {
            server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            new Thread(() -> {
                while (!server.isClosed()) try (Socket client = server.accept()) {
                    client.setSoTimeout(3000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                    String request = reader.readLine(); if (request == null) continue;
                    String line; while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                    boolean absent = request.contains("b.lrc"); int status = absent ? 404 : fail ? 503 : 200;
                    byte[] body = "[00:00]Fixture line A".getBytes(StandardCharsets.UTF_8);
                    String headers = "HTTP/1.1 " + status + " Test\r\nContent-Type: text/plain\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
                    client.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII)); client.getOutputStream().write(body);
                } catch (Exception error) { if (server.isClosed()) return; }
            }, "lyric-fixture-http").start();
        }
        String url(String file) { return "http://127.0.0.1:" + server.getLocalPort() + "/" + file; }
        @Override public void close() throws java.io.IOException { server.close(); }
    }
}
