package com.chao.peakmusic;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chao.peakmusic.activity.MusicLibraryActivity;
import com.chao.peakmusic.activity.MusicPlayActivity;
import com.chao.peakmusic.adapter.MusicLibraryAdapter;
import com.chao.peakmusic.base.ApiAddressManager;
import com.chao.peakmusic.base.ApiUrl;
import com.chao.peakmusic.base.ServiceFactory;
import com.chao.peakmusic.data.MusicLibraryRepository;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.lyrics.LyricOffsetStore;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.service.PlaybackStorage;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real Retrofit -> stable Room identity -> selected-track IPC -> native WAV and lyric HTTP. */
@RunWith(AndroidJUnit4.class)
public class MusicSourceExperienceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void sourceSwitchSharedUrlAndRotatedUrlKeepFavoritesLyricsAndQueueIdentity() throws Exception {
        String previous = ApiAddressManager.getBaseUrl();
        Disposable request = null;
        try (Server server = new Server()) {
            assertTrue(ApiAddressManager.saveBaseUrl(server.url("a/")));
            AtomicReference<MusicModel> a = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            CountDownLatch delivered = new CountDownLatch(1);
            request = ServiceFactory.getInstance().createService(ApiUrl.class).getMusicList("")
                    .subscribeOn(Schedulers.io()).subscribe(body -> {
                        a.set(body.getResult().getRecords().get(0)); delivered.countDown();
                    }, error -> { failure.set(error); delivered.countDown(); });
            assertTrue(server.aEntered.await(10, TimeUnit.SECONDS));
            assertTrue(ApiAddressManager.saveBaseUrl(server.url("b/")));
            server.aRelease.countDown();
            assertTrue(delivered.await(10, TimeUnit.SECONDS)); assertNull(failure.get());
            MusicModel b = ServiceFactory.getInstance().createService(ApiUrl.class).getMusicList("")
                    .blockingFirst().getResult().getRecords().get(0);
            MusicTrackEntity first = MusicTrackEntity.from(a.get()), second = MusicTrackEntity.from(b);
            assertNotEquals(first.source, second.source);
            assertEquals(first.getPlaybackUrl(), second.getPlaybackUrl());
            assertEquals(server.url("a/lyrics.lrc"), first.lyricsUrl);
            assertEquals(server.url("b/lyrics.lrc"), second.lyricsUrl);
            MusicLibraryRepository library = MusicLibraryRepository.get(context);
            library.toggleFavorite(first, null); library.toggleFavorite(second, null);
            AtomicReference<Long> playlist = new AtomicReference<>(); CountDownLatch created = new CountDownLatch(1);
            library.createPlaylist("Source fixture " + System.nanoTime(), id -> { playlist.set(id); created.countDown(); });
            assertTrue(created.await(10, TimeUnit.SECONDS));
            library.addToPlaylist(playlist.get(), first, null); library.addToPlaylist(playlist.get(), second, null);
            LyricOffsetStore offsets = new LyricOffsetStore(context);
            offsets.set(first.source, 1000); offsets.set(second.source, -500);
            assertTrue(saved(library, first.source).favorite);
            try (ActivityScenario<MusicLibraryActivity> page = ActivityScenario.launch(
                    MusicLibraryActivity.intent(context, MusicLibraryActivity.MODE_FAVORITES))) {
                long end = SystemClock.elapsedRealtime() + 10000;
                AtomicBoolean shown = new AtomicBoolean();
                do {
                    page.onActivity(activity -> {
                        MusicLibraryAdapter adapter = (MusicLibraryAdapter) ((RecyclerView) activity.findViewById(R.id.library_list)).getAdapter();
                        if (adapter == null) return;
                        int count = 0;
                        for (MusicTrackEntity track : adapter.getCurrentList()) if (track.source.equals(first.source) || track.source.equals(second.source)) count++;
                        shown.set(count == 2);
                    });
                    if (shown.get()) break;
                    SystemClock.sleep(50);
                } while (SystemClock.elapsedRealtime() < end);
                assertTrue(shown.get()); SystemClock.sleep(250); capture("source-favorites");
            }
            MusicTrackEntity refreshed;
            try (ActivityScenario<MusicPlayActivity> player = ActivityScenario.launch(MusicPlayActivity.class)) {
                player.onActivity(activity -> PlaybackStorage.get(activity).play(List.of(first, second), 0));
                await(player, activity -> text(activity, R.id.tv_lyric_line).equals("Source A lyric") && binder(activity).isPlay());
                player.onActivity(activity -> {
                    assertEquals(first.source, ((MusicTrackEntity) field(activity, "currentTrack")).source);
                    assertTrue(text(activity, R.id.lyric_offset_value).contains("1.0"));
                    activity.findViewById(R.id.playback_next).performClick();
                });
                await(player, activity -> text(activity, R.id.tv_lyric_line).equals("Source B lyric") && binder(activity).isPlay());
                player.onActivity(activity -> {
                    assertEquals(second.source, ((MusicTrackEntity) field(activity, "currentTrack")).source);
                    assertTrue(text(activity, R.id.lyric_offset_value).contains("-0.5"));
                });
                // Same API/ID, a new signed URL: no new favorite, playlist member or lyric key.
                a.get().setMp3("/shared.wav?token=new");
                refreshed = MusicTrackEntity.from(a.get());
                library.saveMetadata(refreshed);
                assertTrue(saved(library, first.source).favorite);
                assertEquals(refreshed.getPlaybackUrl(), saved(library, first.source).getPlaybackUrl());
                player.onActivity(activity -> PlaybackStorage.get(activity).play(List.of(refreshed, second), 0));
                await(player, activity -> text(activity, R.id.tv_lyric_line).equals("Source A lyric") && binder(activity).isPlay());
                player.onActivity(activity -> {
                    try {
                    assertTrue(binder(activity).getDuration() >= 30000);
                    assertTrue(text(activity, R.id.lyric_offset_value).contains("1.0"));
                    binder(activity).pause(); binder(activity).seekTo(12000);
                    } catch (android.os.RemoteException error) { throw new AssertionError(error); }
                });
                capture("source-a-rotated-url");
            }
            // A stopped service is rebuilt from its persisted mixed-source queue, not the API list.
            context.stopService(new Intent(context, MusicService.class));
            SystemClock.sleep(400);
            try (ActivityScenario<MusicPlayActivity> restored = ActivityScenario.launch(MusicPlayActivity.class)) {
                await(restored, activity -> text(activity, R.id.tv_lyric_line).equals("Source A lyric")
                        && binder(activity) != null && binder(activity).getDuration() >= 30000);
                restored.onActivity(activity -> {
                    try {
                    MusicTrackEntity track = (MusicTrackEntity) field(activity, "currentTrack");
                    assertEquals(first.source, track.source); assertEquals(refreshed.getPlaybackUrl(), track.getPlaybackUrl());
                    assertFalse(binder(activity).isPlay());
                    assertTrue(binder(activity).getCurrentPosition() >= 11000);
                    assertTrue(text(activity, R.id.lyric_offset_value).contains("1.0"));
                    } catch (android.os.RemoteException error) { throw new AssertionError(error); }
                });
                await(restored, activity -> text(activity, R.id.playback_total_time).equals("00:30")
                        && !text(activity, R.id.playback_current_time).equals("00:00"));
                capture("source-restored-paused");
            }
            AtomicReference<List<MusicTrackEntity>> members = new AtomicReference<>(); CountDownLatch loaded = new CountDownLatch(1);
            library.loadPlaylistTracks(playlist.get(), tracks -> { members.set(tracks); loaded.countDown(); });
            assertTrue(loaded.await(10, TimeUnit.SECONDS)); assertEquals(2, members.get().size());
            assertEquals(1000, offsets.get(refreshed.source)); assertEquals(-500, offsets.get(second.source));
            assertTrue(server.targets.contains("/shared.wav?token=new"));
            assertTrue(server.targets.contains("/a/lyrics.lrc")); assertTrue(server.targets.contains("/b/lyrics.lrc"));
        } finally {
            if (request != null) request.dispose();
            context.stopService(new Intent(context, MusicService.class));
            // The runner may terminate before apply() is flushed; teardown must be durable.
            assertTrue(context.getSharedPreferences("api_address", 0).edit().putString("music_api", previous).commit());
        }
    }

    private MusicTrackEntity saved(MusicLibraryRepository library, String key) throws Exception {
        AtomicReference<MusicTrackEntity> value = new AtomicReference<>(); CountDownLatch ready = new CountDownLatch(1);
        library.loadTrack(key, track -> { value.set(track); ready.countDown(); });
        assertTrue(ready.await(10, TimeUnit.SECONDS)); return value.get();
    }
    private Object field(Object owner, String name) {
        try { var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private MusicAidlInterface binder(MusicPlayActivity activity) { return (MusicAidlInterface) field(activity, "musicService"); }
    private String text(MusicPlayActivity activity, int id) { return ((TextView) activity.findViewById(id)).getText().toString(); }
    private interface Condition { boolean ready(MusicPlayActivity activity) throws Exception; }
    private void await(ActivityScenario<MusicPlayActivity> page, Condition condition) {
        long end = SystemClock.elapsedRealtime() + 15000; AtomicBoolean ready = new AtomicBoolean();
        do {
            page.onActivity(activity -> { try { ready.set(condition.ready(activity)); } catch (Exception error) { throw new AssertionError(error); } });
            if (ready.get()) return; SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < end);
        fail("Source playback condition timed out");
    }
    private void capture(String name) throws Exception {
        File dir = new File(context.getExternalFilesDir(null), "verification-sources"); assertTrue(dir.isDirectory() || dir.mkdirs());
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        finally { bitmap.recycle(); }
    }

    private static final class Server implements Closeable {
        final ServerSocket socket = new ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"));
        final CountDownLatch aEntered = new CountDownLatch(1), aRelease = new CountDownLatch(1);
        final java.util.Set<String> targets = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        final byte[] wave;
        Server() throws IOException {
            int size = 8000 * 2 * 30;
            ByteBuffer wav = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
            wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + size).put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
            wav.putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16);
            wav.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(size); wave = wav.array();
            new Thread(() -> {
                while (!socket.isClosed()) try (Socket client = socket.accept()) { serve(client); }
                catch (Exception ignored) { if (socket.isClosed()) return; }
            }, "music-source-fixture").start();
        }
        String url(String path) { return "http://127.0.0.1:" + socket.getLocalPort() + "/" + path; }
        void serve(Socket client) throws Exception {
            client.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            String line = in.readLine(); if (line == null) return;
            String target = line.split(" ")[1], path = target.split("\\?", 2)[0]; targets.add(target);
            String range = null;
            while ((line = in.readLine()) != null && !line.isEmpty()) if (line.toLowerCase(java.util.Locale.ROOT).startsWith("range:")) range = line.substring(6).trim();
            byte[] bytes; String mime;
            if (path.endsWith("/music/getMusicList")) {
                if (path.startsWith("/a/")) { aEntered.countDown(); if (!aRelease.await(10, TimeUnit.SECONDS)) return; }
                bytes = ("{\"success\":true,\"result\":{\"records\":[{\"id\":42,\"name\":\"Source "
                        + (path.startsWith("/a/") ? "A" : "B") + " fixture\",\"singer\":\"Synthetic\",\"mp3\":\"/shared.wav?token=old\",\"lrc\":\"lyrics.lrc\"}]}}").getBytes(StandardCharsets.UTF_8);
                mime = "application/json";
            } else if (path.endsWith("lyrics.lrc")) {
                bytes = ("[00:00.00]Source " + (path.startsWith("/a/") ? "A" : "B") + " lyric\n").getBytes(StandardCharsets.UTF_8); mime = "text/plain";
            } else if (path.equals("/shared.wav")) { bytes = wave; mime = "audio/wav"; }
            else {
                client.getOutputStream().write("HTTP/1.1 404 Missing\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                return;
            }
            int start = 0, end = bytes.length - 1;
            if (range != null && range.startsWith("bytes=")) {
                String[] bounds = range.substring(6).split("-", -1); start = Integer.parseInt(bounds[0]);
                if (!bounds[1].isEmpty()) end = Math.min(end, Integer.parseInt(bounds[1]));
            }
            boolean valid = start >= 0 && start <= end;
            String headers = "HTTP/1.1 " + (!valid ? "416 Range" : range == null ? "200 OK" : "206 Partial Content")
                    + "\r\nContent-Type: " + mime + "\r\nContent-Length: " + (valid ? end - start + 1 : 0)
                    + "\r\nConnection: close\r\nAccept-Ranges: bytes\r\n";
            if (range != null && valid) headers += "Content-Range: bytes " + start + "-" + end + "/" + bytes.length + "\r\n";
            OutputStream out = client.getOutputStream(); out.write((headers + "\r\n").getBytes(StandardCharsets.US_ASCII));
            if (valid) out.write(bytes, start, end - start + 1); out.flush();
        }
        @Override public void close() throws IOException { aRelease.countDown(); socket.close(); }
    }
}
