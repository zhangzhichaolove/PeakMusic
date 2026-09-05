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
import androidx.core.content.ContextCompat;
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
import com.chao.peakmusic.service.MusicService;
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
        toolbar = findViewById(R.id.library_toolbar);
        BarUtils.applyTopInset(toolbar);
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
        showRoot();
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
        if (item.getItemId() == R.id.action_new_playlist) {
            showCreatePlaylistDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_clear_music_history) {
            repository.clearHistory(this::showRoot);
            return true;
        }
        if (item.getItemId() == R.id.action_delete_playlist && selectedPlaylistId >= 0) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_playlist)
                    .setMessage(getString(R.string.delete_playlist_confirm, selectedPlaylistName))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (dialog, which) ->
                            repository.deletePlaylist(selectedPlaylistId, () -> {
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
        selectedPlaylistId = -1;
        selectedPlaylistName = null;
        invalidateOptionsMenu();
        if (MODE_HISTORY.equals(mode)) {
            toolbar.setTitle(R.string.recently_played);
            repository.loadHistory(this::showTracks);
        } else if (MODE_PLAYLISTS.equals(mode)) {
            toolbar.setTitle(R.string.playlists);
            repository.loadPlaylists(this::showPlaylists);
        } else {
            toolbar.setTitle(R.string.music_favorites);
            repository.loadFavorites(this::showTracks);
        }
    }

    private void showTracks(List<MusicTrackEntity> tracks) {
        if (trackAdapter == null) {
            trackAdapter = new MusicLibraryAdapter();
            trackAdapter.setListener(new MusicLibraryAdapter.Listener() {
                @Override
                public void onClick(int position, MusicTrackEntity track) {
                    ArrayList<MusicTrackEntity> queue = new ArrayList<>(trackAdapter.getCurrentList());
                    Intent intent = MusicService.createQueueIntent(
                            MusicLibraryActivity.this, queue, position);
                    ContextCompat.startForegroundService(MusicLibraryActivity.this, intent);
                }

                @Override
                public void onLongClick(int position, MusicTrackEntity track) {
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
        trackAdapter.submitList(new ArrayList<>(tracks));
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
    }

    private void openPlaylist(PlaylistSummary playlist) {
        selectedPlaylistId = playlist.id;
        selectedPlaylistName = playlist.name;
        toolbar.setTitle(playlist.name);
        invalidateOptionsMenu();
        loadPlaylistTracks();
    }

    private void loadPlaylistTracks() {
        repository.loadPlaylistTracks(selectedPlaylistId, this::showTracks);
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

    private void handleBack() {
        if (selectedPlaylistId >= 0) {
            showRoot();
        } else {
            finish();
        }
    }

    private void updateMenu(Menu menu) {
        if (menu == null) return;
        menu.findItem(R.id.action_new_playlist).setVisible(
                MODE_PLAYLISTS.equals(mode) && selectedPlaylistId < 0);
        menu.findItem(R.id.action_clear_music_history).setVisible(
                MODE_HISTORY.equals(mode));
        menu.findItem(R.id.action_delete_playlist).setVisible(selectedPlaylistId >= 0);
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
