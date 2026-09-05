package com.chao.peakmusic.lyrics;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Per-track calibration. The old global value is adopted only by the first identified track. */
public final class LyricOffsetStore {
    private final SharedPreferences preferences;
    public LyricOffsetStore(Context context) { preferences = context.getSharedPreferences("lyrics", Context.MODE_PRIVATE); }

    public long get(String source) {
        if (source == null || source.isEmpty()) return 0;
        String key = key(source);
        if (preferences.contains("manual_offset")) {
            SharedPreferences.Editor editor = preferences.edit();
            if (!preferences.contains(key)) editor.putLong(key, clamp(preferences.getLong("manual_offset", 0)));
            editor.remove("manual_offset").apply();
        }
        return clamp(preferences.getLong(key, 0));
    }

    public long set(String source, long offsetMs) {
        if (source == null || source.isEmpty()) return 0;
        long value = clamp(offsetMs);
        preferences.edit().putLong(key(source), value).apply();
        return value;
    }

    private static long clamp(long value) { return Math.max(-10_000, Math.min(10_000, value)); }

    private static String key(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder("track.");
            for (byte value : digest) key.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return key.toString();
        } catch (NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }
}
