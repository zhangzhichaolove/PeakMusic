package com.chao.peakmusic.base;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ApiAddressManagerTest {

    @Test
    public void normalizeTrimsAndAddsTrailingSlash() {
        assertEquals("https://example.com/music/",
                ApiAddressManager.normalize(" https://example.com/music "));
    }

    @Test
    public void normalizePreservesValidHttpUrl() {
        assertEquals("http://10.0.2.2:8080/",
                ApiAddressManager.normalize("http://10.0.2.2:8080/"));
    }

    @Test
    public void normalizeRejectsUnsupportedOrEmptyValues() {
        assertNull(ApiAddressManager.normalize("file:///tmp/music"));
        assertNull(ApiAddressManager.normalize(""));
        assertNull(ApiAddressManager.normalize(null));
    }
}
