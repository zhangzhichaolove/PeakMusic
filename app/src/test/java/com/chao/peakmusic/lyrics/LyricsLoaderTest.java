package com.chao.peakmusic.lyrics;

import static org.junit.Assert.*;
import org.junit.Test;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LyricsLoaderTest {
    @Test public void readsTimedPlainAndEmptyLyrics() throws Exception {
        try (Response response = response(200, "text/plain", "[00:01]Line")) {
            assertEquals(1000, LyricsLoader.read(response).get(0).timeMs);
        }
        try (Response response = response(200, "text/plain", "Untimed line")) {
            assertEquals(-1, LyricsLoader.read(response).get(0).timeMs);
        }
        try (Response response = response(200, "text/plain", "[ar:artist]")) {
            assertTrue(LyricsLoader.read(response).isEmpty());
        }
    }
    @Test public void errorsAndLoginDocumentsDoNotMasqueradeAsLyrics() throws Exception {
        for (Response response : new Response[]{response(503, "text/plain", "bad"),
                response(200, "text/html", "<html>login</html>"), response(200, "application/json", "{}"),
                response(200, "text/plain", "<!doctype html>login")}) {
            try (Response closeable = response) {
                assertThrows(java.io.IOException.class, () -> LyricsLoader.read(closeable));
            }
        }
    }
    @Test public void responseSizeIsBounded() throws Exception {
        try (Response response = response(200, "text/plain", new String(new char[1024 * 1024 + 1]))) {
            assertThrows(java.io.IOException.class, () -> LyricsLoader.read(response));
        }
    }
    private Response response(int code, String mime, String body) {
        return new Response.Builder().request(new Request.Builder().url("https://example.com/song.lrc").build())
                .protocol(Protocol.HTTP_1_1).code(code).message("test").header("Content-Type", mime)
                .body(ResponseBody.create(body, MediaType.get(mime))).build();
    }
}
