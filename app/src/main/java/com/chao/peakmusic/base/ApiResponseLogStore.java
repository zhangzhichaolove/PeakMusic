package com.chao.peakmusic.base;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import com.chao.peakmusic.BuildConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import okio.Okio;

/** Opt-in, bounded, sanitized diagnostics. Never replaces or consumes the application's response. */
public final class ApiResponseLogStore {
    static final long CAPTURE_BYTES = 1024 * 1024;
    static final long RECORD_BYTES = 2 * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_FILES = 20;
    private static final String DIRECTORY = "diagnostics-v1", EXPORTS = "diagnostic-exports";
    private static volatile boolean capture;
    private static long generation;
    private ApiResponseLogStore() { }

    public static void initialize(Context context) {
        Context app = context.getApplicationContext();
        new Thread(() -> listLogs(app), "diagnostic-cleanup").start();
    }
    public static boolean enabled(Context context) {
        return BuildConfig.DEBUG && capture;
    }
    public static synchronized void setEnabled(Context context, boolean enabled) {
        generation++;
        capture = BuildConfig.DEBUG && enabled; // Session-only; a process restart always opts out.
    }
    static synchronized long begin(Context context) { return enabled(context) ? generation : -1; }

    static Response logResponse(Context context, Response response, long id, long ticket, long elapsedMs) {
        if (!current(context, ticket)) return response;
        JsonObject record = metadata(response.request(), id, elapsedMs);
        record.addProperty("http_status", response.code());
        ResponseBody body = response.body();
        record.addProperty("content_bytes", body == null ? 0 : body.contentLength());
        if (body != null) {
            try (ResponseBody peek = response.peekBody(CAPTURE_BYTES + 1)) {
                if (peek.contentLength() > CAPTURE_BYTES) record.addProperty("body_omitted", "Body exceeds 1 MiB capture limit");
                else record.add("body", JsonParser.parseString(DiagnosticRedactor.json(peek.string())));
            } catch (IOException | RuntimeException error) { record.addProperty("body_omitted", "Body inspection failed"); }
        }
        save(context, record, ticket);
        return response; // Original bytes, source ownership and cancellation all remain with OkHttp/Retrofit.
    }

    static void logFailure(Context context, Request request, long id, long ticket, long elapsedMs, IOException error) {
        JsonObject record = metadata(request, id, elapsedMs);
        record.addProperty("network_error", error.getClass().getSimpleName()); // Exception messages may include secrets/URLs.
        save(context, record, ticket);
    }
    private static JsonObject metadata(Request request, long id, long elapsedMs) {
        JsonObject record = new JsonObject(); record.addProperty("schema", 1); record.addProperty("request_id", id);
        record.addProperty("method", request.method()); record.addProperty("origin", DiagnosticRedactor.origin(request.url().toString()));
        record.addProperty("elapsed_ms", elapsedMs); return record; // Never headers, query, fragment or request body.
    }
    private static synchronized boolean current(Context context, long ticket) { return ticket >= 0 && ticket == generation && enabled(context); }
    private static synchronized void save(Context context, JsonObject record, long ticket) {
        if (!current(context, ticket)) return;
        File pending = null;
        try {
            File dir = directory(context); prune(dir); prune(exportDirectory(context));
            String content = record.toString();
            if (content.getBytes(StandardCharsets.UTF_8).length > RECORD_BYTES) {
                record.remove("body"); record.addProperty("body_omitted", "Serialized diagnostic exceeds limit"); content = record.toString();
            }
            String name = System.currentTimeMillis() + "-" + UUID.randomUUID();
            pending = new File(dir, name + ".tmp");
            try (okio.BufferedSink out = Okio.buffer(Okio.sink(pending))) { out.writeUtf8(content); }
            File published = new File(dir, name + ".json");
            if (!pending.renameTo(published)) throw new IOException("Diagnostic publish failed");
            trim(dir, null, MAX_TOTAL_BYTES, MAX_FILES);
            Log.d("API_DIAGNOSTIC", "Sanitized diagnostic recorded"); // No body or URL in logcat.
        } catch (IOException | RuntimeException error) {
            if (pending != null) pending.delete();
            Log.w("API_DIAGNOSTIC", "Diagnostic storage unavailable"); // Diagnostics must not fail an API request.
        }
    }

    /** Call storage operations from a worker, including initial legacy cleanup. */
    public static synchronized File[] listLogs(Context context) {
        File dir = directory(context); prune(dir); prune(exportDirectory(context));
        File[] files = dir.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified())); return files;
    }
    public static synchronized String read(Context context, File file) throws IOException {
        File dir = directory(context); prune(dir); prune(exportDirectory(context));
        if (file == null || !file.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())
                || !file.getName().endsWith(".json")) throw new IOException("Invalid diagnostic file");
        byte[] bytes;
        try (BufferedSource in = Okio.buffer(Okio.source(file))) { bytes = in.readByteArray(Math.min(RECORD_BYTES + 1, file.length())); }
        if (bytes.length > RECORD_BYTES) throw new IOException("Diagnostic too large");
        return PrettyJsonFormatter.format(DiagnosticRedactor.json(new String(bytes, StandardCharsets.UTF_8)));
    }
    public static synchronized File export(Context context, File file) throws IOException {
        String sanitized = read(context, file); // Re-sanitize at the boundary; never share the storage file itself.
        if (sanitized.getBytes(StandardCharsets.UTF_8).length > RECORD_BYTES) throw new IOException("Export too large");
        File dir = exportDirectory(context); prune(dir);
        File copy = new File(dir, UUID.randomUUID() + ".json");
        try (okio.BufferedSink out = Okio.buffer(Okio.sink(copy))) { out.writeUtf8(sanitized); }
        trim(dir, copy, MAX_TOTAL_BYTES, MAX_FILES); return copy;
    }
    public static synchronized void clear(Context context) {
        generation++; deleteFiles(directory(context)); deleteFiles(exportDirectory(context));
    }
    private static File directory(Context context) {
        // Old versions stored raw responses here. Never list/share them after upgrade.
        deleteFiles(new File(context.getCacheDir(), "api-response-logs"));
        File dir = new File(context.getCacheDir(), DIRECTORY); if (!dir.exists()) dir.mkdirs(); return dir;
    }
    private static File exportDirectory(Context context) {
        File dir = new File(context.getCacheDir(), EXPORTS); if (!dir.exists()) dir.mkdirs(); return dir;
    }
    private static void deleteFiles(File directory) {
        File[] files = directory.listFiles(); if (files == null) return;
        for (File file : files) if (file.isFile()) file.delete();
    }
    private static void prune(File directory) {
        File[] files = directory.listFiles(); if (files == null) return;
        long cutoff = System.currentTimeMillis() - MAX_AGE_MS;
        for (File file : files) if (file.isFile() && (file.lastModified() < cutoff || file.getName().endsWith(".tmp"))) file.delete();
        trim(directory, null, MAX_TOTAL_BYTES, MAX_FILES);
    }
    static void trim(File directory, @Nullable File keep, long maxTotalBytes, int maxFiles) {
        File[] files = directory.listFiles(File::isFile); if (files == null) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long total = 0; for (File file : files) total += file.length();
        int count = files.length;
        for (File file : files) {
            if (count <= maxFiles && total <= maxTotalBytes) break;
            if (file.equals(keep)) continue;
            long length = file.length(); if (file.delete()) { total -= length; count--; }
        }
    }
}
