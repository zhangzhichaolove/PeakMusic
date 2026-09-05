package com.chao.peakmusic.base;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.core.content.FileProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Protocol;
import okhttp3.MediaType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 28)
public class DiagnosticStoreTest {
    private final Context context = RuntimeEnvironment.getApplication();
    @Before public void reset() { ApiResponseLogStore.clear(context); ApiResponseLogStore.setEnabled(context, false); }
    @Test public void optInIsRequiredAndOriginalBodyIsNeverConsumedOrReplaced() throws Exception {
        String raw = "{\"token\":\"TOKEN_ORIGINAL\",\"mp3\":\"https://media.example/PRIVATE?q=SIGNATURE\",\"name\":\"Song\"}";
        Response response = response(raw);
        assertEquals(-1, ApiResponseLogStore.begin(context));
        assertSame(response, ApiResponseLogStore.logResponse(context, response, 1, -1, 10));
        assertEquals(0, ApiResponseLogStore.listLogs(context).length);
        ApiResponseLogStore.setEnabled(context, true);
        assertSame(response, ApiResponseLogStore.logResponse(context, response, 1, ApiResponseLogStore.begin(context), 10));
        File[] logs = ApiResponseLogStore.listLogs(context); assertEquals(1, logs.length);
        String stored = new String(Files.readAllBytes(logs[0].toPath()), StandardCharsets.UTF_8);
        for (String secret : new String[]{"TOKEN_ORIGINAL", "PRIVATE", "SIGNATURE", "QUERY_SECRET", "URL_PASSWORD", "HEADER_SECRET"}) assertFalse(stored, stored.contains(secret));
        assertTrue(stored.contains("Song"));
        ApiResponseLogStore.clear(context);
        assertEquals(raw, response.body().string()); // Clearing diagnostics cannot destroy Retrofit's body.
    }
    @Test public void oversizedAndUnknownLengthBodiesKeepOriginalBytesButStoreOnlySummary() throws Exception {
        String raw = "{\"name\":\"" + "x".repeat((int) ApiResponseLogStore.CAPTURE_BYTES + 1) + "\"}";
        ApiResponseLogStore.setEnabled(context, true);
        Response ordinary = response(raw);
        Response response = ordinary.newBuilder().body(new ResponseBody() {
            final okio.BufferedSource source = new okio.Buffer().writeUtf8(raw);
            @Override public long contentLength() { return -1; }
            @Override public MediaType contentType() { return MediaType.get("application/json"); }
            @Override public okio.BufferedSource source() { return source; }
        }).build();
        ApiResponseLogStore.logResponse(context, response, 2, ApiResponseLogStore.begin(context), 10);
        File file = ApiResponseLogStore.listLogs(context)[0];
        String stored = ApiResponseLogStore.read(context, file);
        assertTrue(stored.contains("exceeds 1 MiB")); assertTrue(file.length() < 1000);
        assertEquals(raw, response.body().string());
    }
    @Test public void disablingOrClearingInvalidatesInflightCaptureEvenAfterReenable() {
        ApiResponseLogStore.setEnabled(context, true); long before = ApiResponseLogStore.begin(context);
        ApiResponseLogStore.setEnabled(context, false); ApiResponseLogStore.setEnabled(context, true);
        ApiResponseLogStore.logResponse(context, response("{}"), 1, before, 0);
        assertEquals(0, ApiResponseLogStore.listLogs(context).length);
        long another = ApiResponseLogStore.begin(context); ApiResponseLogStore.clear(context);
        ApiResponseLogStore.logResponse(context, response("{}"), 2, another, 0);
        assertEquals(0, ApiResponseLogStore.listLogs(context).length);
    }
    @Test public void cacheWriteFailureCannotFailResponse() throws Exception {
        ApiResponseLogStore.setEnabled(context, true);
        File directory = new File(context.getCacheDir(), "diagnostics-v1"); assertTrue(directory.delete());
        assertTrue(directory.createNewFile());
        try {
            Response response = response("{\"name\":\"Still readable\"}");
            assertSame(response, ApiResponseLogStore.logResponse(context, response, 1, ApiResponseLogStore.begin(context), 0));
            assertTrue(response.body().string().contains("Still readable"));
        } finally { assertTrue(directory.delete()); }
    }
    @Test public void legacyFilesArePurgedAndOnlySanitizedExportCopiesHaveProviderUris() throws Exception {
        File legacyDir = new File(context.getCacheDir(), "api-response-logs"); legacyDir.mkdirs();
        File raw = new File(legacyDir, "old.json"); Files.write(raw.toPath(), "{\"token\":\"OLD_SECRET\"}".getBytes(StandardCharsets.UTF_8));
        ApiResponseLogStore.listLogs(context); assertFalse(raw.exists());
        File log = new File(context.getCacheDir(), "diagnostics-v1/manual.json");
        Files.write(log.toPath(), "{\"name\":\"Useful\",\"token\":\"MANUAL_SECRET\"}".getBytes(StandardCharsets.UTF_8));
        File exported = ApiResponseLogStore.export(context, log);
        assertNotEquals(log, exported); assertTrue(exported.getParentFile().getName().equals("diagnostic-exports"));
        String text = new String(Files.readAllBytes(exported.toPath()), StandardCharsets.UTF_8);
        assertFalse(text.contains("MANUAL_SECRET")); assertTrue(text.contains("Useful"));
        assertThrows(IllegalArgumentException.class, () -> FileProvider.getUriForFile(context, context.getPackageName() + ".files", log));
        assertNotNull(FileProvider.getUriForFile(context, context.getPackageName() + ".files", exported));
        ApiResponseLogStore.clear(context); assertFalse(exported.exists());
    }
    @Test public void ageAndCountLimitsAlsoApplyToExportsAndOversizedReadsFailClosed() throws Exception {
        File dir = new File(context.getCacheDir(), "diagnostics-v1"); dir.mkdirs();
        for (int i = 0; i < 25; i++) Files.write(new File(dir, i + ".json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        File expired = new File(dir, "expired.json"); Files.write(expired.toPath(), "{}".getBytes(StandardCharsets.UTF_8)); expired.setLastModified(1);
        assertEquals(20, ApiResponseLogStore.listLogs(context).length); assertFalse(expired.exists());
        File first = ApiResponseLogStore.listLogs(context)[0];
        for (int i = 0; i < 25; i++) ApiResponseLogStore.export(context, first);
        assertEquals(20, new File(context.getCacheDir(), "diagnostic-exports").listFiles().length);
        File oversized = new File(dir, "oversized.json"); Files.write(oversized.toPath(), new byte[(int) ApiResponseLogStore.RECORD_BYTES + 1]);
        assertThrows(java.io.IOException.class, () -> ApiResponseLogStore.read(context, oversized));
    }
    @Test public void staleSelectionCannotReadOrExportExpiredDiagnostics() throws Exception {
        ApiResponseLogStore.setEnabled(context, true);
        ApiResponseLogStore.logResponse(context, response("{}"), 1, ApiResponseLogStore.begin(context), 0);
        File log = ApiResponseLogStore.listLogs(context)[0];
        File exported = ApiResponseLogStore.export(context, log);
        assertTrue(log.setLastModified(1)); assertTrue(exported.setLastModified(1));
        assertThrows(java.io.IOException.class, () -> ApiResponseLogStore.read(context, log));
        assertFalse(log.exists()); assertFalse(exported.exists());
        assertThrows(java.io.IOException.class, () -> ApiResponseLogStore.export(context, log));
    }
    @Test public void networkFailureSummaryExcludesExceptionMessageAndRequestSecrets() throws Exception {
        ApiResponseLogStore.setEnabled(context, true);
        ApiResponseLogStore.logFailure(context, response("{}").request(), 3, ApiResponseLogStore.begin(context), 10,
                new java.io.IOException("PRIVATE_ERROR_CONTENT"));
        String saved = ApiResponseLogStore.read(context, ApiResponseLogStore.listLogs(context)[0]);
        assertTrue(saved.contains("IOException")); assertFalse(saved.contains("PRIVATE_ERROR_CONTENT")); assertFalse(saved.contains("QUERY_SECRET"));
    }
    private Response response(String body) {
        return new Response.Builder().request(new Request.Builder().url("https://user:URL_PASSWORD@example.com/path?token=QUERY_SECRET")
                .header("Authorization", "HEADER_SECRET").build()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }
}
