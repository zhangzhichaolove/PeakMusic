package com.chao.peakmusic.activity;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class LibrarySelectionTest {
    @Test public void busyOperationLocksSelectionAndItsResultIsConsumedOnlyOnce() {
        LibrarySelection selection = new LibrarySelection();
        selection.start(); selection.toggle("api:a:id:42"); selection.toggle("api:b:id:42");
        assertEquals(2, selection.keys.size()); selection.begin(); selection.toggle("api:a:id:42");
        assertEquals(2, selection.keys.size()); assertTrue(selection.busy);
        selection.complete(false); assertFalse(selection.busy); assertEquals(Boolean.FALSE, selection.consumeResult());
        assertNull(selection.consumeResult()); assertEquals(2, selection.keys.size());
        selection.toggle("api:a:id:42"); assertEquals(1, selection.keys.size());
        selection.clear(); assertTrue(selection.keys.isEmpty()); assertFalse(selection.active);
    }
}
