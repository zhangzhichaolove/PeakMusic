package com.chao.peakmusic.lyrics;

import static org.junit.Assert.*;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class LyricsLoaderLifecycleTest {
    @Test public void newTrackAndCancellationIgnoreLateNetworkCallbacks() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            if (chain.request().url().encodedPath().equals("/a")) {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                finished.countDown();
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create("[00:00]" + chain.request().url().encodedPath(), MediaType.get("text/plain"))).build();
        }).build();
        LyricsLoader loader = new LyricsLoader(client);
        List<String> states = new ArrayList<>();
        LyricsLoader.Listener listener = (state, lines) -> states.add(state + (lines.isEmpty() ? "" : lines.get(0).text));
        try {
            loader.load("https://example.com/a", listener);
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            loader.load("https://example.com/b", listener);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!states.contains("CONTENT/b") && System.nanoTime() < deadline) {
                Thread.sleep(10); Shadows.shadowOf(Looper.getMainLooper()).idle();
            }
            assertTrue(states.contains("CONTENT/b"));
            loader.cancel(); release.countDown(); assertTrue(finished.await(3, TimeUnit.SECONDS));
            client.dispatcher().executorService().shutdown();
            assertTrue(client.dispatcher().executorService().awaitTermination(3, TimeUnit.SECONDS));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(java.util.Arrays.asList("LOADING", "LOADING", "CONTENT/b"), states);
        } finally { release.countDown(); loader.cancel(); client.dispatcher().executorService().shutdownNow(); }
    }

    @Test public void missingUrlIsEmptyButInvalidUrlIsRetryableError() {
        LyricsLoader loader = new LyricsLoader();
        List<LyricsLoader.State> states = new ArrayList<>();
        loader.load(null, (state, lines) -> states.add(state));
        loader.load("not a URL", (state, lines) -> states.add(state));
        assertEquals(java.util.Arrays.asList(LyricsLoader.State.EMPTY, LyricsLoader.State.LOADING, LyricsLoader.State.ERROR), states);
        loader.cancel();
    }
}
