package com.chao.peakmusic.service;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.StrictMode;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.R;
import com.chao.peakmusic.activity.MusicQueueActivity;
import com.chao.peakmusic.data.MusicTrackEntity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Real Android service IPC and MediaPlayer, not merely serializing an in-process mock queue. */
@RunWith(AndroidJUnit4.class)
public class QueueTransferExperienceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void tenThousandTracksUseSmallIntentPersistAndPlayWithoutMainThreadDiskWork() throws Exception {
        boolean prepareRelease = "true".equals(InstrumentationRegistry.getArguments().getString("stage_for_release"));
        File audio = new File(context.getCacheDir(), "queue-bulk.wav");
        writeSilence(audio);
        ArrayList<MusicTrackEntity> tracks = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            MusicTrackEntity track = new MusicTrackEntity(); track.source = android.net.Uri.fromFile(audio).toString();
            track.name = "Bulk " + i; track.artist = "Generated silence"; track.local = true;
            track.album = "Native queue metadata " + "x".repeat(128); tracks.add(track);
        }
        PlaybackStorage storage = PlaybackStorage.get(context);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Integer> intentBytes = new AtomicReference<>();
        AtomicReference<Long> submitNanos = new AtomicReference<>();
        long started = SystemClock.elapsedRealtime();
        try (ActivityScenario<MusicQueueActivity> queue = ActivityScenario.launch(MusicQueueActivity.class)) {
            queue.onActivity(activity -> {
                StrictMode.ThreadPolicy old = StrictMode.getThreadPolicy();
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites()
                        .penaltyListener(Runnable::run, failure::set).build());
                try {
                    long start = android.os.Debug.threadCpuTimeNanos();
                    storage.stage(tracks, 9000, false, (id, error) -> {
                        if (error != null) { failure.set(error); return; }
                        Intent intent = PlaybackStorage.intent(activity, id);
                        Parcel parcel = Parcel.obtain();
                        try { intent.writeToParcel(parcel, 0); intentBytes.set(parcel.dataSize()); }
                        finally { parcel.recycle(); }
                        ContextCompat.startForegroundService(activity, intent);
                    });
                    submitNanos.set(android.os.Debug.threadCpuTimeNanos() - start);
                } finally { StrictMode.setThreadPolicy(old); }
            });
            await(() -> failure.get() != null || page(queue, 9000).getInt("total") == 10000);
            assertNull("Queue submission must not read/write disk on the UI thread", failure.get());
            assertTrue("Intent has only queue ID", intentBytes.get() < 1024);
            assertEquals("Bulk 9000", page(queue, 9000).getStringArrayList("names").get(0));
            assertEquals("Bulk 9999", page(queue, 9900).getStringArrayList("names").get(99));
            assertEquals(9000, page(queue, 9000).getInt("current"));
            AtomicReference<Boolean> playing = new AtomicReference<>(false);
            await(() -> {
                queue.onActivity(activity -> {
                    try { MusicAidlInterface service = service(activity); playing.set(service != null && service.isPlay() && service.getDuration() >= 59000); }
                    catch (Exception error) { throw new AssertionError(error); }
                }); return playing.get();
            });
            long readyMs = SystemClock.elapsedRealtime() - started;
            AtomicReference<PlaybackStorage.State> saved = new AtomicReference<>();
            storage.load(null, true, (state, request, error) -> { if (error != null) failure.set(error); saved.set(state); });
            await(() -> saved.get() != null);
            assertNull(failure.get()); assertEquals(10000, saved.get().queue.size()); assertEquals(9000, saved.get().index);
            assertEquals(tracks.get(9000).album, saved.get().queue.get(9000).metadata.album);
            androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(context);
            onView(withText(R.string.queue_show_current)).perform(click());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync(); SystemClock.sleep(350);
            File dir = new File(context.getExternalFilesDir(null), "verification"); assertTrue(dir.isDirectory() || dir.mkdirs());
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try (FileOutputStream out = new FileOutputStream(new File(dir, "queue-10000.png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
            screenshot.recycle();
            String metrics = "tracks=10000\nintent_bytes=" + intentBytes.get() + "\nsubmit_main_cpu_ms=" + submitNanos.get() / 1_000_000.0
                    + "\nqueue_and_audio_ready_ms=" + readyMs + "\nui_submission_disk_violations=0\n";
            try (FileOutputStream out = new FileOutputStream(new File(dir, "queue-performance.txt"))) { out.write(metrics.getBytes(StandardCharsets.UTF_8)); }
            if (prepareRelease) {
                String api = InstrumentationRegistry.getArguments().getString("release_api_url");
                assertNotNull(api);
                assertTrue(com.chao.peakmusic.base.ApiAddressManager.saveBaseUrl(api));
                com.chao.peakmusic.model.MusicModel model = new com.chao.peakmusic.model.MusicModel();
                model.setId("release-source"); model.setName("Release pending"); model.setSinger("Generated silence");
                model.setMp3("audio.wav"); model.setLrc("release.lrc"); model.bindApiSource(api);
                MusicTrackEntity sourced = MusicTrackEntity.from(model);
                tracks.set(9000, sourced);
                // Seed real Room data for the platform-only minified library regression.
                com.chao.peakmusic.data.MusicLibraryDao dao = com.chao.peakmusic.data.MusicDatabase.get(context).libraryDao();
                com.chao.peakmusic.data.PlaylistEntity playlist = new com.chao.peakmusic.data.PlaylistEntity();
                playlist.name = "Release batch source"; playlist.createdAt = System.currentTimeMillis();
                long playlistId = dao.insertPlaylist(playlist);
                playlist.name = "Release batch copy"; dao.insertPlaylist(playlist);
                MusicTrackEntity local = new MusicTrackEntity(); local.source = "release-batch-local";
                local.name = "Release local member"; local.local = true;
                local.sourceId = com.chao.peakmusic.data.MusicSource.LOCAL;
                local.playbackUrl = android.net.Uri.fromFile(audio).toString();
                for (MusicTrackEntity member : new MusicTrackEntity[]{sourced, local}) {
                    dao.saveTrack(member);
                    com.chao.peakmusic.data.PlaylistTrackEntity entry = new com.chao.peakmusic.data.PlaylistTrackEntity();
                    entry.playlistId = playlistId; entry.source = member.source; entry.addedAt = System.currentTimeMillis();
                    dao.addPlaylistTrack(entry);
                }
                new com.chao.peakmusic.lyrics.LyricOffsetStore(context).set(sourced.source, 1500);
                AtomicReference<String> pending = new AtomicReference<>();
                storage.stage(tracks, 9000, false, (id, error) -> { if (error != null) failure.set(error); pending.set(id); });
                await(() -> pending.get() != null || failure.get() != null);
                assertNull(failure.get());
            } else {
                queue.onActivity(activity -> { try { MusicAidlInterface service = service(activity); service.clearQueue(service.getQueueVersion()); }
                    catch (Exception error) { throw new AssertionError(error); } });
            }
        } finally {
            context.stopService(new Intent(context, MusicService.class));
            if (!prepareRelease) audio.delete();
        }
    }

    private android.os.Bundle page(ActivityScenario<MusicQueueActivity> queue, int offset) {
        AtomicReference<android.os.Bundle> page = new AtomicReference<>(new android.os.Bundle());
        queue.onActivity(activity -> { try { MusicAidlInterface service = service(activity); if (service != null) page.set(service.getQueuePage(offset)); }
            catch (Exception error) { throw new AssertionError(error); } });
        return page.get();
    }
    private MusicAidlInterface service(MusicQueueActivity activity) throws Exception {
        java.lang.reflect.Field field = MusicQueueActivity.class.getDeclaredField("service"); field.setAccessible(true); return (MusicAidlInterface) field.get(activity);
    }
    private void await(Check check) {
        long end = SystemClock.elapsedRealtime() + 20000;
        while (!check.done() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50);
        assertTrue("Expected queue/audio state before timeout", check.done());
    }
    private interface Check { boolean done(); }
    private void writeSilence(File file) throws Exception {
        int bytes = 60 * 8000 * 2;
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(bytes + 36).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(header.array()); out.write(new byte[bytes]); }
    }
}
