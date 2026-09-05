package com.chao.peakmusic.service;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.core.content.ContextCompat;

import com.chao.peakmusic.R;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serial disk boundary for queue handoff and playback persistence. No queue payload crosses Binder. */
public final class PlaybackStorage {
    public static final String EXTRA_QUEUE_ID = "queue_id";
    private static final String PENDING_ID = "pending_queue_id";
    private static volatile PlaybackStorage instance;
    private final Context context;
    private final File directory;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    // null means a fresh process; "" means the user cancelled all outstanding requests.
    private volatile String currentRequestId;

    PlaybackStorage(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(this.context.getFilesDir(), "playback_queue_requests");
    }

    public static PlaybackStorage get(Context context) {
        if (instance == null) synchronized (PlaybackStorage.class) {
            if (instance == null) instance = new PlaybackStorage(context);
        }
        return instance;
    }

    public void play(List<MusicTrackEntity> tracks, int position) { submit(tracks, position, false); }
    public void playNext(MusicTrackEntity track) { submit(java.util.Collections.singletonList(track), 0, true); }

    private void submit(List<MusicTrackEntity> tracks, int position, boolean next) {
        stage(tracks, position, next, (id, error) -> {
            if (error != null) { showFailure(); return; }
            if (!isCurrent(id)) return;
            try {
                ContextCompat.startForegroundService(context, intent(context, id));
            } catch (RuntimeException failure) {
                cancelPending();
                showFailure();
            }
        });
    }

    public static Intent intent(Context context, String id) {
        return new Intent(context, MusicService.class).setAction(MusicService.ACTION_PLAY_LIBRARY_QUEUE)
                .putExtra(EXTRA_QUEUE_ID, id);
    }

    /** Stage a complete private snapshot before announcing its ID to the service. */
    void stage(List<MusicTrackEntity> tracks, int selected, boolean next, StageCallback callback) {
        selected = Math.max(0, Math.min(selected, tracks.size() - 1));
        Request request = new Request();
        request.id = UUID.randomUUID().toString(); request.next = next;
        for (int i = 0; i < tracks.size(); i++) {
            MusicTrackEntity track = tracks.get(i);
            if (track == null || track.source == null || track.source.isEmpty()) continue;
            if (i == selected) request.position = request.tracks.size();
            request.tracks.add(copyMetadata(track));
        }
        if (request.tracks.isEmpty()) { callback.done(null, new IOException("Empty queue")); return; }
        currentRequestId = request.id;
        worker.execute(() -> {
            if (!isCurrent(request.id)) return;
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Queue directory");
                pruneOldRequests();
                writeRequest(request);
                if (!isCurrent(request.id)) { deleteRequest(request.id); return; }
                String replacedId = preferences().getString(PENDING_ID, null);
                if (!preferences().edit().putString(PENDING_ID, request.id).commit()) throw new IOException("Queue index");
                deleteRequest(replacedId);
                main.post(() -> { if (isCurrent(request.id)) callback.done(request.id, null); });
            } catch (IOException | RuntimeException error) {
                deleteRequest(request.id);
                main.post(() -> { if (isCurrent(request.id)) callback.done(null, error); });
            }
        });
    }

    boolean isCurrent(String id) { return id != null && (currentRequestId == null || id.equals(currentRequestId)); }

    void cancelPending() {
        currentRequestId = "";
        worker.execute(() -> {
            String id = preferences().getString(PENDING_ID, null);
            if (preferences().edit().remove(PENDING_ID).commit()) deleteRequest(id);
        });
    }

    void load(String explicitId, boolean includeState, LoadCallback callback) {
        worker.execute(() -> {
            State state = new State();
            state.queue = new ArrayList<>();
            Request request = null;
            Exception failure = null;
            if (includeState) {
                try { state = readState(); }
                catch (RuntimeException error) { failure = error; }
            }
            String pending = preferences().getString(PENDING_ID, null);
            String id = explicitId != null ? explicitId : pending;
            if (id != null && isCurrent(id)) {
                try {
                    requestFile(id); // Validate even when an unknown/stale ID has no pending entry.
                    if (id.equals(pending)) request = readRequest(id);
                }
                catch (IOException | RuntimeException error) { failure = error; }
            }
            Request loaded = request; State saved = state; Exception error = failure;
            main.post(() -> callback.done(saved, loaded, error));
        });
    }

    void save(State state, String appliedRequestId, SaveCallback callback) {
        worker.execute(() -> {
            boolean success;
            try {
                SharedPreferences preferences = preferences();
                SharedPreferences.Editor editor = preferences.edit();
                if (state.queue != null) {
                    editor.putString("active_queue", PlaybackQueueCodec.encode(state.queue));
                    editor.remove("local_queue").remove("online_queue");
                }
                editor.putString("current_key", state.key).putString("current_source", state.source).putInt("index", state.index)
                        .putInt("position", state.position).putInt("mode", state.mode)
                        .putBoolean("active_local", state.local).putBoolean("playing", state.playing);
                boolean consumes = appliedRequestId != null && appliedRequestId.equals(preferences.getString(PENDING_ID, null));
                if (consumes) editor.remove(PENDING_ID);
                success = editor.commit();
                // A crash before this commit leaves the staged queue available for startup recovery.
                if (success && consumes) deleteRequest(appliedRequestId);
            } catch (RuntimeException error) { success = false; }
            boolean saved = success;
            main.post(() -> callback.done(saved));
        });
    }

    private State readState() {
        SharedPreferences preferences = preferences();
        State state = new State();
        state.local = preferences.getBoolean("active_local", false);
        state.queue = new ArrayList<>();
        for (PlaybackQueueCodec.Item item : PlaybackQueueCodec.decode(preferences.getString(
                preferences.contains("active_queue") ? "active_queue" : state.local ? "local_queue" : "online_queue", null))) {
            if (item != null && item.source != null && !item.source.isEmpty()) state.queue.add(item);
        }
        state.source = preferences.getString("current_source", null);
        state.key = preferences.getString("current_key", null);
        state.index = preferences.getInt("index", -1);
        state.position = preferences.getInt("position", 0);
        state.mode = preferences.getInt("mode", 0);
        state.playing = preferences.getBoolean("playing", false);
        return state;
    }

    private void writeRequest(Request request) throws IOException {
        AtomicFile file = requestFile(request.id);
        FileOutputStream out = file.startWrite();
        try {
            OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            gson.toJson(request, writer);
            writer.flush();
            file.finishWrite(out);
        } catch (RuntimeException | IOException error) { file.failWrite(out); throw error; }
    }

    private Request readRequest(String id) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(requestFile(id).openRead(), StandardCharsets.UTF_8)) {
            Request request = gson.fromJson(reader, Request.class);
            if (request == null || !id.equals(request.id) || request.tracks == null || request.tracks.isEmpty())
                throw new IOException("Invalid queue request");
            return request;
        }
    }

    private AtomicFile requestFile(String id) throws IOException {
        if (id == null || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
            throw new IOException("Invalid queue ID");
        return new AtomicFile(new File(directory, id + ".json"));
    }

    private void deleteRequest(String id) {
        if (id == null) return;
        try { requestFile(id).delete(); } catch (IOException ignored) { }
    }

    private void pruneOldRequests() {
        File[] files = directory.listFiles();
        if (files == null) return;
        String pending = preferences().getString(PENDING_ID, "");
        long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        for (File file : files) {
            if (!file.getName().startsWith(pending + ".") && file.lastModified() < cutoff) file.delete();
        }
    }

    private SharedPreferences preferences() { return context.getSharedPreferences("playback_state", Context.MODE_PRIVATE); }
    private void showFailure() { Toast.makeText(context, R.string.queue_load_failed, Toast.LENGTH_LONG).show(); }

    private static MusicTrackEntity copyMetadata(MusicTrackEntity track) {
        MusicTrackEntity copy = new MusicTrackEntity();
        copy.sourceId = track.sourceId; copy.sourceBaseUrl = track.sourceBaseUrl;
        copy.mediaId = track.mediaId; copy.playbackUrl = track.playbackUrl;
        copy.source = track.source; copy.name = track.name; copy.artist = track.artist;
        copy.imageUrl = track.imageUrl; copy.lyricsUrl = track.lyricsUrl; copy.local = track.local;
        copy.album = track.album; copy.filePath = track.filePath; copy.albumId = track.albumId;
        copy.durationMs = track.durationMs; copy.sizeBytes = track.sizeBytes;
        return copy;
    }

    @Keep static final class Request {
        String id;
        boolean next;
        int position;
        ArrayList<MusicTrackEntity> tracks = new ArrayList<>();
    }
    static final class State {
        List<PlaybackQueueCodec.Item> queue; // null on progress-only writes: reuse persisted queue.
        String source;
        String key;
        int index = -1, position, mode;
        boolean local, playing;
    }
    interface StageCallback { void done(String id, Exception error); }
    interface LoadCallback { void done(State state, Request request, Exception error); }
    interface SaveCallback { void done(boolean saved); }
}
