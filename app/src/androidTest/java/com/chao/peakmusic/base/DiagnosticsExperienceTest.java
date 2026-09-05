package com.chao.peakmusic.base;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chao.peakmusic.R;
import com.chao.peakmusic.activity.ApiLogActivity;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real HTTP, native Activity, clipboard and intercepted chooser; no diagnostic is sent externally. */
@RunWith(AndroidJUnit4.class)
public class DiagnosticsExperienceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private static final String JSON = "{\"name\":\"Useful diagnostic\",\"token\":\"SERVER_SECRET\","
            + "\"video\":\"https://user:PASS_SECRET@media.example/PATH_SECRET?token=SIGN_SECRET#FRAGMENT_SECRET\"}";
    private OkHttpClient client() { return new OkHttpClient.Builder().addInterceptor(new LoggingInterceptor()).build(); }

    @Test public void optInPreviewClipboardAndShareOnlyExposeSanitizedSnapshots() throws Exception {
        ApiResponseLogStore.clear(context); ApiResponseLogStore.setEnabled(context, false);
        AtomicReference<Intent> outgoing = new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!Intent.ACTION_CHOOSER.equals(intent.getAction())) return null;
                outgoing.set(intent.getParcelableExtra(Intent.EXTRA_INTENT));
                return new Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED, null);
            }
        };
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData original = null;
        instrumentation.addMonitor(monitor);
        try (Server server = new Server(); ActivityScenario<ApiLogActivity> page = ActivityScenario.launch(ApiLogActivity.class)) {
            await(() -> enabled(page, R.id.api_log_capture));
            original = clipboard.getPrimaryClip();
            assertFalse(ApiResponseLogStore.enabled(context));
            assertEquals(JSON, fetch(client(), server));
            assertEquals(0, ApiResponseLogStore.listLogs(context).length);
            page.onActivity(activity -> activity.findViewById(R.id.api_log_capture).performClick());
            await(() -> ApiResponseLogStore.enabled(context) && enabled(page, R.id.api_log_refresh));
            assertEquals(JSON, fetch(client(), server));
            assertEquals(1, ApiResponseLogStore.listLogs(context).length);
            page.onActivity(activity -> activity.findViewById(R.id.api_log_refresh).performClick());
            await(() -> count(page) == 1 && enabled(page, R.id.api_log_list));
            page.onActivity(activity -> {
                ListView list = activity.findViewById(R.id.api_log_list);
                list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0));
            });
            await(() -> enabled(page, R.id.api_log_share));
            assertSanitized(text(page)); assertControlContrast(page); capture("sanitized-preview");
            page.onActivity(activity -> activity.getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES));
            await(() -> enabled(page, R.id.api_log_capture));
            selectFirst(page);
            assertControlContrast(page); capture("sanitized-preview-night");
            page.onActivity(activity -> activity.findViewById(R.id.api_log_copy_content).performClick());
            // Returning from the background refreshes the list while the confirmation survives.
            page.moveToState(androidx.lifecycle.Lifecycle.State.STARTED);
            page.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            await(() -> enabled(page, R.id.api_log_refresh));
            clickText(context.getString(android.R.string.ok));
            await(() -> clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemAt(0).coerceToText(context).toString().contains("Useful diagnostic"));
            assertSanitized(clipboard.getPrimaryClip().getItemAt(0).coerceToText(context).toString());
            assertTrue(clipboard.getPrimaryClipDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE"));
            selectFirst(page);
            page.onActivity(activity -> activity.findViewById(R.id.api_log_share).performClick());
            assertNull(outgoing.get()); // Merely opening confirmation never starts a share.
            clickText(context.getString(android.R.string.ok));
            await(() -> outgoing.get() != null);
            Intent send = outgoing.get();
            assertTrue((send.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
            Uri uri = send.getParcelableExtra(Intent.EXTRA_STREAM);
            assertEquals(uri, send.getClipData().getItemAt(0).getUri());
            assertTrue(uri.getPath().contains("diagnostic_exports"));
            try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
                assertSanitized(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            page.recreate();
            await(() -> enabled(page, R.id.api_log_capture));
            page.onActivity(activity -> assertTrue(((SwitchCompat) activity.findViewById(R.id.api_log_capture)).isChecked()));
            page.onActivity(activity -> activity.findViewById(R.id.api_log_clear).performClick());
            clickText(context.getString(R.string.api_log_clear));
            await(() -> count(page) == 0 && enabled(page, R.id.api_log_refresh));
            assertEquals(0, new File(context.getCacheDir(), "diagnostic-exports").listFiles().length);
            page.onActivity(activity -> activity.findViewById(R.id.api_log_capture).performClick());
            assertFalse(ApiResponseLogStore.enabled(context));
            assertEquals(JSON, fetch(client(), server)); assertEquals(0, ApiResponseLogStore.listLogs(context).length);
        } finally {
            instrumentation.removeMonitor(monitor);
            if (original == null) clipboard.clearPrimaryClip(); else clipboard.setPrimaryClip(original);
            ApiResponseLogStore.setEnabled(context, false); ApiResponseLogStore.clear(context);
        }
    }

    @Test public void largeResponseStaysCompleteAndClearRejectsLateCapture() throws Exception {
        ApiResponseLogStore.clear(context); ApiResponseLogStore.setEnabled(context, false);
        java.util.concurrent.ExecutorService calls = java.util.concurrent.Executors.newSingleThreadExecutor();
        try (Server server = new Server(); ActivityScenario<ApiLogActivity> page = ActivityScenario.launch(ApiLogActivity.class)) {
            await(() -> enabled(page, R.id.api_log_capture));
            page.onActivity(activity -> activity.findViewById(R.id.api_log_capture).performClick());
            await(() -> ApiResponseLogStore.enabled(context));
            server.body = "{\"name\":\"" + "x".repeat((int) ApiResponseLogStore.CAPTURE_BYTES + 20) + "\",\"token\":\"SERVER_SECRET\"}";
            assertEquals(server.body, fetch(client(), server));
            String summary = ApiResponseLogStore.read(context, ApiResponseLogStore.listLogs(context)[0]);
            assertTrue(summary.contains("exceeds 1 MiB")); assertFalse(summary.contains("SERVER_SECRET"));
            server.body = JSON; server.block = true;
            java.util.concurrent.Future<String> response = calls.submit(() -> fetch(client(), server));
            assertTrue(server.entered.await(5, TimeUnit.SECONDS));
            page.onActivity(activity -> activity.findViewById(R.id.api_log_clear).performClick()); clickText(context.getString(R.string.api_log_clear));
            await(() -> ApiResponseLogStore.listLogs(context).length == 0 && enabled(page, R.id.api_log_refresh));
            server.release.countDown(); assertEquals(JSON, response.get(10, TimeUnit.SECONDS));
            assertEquals(0, ApiResponseLogStore.listLogs(context).length);
            capture("late-capture-cleared");
        } finally { calls.shutdownNow(); ApiResponseLogStore.setEnabled(context, false); ApiResponseLogStore.clear(context); }
    }

    private String fetch(OkHttpClient client, Server server) throws Exception {
        Request request = new Request.Builder().url(server.url() + "PATH_SECRET?token=QUERY_SECRET")
                .header("Authorization", "HEADER_SECRET")
                .post(new okhttp3.FormBody.Builder().add("password", "POST_SECRET").build()).build();
        try (Response response = client.newCall(request).execute()) { return response.body().string(); }
        finally { client.connectionPool().evictAll(); client.dispatcher().executorService().shutdown(); }
    }
    private void assertSanitized(String text) {
        assertTrue(text, text.contains("Useful diagnostic"));
        for (String secret : new String[]{"SERVER_SECRET", "PASS_SECRET", "PATH_SECRET", "SIGN_SECRET", "FRAGMENT_SECRET", "QUERY_SECRET", "HEADER_SECRET", "POST_SECRET"}) assertFalse(secret, text.contains(secret));
    }
    private void selectFirst(ActivityScenario<ApiLogActivity> page) {
        await(() -> count(page) == 1 && enabled(page, R.id.api_log_list));
        page.onActivity(activity -> {
            ListView list = activity.findViewById(R.id.api_log_list);
            list.performItemClick(list.getChildAt(0), 0, list.getAdapter().getItemId(0));
        });
        await(() -> enabled(page, R.id.api_log_share));
    }
    private void assertControlContrast(ActivityScenario<ApiLogActivity> page) {
        page.onActivity(activity -> {
            android.util.TypedValue background = new android.util.TypedValue();
            activity.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true);
            TextView share = activity.findViewById(R.id.api_log_share);
            assertTrue("Diagnostic action text must have 4.5:1 contrast",
                    androidx.core.graphics.ColorUtils.calculateContrast(share.getCurrentTextColor(), background.data) >= 4.5);
        });
    }
    private int count(ActivityScenario<ApiLogActivity> page) {
        AtomicReference<Integer> result = new AtomicReference<>(0);
        page.onActivity(activity -> result.set(((ListView) activity.findViewById(R.id.api_log_list)).getCount())); return result.get();
    }
    private String text(ActivityScenario<ApiLogActivity> page) {
        AtomicReference<String> result = new AtomicReference<>("");
        page.onActivity(activity -> result.set(((TextView) activity.findViewById(R.id.api_log_preview)).getText().toString())); return result.get();
    }
    private boolean enabled(ActivityScenario<ApiLogActivity> page, int id) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        page.onActivity(activity -> result.set(activity.findViewById(id).isEnabled())); return result.get();
    }
    private void clickText(String text) {
        long end = SystemClock.elapsedRealtime() + 10000;
        do {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                if (!text.contentEquals(node.getText() == null ? "" : node.getText())) continue;
                while (!node.isClickable() && node.getParent() != null) node = node.getParent();
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < end);
        fail("Missing dialog action " + text);
    }
    private void capture(String name) throws Exception {
        SystemClock.sleep(300); File dir = new File(context.getExternalFilesDir(null), "verification-diagnostics"); dir.mkdirs();
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
        screenshot.recycle();
    }
    private void await(Check check) {
        long end = SystemClock.elapsedRealtime() + 15000;
        while (!check.done() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(50);
        assertTrue("Expected native diagnostic state", check.done());
    }
    private interface Check { boolean done(); }
    private static final class Server implements AutoCloseable {
        final java.net.ServerSocket server;
        volatile String body = JSON; volatile boolean block;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Server() throws Exception {
            server = new java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"));
            new Thread(() -> {
                while (!server.isClosed()) try (java.net.Socket socket = server.accept()) {
                    socket.setSoTimeout(3000);
                    java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                    String line; int length = 0;
                    do { line = in.readLine(); if (line != null && line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim()); }
                    while (line != null && !line.isEmpty());
                    for (int i = 0; i < length; i++) in.read();
                    if (block) { entered.countDown(); release.await(10, TimeUnit.SECONDS); }
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(bytes);
                } catch (Exception error) { if (server.isClosed()) return; }
            }, "diagnostic-fixture-http").start();
        }
        String url() { return "http://127.0.0.1:" + server.getLocalPort() + "/"; }
        @Override public void close() throws Exception { release.countDown(); server.close(); }
    }
}
