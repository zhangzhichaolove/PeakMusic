package com.chao.peakmusic;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager2.widget.ViewPager2;

import com.chao.peakmusic.activity.MusicSearchActivity;
import com.chao.peakmusic.adapter.MusicLibraryAdapter;
import com.chao.peakmusic.base.ApiAddressManager;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.google.android.material.tabs.TabLayout;

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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Run only on a disposable API 33+ emulator with audio/notification permissions revoked. */
@RunWith(AndroidJUnit4.class)
public class MusicExperienceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void homeDoesNotRequestPermissionsAndLocalPageExplainsAccess() throws Exception {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO));
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));
        String original = ApiAddressManager.getBaseUrl();
        try (MusicServer server = new MusicServer()) {
            ApiAddressManager.saveBaseUrl(server.baseUrl());
            try (ActivityScenario<MainActivity> home = ActivityScenario.launch(MainActivity.class)) {
                SystemClock.sleep(600);
                assertEquals(Lifecycle.State.RESUMED, home.getState());
                home.onActivity(activity -> ((ViewPager2) activity.findViewById(R.id.vp_content)).setCurrentItem(1, false));
                SystemClock.sleep(500);
                assertEquals(Lifecycle.State.RESUMED, home.getState());
                home.onActivity(activity -> {
                    assertEquals(activity.getString(R.string.local_grant_permission),
                            ((TextView) activity.findViewById(R.id.local_refresh)).getText().toString());
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.local_empty).getVisibility());
                    View play = activity.findViewById(R.id.iv_play);
                    int[] point = new int[2]; play.getLocationOnScreen(point);
                    androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(play);
                    assertNotNull(insets);
                    int bottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
                    assertTrue("Play button must remain above the navigation gesture area",
                            point[1] + play.getHeight() <= activity.getWindow().getDecorView().getHeight() - bottom);
                    assertTrue("Play target must be at least 48dp", play.getHeight()
                            >= 48 * activity.getResources().getDisplayMetrics().density);
                });
                capture("local-permission-explanation");
            }
        } finally { restoreApi(original); }
    }

    @Test public void searchUsesRealEndpointAndHandlesSupersessionRetryAndLocalScope() throws Exception {
        String original = ApiAddressManager.getBaseUrl();
        try (MusicServer server = new MusicServer()) {
            ApiAddressManager.saveBaseUrl(server.baseUrl());
            try (ActivityScenario<MusicSearchActivity> search = ActivityScenario.launch(MusicSearchActivity.class)) {
                SystemClock.sleep(500);
                assertEquals(0, server.searchCount.get()); // Blank input must not fetch the catalogue.
                type(search, "alpha");
                await(() -> server.alphaCount.get() == 1);
                type(search, "beta");
                await(() -> hasTrack(search, "Result beta"));
                SystemClock.sleep(800); // The superseded alpha response has now arrived.
                assertTrue(hasTrack(search, "Result beta"));
                capture("online-search-results");
                server.failBroken = true;
                type(search, "broken");
                await(() -> visible(search, R.id.search_retry));
                capture("search-retry");
                server.failBroken = false;
                search.onActivity(activity -> activity.findViewById(R.id.search_retry).performClick());
                await(() -> hasTrack(search, "Result broken"));
                int count = server.searchCount.get();
                search.onActivity(activity -> ((TabLayout) activity.findViewById(R.id.search_scopes)).getTabAt(1).select());
                SystemClock.sleep(500);
                assertEquals(count, server.searchCount.get());
                assertFalse(hasTrack(search, "Result broken"));
                search.recreate();
                SystemClock.sleep(500);
                assertEquals(count, server.searchCount.get()); // Scope survives recreation; no unexpected online request.
            }
        } finally { restoreApi(original); }
    }

    private void restoreApi(String original) {
        // The runner may exit immediately after teardown: apply() is not a durable restore.
        context.getSharedPreferences("api_address", Context.MODE_PRIVATE).edit().putString("music_api", original).commit();
    }

    private void type(ActivityScenario<MusicSearchActivity> scenario, String query) {
        scenario.onActivity(activity -> ((EditText) activity.findViewById(R.id.search_input)).setText(query));
    }

    private boolean hasTrack(ActivityScenario<MusicSearchActivity> scenario, String name) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        scenario.onActivity(activity -> {
            RecyclerView list = activity.findViewById(R.id.search_results);
            List<MusicTrackEntity> tracks = ((MusicLibraryAdapter) list.getAdapter()).getCurrentList();
            result.set(list.getVisibility() == View.VISIBLE && tracks.size() == 1 && name.equals(tracks.get(0).name));
        });
        return result.get();
    }

    private boolean visible(ActivityScenario<MusicSearchActivity> scenario, int id) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        scenario.onActivity(activity -> result.set(activity.findViewById(id).getVisibility() == View.VISIBLE));
        return result.get();
    }

    private void await(Check check) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (!check.done() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
        assertTrue("Expected UI/network state before timeout", check.done());
    }
    private interface Check { boolean done(); }

    private void capture(String name) throws Exception {
        File directory = new File(context.getExternalFilesDir(null), "verification");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { screenshot.recycle(); }
    }

    private static class MusicServer implements Closeable {
        final AtomicInteger searchCount = new AtomicInteger(), alphaCount = new AtomicInteger();
        final ServerSocket socket;
        volatile boolean failBroken;
        MusicServer() throws Exception {
            socket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
            new Thread(() -> {
                while (!socket.isClosed()) try {
                    Socket client = socket.accept();
                    new Thread(() -> serve(client), "music-fixture-response").start();
                } catch (Exception error) { if (socket.isClosed()) return; }
            }, "music-fixture-http").start();
        }
        String baseUrl() { return "http://127.0.0.1:" + socket.getLocalPort() + "/api/"; }
        void serve(Socket client) {
            try (Socket connection = client) {
                connection.setSoTimeout(3000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                String line = reader.readLine();
                if (line == null) return;
                String path = line.split(" ")[1];
                while ((line = reader.readLine()) != null && !line.isEmpty()) { }
                boolean endpoint = path.startsWith("/api/music/getMusicList?search=");
                String query = endpoint ? android.net.Uri.parse(path).getQueryParameter("search") : "";
                if (endpoint && !query.isEmpty()) searchCount.incrementAndGet();
                if (query.equals("alpha")) { alphaCount.incrementAndGet(); SystemClock.sleep(1200); }
                int code = !endpoint ? 404 : query.equals("broken") && failBroken ? 503 : 200;
                String records = query.isEmpty() ? "" : "{\"name\":\"Result " + query
                        + "\",\"singer\":\"Fixture artist\",\"mp3\":\"/media/sample.mp3\"}";
                byte[] body = ("{\"success\":true,\"result\":{\"records\":[" + records + "]}}").getBytes(StandardCharsets.UTF_8);
                String headers = "HTTP/1.1 " + code + " Test\r\nContent-Type: application/json\r\nContent-Length: "
                        + body.length + "\r\nConnection: close\r\n\r\n";
                connection.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                connection.getOutputStream().write(body);
                connection.getOutputStream().flush();
            } catch (Exception ignored) { /* Canceled searches close their sockets intentionally. */ }
        }
        @Override public void close() throws java.io.IOException { socket.close(); }
    }
}
