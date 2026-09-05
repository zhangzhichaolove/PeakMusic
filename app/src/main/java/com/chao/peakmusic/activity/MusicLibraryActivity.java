package com.chao.peakmusic.activity;

import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.adapter.MusicLibraryAdapter;
import com.chao.peakmusic.data.MusicLibraryRepository;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.data.PlaylistSummary;
import com.chao.peakmusic.databinding.ItemPlaylistBinding;
import com.chao.peakmusic.utils.BarUtils;
import com.chao.peakmusic.utils.MusicActions;
import com.chao.peakmusic.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;

public class MusicLibraryActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_FAVORITES = "favorites";
    public static final String MODE_HISTORY = "history";
    public static final String MODE_PLAYLISTS = "playlists";

    private Toolbar toolbar;
    private RecyclerView list;
    private TextView emptyView;
    private MusicLibraryAdapter trackAdapter;
    private PlaylistAdapter playlistAdapter;
    private MusicLibraryRepository repository;
    private String mode;
    private long selectedPlaylistId = -1;
    private String selectedPlaylistName;
    private int requestGeneration;
    private LibrarySelection selection;

    public static Intent intent(Context context, String mode) {
        return new Intent(context, MusicLibraryActivity.class).putExtra(EXTRA_MODE, mode);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BarUtils.setWindow(this);
        setContentView(R.layout.activity_music_library);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        repository = MusicLibraryRepository.get(this);
        selection = new androidx.lifecycle.ViewModelProvider(this).get(LibrarySelection.class);
        toolbar = findViewById(R.id.library_toolbar);
        BarUtils.applyPageInsets(findViewById(android.R.id.content), toolbar);
        list = findViewById(R.id.library_list);
        emptyView = findViewById(R.id.library_empty);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.menu_setting_back);
        toolbar.setNavigationOnClickListener(view -> handleBack());
        list.setLayoutManager(new LinearLayoutManager(this));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });
        if (savedInstanceState != null) {
            selectedPlaylistId = savedInstanceState.getLong("playlist_id", -1);
            selectedPlaylistName = savedInstanceState.getString("playlist_name");
        }
        if (selectedPlaylistId >= 0) {
            setTitle(selectedPlaylistName); toolbar.setTitle(selectedPlaylistName);
            loadPlaylistTracks();
        } else showRoot();
        selection.changes.observe(this, ignored -> selectionChanged());
    }

    @Override protected void onSaveInstanceState(android.os.Bundle state) {
        state.putLong("playlist_id", selectedPlaylistId);
        state.putString("playlist_name", selectedPlaylistName);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedPlaylistId >= 0) {
            loadPlaylistTracks();
        } else {
            showRoot();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.music_library, menu);
        updateMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenu(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (selection.busy) { ToastUtils.showToast(getString(R.string.batch_processing)); return true; }
        int action = item.getItemId();
        if (action == R.id.action_select_tracks) { selection.start(); return true; }
        if (action == R.id.action_cancel_selection) { selection.clear(); return true; }
        if (action == R.id.action_select_all_tracks) {
            if (selection.keys.size() == trackAdapter.getItemCount()) selection.keys.clear();
            else for (MusicTrackEntity track : trackAdapter.getCurrentList()) selection.keys.add(track.source);
            selection.changed(); return true;
        }
        if (action == R.id.action_batch_playlist) { chooseBatchPlaylist(); return true; }
        if (action == R.id.action_batch_favorite) { favoriteBatch(); return true; }
        if (action == R.id.action_batch_remove) { removeBatch(); return true; }
        if (item.getItemId() == R.id.action_new_playlist) {
            showCreatePlaylistDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_rename_playlist && selectedPlaylistId >= 0) {
            showRenamePlaylistDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_clear_music_history) {
            new AlertDialog.Builder(this).setMessage(R.string.clear_history_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.clear, (dialog, which) -> repository.clearHistory(success -> {
                        if (success) showRoot(); else ToastUtils.showToast(getString(R.string.library_write_failed));
                    })).show();
            return true;
        }
        if (item.getItemId() == R.id.action_delete_playlist && selectedPlaylistId >= 0) {
            long deletingId = selectedPlaylistId;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_playlist)
                    .setMessage(getString(R.string.delete_playlist_confirm, selectedPlaylistName))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (dialog, which) ->
                            repository.deletePlaylist(deletingId, success -> {
                                if (isDestroyed() || selectedPlaylistId != deletingId) return;
                                if (!success) { ToastUtils.showToast(getString(R.string.library_write_failed)); return; }
                                selectedPlaylistId = -1;
                                selectedPlaylistName = null;
                                showRoot();
                                invalidateOptionsMenu();
                            }))
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showRoot() {
        if (isDestroyed()) return;
        int request = ++requestGeneration;
        selectedPlaylistId = -1;
        selectedPlaylistName = null;
        invalidateOptionsMenu();
        if (MODE_HISTORY.equals(mode)) {
            setTitle(R.string.recently_played);
            toolbar.setTitle(R.string.recently_played);
            repository.loadHistory(tracks -> { if (!isDestroyed() && request == requestGeneration) showTracks(tracks); });
        } else if (MODE_PLAYLISTS.equals(mode)) {
            setTitle(R.string.playlists);
            toolbar.setTitle(R.string.playlists);
            repository.loadPlaylists(playlists -> { if (!isDestroyed() && request == requestGeneration) showPlaylists(playlists); });
        } else {
            setTitle(R.string.music_favorites);
            toolbar.setTitle(R.string.music_favorites);
            repository.loadFavorites(tracks -> { if (!isDestroyed() && request == requestGeneration) showTracks(tracks); });
        }
    }

    private void showTracks(List<MusicTrackEntity> tracks) {
        if (trackAdapter == null) {
            trackAdapter = new MusicLibraryAdapter();
            trackAdapter.setListener(new MusicLibraryAdapter.Listener() {
                @Override
                public void onClick(int position, MusicTrackEntity track) {
                    if (selection.busy) return;
                    if (selection.active) { selection.toggle(track.source); return; }
                    com.chao.peakmusic.service.PlaybackStorage.get(MusicLibraryActivity.this)
                            .play(trackAdapter.getCurrentList(), position);
                }

                @Override
                public void onLongClick(int position, MusicTrackEntity track) {
                    if (selection.busy) return;
                    if (selection.active) { selection.toggle(track.source); return; }
                    if (selectedPlaylistId >= 0) {
                        new AlertDialog.Builder(MusicLibraryActivity.this)
                                .setTitle(track.name)
                                .setItems(new String[]{getString(R.string.remove_from_playlist),
                                                getString(R.string.music_details)},
                                        (dialog, which) -> {
                                            if (which == 0) {
                                                repository.removeFromPlaylist(selectedPlaylistId,
                                                        track.source,
                                                        MusicLibraryActivity.this::loadPlaylistTracks);
                                            } else {
                                                MusicActions.show(MusicLibraryActivity.this, track);
                                            }
                                        })
                                .show();
                    } else {
                        MusicActions.show(MusicLibraryActivity.this, track,
                                MusicLibraryActivity.this::showRoot);
                    }
                }
            });
        }
        list.setAdapter(trackAdapter);
        int generation = requestGeneration;
        trackAdapter.submitList(new ArrayList<>(tracks), () -> {
            if (isDestroyed() || generation != requestGeneration || list.getAdapter() != trackAdapter) return;
            java.util.Set<String> visible = new java.util.HashSet<>();
            for (MusicTrackEntity track : tracks) visible.add(track.source);
            selection.keys.retainAll(visible);
            selection.changed();
        });
        emptyView.setText(R.string.music_library_empty);
        emptyView.setVisibility(tracks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPlaylists(List<PlaylistSummary> playlists) {
        if (playlistAdapter == null) {
            playlistAdapter = new PlaylistAdapter();
        }
        list.setAdapter(playlistAdapter);
        playlistAdapter.setItems(playlists);
        emptyView.setText(R.string.playlists_empty);
        emptyView.setVisibility(playlists.isEmpty() ? View.VISIBLE : View.GONE);
        invalidateOptionsMenu();
    }

    private void openPlaylist(PlaylistSummary playlist) {
        selectedPlaylistId = playlist.id;
        selectedPlaylistName = playlist.name;
        setTitle(playlist.name);
        toolbar.setTitle(playlist.name);
        invalidateOptionsMenu();
        loadPlaylistTracks();
    }

    private void loadPlaylistTracks() {
        if (isDestroyed() || selectedPlaylistId < 0) return;
        int request = ++requestGeneration;
        repository.loadPlaylistTracks(selectedPlaylistId, tracks -> {
            if (!isDestroyed() && request == requestGeneration) showTracks(tracks);
        });
    }

    private void showCreatePlaylistDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(this)
                .setTitle(R.string.new_playlist)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialog, which) ->
                        repository.createPlaylist(input.getText().toString(), id -> {
                            if (id < 0) {
                                ToastUtils.showToast(getString(R.string.playlist_name_required));
                            }
                            showRoot();
                        }))
                .show();
    }

    private void showRenamePlaylistDialog() {
        long playlistId = selectedPlaylistId;
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(selectedPlaylistName);
        input.selectAll();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.rename_playlist).setView(input)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError(getString(R.string.playlist_name_required)); return; }
            repository.renamePlaylist(playlistId, name, renamed -> {
                if (isDestroyed() || !dialog.isShowing()) return;
                if (!renamed) { input.setError(getString(R.string.library_write_failed)); return; }
                dialog.dismiss();
                if (playlistId == selectedPlaylistId) { selectedPlaylistName = name; setTitle(name); toolbar.setTitle(name); }
            });
        }));
        dialog.show();
    }

    private void handleBack() {
        if (selection.busy) { ToastUtils.showToast(getString(R.string.batch_processing)); return; }
        if (selection.active) { selection.clear(); return; }
        if (selectedPlaylistId >= 0) {
            showRoot();
        } else {
            finish();
        }
    }

    private void updateMenu(Menu menu) {
        if (menu == null || selection == null) return;
        boolean trackPage = !MODE_PLAYLISTS.equals(mode) || selectedPlaylistId >= 0;
        boolean hasTracks = trackPage && list.getAdapter() == trackAdapter && trackAdapter != null && trackAdapter.getItemCount() > 0;
        boolean active = selection.active;
        boolean chosen = !selection.keys.isEmpty();
        menu.findItem(R.id.action_select_tracks).setVisible(hasTracks && !active);
        menu.findItem(R.id.action_select_all_tracks).setVisible(active && hasTracks).setEnabled(!selection.busy)
                .setTitle(hasTracks && chosen && selection.keys.size() == trackAdapter.getItemCount() ? R.string.deselect_all_tracks : R.string.select_all_tracks);
        menu.findItem(R.id.action_cancel_selection).setVisible(active).setEnabled(!selection.busy);
        menu.findItem(R.id.action_batch_playlist).setVisible(active).setEnabled(chosen && !selection.busy);
        menu.findItem(R.id.action_batch_favorite).setVisible(active).setEnabled(chosen && !selection.busy)
                .setTitle(allSelectedFavorite() ? R.string.unfavorite_selected : R.string.favorite_selected);
        menu.findItem(R.id.action_batch_remove).setVisible(active && selectedPlaylistId >= 0).setEnabled(chosen && !selection.busy);
        menu.findItem(R.id.action_new_playlist).setVisible(
                MODE_PLAYLISTS.equals(mode) && selectedPlaylistId < 0 && !active);
        menu.findItem(R.id.action_clear_music_history).setVisible(
                MODE_HISTORY.equals(mode) && !active);
        menu.findItem(R.id.action_rename_playlist).setVisible(selectedPlaylistId >= 0 && !active);
        menu.findItem(R.id.action_delete_playlist).setVisible(selectedPlaylistId >= 0 && !active);
    }

    private void selectionChanged() {
        Boolean result = selection.consumeResult();
        if (result != null) {
            ToastUtils.showToast(getString(result ? R.string.batch_complete : R.string.library_write_failed));
            if (result) selection.clear();
            if (selectedPlaylistId >= 0) loadPlaylistTracks(); else showRoot();
        }
        toolbar.setSubtitle(selection.busy ? getString(R.string.batch_processing)
                : selection.active ? getString(R.string.selected_track_count, selection.keys.size()) : null);
        if (trackAdapter != null) trackAdapter.setSelection(selection.active, selection.keys, selection.busy);
        invalidateOptionsMenu();
    }

    private List<String> selectedKeys() {
        List<String> keys = new ArrayList<>();
        if (trackAdapter != null) for (MusicTrackEntity track : trackAdapter.getCurrentList())
            if (selection.keys.contains(track.source)) keys.add(track.source);
        return keys;
    }

    private boolean allSelectedFavorite() {
        if (selection.keys.isEmpty() || trackAdapter == null) return false;
        for (MusicTrackEntity track : trackAdapter.getCurrentList())
            if (selection.keys.contains(track.source) && !track.favorite) return false;
        return true;
    }

    private void favoriteBatch() {
        List<String> keys = selectedKeys();
        if (keys.isEmpty()) return;
        boolean favorite = !allSelectedFavorite();
        LibrarySelection operation = selection;
        Runnable apply = () -> { operation.begin(); repository.setFavorites(keys, favorite, operation::complete); };
        if (favorite) apply.run();
        else new AlertDialog.Builder(this).setTitle(R.string.unfavorite_selected)
                .setMessage(getString(R.string.batch_unfavorite_confirm, keys.size()))
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.remove_favorite, (dialog, which) -> apply.run()).show();
    }

    private void removeBatch() {
        List<String> keys = selectedKeys();
        if (keys.isEmpty() || selectedPlaylistId < 0) return;
        long playlistId = selectedPlaylistId;
        LibrarySelection operation = selection;
        new AlertDialog.Builder(this).setTitle(R.string.remove_selected_from_playlist)
                .setMessage(getString(R.string.batch_remove_confirm, keys.size()))
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.remove_from_playlist, (dialog, which) -> {
                    operation.begin(); repository.removeTracksFromPlaylist(playlistId, keys, operation::complete);
                }).show();
    }

    private void chooseBatchPlaylist() {
        List<String> keys = selectedKeys();
        if (keys.isEmpty()) return;
        repository.loadPlaylists(playlists -> {
            if (isDestroyed() || !selection.active || selection.busy || !keys.equals(selectedKeys())) return;
            if (playlists.isEmpty()) { ToastUtils.showToast(getString(R.string.playlists_empty_create_first)); return; }
            String[] names = new String[playlists.size()];
            for (int i = 0; i < names.length; i++) names[i] = playlists.get(i).name;
            LibrarySelection operation = selection;
            new AlertDialog.Builder(this).setTitle(R.string.choose_playlist)
                    .setItems(names, (dialog, which) -> {
                        operation.begin(); repository.addTracksToPlaylist(playlists.get(which).id, keys, operation::complete);
                    }).setNegativeButton(R.string.cancel, null).show();
        });
    }

    private final class PlaylistAdapter extends ListAdapter<PlaylistSummary, PlaylistAdapter.Holder> {
        PlaylistAdapter() {
            super(new DiffUtil.ItemCallback<PlaylistSummary>() {
                @Override
                public boolean areItemsTheSame(@NonNull PlaylistSummary oldItem,
                                               @NonNull PlaylistSummary newItem) {
                    return oldItem.id == newItem.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull PlaylistSummary oldItem,
                                                  @NonNull PlaylistSummary newItem) {
                    return oldItem.trackCount == newItem.trackCount
                            && java.util.Objects.equals(oldItem.name, newItem.name);
                }
            });
        }

        void setItems(List<PlaylistSummary> playlists) {
            submitList(new ArrayList<>(playlists));
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemPlaylistBinding.inflate(
                    android.view.LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            PlaylistSummary playlist = getItem(position);
            holder.name.setText(playlist.name);
            holder.count.setText(getString(R.string.playlist_track_count, playlist.trackCount));
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView count;

            Holder(ItemPlaylistBinding binding) {
                super(binding.getRoot());
                name = binding.playlistName;
                count = binding.playlistCount;
                itemView.setOnClickListener(view -> {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        openPlaylist(getItem(position));
                    }
                });
            }
        }
    }
}
