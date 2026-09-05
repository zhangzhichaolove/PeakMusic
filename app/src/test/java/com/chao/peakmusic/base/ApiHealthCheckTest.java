package com.chao.peakmusic.base;

import static org.junit.Assert.*;
import org.junit.Test;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ApiHealthCheckTest {
    @Test public void probesActualListEndpointBelowConfiguredPath() {
        assertEquals("https://example.com/api/music/getMusicList?search=", ApiAddressManager.connectionUrl("https://example.com/api").toString());
    }
    @Test public void rejectsErrorStatusEvenWithValidJson() throws Exception {
        for (int code : new int[]{401, 403, 404, 500, 503}) {
            assertEquals("HTTP " + code, failure(code, "{\"success\":true,\"result\":{\"records\":[]}}"));
        }
    }
    @Test public void acceptsValidEmptyAndPlayableCatalogue() throws Exception {
        assertNull(failure(200, "{\"success\":true,\"result\":{\"records\":[]}}"));
        assertNull(failure(200, "{\"success\":true,\"result\":{\"records\":[{\"mp3\":\"/media/sample\"}]}}"));
    }
    @Test public void rejectsHtmlMalformedAndMissingOrWrongSchema() throws Exception {
        for (String body : new String[]{"<html>login</html>", "{", "null", "[]", "{}",
                "{\"success\":false}", "{\"success\":\"true\"}",
                "{\"success\":true,\"result\":{}}",
                "{\"success\":true,\"result\":{\"records\":{}}}",
                "{\"success\":true,\"result\":{\"records\":[null]}}",
                "{\"success\":true,\"result\":{\"records\":[{\"mp3\":3}]}}"}) {
            assertNotNull(body, failure(200, body));
        }
    }
    @Test public void baseUrlRejectsEmbeddedSecretsAndAmbiguousQuery() {
        assertNull(ApiAddressManager.normalize("https://user:secret@example.com"));
        assertNull(ApiAddressManager.normalize("https://example.com?token=value"));
        assertNull(ApiAddressManager.normalize("https://example.com#fragment"));
    }
    @Test public void oversizedBodyIsBounded() throws Exception {
        assertNotNull(failure(200, new String(new char[1024 * 1024 + 1]).replace('\0', ' ')));
    }
    private String failure(int code, String body) throws Exception {
        try (Response response = new Response.Builder().request(new Request.Builder().url("https://example.com").build())
                .protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(ResponseBody.create(body, MediaType.get("application/json"))).build()) {
            return ApiHealthCheck.failure(response);
        }
    }
}
