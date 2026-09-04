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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.R;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.widget.MusicAlbumView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private ObjectAnimator albumAnimator;
    private Call lyricsCall;
    private MusicAidlInterface musicService;
    private boolean serviceBound;
    private String trackTitle;
    private boolean hasTimedLyrics;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateCurrentLyric();
            progressHandler.postDelayed(this, 300);
        }
    };

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

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
        lyricsLayoutManager = new LinearLayoutManager(this);
        lyricsAdapter = new LyricsAdapter();
        lyricsList.setLayoutManager(lyricsLayoutManager);
        lyricsList.setAdapter(lyricsAdapter);
        lyricsList.post(() -> {
            int verticalPadding = lyricsList.getHeight() / 2;
            lyricsList.setPadding(0, verticalPadding, 0, verticalPadding);
        });

        MusicModel music = (MusicModel) getIntent().getSerializableExtra(EXTRA_MUSIC);
        String name = music == null ? getIntent().getStringExtra(EXTRA_NAME) : music.getName();
        String singer = music == null ? getIntent().getStringExtra(EXTRA_SINGER) : music.getSinger();
        String image = music == null ? getIntent().getStringExtra(EXTRA_IMAGE) : music.getImg();

        name = TextUtils.isEmpty(name) ? getString(R.string.unknown_music) : name;
        singer = TextUtils.isEmpty(singer) ? getString(R.string.unknown_singer) : singer;
        trackTitle = name;
        setTitle(trackTitle);
        mToolbar.setTitle(trackTitle);
        mToolbar.post(() -> mToolbar.setTitle(trackTitle));
        musicName.setText(name);
        musicSinger.setText(singer);
        if (!TextUtils.isEmpty(image)) {
            ImageLoaderV4.getInstance().load(this, albumMusic, image);
        }

        startAlbumAnimation();
        if (music == null || TextUtils.isEmpty(music.getLrc())) {
            showLyricsMessage(getString(R.string.lyrics_empty));
        } else {
            loadLyrics(music.getLrc());
        }
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
        lyricsCall = new OkHttpClient().newCall(request);
        lyricsCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                if (!call.isCanceled()) {
                    runOnUiThread(() -> showLyricsMessage(getString(R.string.lyrics_empty)));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String text = response.isSuccessful() && response.body() != null
                        ? response.body().string() : "";
                List<LyricLine> parsedLyrics = parseLyrics(text);
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

    private List<LyricLine> parseLyrics(String source) {
        List<LyricLine> result = new ArrayList<>();
        List<String> untimedLines = new ArrayList<>();
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            Matcher matcher = TIME_PATTERN.matcher(line);
            String lyricLine = matcher.replaceAll("").trim();
            if (lyricLine.matches("^\\[[a-zA-Z]+:.*]$") || lyricLine.isEmpty()) {
                continue;
            }
            matcher.reset();
            boolean foundTime = false;
            while (matcher.find()) {
                foundTime = true;
                result.add(new LyricLine(parseTime(matcher), lyricLine));
            }
            if (!foundTime) {
                untimedLines.add(lyricLine);
            }
        }
        if (!result.isEmpty()) {
            Collections.sort(result, (first, second) -> first.timeMs < second.timeMs
                    ? -1 : first.timeMs == second.timeMs ? 0 : 1);
            return result;
        }
        for (String line : untimedLines) {
            result.add(new LyricLine(-1, line));
        }
        return result;
    }

    private long parseTime(Matcher matcher) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long milliseconds = 0;
        if (fraction != null) {
            if (fraction.length() == 1) {
                milliseconds = Long.parseLong(fraction) * 100;
            } else if (fraction.length() == 2) {
                milliseconds = Long.parseLong(fraction) * 10;
            } else {
                milliseconds = Long.parseLong(fraction.substring(0, 3));
            }
        }
        return (minutes * 60 + seconds) * 1000 + milliseconds;
    }

    private void showLyricsMessage(String message) {
        hasTimedLyrics = false;
        List<LyricLine> lines = new ArrayList<>();
        lines.add(new LyricLine(-1, message));
        lyricsAdapter.setLines(lines);
    }

    private void updateCurrentLyric() {
        if (!hasTimedLyrics || musicService == null || lyricsAdapter.getItemCount() == 0) {
            return;
        }
        try {
            long position = musicService.getCurrentPosition();
            int currentLine = -1;
            for (int i = 0; i < lyricsAdapter.getItemCount(); i++) {
                if (lyricsAdapter.getLine(i).timeMs > position) {
                    break;
                }
                currentLine = i;
            }
            if (currentLine >= 0 && lyricsAdapter.setCurrentLine(currentLine)) {
                lyricsList.smoothScrollToPosition(currentLine);
            }
        } catch (RemoteException ignored) {
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            musicService = MusicAidlInterface.Stub.asInterface(service);
            updateCurrentLyric();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
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

    private static final class LyricLine {
        final long timeMs;
        final String text;

        LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private final class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.ViewHolder> {
        private final List<LyricLine> lines = new ArrayList<>();
        private int currentLine = -1;

        void setLines(List<LyricLine> newLines) {
            lines.clear();
            lines.addAll(newLines);
            currentLine = -1;
            notifyDataSetChanged();
        }

        LyricLine getLine(int position) {
            return lines.get(position);
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
