package com.chao.peakmusic.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import com.chao.peakmusic.BuildConfig;
import com.chao.peakmusic.R;
import com.chao.peakmusic.base.ApiResponseLogStore;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Disk reads, formatting and export snapshots run off the UI thread. */
public final class ApiLogActivity extends AppCompatActivity {
    private static final int PREVIEW_CHARS = 65536;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<File> files = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView preview, empty;
    private Button copyPath, copyContent, share, clear, refresh;
    private ListView list;
    private SwitchCompat capture;
    private File selected;
    private String content;
    private int generation;
    private boolean busy, updatingSwitch;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        com.chao.peakmusic.utils.BarUtils.setWindow(this);
        setContentView(R.layout.activity_api_logs);
        Toolbar toolbar = findViewById(R.id.api_log_toolbar);
        com.chao.peakmusic.utils.BarUtils.applyPageInsets(findViewById(android.R.id.content), toolbar);
        setSupportActionBar(toolbar); setTitle(R.string.api_logs_title);
        toolbar.setNavigationIcon(R.drawable.menu_setting_back);
        toolbar.setNavigationOnClickListener(view -> finish());
        preview = findViewById(R.id.api_log_preview); empty = findViewById(R.id.api_log_empty);
        empty.setText(BuildConfig.DEBUG ? R.string.api_logs_empty : R.string.api_logs_empty_release);
        preview.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        copyPath = findViewById(R.id.api_log_copy_path); copyContent = findViewById(R.id.api_log_copy_content);
        share = findViewById(R.id.api_log_share); clear = findViewById(R.id.api_log_clear); refresh = findViewById(R.id.api_log_refresh);
        capture = findViewById(R.id.api_log_capture); list = findViewById(R.id.api_log_list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>()); list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> select(files.get(position)));
        copyPath.setOnClickListener(view -> copy(selected.getAbsolutePath()));
        copyContent.setOnClickListener(view -> {
            String snapshot = content; // onResume may refresh selection while consent is visible.
            confirm(() -> {
                if (snapshot.length() > PREVIEW_CHARS) message(R.string.api_log_copy_too_large); else copy(snapshot);
            });
        });
        share.setOnClickListener(view -> { File file = selected; confirm(() -> export(file)); });
        clear.setOnClickListener(view -> new AlertDialog.Builder(this).setMessage(R.string.api_log_clear_confirm)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.api_log_clear, (dialog, which) -> {
                    selected = null; content = null; preview.setText(""); busy(true);
                    int request = ++generation;
                    worker.execute(() -> { ApiResponseLogStore.clear(getApplicationContext()); main.post(() -> { if (valid(request)) reload(); }); });
                }).show());
        refresh.setOnClickListener(view -> reload());
        capture.setOnCheckedChangeListener((button, checked) -> {
            if (updatingSwitch) return;
            ApiResponseLogStore.setEnabled(getApplicationContext(), checked); reload();
        });
    }
    @Override protected void onResume() { super.onResume(); reload(); }

    private void reload() {
        int request = ++generation; busy(true); selected = null; content = null; preview.setText("");
        worker.execute(() -> {
            File[] found = ApiResponseLogStore.listLogs(getApplicationContext());
            boolean enabled = ApiResponseLogStore.enabled(getApplicationContext());
            List<String> labels = new ArrayList<>();
            for (File file : found) labels.add(getString(R.string.api_log_item, file.getName(),
                    android.text.format.Formatter.formatShortFileSize(getApplicationContext(), file.length()),
                    java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(file.lastModified()))));
            main.post(() -> {
                if (!valid(request)) return;
                files.clear(); java.util.Collections.addAll(files, found); adapter.clear(); adapter.addAll(labels);
                updatingSwitch = true; capture.setChecked(enabled); updatingSwitch = false;
                empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE); busy(false);
            });
        });
    }
    private void select(File file) {
        int request = ++generation; selected = file; content = null; busy(true); preview.setText(R.string.api_log_loading);
        worker.execute(() -> {
            try {
                String text = ApiResponseLogStore.read(getApplicationContext(), file);
                main.post(() -> {
                    if (!valid(request)) return;
                    content = text;
                    preview.setText(text.length() <= PREVIEW_CHARS ? text : text.substring(0, PREVIEW_CHARS) + "\n" + getString(R.string.api_log_preview_truncated));
                    busy(false);
                });
            } catch (Exception error) { main.post(() -> { if (valid(request)) { selected = null; preview.setText(R.string.api_log_unavailable); busy(false); } }); }
        });
    }
    private void export(File file) {
        int request = ++generation; busy(true);
        worker.execute(() -> {
            try {
                File copy = ApiResponseLogStore.export(getApplicationContext(), file);
                main.post(() -> {
                    if (!valid(request)) return;
                    busy(false);
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", copy);
                    Intent send = new Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newRawUri("Sanitized diagnostic", uri));
                    startActivity(Intent.createChooser(send, getString(R.string.api_log_share)));
                });
            } catch (Exception error) { main.post(() -> { if (valid(request)) { busy(false); message(R.string.api_log_unavailable); } }); }
        });
    }
    private void confirm(Runnable action) {
        new AlertDialog.Builder(this).setTitle(R.string.api_log_export_title).setMessage(R.string.api_log_export_warning)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(android.R.string.ok, (dialog, which) -> action.run()).show();
    }
    private void copy(String text) {
        ClipData clip = ClipData.newPlainText("Sanitized diagnostic", text);
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            android.os.PersistableBundle extras = new android.os.PersistableBundle(); extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
        }
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip); message(R.string.copied);
    }
    private boolean valid(int request) { return request == generation && !isDestroyed(); }
    private void busy(boolean value) {
        busy = value;
        boolean ready = !busy && selected != null && content != null;
        copyPath.setEnabled(ready); copyContent.setEnabled(ready); share.setEnabled(ready);
        list.setEnabled(!busy); clear.setEnabled(!busy); refresh.setEnabled(!busy); capture.setEnabled(!busy && BuildConfig.DEBUG);
    }
    private void message(int resource) { android.widget.Toast.makeText(this, resource, android.widget.Toast.LENGTH_SHORT).show(); }
    @Override protected void onDestroy() { generation++; worker.shutdownNow(); super.onDestroy(); }
}
