package com.chao.peakmusic;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.CheckBox;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.chao.peakmusic.activity.LibrarySelection;
import com.chao.peakmusic.activity.MusicLibraryActivity;
import com.chao.peakmusic.adapter.MusicLibraryAdapter;
import com.chao.peakmusic.data.*;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.service.MusicService;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Real Room/UI actions, two API identities sharing a URL, lifecycle and transaction failure. */
@RunWith(AndroidJUnit4.class)
public class LibraryBatchExperienceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final MusicDatabase database = MusicDatabase.get(context);
    private final MusicLibraryDao dao = database.libraryDao();

    @Test public void batchSelectionCopiesAndRemovesOnlyEstablishedIdentities() throws Exception {
        context.stopService(new Intent(context, MusicService.class));
        try (Fixture fixture = new Fixture(); ActivityScenario<MusicLibraryActivity> page = open(fixture)) {
            onView(withText(R.string.select_tracks)).perform(click());
            onView(withText(fixture.a.name)).perform(click());
            onView(allOf(withId(R.id.track_selected), withParent(hasDescendant(withText(fixture.b.name))))).perform(click());
            selected(page, Set.of(fixture.a.source, fixture.b.source));
            assertNotPlaying();
            capture("selected-two-sources");
            page.recreate();
            ready(page, 3);
            selected(page, Set.of(fixture.a.source, fixture.b.source));
            page.onActivity(activity -> assertEquals(fixture.name, activity.getTitle().toString()));
            onView(withText(R.string.select_all_tracks)).perform(click());
            selected(page, Set.of(fixture.a.source, fixture.b.source, fixture.local.source));
            onView(withText(R.string.deselect_all_tracks)).perform(click());
            selected(page, Set.of());
            onView(withText(fixture.a.name)).perform(click());
            onView(withText(fixture.b.name)).perform(click());
            // Metadata may change while the list is visible. The operation must not write its stale URL back.
            MusicTrackEntity newer = dao.findTrack(fixture.a.source);
            newer.playbackUrl = "https://media.example/new-signature.wav";
            dao.saveTrack(newer);
            overflow(R.string.favorite_selected);
            completed(page);
            assertTrue(dao.findTrack(fixture.a.source).favorite);
            assertTrue(dao.findTrack(fixture.b.source).favorite);
            assertFalse(dao.findTrack(fixture.local.source).favorite);
            assertEquals(newer.playbackUrl, dao.findTrack(fixture.a.source).getPlaybackUrl());

            onView(withText(R.string.select_tracks)).perform(click());
            onView(withText(R.string.select_all_tracks)).perform(click());
            overflow(R.string.add_selected_to_playlist);
            dialog(fixture.copyName);
            completed(page);
            assertEquals(3, dao.playlistTracks(fixture.copyId).size());
            onView(withText(R.string.select_tracks)).perform(click());
            onView(withText(fixture.a.name)).perform(click());
            onView(withText(fixture.b.name)).perform(click());
            overflow(R.string.remove_selected_from_playlist);
            dialog(context.getString(R.string.cancel));
            assertEquals(3, dao.playlistTracks(fixture.id).size());
            selected(page, Set.of(fixture.a.source, fixture.b.source));
            overflow(R.string.remove_selected_from_playlist);
            dialog(context.getString(R.string.remove_from_playlist));
            completed(page); ready(page, 1);
            assertEquals(fixture.local.source, dao.playlistTracks(fixture.id).get(0).source);
            assertEquals(3, dao.playlistTracks(fixture.copyId).size());
            assertTrue(dao.findTrack(fixture.a.source).favorite);
            assertTrue(dao.findTrack(fixture.b.source).favorite);
            assertTrue(fixture.audio.exists());
            assertEquals(4, dao.findTrack(fixture.a.source).playCount);
            assertEquals(1234, dao.findTrack(fixture.b.source).lastPlayedAt);
            androidx.test.espresso.Espresso.pressBack();
            await(() -> {
                AtomicBoolean root = new AtomicBoolean();
                page.onActivity(activity -> {
                    Toolbar toolbar = activity.findViewById(R.id.library_toolbar);
                    root.set(context.getString(R.string.playlists).contentEquals(toolbar.getTitle())
                            && !toolbar.getMenu().findItem(R.id.action_select_tracks).isVisible());
                }); return root.get();
            });
            onView(withText(fixture.copyName)).perform(click()); ready(page, 3);
            onView(withText(R.string.select_tracks)).perform(click());
            onView(withText(fixture.a.name)).perform(click());
            onView(withText(fixture.b.name)).perform(click());
            overflow(R.string.unfavorite_selected);
            dialog(context.getString(R.string.remove_favorite));
            completed(page);
            assertFalse(dao.findTrack(fixture.a.source).favorite);
            assertFalse(dao.findTrack(fixture.b.source).favorite);
            assertEquals(3, dao.playlistTracks(fixture.copyId).size());
            assertEquals(4, dao.findTrack(fixture.a.source).playCount);
            assertTrue(fixture.audio.exists());
            assertNotPlaying();
            capture("copied-playlist");
        }
    }

    @Test public void inFlightRecreationFailureUnlocksAndPreservesSelectionForRetry() throws Exception {
        try (Fixture fixture = new Fixture(); ActivityScenario<MusicLibraryActivity> page = open(fixture)) {
            onView(withText(R.string.select_tracks)).perform(click());
            onView(withText(fixture.a.name)).perform(click());
            onView(withText(fixture.b.name)).perform(click());
            database.getOpenHelper().getWritableDatabase().execSQL("CREATE TEMP TRIGGER batch_native_failure BEFORE UPDATE ON music_tracks "
                    + "WHEN NEW.source = '" + fixture.b.source + "' BEGIN SELECT RAISE(ABORT, 'batch fixture'); END");
            CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread blocker = new Thread(() -> {
                try { database.runInTransaction(() -> {
                    locked.countDown();
                    try { if (!release.await(20, TimeUnit.SECONDS)) throw new AssertionError("Test lock timed out"); }
                    catch (InterruptedException error) { throw new AssertionError(error); }
                }); } catch (Throwable error) { failure.set(error); }
            });
            blocker.start();
            try {
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                overflow(R.string.favorite_selected);
                await(() -> isBusy(page));
                page.recreate();
                page.onActivity(activity -> {
                    LibrarySelection model = new ViewModelProvider(activity).get(LibrarySelection.class);
                    assertTrue(model.busy);
                    assertEquals(Set.of(fixture.a.source, fixture.b.source), model.keys);
                    assertEquals(context.getString(R.string.batch_processing), ((Toolbar) activity.findViewById(R.id.library_toolbar)).getSubtitle());
                    activity.getOnBackPressedDispatcher().onBackPressed();
                    assertTrue(model.active);
                });
            } finally { release.countDown(); blocker.join(5000); }
            assertNull(failure.get());
            await(() -> !isBusy(page)); ready(page, 3);
            selected(page, Set.of(fixture.a.source, fixture.b.source));
            assertFalse(dao.findTrack(fixture.a.source).favorite);
            assertFalse(dao.findTrack(fixture.b.source).favorite);
            assertEquals(3, dao.playlistTracks(fixture.id).size());
            capture("failed-batch-selection-retained");
            database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER batch_native_failure");
            overflow(R.string.favorite_selected); completed(page);
            assertTrue(dao.findTrack(fixture.a.source).favorite);
            assertTrue(dao.findTrack(fixture.b.source).favorite);
        } finally { database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER IF EXISTS batch_native_failure"); }
    }

    private ActivityScenario<MusicLibraryActivity> open(Fixture fixture) {
        ActivityScenario<MusicLibraryActivity> page = ActivityScenario.launch(
                MusicLibraryActivity.intent(context, MusicLibraryActivity.MODE_PLAYLISTS));
        await(() -> {
            AtomicBoolean found = new AtomicBoolean();
            page.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.library_list);
                found.set(list.getAdapter() != null && list.getAdapter().getItemCount() >= 2);
            }); return found.get();
        });
        onView(withText(fixture.name)).perform(click()); ready(page, 3); return page;
    }
    private void ready(ActivityScenario<MusicLibraryActivity> page, int count) {
        await(() -> {
            AtomicBoolean ready = new AtomicBoolean();
            page.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.library_list);
                ready.set(list.getAdapter() instanceof MusicLibraryAdapter && list.getAdapter().getItemCount() == count);
            }); return ready.get();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void selected(ActivityScenario<MusicLibraryActivity> page, Set<String> keys) {
        page.onActivity(activity -> {
            assertEquals(keys, new ViewModelProvider(activity).get(LibrarySelection.class).keys);
            assertEquals(context.getString(R.string.selected_track_count, keys.size()),
                    ((Toolbar) activity.findViewById(R.id.library_toolbar)).getSubtitle());
            RecyclerView list = activity.findViewById(R.id.library_list);
            MusicLibraryAdapter adapter = (MusicLibraryAdapter) list.getAdapter();
            for (int i = 0; i < list.getChildCount(); i++) {
                View row = list.getChildAt(i);
                CheckBox box = row.findViewById(R.id.track_selected);
                assertEquals(View.VISIBLE, box.getVisibility());
                assertTrue(box.isEnabled());
                int position = list.getChildAdapterPosition(row);
                assertEquals(keys.contains(adapter.getCurrentList().get(position).source), box.isChecked());
            }
        });
    }
    private boolean isBusy(ActivityScenario<MusicLibraryActivity> page) {
        AtomicBoolean busy = new AtomicBoolean();
        page.onActivity(activity -> busy.set(new ViewModelProvider(activity).get(LibrarySelection.class).busy));
        return busy.get();
    }
    private void completed(ActivityScenario<MusicLibraryActivity> page) {
        await(() -> {
            AtomicBoolean done = new AtomicBoolean();
            page.onActivity(activity -> {
                LibrarySelection selection = new ViewModelProvider(activity).get(LibrarySelection.class);
                done.set(!selection.active && !selection.busy);
            }); return done.get();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void overflow(int title) {
        androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu(context);
        onView(withText(title)).perform(click());
    }
    private void dialog(String title) {
        onView(withText(title)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
    }
    private void assertNotPlaying() {
        android.app.ActivityManager manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(100))
            assertNotEquals("Selection must not start playback", MusicService.class.getName(), service.service.getClassName());
    }
    private void capture(String name) throws Exception {
        SystemClock.sleep(250);
        File dir = new File(context.getExternalFilesDir(null), "verification-batch"); assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        image.recycle();
    }
    private void await(Check check) {
        long end = SystemClock.elapsedRealtime() + 15000;
        while (!check.done() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50);
        assertTrue("Expected native library state before timeout", check.done());
    }
    private interface Check { boolean done(); }

    private final class Fixture implements AutoCloseable {
        final String token = Long.toString(System.nanoTime());
        final String name = "Batch source " + token, copyName = "Batch copy " + token;
        final File audio = new File(context.getCacheDir(), "batch-" + token + ".wav");
        final MusicTrackEntity a = api("a"), b = api("b"), local = new MusicTrackEntity();
        final long id, copyId;
        Fixture() throws Exception {
            try (FileOutputStream out = new FileOutputStream(audio)) { out.write(new byte[]{1, 2, 3}); }
            local.source = android.net.Uri.fromFile(audio).toString(); local.local = true;
            local.sourceId = MusicSource.LOCAL; local.name = "Local batch file";
            PlaylistEntity playlist = new PlaylistEntity(); playlist.name = name; playlist.createdAt = System.currentTimeMillis();
            id = dao.insertPlaylist(playlist); playlist.name = copyName; copyId = dao.insertPlaylist(playlist);
            int i = 0;
            for (MusicTrackEntity track : new MusicTrackEntity[]{a, b, local}) {
                track.playCount = 4; track.lastPlayedAt = 1234; dao.saveTrack(track);
                PlaylistTrackEntity member = new PlaylistTrackEntity(); member.playlistId = id;
                member.source = track.source; member.addedAt = 100 - i++; dao.addPlaylistTrack(member);
            }
        }
        private MusicTrackEntity api(String source) {
            MusicModel model = new MusicModel(); model.setId(token); model.setName("Batch " + source.toUpperCase());
            model.setSinger("Same URL, separate source"); model.setMp3("https://media.example/shared.wav");
            model.bindApiSource("https://" + source + ".example/api/"); return MusicTrackEntity.from(model);
        }
        @Override public void close() {
            dao.deletePlaylist(id); dao.deletePlaylist(copyId);
            for (MusicTrackEntity track : new MusicTrackEntity[]{a, b, local})
                database.getOpenHelper().getWritableDatabase().execSQL("DELETE FROM music_tracks WHERE source = ?", new Object[]{track.source});
            audio.delete();
        }
    }
}
