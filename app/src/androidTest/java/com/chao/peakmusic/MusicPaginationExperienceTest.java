package com.chao.peakmusic;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chao.peakmusic.activity.MusicSearchActivity;
import com.chao.peakmusic.adapter.MusicLibraryAdapter;
import com.chao.peakmusic.adapter.OnlineContentMusicAdapter;
import com.chao.peakmusic.base.ApiAddressManager;
import com.chao.peakmusic.utils.MusicDataUtils;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MusicPaginationExperienceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void catalogueLoadsAll21RetriesSecondPageAndPlaysAppendedSong() throws Exception {
        String original = ApiAddressManager.getBaseUrl();
        try (Server server = new Server()) {
            ApiAddressManager.saveBaseUrl(server.base()); server.failPage2 = true;
            try (ActivityScenario<MainActivity> page = ActivityScenario.launch(MainActivity.class)) {
                await(page, a -> catalogue(a).getItemCount() == 12);
                assertEquals(List.of(":1"), server.requests);
                page.onActivity(a -> a.findViewById(R.id.music_page_more).performClick());
                await(page, a -> ((TextView) a.findViewById(R.id.music_page_message)).getText().toString().contains("加载失败"));
                page.onActivity(a -> assertEquals(12, catalogue(a).getItemCount()));
                capture("catalogue-page2-retry");
                server.failPage2 = false;
                page.onActivity(a -> a.findViewById(R.id.music_page_more).performClick());
                await(page, a -> catalogue(a).getItemCount() == 21);
                assertEquals(List.of(":1", ":2", ":2"), server.requests);
                page.onActivity(a -> {
                    assertEquals(View.GONE, a.findViewById(R.id.music_page_more).getVisibility());
                    assertEquals(21, MusicDataUtils.getInstance().getMusicList().size());
                    ((RecyclerView) a.findViewById(R.id.rl_content)).scrollToPosition(20);
                });
                await(page, a -> ((RecyclerView) a.findViewById(R.id.rl_content)).findViewHolderForAdapterPosition(20) != null);
                capture("catalogue-all-21");
                page.onActivity(a -> ((RecyclerView) a.findViewById(R.id.rl_content)).findViewHolderForAdapterPosition(20).itemView.performClick());
                await(page, a -> service(a) != null && service(a).isPlay() && service(a).getCurrentIndex() == 20);
                page.onActivity(a -> {
                    try {
                        assertEquals(21, service(a).getQueuePage(0).getInt("total"));
                        assertEquals("Catalogue 21", service(a).getMusicName()); service(a).pause();
                    } catch (Exception error) { throw new AssertionError(error); }
                });
                assertEquals(3, server.requests.size());
            }
        } finally { restore(original); }
    }

    @Test public void searchPagesKeepQueryAndDropSupersededAppend() throws Exception {
        String original = ApiAddressManager.getBaseUrl();
        try (Server server = new Server()) {
            ApiAddressManager.saveBaseUrl(server.base());
            try (ActivityScenario<MusicSearchActivity> page = ActivityScenario.launch(MusicSearchActivity.class)) {
                page.onActivity(a -> ((EditText) a.findViewById(R.id.search_input)).setText("alpha"));
                await(page, a -> search(a).getItemCount() == 12);
                page.onActivity(a -> a.findViewById(R.id.music_page_more).performClick());
                assertTrue(server.alphaPage2.await(10, TimeUnit.SECONDS));
                page.onActivity(a -> ((EditText) a.findViewById(R.id.search_input)).setText("beta"));
                await(page, a -> search(a).getItemCount() == 12 && search(a).getCurrentList().get(0).name.equals("beta 1"));
                server.releaseAlpha.countDown(); SystemClock.sleep(250);
                page.onActivity(a -> a.findViewById(R.id.music_page_more).performClick());
                await(page, a -> search(a).getItemCount() == 21);
                page.onActivity(a -> {
                    assertTrue(search(a).getCurrentList().stream().allMatch(track -> track.name.startsWith("beta ")));
                    assertEquals(View.GONE, a.findViewById(R.id.music_page_more).getVisibility());
                });
                assertEquals(List.of("alpha:1", "alpha:2", "beta:1", "beta:2"), server.requests);
                capture("search-all-21");
            }
        } finally { restore(original); }
    }

    private static OnlineContentMusicAdapter catalogue(Activity a) { return (OnlineContentMusicAdapter) ((RecyclerView) a.findViewById(R.id.rl_content)).getAdapter(); }
    private static MusicLibraryAdapter search(Activity a) { return (MusicLibraryAdapter) ((RecyclerView) a.findViewById(R.id.search_results)).getAdapter(); }
    private static MusicAidlInterface service(MainActivity a) throws Exception {
        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("mService"); field.setAccessible(true); return (MusicAidlInterface) field.get(a);
    }
    private <A extends Activity> void await(ActivityScenario<A> page, Check<A> check) {
        AtomicBoolean ready = new AtomicBoolean(); long end = SystemClock.elapsedRealtime() + 12000;
        do {
            page.onActivity(a -> { try { ready.set(check.ready(a)); } catch (Exception ignored) { } });
            if (ready.get()) return; SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < end);
        fail("Expected pagination/selection state");
    }
    private interface Check<A> { boolean ready(A a) throws Exception; }
    private void restore(String original) { context.getSharedPreferences("api_address", 0).edit().putString("music_api", original).commit(); }
    private void capture(String name) throws Exception {
        SystemClock.sleep(250); File dir = new File(context.getExternalFilesDir(null), "verification-pagination"); dir.mkdirs();
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        image.recycle();
    }

    private static final class Server implements Closeable {
        final ServerSocket socket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        final List<String> requests = new CopyOnWriteArrayList<>();
        final CountDownLatch alphaPage2 = new CountDownLatch(1), releaseAlpha = new CountDownLatch(1);
        volatile boolean failPage2;
        Server() throws Exception {
            new Thread(() -> { while (!socket.isClosed()) try {
                Socket client = socket.accept(); new Thread(() -> serve(client), "pagination-response").start();
            } catch (IOException error) { if (socket.isClosed()) return; } }, "pagination-http").start();
        }
        String base() { return "http://127.0.0.1:" + socket.getLocalPort() + "/api/"; }
        void serve(Socket client) {
            try (Socket connection = client) {
                connection.setSoTimeout(5000);
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                String line = in.readLine(); if (line == null) return;
                Uri uri = Uri.parse(line.split(" ")[1]);
                while ((line = in.readLine()) != null && !line.isEmpty()) { }
                byte[] body; String mime; int code = 200;
                if (uri.getPath().endsWith("getMusicList")) {
                    String query = uri.getQueryParameter("search"), pageArg = uri.getQueryParameter("page");
                    if (query == null) query = "";
                    int page = pageArg == null ? 1 : Integer.parseInt(pageArg);
                    requests.add(query + ":" + (pageArg == null ? "MISSING" : pageArg));
                    if (query.equals("alpha") && page == 2) { alphaPage2.countDown(); releaseAlpha.await(10, TimeUnit.SECONDS); }
                    if (failPage2 && page == 2) code = 503;
                    StringBuilder rows = new StringBuilder();
                    for (int i = page == 1 ? 1 : 13; i <= (page == 1 ? 12 : 21); i++) {
                        if (rows.length() > 0) rows.append(',');
                        rows.append("{\"id\":\"").append(query).append(i).append("\",\"name\":\"")
                                .append(query.isEmpty() ? "Catalogue" : query).append(' ').append(i)
                                .append("\",\"singer\":\"Pagination fixture\",\"mp3\":\"song.wav\"}");
                    }
                    body = ("{\"success\":true,\"result\":{\"current\":" + page + ",\"pages\":2,\"size\":12,\"total\":21,\"records\":[" + rows + "]}}").getBytes(StandardCharsets.UTF_8);
                    mime = "application/json";
                } else if (uri.getPath().endsWith("song.wav")) {
                    ByteBuffer wav = ByteBuffer.allocate(44 + 16000 * 10).order(ByteOrder.LITTLE_ENDIAN);
                    wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(wav.capacity() - 8).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
                            .putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16)
                            .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(wav.capacity() - 44);
                    body = wav.array(); mime = "audio/wav";
                } else { code = 404; body = new byte[0]; mime = "text/plain"; }
                connection.getOutputStream().write(("HTTP/1.1 " + code + " Result\r\nContent-Type: " + mime + "\r\nContent-Length: " + body.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                connection.getOutputStream().write(body);
            } catch (Exception ignored) { /* Superseded Retrofit calls close their sockets. */ }
        }
        @Override public void close() throws IOException { releaseAlpha.countDown(); socket.close(); }
    }
}
