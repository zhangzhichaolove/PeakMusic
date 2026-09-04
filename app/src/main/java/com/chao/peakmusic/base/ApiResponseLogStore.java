package com.chao.peakmusic.base;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/** Stores complete oversized debug API responses without copying them into memory. */
public final class ApiResponseLogStore {
    static final long LOGCAT_BODY_LIMIT = 256L * 1024L;
    private static final long MAX_TOTAL_BYTES = 20L * 1024L * 1024L;
    private static final int MAX_FILES = 20;
    private static final String DIRECTORY = "api-response-logs";

    private ApiResponseLogStore() {
    }

    static Response logResponse(Context context, Response response, long requestId)
            throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            return response;
        }
        long contentLength = body.contentLength();
        ResponseBody preview = response.peekBody(LOGCAT_BODY_LIMIT + 1);
        boolean fitsLogcat = contentLength >= 0
                ? contentLength <= LOGCAT_BODY_LIMIT
                : preview.contentLength() <= LOGCAT_BODY_LIMIT;
        if (fitsLogcat) {
            PrettyJsonLogger.log(requestId, response.request().url().toString(), preview.string());
            return response;
        }

        File directory = getDirectory(context);
        trim(directory, null);
        File file = new File(directory, System.currentTimeMillis() + "-" + requestId + ".json");
        try (BufferedSink sink = Okio.buffer(Okio.sink(file))) {
            body.source().readAll(sink);
        }
        body.close();
        trim(directory, file);
        Log.d(PrettyJsonLogger.TAG, "#" + requestId + " complete response saved: "
                + file.getAbsolutePath());
        return response.newBuilder()
                .body(new FileResponseBody(body.contentType(), file))
                .build();
    }

    public static File[] listLogs(Context context) {
        File[] files = getDirectory(context).listFiles(File::isFile);
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, (first, second) ->
                Long.compare(second.lastModified(), first.lastModified()));
        return files;
    }

    public static void clear(Context context) {
        for (File file : listLogs(context)) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static File getDirectory(Context context) {
        File directory = new File(context.getCacheDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(PrettyJsonLogger.TAG, "Unable to create API log directory: " + directory);
        }
        return directory;
    }

    private static void trim(File directory, @Nullable File keep) {
        trim(directory, keep, MAX_TOTAL_BYTES, MAX_FILES);
    }

    static void trim(File directory, @Nullable File keep, long maxTotalBytes, int maxFiles) {
        File[] files = directory.listFiles(File::isFile);
        if (files == null || files.length == 0) {
            return;
        }
        Arrays.sort(files, (first, second) ->
                Long.compare(first.lastModified(), second.lastModified()));
        long total = 0;
        for (File file : files) {
            total += file.length();
        }
        int count = files.length;
        for (File file : files) {
            if (count <= maxFiles && total <= maxTotalBytes) {
                break;
            }
            if (file.equals(keep)) {
                continue;
            }
            long length = file.length();
            if (file.delete()) {
                total -= length;
                count--;
            }
        }
    }

    private static final class FileResponseBody extends ResponseBody {
        private final MediaType contentType;
        private final File file;
        private BufferedSource source;

        FileResponseBody(MediaType contentType, File file) {
            this.contentType = contentType;
            this.file = file;
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return file.length();
        }

        @Override
        public BufferedSource source() {
            if (source == null) {
                try {
                    source = Okio.buffer(Okio.source(file));
                } catch (java.io.FileNotFoundException error) {
                    throw new IllegalStateException("API response log disappeared", error);
                }
            }
            return source;
        }
    }
}
