package com.chao.peakmusic.activity;

import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.adapter.MusicLibraryAdapter;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.utils.BarUtils;
import com.chao.peakmusic.utils.MusicActions;
import com.chao.peakmusic.utils.MusicDataUtils;
import com.chao.peakmusic.utils.ScanningUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MusicSearchActivity extends AppCompatActivity {
    private final List<MusicTrackEntity> allTracks = new ArrayList<>();
    private final List<MusicTrackEntity> results = new ArrayList<>();
    private MusicLibraryAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BarUtils.setWindow(this);
        setContentView(R.layout.activity_music_search);
        Toolbar toolbar = findViewById(R.id.search_toolbar);
        BarUtils.applyTopInset(toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.search_music);
        toolbar.setNavigationIcon(R.drawable.menu_setting_back);
        toolbar.setNavigationOnClickListener(view -> finish());
        emptyView = findViewById(R.id.search_empty);
        RecyclerView list = findViewById(R.id.search_results);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MusicLibraryAdapter();
        list.setAdapter(adapter);
        adapter.setListener(new MusicLibraryAdapter.Listener() {
            @Override
            public void onClick(int position, MusicTrackEntity track) {
                Intent intent = MusicService.createQueueIntent(MusicSearchActivity.this,
                        new ArrayList<>(results), position);
                ContextCompat.startForegroundService(MusicSearchActivity.this, intent);
            }

            @Override
            public void onLongClick(int position, MusicTrackEntity track) {
                MusicActions.show(MusicSearchActivity.this, track);
            }
        });
        collectTracks();

        EditText input = findViewById(R.id.search_input);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        filter("");
    }

    private void collectTracks() {
        List<MusicModel> online = MusicDataUtils.getInstance().getMusicList();
        if (online != null) {
            for (MusicModel music : online) {
                if (music != null && music.getMp3() != null) {
                    allTracks.add(MusicTrackEntity.from(music));
                }
            }
        }
        List<SongModel> local = ScanningUtils.getInstance(this).getMusic();
        if (local != null) {
            for (SongModel song : local) {
                if (song != null && song.getPath() != null) {
                    allTracks.add(MusicTrackEntity.from(song));
                }
            }
        }
    }

    private void filter(String query) {
        String normalized = query.trim().toLowerCase(Locale.getDefault());
        results.clear();
        for (MusicTrackEntity track : allTracks) {
            if (normalized.isEmpty() || contains(track.name, normalized)
                    || contains(track.artist, normalized) || contains(track.album, normalized)) {
                results.add(track);
            }
        }
        adapter.submitList(new ArrayList<>(results));
        emptyView.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.getDefault()).contains(query);
    }
}
