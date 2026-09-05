package com.chao.peakmusic.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.MainActivity;
import com.chao.peakmusic.R;
import com.chao.peakmusic.adapter.LocalGroupAdapter;
import com.chao.peakmusic.adapter.LocalMusicAdapter;
import com.chao.peakmusic.base.BaseFragment;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.local.LocalLibraryIndex;
import com.chao.peakmusic.local.LocalLibraryIndex.Group;
import com.chao.peakmusic.local.LocalLibraryIndex.Section;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.service.PlaybackStorage;
import com.chao.peakmusic.utils.MusicActions;
import com.chao.peakmusic.utils.ScanningUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local browsing uses the permission-scoped scan, never a new file traversal. */
public class LocalMusicFragment extends BaseFragment {
    private RecyclerView musicList;
    private TextView emptyView;
    private Button refresh, groupBack;
    private Spinner sections;
    private ScanningUtils.State state = ScanningUtils.State.IDLE;
    private LocalMusicAdapter adapter;
    private LocalGroupAdapter groupAdapter;
    private ArrayList<SongModel> music;
    private LocalLibraryIndex index;
    private Section section = Section.ALL, renderedSection;
    private String groupKey, groupTitle, renderedGroupKey;
    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;
    private boolean building;

    public static LocalMusicFragment newInstance() { return new LocalMusicFragment(); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            int position = saved.getInt("local_section", 0);
            if (position >= 0 && position < Section.values().length) section = Section.values()[position];
            groupKey = saved.getString("local_group_key"); groupTitle = saved.getString("local_group_title");
        }
    }

    @Override public void onSaveInstanceState(Bundle saved) {
        saved.putInt("local_section", section.ordinal());
        saved.putString("local_group_key", groupKey); saved.putString("local_group_title", groupTitle);
        super.onSaveInstanceState(saved); // Only small navigation values, never the scanned media list.
    }

    @Override public int getLayout() { return R.layout.fragment_local_music; }

    @Override public void initView() {
        musicList = rootView.findViewById(R.id.local_music_list);
        emptyView = rootView.findViewById(R.id.local_empty);
        refresh = rootView.findViewById(R.id.local_refresh);
        groupBack = rootView.findViewById(R.id.local_group_back);
        sections = rootView.findViewById(R.id.local_sections);
        refresh.setOnClickListener(view -> ((MainActivity) requireActivity()).requestLocalMusic());
        groupBack.setOnClickListener(view -> navigateUp());
        ArrayAdapter<CharSequence> choices = ArrayAdapter.createFromResource(requireContext(),
                R.array.local_sections, android.R.layout.simple_spinner_item);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sections.setAdapter(choices); sections.setSelection(section.ordinal());
        sections.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Section chosen = Section.values()[position];
                if (chosen != section) { section = chosen; groupKey = null; groupTitle = null; render(); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        musicList.setLayoutManager(new LinearLayoutManager(mContext));
        adapter = new LocalMusicAdapter();
        groupAdapter = new LocalGroupAdapter(group -> {
            groupKey = group.key; groupTitle = LocalGroupAdapter.title(requireContext(), group); render();
        });
        ScanningUtils scanner = ScanningUtils.getInstance(mContext);
        state = scanner.getState();
        // Read the latest snapshot after a view/configuration recreation, not a detached Activity's array.
        music = scanner.getMusic();
        rebuildIndex();
    }

    @Override public void initListener() {
        adapter.setListener(new LocalMusicAdapter.onItemClick() {
            @Override public void itemClickListener(int position) {
                if (state == ScanningUtils.State.UNAUTHORIZED) return;
                // Use exactly the committed, displayed group snapshot. A rescan cannot shift this index.
                List<SongModel> visible = adapter.getCurrentList();
                ArrayList<MusicTrackEntity> queue = new ArrayList<>();
                for (SongModel song : visible) queue.add(MusicTrackEntity.from(song));
                PlaybackStorage.get(requireContext()).play(queue, position);
            }
            @Override public void itemLongClickListener(int position) {
                if (state != ScanningUtils.State.UNAUTHORIZED)
                    MusicActions.show(requireActivity(), MusicTrackEntity.from(adapter.getCurrentList().get(position)));
            }
        });
    }

    public void setMusic(ArrayList<SongModel> music) { this.music = music; rebuildIndex(); }

    public void setScanState(ScanningUtils.State state) {
        this.state = state;
        if (state == ScanningUtils.State.UNAUTHORIZED) {
            generation++; music = null; index = null; building = false;
        }
        render();
    }

    private void rebuildIndex() {
        if (adapter == null) return;
        int request = ++generation;
        if (music == null) { index = null; building = false; render(); return; }
        List<SongModel> snapshot = new ArrayList<>(music);
        building = true; render();
        indexExecutor.execute(() -> {
            LocalLibraryIndex result = new LocalLibraryIndex(snapshot);
            main.post(() -> {
                if (request != generation || adapter == null) return;
                index = result; building = false; render();
            });
        });
    }

    /** Both system Back and the visible breadcrumb return to the current category, not the home screen. */
    public boolean navigateUp() {
        if (groupKey == null || musicList == null) return false;
        groupKey = null; groupTitle = null; render(); return true;
    }

    private void render() {
        if (musicList == null) return;
        if (section != renderedSection || !Objects.equals(groupKey, renderedGroupKey)) {
            adapter.submitList(null); groupAdapter.submitList(null);
            renderedSection = section; renderedGroupKey = groupKey;
        }
        boolean showGroups = section != Section.ALL && groupKey == null;
        List<SongModel> tracks = Collections.emptyList();
        List<Group> groups = Collections.emptyList();
        if (index != null) {
            if (showGroups) groups = index.groups(section);
            else if (section == Section.ALL) tracks = index.all;
            else {
                Group selected = index.find(section, groupKey);
                if (selected != null) { tracks = selected.songs; groupTitle = LocalGroupAdapter.title(requireContext(), selected); }
            }
        }
        RecyclerView.Adapter<?> displayed = showGroups ? groupAdapter : adapter;
        if (musicList.getAdapter() != displayed) musicList.setAdapter(displayed);
        if (showGroups) groupAdapter.submitList(groups); else adapter.submitList(tracks);
        boolean empty = showGroups ? groups.isEmpty() : tracks.isEmpty();
        boolean inaccessible = state == ScanningUtils.State.UNAUTHORIZED || state == ScanningUtils.State.ERROR;
        emptyView.setVisibility(empty || inaccessible ? View.VISIBLE : View.GONE);
        musicList.setVisibility(empty || inaccessible ? View.GONE : View.VISIBLE);
        sections.setEnabled(!inaccessible);
        groupBack.setVisibility(groupKey != null && !inaccessible ? View.VISIBLE : View.GONE);
        groupBack.setText(getString(R.string.local_group_back,
                getResources().getStringArray(R.array.local_sections)[section.ordinal()], groupTitle == null ? "" : groupTitle));
        refresh.setEnabled(state != ScanningUtils.State.SCANNING && !building);
        refresh.setText(state == ScanningUtils.State.UNAUTHORIZED ? R.string.local_grant_permission
                : state == ScanningUtils.State.SCANNING || building ? R.string.local_scanning : R.string.local_refresh);
        emptyView.setText(state == ScanningUtils.State.UNAUTHORIZED ? R.string.local_permission_explanation
                : state == ScanningUtils.State.ERROR ? R.string.local_scan_failed
                : state == ScanningUtils.State.SCANNING || building ? R.string.local_scanning
                : groupKey != null ? R.string.local_group_empty : R.string.local_music_empty);
    }

    @Override public void onDestroyView() {
        generation++;
        musicList = null; emptyView = null; refresh = null; groupBack = null; sections = null;
        adapter = null; groupAdapter = null; renderedSection = null;
        super.onDestroyView();
    }
    @Override public void onDestroy() { indexExecutor.shutdownNow(); super.onDestroy(); }
}
