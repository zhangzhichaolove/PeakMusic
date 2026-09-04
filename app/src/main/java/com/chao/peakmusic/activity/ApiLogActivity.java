package com.chao.peakmusic.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.chao.peakmusic.R;
import com.chao.peakmusic.base.ApiResponseLogStore;
import com.chao.peakmusic.utils.ToastUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Lets developers inspect, copy, share, and clear complete oversized API responses. */
public final class ApiLogActivity extends AppCompatActivity {
    private final List<File> files = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView preview;
    private TextView empty;
    private Button copyPath;
    private Button copyContent;
    private Button share;
    private File selected;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_logs);
        setTitle(R.string.api_logs_title);
        preview = findViewById(R.id.api_log_preview);
        empty = findViewById(R.id.api_log_empty);
        copyPath = findViewById(R.id.api_log_copy_path);
        copyContent = findViewById(R.id.api_log_copy_content);
        share = findViewById(R.id.api_log_share);
        ListView list = findViewById(R.id.api_log_list);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> select(files.get(position)));
        copyPath.setOnClickListener(view -> copy("API log path", selected.getAbsolutePath()));
        copyContent.setOnClickListener(view -> copy("API response", read(selected)));
        share.setOnClickListener(view -> share(selected));
        findViewById(R.id.api_log_clear).setOnClickListener(view -> {
            ApiResponseLogStore.clear(this);
            reload();
        });
        reload();
    }

    private void reload() {
        files.clear();
        adapter.clear();
        for (File file : ApiResponseLogStore.listLogs(this)) {
            files.add(file);
            adapter.add(getString(R.string.api_log_item, file.getName(), formatSize(file.length()),
                    DateFormat.getDateFormat(this).format(new Date(file.lastModified())) + " "
                            + DateFormat.getTimeFormat(this).format(new Date(file.lastModified()))));
        }
        adapter.notifyDataSetChanged();
        selected = null;
        preview.setText("");
        empty.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        setActionsEnabled(false);
    }

    private void select(File file) {
        selected = file;
        preview.setText(read(file));
        setActionsEnabled(true);
    }

    private String read(File file) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            }
            try (okio.BufferedSource source = okio.Okio.buffer(okio.Okio.source(file))) {
                return source.readUtf8();
            }
        } catch (IOException error) {
            return getString(R.string.api_log_read_failed, error.getMessage());
        }
    }

    private void copy(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        ToastUtils.showToast(getString(R.string.copied));
    }

    private void share(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, getString(R.string.api_log_share)));
    }

    private void setActionsEnabled(boolean enabled) {
        copyPath.setEnabled(enabled);
        copyContent.setEnabled(enabled);
        share.setEnabled(enabled);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        return String.format(Locale.US, "%.1f KiB", bytes / 1024d);
    }
}
