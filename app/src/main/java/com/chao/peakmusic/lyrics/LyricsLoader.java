package com.chao.peakmusic.lyrics;

import android.os.Handler;
import android.os.Looper;
import com.chao.peakmusic.utils.LyricsParser;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Lifecycle-owned lyric request with distinct absent/error states and stale callback rejection. */
public final class LyricsLoader {
    public enum State { LOADING, CONTENT, EMPTY, ERROR }
    public interface Listener { void onResult(State state, List<LyricsParser.LyricLine> lines); }
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS).build();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;
    public LyricsLoader() { this(CLIENT); }
    LyricsLoader(OkHttpClient client) { this.client = client; }
    private Call current;
    private long generation;

    public void load(String url, Listener listener) {
        cancel();
        long version = generation;
        if (url == null || url.trim().isEmpty()) {
            listener.onResult(State.EMPTY, Collections.emptyList());
            return;
        }
        listener.onResult(State.LOADING, Collections.emptyList());
        try {
            current = client.newCall(new Request.Builder().url(url).build());
        } catch (IllegalArgumentException error) {
            listener.onResult(State.ERROR, Collections.emptyList());
            return;
        }
        current.enqueue(new Callback() {
            private void deliver(State state, List<LyricsParser.LyricLine> lines) {
                main.post(() -> {
                    if (version == generation) listener.onResult(state, lines);
                });
            }
            @Override public void onFailure(Call call, IOException error) { deliver(State.ERROR, Collections.emptyList()); }
            @Override public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    if (closeable.code() == 404 || closeable.code() == 204) {
                        deliver(State.EMPTY, Collections.emptyList());
                        return;
                    }
                    List<LyricsParser.LyricLine> lines = read(closeable);
                    deliver(lines.isEmpty() ? State.EMPTY : State.CONTENT, lines);
                } catch (IOException | RuntimeException error) { deliver(State.ERROR, Collections.emptyList()); }
            }
        });
    }

    static List<LyricsParser.LyricLine> read(Response response) throws IOException {
        if (!response.isSuccessful() || response.body() == null) throw new IOException("Lyric response failed");
        byte[] bytes = response.peekBody(1024 * 1024 + 1L).bytes();
        if (bytes.length > 1024 * 1024) throw new IOException("Lyric response too large");
        String text = LyricsParser.decode(bytes);
        String type = response.header("Content-Type", "").toLowerCase(java.util.Locale.ROOT);
        String prefix = text.trim().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("html") || type.contains("json") || prefix.startsWith("<html") || prefix.startsWith("<!doctype"))
            throw new IOException("Expected lyric text");
        return LyricsParser.parse(text);
    }

    public void cancel() {
        generation++;
        if (current != null) current.cancel();
        current = null;
    }
}
