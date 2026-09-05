package com.chao.peakmusic.activity;

import android.animation.ObjectAnimator;
import android.support.v4.media.session.PlaybackStateCompat;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Button;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.ActivityCall;
import com.chao.peakmusic.R;
import com.chao.peakmusic.lyrics.LyricsLoader;
import com.chao.peakmusic.lyrics.LyricOffsetStore;
import androidx.appcompat.app.AlertDialog;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.data.MusicLibraryRepository;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.utils.LyricsParser;
import com.chao.peakmusic.utils.LyricsParser.LyricLine;
import com.chao.peakmusic.widget.MusicAlbumView;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by Chao on 2017-12-19.
 */

public class MusicPlayActivity extends BaseActivity {
    public static final String EXTRA_MUSIC = "music";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_SINGER = "singer";
    public static final String EXTRA_IMAGE = "image";

    private MusicAlbumView albumMusic;
    private TextView musicName;
    private TextView musicSinger;
    private RecyclerView lyricsList;
    private LinearLayoutManager lyricsLayoutManager;
    private LyricsAdapter lyricsAdapter;
    private SeekBar playbackProgress;
    private TextView currentTime;
    private TextView totalTime;
    private Button followLyrics;
    private Button playbackToggle;
    private TextView playbackStatus;
    private ObjectAnimator albumAnimator;
    private final LyricsLoader lyricsLoader = new LyricsLoader();
    private LyricOffsetStore offsetStore;
    private String currentLyricsUrl;
    private Button lyricsRetry;
    private Button favoriteButton;
    private MusicTrackEntity currentTrack;
    private int favoriteGeneration;
    private AlertDialog calibrationDialog;
    private MusicAidlInterface musicService;
    private boolean serviceBound;
    private String trackTitle;
    private String currentTrackKey;
    private long lyricOffsetMs;
    private TextView lyricOffsetLabel;
    private boolean hasTimedLyrics;
    private boolean userSeeking;
    private boolean followCurrentLyric = true;
    private int currentLyricLine = -1;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateCurrentLyric();
            progressHandler.postDelayed(this, 300);
        }
    };

    @Override
    public int getLayout() {
        return R.layout.activity_play_music;
    }

    @Override
    public void initView() {
        com.chao.peakmusic.utils.BarUtils.applyBottomInsets(findViewById(android.R.id.content));
        offsetStore = new LyricOffsetStore(this);
        albumMusic = findViewById(R.id.album_music);
        lyricsRetry = findViewById(R.id.lyrics_retry);
        lyricsRetry.setOnClickListener(view -> loadLyrics(currentLyricsUrl));
        favoriteButton = findViewById(R.id.playback_favorite);
        favoriteButton.setEnabled(false);
        favoriteButton.setOnClickListener(view -> toggleFavorite());
        findViewById(R.id.playback_mode).setOnClickListener(view -> showPlaybackMode());
        findViewById(R.id.lyrics_calibrate).setOnClickListener(view -> showCalibration());
        playbackToggle = findViewById(R.id.playback_toggle);
        playbackStatus = findViewById(R.id.playback_status);
        playbackToggle.setOnClickListener(view -> controlPlayback(0));
        findViewById(R.id.playback_previous).setOnClickListener(view -> controlPlayback(-1));
        findViewById(R.id.playback_next).setOnClickListener(view -> controlPlayback(1));
        musicName = findViewById(R.id.tv_music_name);
        musicSinger = findViewById(R.id.tv_music_singer);
        lyricsList = findViewById(R.id.lyrics_list);
        playbackProgress = findViewById(R.id.playback_progress);
        currentTime = findViewById(R.id.playback_current_time);
        totalTime = findViewById(R.id.playback_total_time);
        followLyrics = findViewById(R.id.lyrics_follow);
        lyricOffsetLabel = findViewById(R.id.lyric_offset_value);
        lyricOffsetMs = 0;
        updateLyricOffsetLabel();
        lyricsLayoutManager = new LinearLayoutManager(this);
        lyricsAdapter = new LyricsAdapter();
        lyricsList.setLayoutManager(lyricsLayoutManager);
        lyricsList.setAdapter(lyricsAdapter);
        // Highlight/line replacements should not fade lyrics out during calibration or track changes.
        lyricsList.setItemAnimator(null);
        playbackProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (musicService != null) {
                    try {
                        musicService.seekTo(seekBar.getProgress());
                    } catch (RemoteException ignored) {
                    }
                }
            }
        });
        lyricsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    followCurrentLyric = false;
                    followLyrics.setVisibility(View.VISIBLE);
                }
            }
        });
        followLyrics.setOnClickListener(view -> {
            followCurrentLyric = true;
            followLyrics.setVisibility(View.GONE);
            scrollToCurrentLyric();
        });
        lyricsList.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int padding = (bottom - top) / 2;
            if (lyricsList.getPaddingTop() != padding) lyricsList.setPadding(0, padding, 0, padding);
        });

        MusicModel music = IntentCompat.getParcelableExtra(
                getIntent(), EXTRA_MUSIC, MusicModel.class);
        String name = music == null ? getIntent().getStringExtra(EXTRA_NAME) : music.getName();
        String singer = music == null ? getIntent().getStringExtra(EXTRA_SINGER) : music.getSinger();
        String image = music == null ? getIntent().getStringExtra(EXTRA_IMAGE) : music.getImg();
        currentTrack = music == null ? null : MusicTrackEntity.from(music);
        currentTrackKey = currentTrack == null ? null : currentTrack.source;
        lyricOffsetMs = offsetStore.get(currentTrackKey);
        updateLyricOffsetLabel();
        updateFavorite();

        displayTrack(name, singer, image, music == null ? null : music.getLrc());

        startAlbumAnimation();
    }

    private void displayTrack(String name, String singer, String image, String lyrics) {
        name = TextUtils.isEmpty(name) ? getString(R.string.unknown_music) : name;
        singer = TextUtils.isEmpty(singer) ? getString(R.string.unknown_singer) : singer;
        trackTitle = name;
        setTitle(trackTitle);
        mToolbar.setTitle(trackTitle);
        musicName.setText(name);
        musicSinger.setText(singer);
        ImageLoaderV4.getInstance().load(this, albumMusic,
                TextUtils.isEmpty(image) ? R.drawable.default_cover : image);
        lyricsLoader.cancel();
        currentLyricsUrl = lyrics;
        currentLyricLine = -1;
        followCurrentLyric = true;
        followLyrics.setVisibility(View.GONE);
        loadLyrics(lyrics);
    }

    private void adjustLyricOffset(long deltaMs) {
        setLyricOffset(Math.max(-10_000, Math.min(10_000, lyricOffsetMs + deltaMs)));
    }

    private void setLyricOffset(long offsetMs) {
        lyricOffsetMs = offsetStore.set(currentTrackKey, offsetMs);
        updateLyricOffsetLabel();
        currentLyricLine = -1;
        updateCurrentLyric();
    }

    private void updateLyricOffsetLabel() {
        lyricOffsetLabel.setText(getString(R.string.lyric_offset_value,
                lyricOffsetMs / 1000f));
        if (calibrationDialog != null && calibrationDialog.isShowing())
            calibrationDialog.setMessage(getString(R.string.lyric_calibration_message, lyricOffsetMs / 1000f));
    }

    private void startAlbumAnimation() {
        albumAnimator = ObjectAnimator.ofFloat(albumMusic, "rotation", 0, 360);
        albumAnimator.setDuration(8000);
        albumAnimator.setInterpolator(new LinearInterpolator());
        albumAnimator.setRepeatCount(ValueAnimator.INFINITE);
        albumAnimator.start();
    }

    private void loadLyrics(String url) {
        lyricsLoader.load(url, (state, lines) -> {
            if (isDestroyed() || isFinishing()) return;
            lyricsRetry.setVisibility(state == LyricsLoader.State.ERROR ? View.VISIBLE : View.GONE);
            if (state == LyricsLoader.State.CONTENT) {
                hasTimedLyrics = lines.get(0).timeMs >= 0;
                lyricsAdapter.setLines(lines);
                updateCurrentLyric();
            } else {
                showLyricsMessage(getString(state == LyricsLoader.State.LOADING ? R.string.lyrics_loading
                        : state == LyricsLoader.State.ERROR ? R.string.lyrics_failed : R.string.lyrics_empty));
            }
        });
    }

    private void showCalibration() {
        if (TextUtils.isEmpty(currentTrackKey)) return;
        calibrationDialog = new AlertDialog.Builder(this).setTitle(R.string.lyrics_calibrate)
                .setMessage(getString(R.string.lyric_calibration_message, lyricOffsetMs / 1000f))
                .setNegativeButton(R.string.lyric_offset_minus, null)
                .setNeutralButton(R.string.lyric_offset_reset, null)
                .setPositiveButton(R.string.lyric_offset_plus, null).create();
        calibrationDialog.setOnShowListener(dialog -> {
            calibrationDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> adjustLyricOffset(-500));
            calibrationDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> setLyricOffset(0));
            calibrationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> adjustLyricOffset(500));
        });
        calibrationDialog.show();
    }

    private void updateFavorite() {
        int generation = ++favoriteGeneration;
        favoriteButton.setEnabled(false);
        if (currentTrack == null || TextUtils.isEmpty(currentTrack.source)) return;
        MusicLibraryRepository.get(this).isFavorite(currentTrack, favorite -> {
            if (generation != favoriteGeneration || isDestroyed()) return;
            favoriteButton.setText(favorite ? R.string.remove_favorite : R.string.add_favorite);
            favoriteButton.setEnabled(true);
        });
    }

    private void toggleFavorite() {
        if (currentTrack == null) return;
        int generation = ++favoriteGeneration;
        favoriteButton.setEnabled(false);
        MusicLibraryRepository.get(this).toggleFavorite(currentTrack, favorite -> {
            if (generation != favoriteGeneration || isDestroyed()) return;
            favoriteButton.setText(favorite ? R.string.remove_favorite : R.string.add_favorite);
            favoriteButton.setEnabled(true);
        });
    }

    private void showPlaybackMode() {
        if (musicService == null) return;
        try {
            String[] modes = getResources().getStringArray(R.array.play_modes);
            new AlertDialog.Builder(this).setTitle(R.string.play_mode)
                    .setSingleChoiceItems(modes, musicService.getPlayMode(), (dialog, which) -> {
                        try { if (musicService != null) musicService.seekPlayMode(which); }
                        catch (RemoteException error) { playbackStatus.setText(R.string.playback_service_unavailable); }
                        dialog.dismiss();
                    }).setNegativeButton(R.string.cancel, null).show();
        } catch (RemoteException error) { playbackStatus.setText(R.string.playback_service_unavailable); }
    }

    private void showLyricsMessage(String message) {
        hasTimedLyrics = false;
        List<LyricLine> lines = new ArrayList<>();
        lines.add(new LyricLine(-1, message));
        lyricsAdapter.setLines(lines);
    }

    private void updateCurrentLyric() {
        updatePlaybackProgress();
        if (!hasTimedLyrics || musicService == null || lyricsAdapter.getItemCount() == 0) {
            return;
        }
        try {
            long position = musicService.getCurrentPosition() + lyricOffsetMs;
            int currentLine = LyricsParser.findLineAt(lyricsAdapter.lines, position);
            if (currentLine >= 0 && lyricsAdapter.setCurrentLine(currentLine)) {
                currentLyricLine = currentLine;
                scrollToCurrentLyric();
            }
        } catch (RemoteException ignored) {
        }
    }

    private void controlPlayback(int direction) {
        if (musicService == null) return;
        try {
            if (direction < 0) musicService.pre();
            else if (direction > 0) musicService.next();
            else if (musicService.isPlay()
                    || musicService.getPlaybackState() == PlaybackStateCompat.STATE_BUFFERING) {
                musicService.pause();
            } else musicService.play();
            updatePlaybackControls();
        } catch (RemoteException error) {
            playbackStatus.setText(R.string.playback_service_unavailable);
        }
    }

    private void updatePlaybackControls() {
        playbackToggle.setEnabled(musicService != null);
        findViewById(R.id.playback_mode).setEnabled(musicService != null);
        if (musicService == null) return;
        try {
            int state = musicService.getPlaybackState();
            boolean playing = musicService.isPlay();
            String error = musicService.getPlaybackError();
            playbackToggle.setText(playing || state == PlaybackStateCompat.STATE_BUFFERING
                    ? R.string.player_pause : TextUtils.isEmpty(error)
                    ? R.string.player_play : R.string.player_retry);
            playbackStatus.setText(!TextUtils.isEmpty(error) ? error
                    : state == PlaybackStateCompat.STATE_BUFFERING
                    ? getString(R.string.player_buffering) : "");
            playbackStatus.setVisibility(playbackStatus.length() == 0 ? View.GONE : View.VISIBLE);
            if (albumAnimator != null) {
                if (playing) albumAnimator.resume();
                else albumAnimator.pause();
            }
        } catch (RemoteException error) {
            playbackStatus.setText(R.string.playback_service_unavailable);
        }
    }

    private void updatePlaybackProgress() {
        updatePlaybackControls();
        if (musicService == null || userSeeking) {
            return;
        }
        try {
            long duration = Math.max(0, musicService.getDuration());
            int position = Math.max(0, musicService.getCurrentPosition());
            playbackProgress.setMax((int) Math.min(Integer.MAX_VALUE, duration));
            playbackProgress.setProgress(position);
            currentTime.setText(formatTime(position));
            totalTime.setText(formatTime(duration));
        } catch (RemoteException ignored) {
        }
    }

    private void scrollToCurrentLyric() {
        if (followCurrentLyric && currentLyricLine >= 0) {
            LinearSmoothScroller scroller = new LinearSmoothScroller(this) {
                @Override
                public int calculateDtToFit(int viewStart, int viewEnd,
                                            int boxStart, int boxEnd, int snapPreference) {
                    return (boxStart + boxEnd) / 2 - (viewStart + viewEnd) / 2;
                }
            };
            scroller.setTargetPosition(currentLyricLine);
            lyricsLayoutManager.startSmoothScroll(scroller);
        }
    }

    private String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds) / 1000;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d",
                totalSeconds / 60, totalSeconds % 60);
    }

    @Override public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.playback, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_playback_queue) {
            startActivity(new Intent(this, MusicQueueActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = MusicAidlInterface.Stub.asInterface(service);
            try {
                musicService.registerCallback(playbackCallback);
            } catch (RemoteException ignored) {
            }
            updateCurrentLyric();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
        }
    };

    private final ActivityCall.Stub playbackCallback = new ActivityCall.Stub() {
        @Override public void call(boolean isPlay) {
            runOnUiThread(() -> updatePlaybackControls());
        }
        @Override public void pre() { }
        @Override public void next() { }
        @Override public void defaultPlay() { }

        @Override
        public void trackChanged(MusicTrackEntity track) {
            runOnUiThread(() -> updateTrack(track));
        }
    };

    private void updateTrack(MusicTrackEntity track) {
        if (track != null && currentTrack != null && TextUtils.equals(track.source, currentTrackKey)
                && TextUtils.equals(track.getPlaybackUrl(), currentTrack.getPlaybackUrl())
                && TextUtils.equals(track.name, currentTrack.name) && TextUtils.equals(track.artist, currentTrack.artist)
                && TextUtils.equals(track.imageUrl, currentTrack.imageUrl) && TextUtils.equals(track.lyricsUrl, currentTrack.lyricsUrl)) {
            updateFavorite();
            return;
        }
        currentTrackKey = track == null ? null : track.source;
        currentTrack = track;
        lyricsLoader.cancel();
        currentLyricsUrl = null;
        lyricsRetry.setVisibility(View.GONE);
        showLyricsMessage(getString(R.string.lyrics_loading));
        if (calibrationDialog != null) calibrationDialog.dismiss();
        lyricOffsetMs = offsetStore.get(currentTrackKey);
        updateLyricOffsetLabel();
        updateFavorite();
        displayTrack(track == null ? null : track.name, track == null ? null : track.artist,
                track == null ? null : track.imageUrl, track == null ? null : track.lyricsUrl);
        // Old persisted queues may have no metadata. Restore from their established library key;
        // never look up a newer catalogue by URL, which can belong to a different API source.
        if (track != null && track.imageUrl == null && track.lyricsUrl == null) {
            MusicLibraryRepository.get(this).loadTrack(track.source, saved -> {
                if (isDestroyed() || currentTrack != track || saved == null) return;
                currentTrack = saved;
                updateFavorite();
                displayTrack(track.name, track.artist, saved.imageUrl, saved.lyricsUrl);
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        serviceBound = bindService(new Intent(this, MusicService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
        progressHandler.post(progressUpdater);
        updateFavorite();
    }

    @Override
    protected void onStop() {
        if (albumAnimator != null) albumAnimator.pause();
        progressHandler.removeCallbacks(progressUpdater);
        if (serviceBound) {
            try {
                if (musicService != null) {
                    musicService.unregisterCallback(playbackCallback);
                }
            } catch (RemoteException ignored) {
            }
            unbindService(serviceConnection);
            serviceBound = false;
        }
        musicService = null;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        lyricsLoader.cancel();
        if (calibrationDialog != null) calibrationDialog.dismiss();
        if (albumAnimator != null) {
            albumAnimator.cancel();
        }
        super.onDestroy();
    }

    private final class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.ViewHolder> {
        private final List<LyricLine> lines = new ArrayList<>();
        private int currentLine = -1;

        void setLines(List<LyricLine> newLines) {
            int previousCount = lines.size();
            lines.clear();
            if (previousCount > 0) {
                notifyItemRangeRemoved(0, previousCount);
            }
            lines.addAll(newLines);
            currentLine = -1;
            if (!lines.isEmpty()) {
                notifyItemRangeInserted(0, lines.size());
            }
        }

        boolean setCurrentLine(int position) {
            if (currentLine == position) {
                return false;
            }
            int previousLine = currentLine;
            currentLine = position;
            if (previousLine >= 0) {
                notifyItemChanged(previousLine);
            }
            notifyItemChanged(currentLine);
            return true;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_lyric, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            boolean active = position == currentLine;
            holder.text.setText(lines.get(position).text);
            holder.text.setTextSize(active ? 20 : 16);
            holder.text.setTextColor(ContextCompat.getColor(MusicPlayActivity.this,
                    active ? R.color.lyric_active : R.color.lyric_inactive));
            holder.text.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }

        final class ViewHolder extends RecyclerView.ViewHolder {
            final TextView text;

            ViewHolder(View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.tv_lyric_line);
            }
        }
    }
}
