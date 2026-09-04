package com.chao.peakmusic.service;

import java.util.Random;

/** Pure queue navigation used by the playback service and unit tests. */
public final class PlaybackModeNavigator {
    public static final int SEQUENTIAL = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;
    public static final int SHUFFLE = 3;

    private PlaybackModeNavigator() {
    }

    public static int next(int current, int size, int mode, boolean automatic, Random random) {
        if (size <= 0) {
            return -1;
        }
        int normalized = Math.max(0, Math.min(current, size - 1));
        if (automatic && mode == REPEAT_ONE) {
            return normalized;
        }
        if (mode == SHUFFLE) {
            if (size == 1) {
                return 0;
            }
            int candidate = random.nextInt(size - 1);
            return candidate >= normalized ? candidate + 1 : candidate;
        }
        if (normalized + 1 < size) {
            return normalized + 1;
        }
        return mode == REPEAT_ALL ? 0 : -1;
    }

    public static int previous(int current, int size, int mode) {
        if (size <= 0) {
            return -1;
        }
        int normalized = Math.max(0, Math.min(current, size - 1));
        if (normalized > 0) {
            return normalized - 1;
        }
        return mode == REPEAT_ALL ? size - 1 : -1;
    }

    public static int normalizeMode(int mode) {
        return mode >= SEQUENTIAL && mode <= SHUFFLE ? mode : SEQUENTIAL;
    }
}
