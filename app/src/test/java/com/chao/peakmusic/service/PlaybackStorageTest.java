package com.chao.peakmusic.service;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.Parcel;

import com.chao.peakmusic.data.MusicTrackEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class PlaybackStorageTest {
    private Context context;
    private PlaybackStorage storage;
    private final List<PlaybackStorage> opened = new ArrayList<>();

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("playback_state", Context.MODE_PRIVATE).edit().clear().commit();
        storage = open();
    }
    private PlaybackStorage open() {
        PlaybackStorage value = new PlaybackStorage(context); opened.add(value); return value;
    }
    @After public void tearDown() throws Exception {
        for (PlaybackStorage value : opened) { drain(value); executor(value).shutdown(); }
    }
    private ExecutorService executor(PlaybackStorage value) { return ReflectionHelpers.getField(value, "worker"); }
    private void drain(PlaybackStorage value) throws Exception {
        for (int i = 0; i < 3; i++) {
            executor(value).submit(() -> {}).get(15, TimeUnit.SECONDS);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }
    private MusicTrackEntity track(String source) {
        MusicTrackEntity track = new MusicTrackEntity(); track.source = source;
        track.name = "Track"; track.artist = "Artist"; track.imageUrl = "https://example.com/cover.jpg";
        track.lyricsUrl = "https://example.com/lyrics.lrc"; return track;
    }
    private String stage(List<MusicTrackEntity> tracks, int position) throws Exception {
        AtomicReference<String> id = new AtomicReference<>();
        storage.stage(tracks, position, false, (value, error) -> { assertNull(error); id.set(value); });
        drain(storage); assertNotNull(id.get()); return id.get();
    }

    @Test public void tenThousandTracksPersistAcrossStorageRecreationWithAnIdOnlyIntent() throws Exception {
        ArrayList<MusicTrackEntity> tracks = new ArrayList<>();
        for (int i = 0; i < 10000; i++) tracks.add(track("https://example.com/" + i + ".mp3"));
        String id = stage(tracks, 9000);
        Intent intent = PlaybackStorage.intent(context, id);
        assertEquals(java.util.Set.of(PlaybackStorage.EXTRA_QUEUE_ID), intent.getExtras().keySet());
        Parcel parcel = Parcel.obtain();
        try { intent.writeToParcel(parcel, 0); assertTrue(parcel.dataSize() < 1024); }
        finally { parcel.recycle(); }
        PlaybackStorage recreated = open();
        AtomicReference<PlaybackStorage.Request> loaded = new AtomicReference<>();
        recreated.load(null, true, (state, request, error) -> { assertNull(error); loaded.set(request); });
        drain(recreated);
        assertEquals(10000, loaded.get().tracks.size()); assertEquals(9000, loaded.get().position);
        assertEquals(tracks.get(9999).source, loaded.get().tracks.get(9999).source);
        assertEquals(tracks.get(9000).lyricsUrl, loaded.get().tracks.get(9000).lyricsUrl);
    }

    @Test public void stagedSnapshotIsIndependentOfListAndTrackMutations() throws Exception {
        MusicTrackEntity first = track("https://example.com/original.mp3");
        ArrayList<MusicTrackEntity> tracks = new ArrayList<>(List.of(first));
        AtomicReference<String> id = new AtomicReference<>();
        storage.stage(tracks, 0, false, (value, error) -> id.set(value));
        first.source = "changed"; first.lyricsUrl = "changed"; tracks.clear();
        drain(storage);
        storage.load(id.get(), false, (state, request, error) -> {
            assertNull(error); assertEquals("https://example.com/original.mp3", request.tracks.get(0).source);
            assertEquals("https://example.com/lyrics.lrc", request.tracks.get(0).lyricsUrl);
        });
        drain(storage);
    }

    @Test public void stagedMetadataRetainsIdentityAndMutableUrlWithoutApiGsonLosingClientFields() throws Exception {
        com.chao.peakmusic.model.MusicModel model = new com.chao.peakmusic.model.MusicModel();
        model.setId("42"); model.setMp3("song.mp3?token=new"); model.bindApiSource("https://example.com/a/");
        MusicTrackEntity track = MusicTrackEntity.from(model);
        String id = stage(List.of(track), 0);
        storage.load(id, false, (state, request, error) -> {
            assertNull(error);
            MusicTrackEntity copy = request.tracks.get(0);
            assertEquals(track.source, copy.source); assertEquals(track.sourceId, copy.sourceId);
            assertEquals("42", copy.mediaId); assertEquals(track.sourceBaseUrl, copy.sourceBaseUrl);
            assertEquals("https://example.com/a/song.mp3?token=new", copy.getPlaybackUrl());
        });
        drain(storage);
    }

    @Test public void onlyLatestRapidRequestIsDeliveredAndSupersededFilesAreRemoved() throws Exception {
        List<String> delivered = new ArrayList<>();
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        executor(storage).execute(() -> { try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        storage.stage(List.of(track("A")), 0, false, (id, error) -> delivered.add(id));
        storage.stage(List.of(track("B")), 0, false, (id, error) -> delivered.add(id));
        release.countDown(); drain(storage);
        assertEquals(1, delivered.size());
        String second = stage(List.of(track("C")), 0);
        assertFalse(new File(context.getFilesDir(), "playback_queue_requests/" + delivered.get(0) + ".json").exists());
        storage.load(second, false, (state, request, error) -> assertEquals("C", request.tracks.get(0).source));
        drain(storage);
    }

    @Test public void cancelPreventsQueuedDeliveryAndDurableStartupReplay() throws Exception {
        List<String> delivered = new ArrayList<>();
        storage.stage(List.of(track("A")), 0, false, (id, error) -> delivered.add(id));
        storage.cancelPending(); drain(storage);
        assertTrue(delivered.isEmpty());
        PlaybackStorage recreated = open();
        recreated.load(null, true, (state, request, error) -> { assertNull(error); assertNull(request); });
        drain(recreated);
    }

    @Test public void acknowledgementAndProgressOnlyWriteKeepQueueAndRemoveConsumedRequest() throws Exception {
        String id = stage(List.of(track("A")), 0);
        PlaybackStorage.State state = new PlaybackStorage.State();
        PlaybackQueueCodec.Item item = new PlaybackQueueCodec.Item("A", "Title", "Artist", false);
        item.metadata = track("A"); state.queue = List.of(item); state.source = "A"; state.index = 0;
        storage.save(state, id, saved -> assertTrue(saved)); drain(storage);
        assertFalse(new File(context.getFilesDir(), "playback_queue_requests/" + id + ".json").exists());
        PlaybackStorage.State progress = new PlaybackStorage.State(); progress.source = "A";
        progress.index = 0; progress.position = 12345; progress.mode = 2;
        storage.save(progress, id, saved -> assertTrue(saved)); drain(storage);
        storage.load(null, true, (saved, request, error) -> {
            assertNull(request); assertEquals(1, saved.queue.size()); assertEquals(12345, saved.position);
            assertEquals(2, saved.mode); assertEquals("https://example.com/lyrics.lrc", saved.queue.get(0).metadata.lyricsUrl);
        }); drain(storage);
    }

    @Test public void oldStateAcknowledgementCannotDeleteANewerPendingRequest() throws Exception {
        String old = stage(List.of(track("A")), 0);
        String current = stage(List.of(track("B")), 0);
        PlaybackStorage.State state = new PlaybackStorage.State(); state.queue = new ArrayList<>();
        storage.save(state, old, saved -> assertTrue(saved)); drain(storage);
        storage.load(null, true, (saved, request, error) -> assertEquals(current, request.id)); drain(storage);
    }

    @Test public void corruptAndInvalidIdsReportFailureWithoutDiscardingSavedState() throws Exception {
        String id = stage(List.of(track("A")), 0);
        context.getSharedPreferences("playback_state", Context.MODE_PRIVATE).edit().putInt("position", 321).commit();
        Files.write(new File(context.getFilesDir(), "playback_queue_requests/" + id + ".json").toPath(), "{broken".getBytes(StandardCharsets.UTF_8));
        storage.load(id, true, (state, request, error) -> { assertNotNull(error); assertNull(request); assertEquals(321, state.position); });
        drain(storage);
        PlaybackStorage fresh = open();
        fresh.load("../../private", true, (state, request, error) -> { assertNotNull(error); assertNull(request); }); drain(fresh);
    }

    @Test public void failedStagingDoesNotAnnounceAnIntentOrReplaceSavedQueue() throws Exception {
        File directory = new File(context.getFilesDir(), "playback_queue_requests");
        assertTrue(directory.createNewFile());
        AtomicReference<Exception> failure = new AtomicReference<>();
        storage.stage(List.of(track("A")), 0, false, (id, error) -> { assertNull(id); failure.set(error); });
        drain(storage); assertNotNull(failure.get());
        assertFalse(context.getSharedPreferences("playback_state", Context.MODE_PRIVATE).contains("pending_queue_id"));
    }
}
