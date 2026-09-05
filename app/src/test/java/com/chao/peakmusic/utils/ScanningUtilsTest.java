package com.chao.peakmusic.utils;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;

import com.chao.peakmusic.model.SongModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScanningUtilsTest {
    private ScanningUtils scanner;
    private final MediaProvider provider = new MediaProvider();
    private ArrayList<SongModel> latest;
    // Keep a strong reference: the production scanner deliberately holds a weak listener.
    private final ScanningUtils.ScanningListener listener = music -> latest = music;

    @Before public void setUp() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        ShadowContentResolver.registerProviderInternal("media", provider);
        scanner = ReflectionHelpers.callConstructor(ScanningUtils.class,
                ReflectionHelpers.ClassParameter.from(android.content.Context.class, RuntimeEnvironment.getApplication()));
        scanner.setListener(listener);
    }

    @After public void tearDown() {
        provider.release.countDown();
        ((ExecutorService) ReflectionHelpers.getField(scanner, "scanExecutor")).shutdownNow();
    }

    @Test public void unauthorizedIsDistinctFromEmptyAndDoesNotQuery() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        scanner.scanMusic();
        assertEquals(ScanningUtils.State.UNAUTHORIZED, scanner.getState());
        assertEquals(0, provider.queries);
        assertNull(scanner.getMusic());
    }

    @Test public void genuinelyEmptyCursorIsSuccessfulEmptyLibrary() throws Exception {
        scanner.scanMusic(); await(ScanningUtils.State.READY);
        assertNotNull(latest); assertTrue(latest.isEmpty());
    }

    @Test public void scanFailureAndNullCursorDoNotMasqueradeAsEmptyLibrary() throws Exception {
        provider.fail = true;
        scanner.scanMusic(); await(ScanningUtils.State.ERROR);
        assertNull(latest);
        provider.fail = false; provider.nullCursor = true;
        scanner.scanMusic(); await(ScanningUtils.State.ERROR);
        assertNull(latest);
    }

    @Test public void manualRefreshPicksUpNewTracksAndUsesContentUris() throws Exception {
        scanner.scanMusic(); await(ScanningUtils.State.READY);
        provider.addTrack = true;
        scanner.invalidate(); scanner.scanMusic(); await(ScanningUtils.State.READY);
        assertEquals(1, latest.size());
        assertEquals("content://media/external/audio/media/7", latest.get(0).getPath());
        assertEquals("/music", com.chao.peakmusic.local.LocalLibraryIndex.directory(latest.get(0)));
    }

    @Test @Config(sdk = 29) public void scopedStorageFolderDoesNotRequireRawDataPath() throws Exception {
        provider.addTrack = true; provider.hideFilePath = true;
        scanner.scanMusic(); await(ScanningUtils.State.READY);
        assertNull(latest.get(0).getFilePath());
        assertEquals("external_primary", latest.get(0).getVolumeName());
        assertEquals("Music/Album/", latest.get(0).getRelativePath());
        assertEquals("external_primary:/Music/Album", com.chao.peakmusic.local.LocalLibraryIndex.directory(latest.get(0)));
    }

    @Test public void revocationWhileScanningDiscardsInFlightResults() throws Exception {
        provider.block = true; provider.addTrack = true;
        scanner.scanMusic(); assertTrue(provider.entered.await(2, TimeUnit.SECONDS));
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        scanner.invalidate(); provider.release.countDown();
        ((ExecutorService) ReflectionHelpers.getField(scanner, "scanExecutor")).submit(() -> {}).get(2, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(ScanningUtils.State.UNAUTHORIZED, scanner.getState());
        assertNull(latest); assertNull(scanner.getMusic());
    }

    private void await(ScanningUtils.State expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (scanner.getState() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10); Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
        assertEquals(expected, scanner.getState());
    }

    private static class MediaProvider extends ContentProvider {
        volatile boolean fail, nullCursor, addTrack, block, hideFilePath;
        volatile int queries;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
            queries++; entered.countDown();
            if (block) try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            if (fail) throw new IllegalStateException("provider failed");
            if (nullCursor) return null;
            MatrixCursor cursor = new MatrixCursor(projection);
            if (addTrack) {
                MatrixCursor.RowBuilder row = cursor.newRow();
                for (String column : projection) {
                    if (MediaStore.Audio.Media._ID.equals(column)) row.add(7);
                    else if (MediaStore.Audio.Media.DATA.equals(column)) row.add(hideFilePath ? null : "/music/sample.mp3");
                    else if (MediaStore.MediaColumns.VOLUME_NAME.equals(column)) row.add("external_primary");
                    else if (MediaStore.MediaColumns.RELATIVE_PATH.equals(column)) row.add("Music/Album/");
                    else if (MediaStore.Audio.Media.DISPLAY_NAME.equals(column)) row.add("sample.mp3");
                    else if (MediaStore.Audio.Media.ARTIST.equals(column)) row.add("Artist");
                    else if (MediaStore.Audio.Media.ALBUM.equals(column)) row.add("Album");
                    else row.add(0);
                }
            }
            return cursor;
        }
        @Override public boolean onCreate() { return true; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    }
}
