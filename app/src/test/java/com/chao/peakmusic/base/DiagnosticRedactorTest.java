package com.chao.peakmusic.base;

import static org.junit.Assert.*;
import org.junit.Test;

public class DiagnosticRedactorTest {
    @Test public void credentialFieldsNestedArraysHeadersAndEncodedNamesAreMasked() {
        String raw = "{\"success\":true,\"records\":[{\"name\":\"Song\",\"access_token\":\"TOKEN_VALUE\",\"id\":0}],"
                + "\"headers\":{\"Authorization\":\"AUTH_VALUE\",\"Set-Cookie\":\"COOKIE_VALUE\"},"
                + "\"%74oken\":\"ENCODED_VALUE\",\"private_key\":\"KEY_VALUE\",\"sessionId\":\"SESSION_VALUE\","
                + "\"email\":\"EMAIL_VALUE\",\"password\":{\"nested\":\"PASSWORD_VALUE\"}}";
        String result = DiagnosticRedactor.json(raw);
        for (String secret : new String[]{"TOKEN_VALUE", "AUTH_VALUE", "COOKIE_VALUE", "ENCODED_VALUE", "KEY_VALUE", "SESSION_VALUE", "EMAIL_VALUE", "PASSWORD_VALUE"})
            assertFalse(result, result.contains(secret));
        assertTrue(result.contains("Song")); assertTrue(result.contains("\"id\":0"));
        assertEquals(result, DiagnosticRedactor.json(result));
    }
    @Test public void allUrlComponentsExceptOriginAreRemovedIncludingPathSecretsAndOrdinaryQueries() {
        String url = "https://user:pass@example.com:8443/path/PRIVATE_PATH?q=PRIVATE_QUERY#PRIVATE_FRAGMENT";
        assertEquals("https://example.com:8443/", DiagnosticRedactor.origin(url));
        String result = DiagnosticRedactor.json("{\"video\":\"" + url + "\",\"text\":\"fetch " + url + " later\",\"file\":\"content://media/PRIVATE_URI\"}");
        for (String value : new String[]{"user", "pass", "PRIVATE_PATH", "PRIVATE_QUERY", "PRIVATE_FRAGMENT", "PRIVATE_URI"}) assertFalse(result, result.contains(value));
        assertTrue(result.contains("https://example.com:8443/"));
    }
    @Test public void embeddedJsonBearerJwtAndLocalPathsDoNotBypassFiltering() {
        String raw = "{\"payload\":\"{\\\"token\\\":\\\"EMBEDDED_SECRET\\\"}\",\"note\":\"Bearer AUTH_VALUE\","
                + "\"unknown\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTYifQ.SignatureValueHere\",\"file\":\"/storage/emulated/0/PRIVATE_FILE\"}";
        String result = DiagnosticRedactor.json(raw);
        for (String secret : new String[]{"EMBEDDED_SECRET", "AUTH_VALUE", "SignatureValueHere", "PRIVATE_FILE"}) assertFalse(result, result.contains(secret));
    }
    @Test public void invalidTruncatedNonJsonAndTooDeepBodiesAreNotPartiallyLogged() {
        for (String body : new String[]{"plain SECRET", "{\"name\":\"SECRET\",", "{\"name\":\"SECRET\"} trailing", "[NaN]", "[".repeat(40) + "\"SECRET\"" + "]".repeat(40)}) {
            assertEquals(DiagnosticRedactor.OMITTED, DiagnosticRedactor.json(body));
        }
    }
}
