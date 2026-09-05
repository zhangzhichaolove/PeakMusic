package com.chao.peakmusic.activity;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.adapter.MusicLibraryAdapter;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.base.ApiUrl;
import com.chao.peakmusic.base.ServiceFactory;
import com.chao.peakmusic.search.MusicSearchController;
import com.chao.peakmusic.catalog.MusicPageFooter;
import com.chao.peakmusic.catalog.MusicPageController;
import com.google.android.material.tabs.TabLayout;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.utils.BarUtils;
import com.chao.peakmusic.utils.MusicActions;
import com.chao.peakmusic.utils.ScanningUtils;

import java.util.ArrayList;
import java.util.List;

public class MusicSearchActivity extends AppCompatActivity {
    private final List<MusicTrackEntity> localTracks = new ArrayList<>();
    private MusicLibraryAdapter adapter;
    private TextView emptyView;
    private View retry;
    private View loading;
    private EditText input;
    private TabLayout scopes;
    private MusicSearchController controller;
    private MusicPageFooter footer;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BarUtils.setWindow(this);
        setContentView(R.layout.activity_music_search);
        Toolbar toolbar = findViewById(R.id.search_toolbar);
        BarUtils.applyPageInsets(findViewById(android.R.id.content), toolbar);
        setSupportActionBar(toolbar);
        setTitle(R.string.search_music);
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
                com.chao.peakmusic.service.PlaybackStorage.get(MusicSearchActivity.this)
                        .play(adapter.getCurrentList(), position);
            }

            @Override
            public void onLongClick(int position, MusicTrackEntity track) {
                MusicActions.show(MusicSearchActivity.this, track);
            }
        });
        collectLocalTracks();
        input = findViewById(R.id.search_input);
        scopes = findViewById(R.id.search_scopes);
        scopes.addTab(scopes.newTab().setText(R.string.online_music));
        scopes.addTab(scopes.newTab().setText(R.string.local_music));
        if (savedInstanceState != null && savedInstanceState.getInt("search_scope", 0) == 1) scopes.getTabAt(1).select();
        retry = findViewById(R.id.search_retry);
        loading = findViewById(R.id.search_loading);
        footer = new MusicPageFooter(findViewById(android.R.id.content), () -> controller.loadNext());
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recycler, int dx, int dy) {
                if (dy > 0 && ((LinearLayoutManager) recycler.getLayoutManager()).findLastVisibleItemPosition()
                        >= adapter.getItemCount() - 4 && controller.onlinePage() != null
                        && controller.onlinePage().state() != MusicPageController.State.MORE_ERROR) controller.loadNext();
            }
        });
        controller = new MusicSearchController(ServiceFactory.getInstance().createService(ApiUrl.class),
                Schedulers.io(), AndroidSchedulers.mainThread(), (state, tracks) -> {
                    adapter.submitList(tracks);
                    boolean content = !tracks.isEmpty();
                    list.setVisibility(content ? View.VISIBLE : View.GONE);
                    emptyView.setVisibility(content ? View.GONE : View.VISIBLE);
                    loading.setVisibility(state == MusicSearchController.State.LOADING ? View.VISIBLE : View.GONE);
                    retry.setVisibility(state == MusicSearchController.State.ERROR ? View.VISIBLE : View.GONE);
                    int message = state == MusicSearchController.State.PROMPT ? R.string.search_online_prompt
                            : state == MusicSearchController.State.LOADING ? R.string.music_loading
                            : state == MusicSearchController.State.ERROR ? R.string.search_failed : R.string.search_no_results;
                    if (scopes.getSelectedTabPosition() == 1 && localTracks.isEmpty()) message = R.string.search_local_unavailable;
                    emptyView.setText(message);
                    footer.render(controller.onlinePage());
                });
        retry.setOnClickListener(view -> search(true));
        scopes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { search(true); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            search(true);
            return true;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                search(false);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        search(true);
    }

    private void collectLocalTracks() {
        localTracks.clear();
        List<SongModel> local = ScanningUtils.getInstance(this).getMusic();
        if (local == null) return;
        for (SongModel song : local) {
            if (song != null && song.getPath() != null) localTracks.add(MusicTrackEntity.from(song));
        }
    }

    private void search(boolean immediate) {
        controller.search(input.getText().toString(), scopes.getSelectedTabPosition() == 0, localTracks, immediate);
    }

    @Override protected void onSaveInstanceState(android.os.Bundle state) {
        state.putInt("search_scope", scopes.getSelectedTabPosition());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        controller.close();
        super.onDestroy();
    }
}
