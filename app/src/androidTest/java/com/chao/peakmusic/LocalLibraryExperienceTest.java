package com.chao.peakmusic;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager2.widget.ViewPager2;
import com.chao.peakmusic.adapter.LocalGroupAdapter;
import com.chao.peakmusic.adapter.LocalMusicAdapter;
import com.chao.peakmusic.base.ApiAddressManager;
import com.chao.peakmusic.data.MusicDatabase;
import com.chao.peakmusic.local.LocalLibraryIndex;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.service.MusicService;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class LocalLibraryExperienceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void realMediaStoreCategoriesRestoreRefreshAndPlayOnlyVisibleGroup() throws Exception {
        String original = ApiAddressManager.getBaseUrl();
        try (EmptyApi server = new EmptyApi(); LocalMediaFixture media = new LocalMediaFixture(context,
                InstrumentationRegistry.getInstrumentation().getContext())) {
            ApiAddressManager.saveBaseUrl(server.url());
            try (ActivityScenario<MainActivity> page = ActivityScenario.launch(MainActivity.class)) {
                try {
                    page.onActivity(activity -> ((ViewPager2) activity.findViewById(R.id.vp_content)).setCurrentItem(1, false));
                    await(() -> names(page).containsAll(List.of(media.aName, media.bName, media.cName, media.unknownName)));
                    chooseSection(page, 1); clickGroup(page, "Browse Artist A");
                    await(() -> Set.copyOf(names(page)).equals(Set.of(media.aName, media.bName)));
                    List<String> displayed = names(page);
                    onView(withText(media.bName)).perform(click());
                    await(() -> queue(page).getInt("total") == 2 && queue(page).getStringArrayList("names").equals(displayed));
                    await(() -> {
                        AtomicReference<Boolean> playing = new AtomicReference<>(false);
                        page.onActivity(activity -> {
                            try { playing.set(service(activity).isPlay() && service(activity).getDuration() >= 59000); }
                            catch (Exception error) { throw new AssertionError(error); }
                        }); return playing.get();
                    });
                    page.onActivity(activity -> { try { service(activity).pause(); } catch (Exception error) { throw new AssertionError(error); } });
                    capture("artist-group-playing");
                    page.onActivity(activity -> {
                        RecyclerView list = activity.findViewById(R.id.local_music_list);
                        for (int i = 0; i < list.getChildCount(); i++)
                            assertNotNull("Missing album art keeps a default cover", ((android.widget.ImageView)
                                    list.getChildAt(i).findViewById(R.id.iv_cover)).getDrawable());
                    });
                    page.recreate();
                    await(() -> Set.copyOf(names(page)).equals(Set.of(media.aName, media.bName)));
                    page.onActivity(activity -> {
                        assertEquals(1, ((Spinner) activity.findViewById(R.id.local_sections)).getSelectedItemPosition());
                        assertTrue(((TextView) activity.findViewById(R.id.local_group_back)).getText().toString().contains("Browse Artist A"));
                    });
                    String added = "browse-added-" + media.token + ".mp3";
                    media.add("a", "First/Disc", added);
                    // Exercise the actual MediaStore observer on the restored FragmentStateAdapter instance.
                    await(() -> Set.copyOf(names(page)).equals(Set.of(media.aName, media.bName, added)));
                    assertEquals(displayed, queue(page).getStringArrayList("names"));
                    media.delete(media.b);
                    await(() -> Set.copyOf(names(page)).equals(Set.of(media.aName, added)));
                    assertEquals(displayed, queue(page).getStringArrayList("names"));
                    androidx.test.espresso.Espresso.pressBack();
                    await(() -> groups(page).stream().anyMatch(group -> group.title.equals("Browse Artist A")));
                    capture("artist-groups");

                    chooseSection(page, 2); clickGroup(page, "Browse Album A");
                    await(() -> Set.copyOf(names(page)).equals(Set.of(media.aName, added)));
                    onView(withId(R.id.local_group_back)).perform(click());
                    chooseSection(page, 3);
                    await(() -> groups(page).stream().filter(group -> group.title.contains(media.token) && group.title.endsWith("/Disc")).count() == 2);
                    capture("distinct-folders");
                    clickGroup(page, "external_primary:/" + media.root + "Second/Disc");
                    await(() -> names(page).equals(List.of(media.cName)));
                    media.delete(media.c);
                    await(() -> {
                        AtomicReference<Boolean> empty = new AtomicReference<>(false);
                        page.onActivity(activity -> empty.set(activity.findViewById(R.id.local_empty).getVisibility() == View.VISIBLE
                                && ((TextView) activity.findViewById(R.id.local_empty)).getText().toString().equals(context.getString(R.string.local_group_empty))));
                        return empty.get();
                    });
                    onView(withId(R.id.local_group_back)).perform(click());
                    chooseSection(page, 1); clickGroup(page, context.getString(R.string.local_unknown_artist));
                    await(() -> names(page).contains(media.unknownName));
                    chooseSection(page, 0);
                    await(() -> names(page).containsAll(List.of(media.aName, added, media.unknownName)));
                    assertFalse(names(page).contains(media.bName)); assertFalse(names(page).contains(media.cName));
                    // Browsing, rescans and deletion did not replace or resume the paused playback queue.
                    page.onActivity(activity -> { try { assertFalse(service(activity).isPlay()); } catch (Exception error) { throw new AssertionError(error); } });
                } finally {
                    page.onActivity(activity -> {
                        try { MusicAidlInterface service = service(activity); if (service != null) service.clearQueue(service.getQueueVersion()); }
                        catch (Exception error) { throw new AssertionError(error); }
                    });
                }
            } finally {
                context.stopService(new Intent(context, MusicService.class));
                for (android.net.Uri uri : media.uris) MusicDatabase.get(context).getOpenHelper().getWritableDatabase()
                        .execSQL("DELETE FROM music_tracks WHERE source = ?", new Object[]{uri.toString()});
            }
        } finally { context.getSharedPreferences("api_address", Context.MODE_PRIVATE).edit().putString("music_api", original).commit(); }
    }

    private void chooseSection(ActivityScenario<MainActivity> page, int position) {
        onView(withId(R.id.local_sections)).perform(click());
        androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.anything()).atPosition(position).perform(click());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void clickGroup(ActivityScenario<MainActivity> page, String title) {
        await(() -> groups(page).stream().anyMatch(group -> LocalGroupAdapter.title(context, group).equals(title)));
        page.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.local_music_list);
            List<LocalLibraryIndex.Group> groups = ((LocalGroupAdapter) list.getAdapter()).getCurrentList();
            for (int i = 0; i < groups.size(); i++) if (LocalGroupAdapter.title(context, groups.get(i)).equals(title)) { list.scrollToPosition(i); break; }
        });
        onView(withText(title)).perform(click());
    }
    private List<String> names(ActivityScenario<MainActivity> page) {
        List<String> result = new ArrayList<>();
        page.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.local_music_list);
            if (list != null && list.getAdapter() instanceof LocalMusicAdapter)
                for (SongModel song : ((LocalMusicAdapter) list.getAdapter()).getCurrentList()) result.add(song.getSong());
        }); return result;
    }
    private List<LocalLibraryIndex.Group> groups(ActivityScenario<MainActivity> page) {
        List<LocalLibraryIndex.Group> result = new ArrayList<>();
        page.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.local_music_list);
            if (list != null && list.getAdapter() instanceof LocalGroupAdapter)
                result.addAll(((LocalGroupAdapter) list.getAdapter()).getCurrentList());
        }); return result;
    }
    private android.os.Bundle queue(ActivityScenario<MainActivity> page) {
        AtomicReference<android.os.Bundle> result = new AtomicReference<>(new android.os.Bundle());
        page.onActivity(activity -> { try { if (service(activity) != null) result.set(service(activity).getQueuePage(0)); }
            catch (Exception error) { throw new AssertionError(error); } }); return result.get();
    }
    private MusicAidlInterface service(MainActivity activity) throws Exception {
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("mService"); field.setAccessible(true);
        return (MusicAidlInterface) field.get(activity);
    }
    private void await(Check check) {
        long end = SystemClock.elapsedRealtime() + 20000;
        while (!check.done() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50);
        assertTrue("Expected native local browsing state before timeout", check.done());
    }
    private interface Check { boolean done(); }
    private void capture(String name) throws Exception {
        SystemClock.sleep(300);
        File dir = new File(context.getExternalFilesDir(null), "verification-local"); assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
        screenshot.recycle();
    }
    private static final class EmptyApi implements AutoCloseable {
        final ServerSocket server;
        EmptyApi() throws Exception {
            server = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            new Thread(() -> {
                while (!server.isClosed()) try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                    String line; do { line = in.readLine(); } while (line != null && !line.isEmpty());
                    byte[] body = "{\"success\":true,\"result\":{\"records\":[]}}".getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(body);
                } catch (Exception error) { if (server.isClosed()) return; }
            }, "local-browser-empty-api").start();
        }
        String url() { return "http://127.0.0.1:" + server.getLocalPort() + "/"; }
        @Override public void close() throws Exception { server.close(); }
    }
}
