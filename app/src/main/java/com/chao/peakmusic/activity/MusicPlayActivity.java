package com.chao.peakmusic.activity;

import android.animation.ObjectAnimator;
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
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.data.MusicLibraryRepository;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.utils.LyricsParser;
import com.chao.peakmusic.utils.LyricsParser.LyricLine;
import com.chao.peakmusic.widget.MusicAlbumView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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
    private ObjectAnimator albumAnimator;
    private Call lyricsCall;
    private MusicAidlInterface musicService;
    private boolean serviceBound;
    private String trackTitle;
    private String currentSource;
    private long lyricOffsetMs;
    private TextView lyricOffsetLabel;
    private final OkHttpClient lyricsClient = new OkHttpClient();
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
        albumMusic = findViewById(R.id.album_music);
        musicName = findViewById(R.id.tv_music_name);
        musicSinger = findViewById(R.id.tv_music_singer);
        lyricsList = findViewById(R.id.lyrics_list);
        playbackProgress = findViewById(R.id.playback_progress);
        currentTime = findViewById(R.id.playback_current_time);
        totalTime = findViewById(R.id.playback_total_time);
        followLyrics = findViewById(R.id.lyrics_follow);
        lyricOffsetLabel = findViewById(R.id.lyric_offset_value);
        lyricOffsetMs = getSharedPreferences("lyrics", MODE_PRIVATE)
                .getLong("manual_offset", 0);
        updateLyricOffsetLabel();
        findViewById(R.id.lyric_offset_minus).setOnClickListener(view -> adjustLyricOffset(-500));
        findViewById(R.id.lyric_offset_reset).setOnClickListener(view -> setLyricOffset(0));
        findViewById(R.id.lyric_offset_plus).setOnClickListener(view -> adjustLyricOffset(500));
        lyricsLayoutManager = new LinearLayoutManager(this);
        lyricsAdapter = new LyricsAdapter();
        lyricsList.setLayoutManager(lyricsLayoutManager);
        lyricsList.setAdapter(lyricsAdapter);
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
        lyricsList.post(() -> {
            int verticalPadding = lyricsList.getHeight() / 2;
            lyricsList.setPadding(0, verticalPadding, 0, verticalPadding);
        });

        MusicModel music = IntentCompat.getParcelableExtra(
                getIntent(), EXTRA_MUSIC, MusicModel.class);
        String name = music == null ? getIntent().getStringExtra(EXTRA_NAME) : music.getName();
        String singer = music == null ? getIntent().getStringExtra(EXTRA_SINGER) : music.getSinger();
        String image = music == null ? getIntent().getStringExtra(EXTRA_IMAGE) : music.getImg();

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
        if (lyricsCall != null) {
            lyricsCall.cancel();
            lyricsCall = null;
        }
        currentLyricLine = -1;
        followCurrentLyric = true;
        followLyrics.setVisibility(View.GONE);
        if (TextUtils.isEmpty(lyrics)) {
            showLyricsMessage(getString(R.string.lyrics_empty));
        } else {
            loadLyrics(lyrics);
        }
    }

    private void adjustLyricOffset(long deltaMs) {
        setLyricOffset(Math.max(-10_000, Math.min(10_000, lyricOffsetMs + deltaMs)));
    }

    private void setLyricOffset(long offsetMs) {
        lyricOffsetMs = offsetMs;
        getSharedPreferences("lyrics", MODE_PRIVATE).edit()
                .putLong("manual_offset", lyricOffsetMs).apply();
        updateLyricOffsetLabel();
        currentLyricLine = -1;
        updateCurrentLyric();
    }

    private void updateLyricOffsetLabel() {
        lyricOffsetLabel.setText(getString(R.string.lyric_offset_value,
                lyricOffsetMs / 1000f));
    }

    private void startAlbumAnimation() {
        albumAnimator = ObjectAnimator.ofFloat(albumMusic, "rotation", 0, 360);
        albumAnimator.setDuration(8000);
        albumAnimator.setInterpolator(new LinearInterpolator());
        albumAnimator.setRepeatCount(ValueAnimator.INFINITE);
        albumAnimator.start();
    }

    private void loadLyrics(String url) {
        showLyricsMessage(getString(R.string.lyrics_loading));
        Request request = new Request.Builder().url(url).build();
        lyricsCall = lyricsClient.newCall(request);
        lyricsCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                if (!call.isCanceled()) {
                    runOnUiThread(() -> showLyricsMessage(getString(R.string.lyrics_empty)));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String text;
                try (Response closeableResponse = response) {
                    text = closeableResponse.isSuccessful() && closeableResponse.body() != null
                            ? LyricsParser.decode(closeableResponse.body().bytes()) : "";
                }
                List<LyricLine> parsedLyrics = LyricsParser.parse(text);
                runOnUiThread(() -> {
                    if (parsedLyrics.isEmpty()) {
                        showLyricsMessage(getString(R.string.lyrics_empty));
                    } else {
                        hasTimedLyrics = parsedLyrics.get(0).timeMs >= 0;
                        lyricsAdapter.setLines(parsedLyrics);
                        updateCurrentLyric();
                    }
                });
            }
        });
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

    private void updatePlaybackProgress() {
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
        @Override public void call(boolean isPlay) { }
        @Override public void pre() { }
        @Override public void next() { }
        @Override public void defaultPlay() { }

        @Override
        public void trackChanged(String source, String name, String artist, boolean local) {
            if (TextUtils.equals(source, currentSource)) {
                return;
            }
            currentSource = source;
            MusicLibraryRepository.get(MusicPlayActivity.this).loadTrack(source, track -> {
                if (!TextUtils.equals(source, currentSource)) {
                    return;
                }
                displayTrack(name, artist,
                        track == null ? null : track.imageUrl,
                        track == null ? null : track.lyricsUrl);
            });
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        serviceBound = bindService(new Intent(this, MusicService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);
        progressHandler.post(progressUpdater);
    }

    @Override
    protected void onStop() {
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
        if (lyricsCall != null) {
            lyricsCall.cancel();
        }
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
                    active ? R.color.lyric_active : R.color.normalColor));
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
