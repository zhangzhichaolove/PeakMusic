package com.chao.peakmusic.lyrics;

import static org.junit.Assert.*;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class LyricOffsetStoreTest {
    private final Context context = RuntimeEnvironment.getApplication();
    @Before public void clear() { context.getSharedPreferences("lyrics", 0).edit().clear().commit(); }
    @Test public void eachTrackHasIndependentPersistentOffsetAndReset() {
        LyricOffsetStore store = new LyricOffsetStore(context);
        store.set("A", 1500); store.set("B", -500);
        store = new LyricOffsetStore(context);
        assertEquals(1500, store.get("A")); assertEquals(-500, store.get("B"));
        store.set("A", 0);
        assertEquals(0, store.get("A")); assertEquals(-500, store.get("B"));
    }
    @Test public void limitsOffsetAndDoesNotStoreUnidentifiedTracks() {
        LyricOffsetStore store = new LyricOffsetStore(context);
        assertEquals(10000, store.set("A", Long.MAX_VALUE));
        assertEquals(-10000, store.set("B", Long.MIN_VALUE));
        assertEquals(0, store.set(null, 500)); assertEquals(0, store.get(""));
    }
    @Test public void oldGlobalValueIsAdoptedOnceNotCopiedToEveryNewTrack() {
        context.getSharedPreferences("lyrics", 0).edit().putLong("manual_offset", 2000).commit();
        LyricOffsetStore store = new LyricOffsetStore(context);
        assertEquals(0, store.get(null));
        assertEquals(2000, store.get("A")); assertEquals(0, store.get("B"));
        assertFalse(context.getSharedPreferences("lyrics", 0).contains("manual_offset"));
    }
    @Test public void preferenceKeysDoNotExposeMediaUrlsOrTokens() {
        new LyricOffsetStore(context).set("https://example.com/song?token=private", 500);
        for (String key : context.getSharedPreferences("lyrics", 0).getAll().keySet()) {
            assertFalse(key.contains("private")); assertTrue(key.startsWith("track."));
        }
    }
}
