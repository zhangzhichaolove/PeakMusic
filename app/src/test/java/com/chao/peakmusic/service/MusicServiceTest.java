package com.chao.peakmusic.service;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.AudioManager;
import android.os.Looper;
import android.support.v4.media.session.PlaybackStateCompat;

import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.data.MusicTrackEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class MusicServiceTest {
    private static final String SOURCE = "https://example.com/song.mp3";
    private ServiceController<MusicService> controller;
    private MusicService service;
    private MusicAidlInterface binder;

    @Before public void setUp() {
        ReflectionHelpers.setStaticField(PlaybackStorage.class, "instance", null);
        RuntimeEnvironment.getApplication().getSharedPreferences("playback_state", Context.MODE_PRIVATE)
                .edit().clear().commit();
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(SOURCE),
                new ShadowMediaPlayer.MediaInfo(180_000, 1000));
        createService();
    }

    private void createService() {
        controller = Robolectric.buildService(MusicService.class).create();
        service = controller.get();
        binder = MusicAidlInterface.Stub.asInterface(service.onBind(new Intent()));
        drainStorage();
    }

    @After public void tearDown() {
        if (controller != null) controller.destroy();
        drainStorage();
        java.util.concurrent.ExecutorService worker = ReflectionHelpers.getField(PlaybackStorage.get(service), "worker");
        worker.shutdown();
    }

    private void drainStorage() {
        try {
            java.util.concurrent.ExecutorService worker = ReflectionHelpers.getField(PlaybackStorage.get(service), "worker");
            for (int i = 0; i < 3; i++) {
                worker.submit(() -> { }).get(15, java.util.concurrent.TimeUnit.SECONDS);
                Shadows.shadowOf(Looper.getMainLooper()).idle();
            }
        } catch (Exception error) { throw new AssertionError(error); }
    }

    private void startQueue(ArrayList<MusicTrackEntity> tracks, int position) { stageAndStart(tracks, position, false); }
    private void stageAndStart(ArrayList<MusicTrackEntity> tracks, int position, boolean next) {
        PlaybackStorage.get(service).stage(tracks, position, next, (id, error) -> {
            if (error == null) service.onStartCommand(PlaybackStorage.intent(service, id), 0, 1);
        });
        drainStorage();
    }

    private void prepare() {
        ReflectionHelpers.callInstanceMethod(service, "prepareSource",
                ClassParameter.from(String.class, SOURCE),
                ClassParameter.from(boolean.class, true), ClassParameter.from(int.class, 0));
    }

    @Test public void pauseDuringPreparationStaysPausedWhenReady() throws Exception {
        prepare();
        assertEquals(PlaybackStateCompat.STATE_BUFFERING, binder.getPlaybackState());
        binder.pause();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertFalse(binder.isPlay());
        assertEquals(PlaybackStateCompat.STATE_PAUSED, binder.getPlaybackState());
    }

    @Test public void playAfterBufferingPauseRestoresPlayIntent() throws Exception {
        prepare();
        binder.pause();
        binder.play();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue(binder.isPlay());
    }

    @Test public void headphoneDisconnectDuringPreparationDoesNotAutoplay() throws Exception {
        prepare();
        service.sendBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertFalse(binder.isPlay());
    }

    @Test public void sleepTimerDuringPreparationDoesNotAutoplay() throws Exception {
        prepare();
        service.onStartCommand(new Intent(service, MusicService.class)
                .setAction(MusicService.ACTION_SET_SLEEP_TIMER)
                .putExtra(MusicService.EXTRA_SLEEP_DELAY, 100L), 0, 1);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertFalse(binder.isPlay());
    }

    @Test public void userPauseCancelsPendingAudioFocusResume() throws Exception {
        prepare();
        AudioManager.OnAudioFocusChangeListener listener = ReflectionHelpers.getField(service,
                "focusChangeListener");
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        binder.pause();
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertFalse(binder.isPlay());
    }

    @Test public void revokedFilePermissionBecomesARecoverableError() throws Exception {
        ShadowMediaPlayer.addException(DataSource.toDataSource(SOURCE), new SecurityException("revoked"));
        prepare();
        assertEquals(PlaybackStateCompat.STATE_ERROR, binder.getPlaybackState());
        assertFalse(binder.getPlaybackError().isEmpty());
    }

    @Test public void retryUsesCurrentSourceWithoutAnActivityCallback() throws Exception {
        prepare();
        MediaPlayer player = ReflectionHelpers.getField(service, "mediaPlayer");
        Shadows.shadowOf(player).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        assertEquals(PlaybackStateCompat.STATE_ERROR, binder.getPlaybackState());
        assertFalse(binder.getPlaybackError().isEmpty());
        binder.play();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue(binder.isPlay());
        assertEquals("", binder.getPlaybackError());
    }

    @Test public void mixedQueueRestoresAfterCrossingSourceTypes() throws Exception {
        ArrayList<MusicTrackEntity> tracks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            MusicTrackEntity track = new MusicTrackEntity();
            track.source = SOURCE + "?track=" + i;
            track.name = "Track " + i;
            track.local = i != 1;
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(track.source),
                    new ShadowMediaPlayer.MediaInfo(180_000, 1000));
            tracks.add(track);
        }
        startQueue(tracks, 0);
        binder.next();
        binder.pause();
        controller.destroy();
        controller = null;
        createService();
        List<?> restored = ReflectionHelpers.getField(service, "activeQueue");
        assertEquals(3, restored.size());
        assertEquals(1, binder.getCurrentIndex());
        assertEquals("Track 1", binder.getMusicName());
        assertFalse(binder.isPlay());
        binder.next();
        assertEquals(3, ((List<?>) ReflectionHelpers.getField(service, "activeQueue")).size());
        assertEquals("Track 2", binder.getMusicName());
    }

    @Test public void sameUrlDifferentSourceQueueRestoresByKeyAndCallbacksCarryCompleteMetadata() throws Exception {
        ArrayList<MusicTrackEntity> tracks = new ArrayList<>();
        for (String source : List.of("a", "b")) {
            com.chao.peakmusic.model.MusicModel model = new com.chao.peakmusic.model.MusicModel();
            model.setId("42"); model.setName(source); model.setMp3(SOURCE); model.setLrc("lyrics.lrc");
            model.bindApiSource("https://example.com/" + source + "/");
            tracks.add(MusicTrackEntity.from(model));
        }
        java.util.concurrent.atomic.AtomicReference<MusicTrackEntity> current = new java.util.concurrent.atomic.AtomicReference<>();
        binder.registerCallback(new com.chao.peakmusic.ActivityCall.Stub() {
            @Override public void call(boolean value) { }
            @Override public void pre() { }
            @Override public void next() { }
            @Override public void defaultPlay() { }
            @Override public void trackChanged(MusicTrackEntity track) { current.set(track); }
        });
        startQueue(tracks, 1);
        assertEquals(tracks.get(1).source, current.get().source);
        assertEquals(SOURCE, current.get().getPlaybackUrl());
        assertEquals("https://example.com/b/lyrics.lrc", current.get().lyricsUrl);
        assertEquals(SOURCE, ReflectionHelpers.getField(service, "currentSource"));
        binder.pause(); controller.destroy(); controller = null; drainStorage();
        // An invalid saved index must use the stable key, not select A merely because URLs match.
        service.getSharedPreferences("playback_state", 0).edit().putInt("index", -1).commit();
        createService();
        assertEquals(1, binder.getCurrentIndex()); assertEquals("b", binder.getMusicName());
        assertFalse(binder.isPlay());
    }

    private ArrayList<MusicTrackEntity> queue(int count) {
        ArrayList<MusicTrackEntity> tracks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MusicTrackEntity track = new MusicTrackEntity();
            track.source = SOURCE + "?queue=" + i; track.name = "Queue " + i; track.local = i % 2 == 0;
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(track.source),
                    new ShadowMediaPlayer.MediaInfo(180_000, 1000));
            tracks.add(track);
        }
        return tracks;
    }

    @Test public void reorderAndRemoveOtherTracksKeepCurrentAudioAndPosition() throws Exception {
        startQueue(queue(3), 1);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        binder.pause(); binder.seekTo(32000);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        MediaPlayer player = ReflectionHelpers.getField(service, "mediaPlayer");
        assertTrue(binder.moveQueueItem(1, 2, binder.getQueueVersion()));
        assertEquals(2, binder.getCurrentIndex());
        assertTrue(binder.removeQueueItem(0, binder.getQueueVersion()));
        assertEquals(1, binder.getCurrentIndex());
        assertEquals("Queue 1", binder.getMusicName());
        assertEquals(32000, binder.getCurrentPosition());
        assertSame(player, ReflectionHelpers.getField(service, "mediaPlayer"));
        assertEquals(PlaybackStateCompat.STATE_PAUSED, binder.getPlaybackState());
        controller.destroy(); controller = null; createService();
        assertEquals(List.of("Queue 2", "Queue 1"), binder.getQueuePage(0).getStringArrayList("names"));
        assertEquals(1, binder.getCurrentIndex());
    }

    @Test public void removeCurrentDuringPausedPreparationNeverAutoplaysReplacement() throws Exception {
        startQueue(queue(3), 1);
        binder.pause();
        assertTrue(binder.removeQueueItem(1, binder.getQueueVersion()));
        assertEquals("Queue 2", binder.getMusicName());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertFalse(binder.isPlay());
        assertEquals(PlaybackStateCompat.STATE_PAUSED, binder.getPlaybackState());
    }

    @Test public void removePlayingCurrentContinuesAtSuccessorAndLastRemovalStops() throws Exception {
        startQueue(queue(2), 0);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue(binder.isPlay());
        assertTrue(binder.removeQueueItem(0, binder.getQueueVersion()));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertEquals("Queue 1", binder.getMusicName());
        assertTrue(binder.isPlay());
        assertTrue(binder.removeQueueItem(0, binder.getQueueVersion()));
        assertFalse(binder.isPlay());
        assertEquals(-1, binder.getCurrentIndex());
        assertEquals(0, binder.getDuration());
        assertEquals(PlaybackStateCompat.STATE_STOPPED, binder.getPlaybackState());
    }

    @Test public void staleRowAndClearCommandsCannotMutateAChangedQueue() throws Exception {
        startQueue(queue(3), 0);
        long old = binder.getQueueVersion();
        binder.next();
        assertFalse(binder.removeQueueItem(0, old));
        assertFalse(binder.moveQueueItem(0, 1, old));
        assertFalse(binder.playQueueItem(0, old));
        assertFalse(binder.clearQueue(old));
        assertEquals(3, binder.getQueuePage(0).getInt("total"));
        assertEquals(1, binder.getCurrentIndex());
        assertFalse(binder.removeQueueItem(-1, binder.getQueueVersion()));
        assertFalse(binder.moveQueueItem(1, 3, binder.getQueueVersion()));
    }

    @Test public void clearDuringPreparationCancelsAudioAndSurvivesRestart() throws Exception {
        startQueue(queue(2), 0);
        assertTrue(binder.clearQueue(binder.getQueueVersion()));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));
        assertFalse(binder.isPlay());
        assertNull(ReflectionHelpers.getField(service, "currentSource"));
        controller.destroy(); controller = null; createService();
        binder.next(); binder.pre();
        assertEquals(0, binder.getQueuePage(0).getInt("total"));
        assertEquals(-1, binder.getCurrentIndex());
        assertFalse(binder.isPlay());
    }

    @Test public void nextPlayInsertsWithoutInterruptingAndKeepsDuplicateOccurrenceOnRestore() throws Exception {
        ArrayList<MusicTrackEntity> tracks = queue(2);
        startQueue(tracks, 0);
        binder.pause();
        stageAndStart(new ArrayList<>(List.of(tracks.get(0))), 0, true);
        assertEquals(List.of("Queue 0", "Queue 0", "Queue 1"), binder.getQueuePage(0).getStringArrayList("names"));
        assertEquals(0, binder.getCurrentIndex());
        assertFalse(binder.isPlay());
        assertTrue(binder.playQueueItem(1, binder.getQueueVersion())); binder.pause();
        controller.destroy(); controller = null; createService();
        assertEquals(1, binder.getCurrentIndex());
        assertTrue(binder.moveQueueItem(1, 2, binder.getQueueVersion()));
        assertEquals(2, binder.getCurrentIndex());
    }

    @Test public void nextPlayOnEmptyQueueStartsItAndEmptyReplacementIsIgnored() throws Exception {
        stageAndStart(queue(1), 0, true);
        startQueue(new ArrayList<>(), 0);
        assertEquals(1, binder.getQueuePage(0).getInt("total"));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertTrue(binder.isPlay());
    }

    @Test public void oldIndexOnlyQueueStillRestoresWithoutCurrentSourcePreference() throws Exception {
        startQueue(queue(2), 1);
        binder.pause();
        controller.destroy(); controller = null;
        drainStorage();
        android.content.SharedPreferences legacy = RuntimeEnvironment.getApplication()
                .getSharedPreferences("playback_state", Context.MODE_PRIVATE);
        legacy.edit().putString("online_queue", legacy.getString("active_queue", null))
                .putBoolean("active_local", false).remove("current_source").remove("active_queue").commit();
        createService();
        assertEquals(2, binder.getQueuePage(0).getInt("total"));
        assertEquals(1, binder.getCurrentIndex());
        assertEquals("Queue 1", binder.getMusicName());
        assertFalse(binder.isPlay());
    }

    @Test public void manualNextAtSequentialEndKeepsActualAndReportedPlaybackInSync() throws Exception {
        startQueue(queue(1), 0);
        binder.next();
        assertEquals(PlaybackStateCompat.STATE_BUFFERING, binder.getPlaybackState());
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        binder.next();
        assertTrue(binder.isPlay());
        assertEquals(PlaybackStateCompat.STATE_PLAYING, binder.getPlaybackState());
    }

    @Test public void tenThousandItemQueueReturnsBoundedPagesWithoutUrls() throws Exception {
        ArrayList<MusicTrackEntity> tracks = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            MusicTrackEntity track = new MusicTrackEntity(); track.source = SOURCE;
            track.name = "Title " + i + "x".repeat(500); track.artist = track.name; tracks.add(track);
        }
        startQueue(tracks, 0);
        android.os.Bundle page = binder.getQueuePage(100);
        assertEquals(10000, page.getInt("total"));
        assertEquals(100, page.getInt("offset"));
        assertEquals(100, page.getStringArrayList("names").size());
        assertEquals(256, page.getStringArrayList("names").get(0).length());
        assertEquals(9900, binder.getQueuePage(Integer.MAX_VALUE).getInt("offset"));
        android.os.Parcel parcel = android.os.Parcel.obtain();
        try {
            page.writeToParcel(parcel, 0);
            assertTrue("Bounded well below Binder's shared 1 MiB budget", parcel.dataSize() < 110000);
            assertFalse(page.containsKey("source"));
        } finally { parcel.recycle(); }
    }

    @Test public void pauseWhileInitialStateIsReadingDoesNotRestoreAutoplay() throws Exception {
        startQueue(queue(2), 1);
        controller.destroy(); controller = null; drainStorage();
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService worker = ReflectionHelpers.getField(PlaybackStorage.get(service), "worker");
        worker.execute(() -> { try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        controller = Robolectric.buildService(MusicService.class).create(); service = controller.get();
        binder = MusicAidlInterface.Stub.asInterface(service.onBind(new Intent()));
        binder.pause(); release.countDown(); drainStorage();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertEquals("Queue 1", binder.getMusicName());
        assertFalse(binder.isPlay());
    }

    @Test public void pauseCancelsAQueueAlreadyBeingLoaded() throws Exception {
        startQueue(queue(2), 0);
        PlaybackStorage.get(service).stage(queue(3), 2, false, (id, error) -> {
            service.onStartCommand(PlaybackStorage.intent(service, id), 0, 2);
            try { binder.pause(); } catch (Exception failure) { throw new AssertionError(failure); }
        });
        drainStorage();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertEquals("Queue 0", binder.getMusicName()); assertFalse(binder.isPlay());
        assertEquals(2, binder.getQueuePage(0).getInt("total"));
    }

    @Test public void pendingDurableQueueIsRecoveredIfIntentWasNotDeliveredBeforeRestart() throws Exception {
        controller.destroy(); controller = null; drainStorage();
        PlaybackStorage.get(service).stage(queue(3), 2, false, (id, error) -> assertNull(error));
        drainStorage(); createService();
        assertEquals(3, binder.getQueuePage(0).getInt("total"));
        assertEquals("Queue 2", binder.getMusicName());
        assertFalse(service.getSharedPreferences("playback_state", Context.MODE_PRIVATE).contains("pending_queue_id"));
    }

    @Test public void consumedIdRedeliveryCannotUndoLaterQueueEdits() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> request = new java.util.concurrent.atomic.AtomicReference<>();
        PlaybackStorage.get(service).stage(queue(3), 0, false, (id, error) -> {
            request.set(id); service.onStartCommand(PlaybackStorage.intent(service, id), 0, 1);
        }); drainStorage();
        binder.pause(); binder.removeQueueItem(2, binder.getQueueVersion()); drainStorage();
        service.onStartCommand(PlaybackStorage.intent(service, request.get()), 0, 2); drainStorage();
        assertEquals(2, binder.getQueuePage(0).getInt("total")); assertFalse(binder.isPlay());
    }

    @Test public void expiredTimerSuppressesPendingStartupButNotANewExplicitSelection() throws Exception {
        controller.destroy(); controller = null; drainStorage();
        PlaybackStorage.get(service).stage(queue(2), 0, false, (id, error) -> assertNull(error)); drainStorage();
        service.getSharedPreferences(MusicService.SLEEP_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putLong(MusicService.KEY_SLEEP_END, System.currentTimeMillis() - 1000).commit();
        createService();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertEquals("Queue 0", binder.getMusicName()); assertFalse(binder.isPlay());
        startQueue(queue(2), 1);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertEquals("Queue 1", binder.getMusicName()); assertTrue(binder.isPlay());
    }

    @Test public void cancelledIntentArrivingAfterPauseCannotEraseStartupPauseOverride() throws Exception {
        startQueue(queue(2), 0);
        controller.destroy(); controller = null; drainStorage();
        java.util.concurrent.atomic.AtomicReference<String> request = new java.util.concurrent.atomic.AtomicReference<>();
        PlaybackStorage.get(service).stage(queue(3), 2, false, (id, error) -> request.set(id)); drainStorage();
        controller = Robolectric.buildService(MusicService.class).create(); service = controller.get();
        binder = MusicAidlInterface.Stub.asInterface(service.onBind(new Intent()));
        binder.pause();
        service.onStartCommand(PlaybackStorage.intent(service, request.get()), 0, 1);
        drainStorage(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertEquals("Queue 0", binder.getMusicName()); assertFalse(binder.isPlay());
    }

    @Test public void clearBeforeStartupReadReturnsCannotResurrectSavedTracks() throws Exception {
        startQueue(queue(2), 0);
        controller.destroy(); controller = null; drainStorage();
        controller = Robolectric.buildService(MusicService.class).create(); service = controller.get();
        binder = MusicAidlInterface.Stub.asInterface(service.onBind(new Intent()));
        assertTrue(binder.clearQueue(binder.getQueueVersion()));
        drainStorage(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1100));
        assertEquals(0, binder.getQueuePage(0).getInt("total")); assertFalse(binder.isPlay());
        controller.destroy(); controller = null; createService();
        assertEquals(0, binder.getQueuePage(0).getInt("total"));
    }
}
