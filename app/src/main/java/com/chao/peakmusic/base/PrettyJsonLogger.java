package com.chao.peakmusic.base;

import android.util.Log;

final class PrettyJsonLogger {
    static final String TAG = "API_RESPONSE_JSON";
    private static final int MAX_CHARS_PER_LOG = 1000;

    private PrettyJsonLogger() {
    }

    static void log(long requestId, String url, String body) {
        String formattedBody = PrettyJsonFormatter.format(body);

        Log.d(TAG, "#" + requestId + " response: " + url);
        for (String line : formattedBody.split("\\n", -1)) {
            logCompleteLine(line);
        }
        Log.d(TAG, "#" + requestId + " end response");
    }

    private static void logCompleteLine(String line) {
        if (line.isEmpty()) {
            Log.d(TAG, "");
            return;
        }
        for (int start = 0; start < line.length(); start += MAX_CHARS_PER_LOG) {
            int end = Math.min(start + MAX_CHARS_PER_LOG, line.length());
            Log.d(TAG, line.substring(start, end));
        }
    }
}
