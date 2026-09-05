package com.chao.peakmusic;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.widget.CompoundButton;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only platform classes: prove the actual minified build cannot enable capture and purges raw legacy logs. */
public final class ReleaseDiagnosticCheck {
    public static void run(Instrumentation instrumentation, File evidence) throws Exception {
        android.content.Context context = instrumentation.getTargetContext();
        File oldDir = new File(context.getCacheDir(), "api-response-logs"); oldDir.mkdirs();
        File legacy = new File(oldDir, "release-privacy-fixture.json");
        try (FileOutputStream out = new FileOutputStream(legacy)) { out.write("{\"token\":\"LEGACY_CANARY\"}".getBytes(StandardCharsets.UTF_8)); }
        Activity page = instrumentation.startActivitySync(new Intent()
                .setComponent(new ComponentName(context, "com.chao.peakmusic.activity.ApiLogActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            int toggleId = context.getResources().getIdentifier("api_log_capture", "id", context.getPackageName());
            int refreshId = context.getResources().getIdentifier("api_log_refresh", "id", context.getPackageName());
            AtomicBoolean ready = new AtomicBoolean(); long end = SystemClock.elapsedRealtime() + 15000;
            do {
                instrumentation.runOnMainSync(() -> ready.set(page.findViewById(refreshId).isEnabled()));
                if (ready.get()) break;
                SystemClock.sleep(50);
            } while (SystemClock.elapsedRealtime() < end);
            if (!ready.get()) throw new AssertionError("Release diagnostic page did not load");
            instrumentation.runOnMainSync(() -> {
                CompoundButton capture = page.findViewById(toggleId);
                if (capture.isEnabled() || capture.isChecked()) throw new AssertionError("Release can enable diagnostic capture");
                android.widget.TextView empty = page.findViewById(context.getResources().getIdentifier("api_log_empty", "id", context.getPackageName()));
                String expected = context.getString(context.getResources().getIdentifier("api_logs_empty_release", "string", context.getPackageName()));
                if (!expected.contentEquals(empty.getText())) throw new AssertionError("Release empty state suggests enabling capture");
            });
            if (legacy.exists()) throw new AssertionError("Legacy raw log not purged");
            // Recreate the canary so FileNotFound cannot falsely prove that the root was removed.
            try (FileOutputStream out = new FileOutputStream(legacy)) { out.write("CANARY".getBytes(StandardCharsets.UTF_8)); }
            boolean denied = false;
            try (java.io.InputStream ignored = context.getContentResolver().openInputStream(android.net.Uri.parse(
                    "content://" + context.getPackageName() + ".files/api_response_logs/release-privacy-fixture.json"))) {
                // The old FileProvider root must no longer be exported, regardless of the filename.
            } catch (IllegalArgumentException error) { denied = true; }
            if (!denied) throw new AssertionError("Legacy raw log provider URI still readable");
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            try (FileOutputStream out = new FileOutputStream(new File(evidence, "diagnostic-privacy.png"))) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, out); }
            screenshot.recycle();
            try (FileOutputStream out = new FileOutputStream(new File(evidence, "diagnostic-privacy.txt"))) {
                out.write("minified_capture_disabled=true\nlegacy_raw_cache_purged=true\nlegacy_provider_root_denied=true\n".getBytes(StandardCharsets.UTF_8));
            }
        } finally { legacy.delete(); instrumentation.runOnMainSync(page::finish); }
    }
}
