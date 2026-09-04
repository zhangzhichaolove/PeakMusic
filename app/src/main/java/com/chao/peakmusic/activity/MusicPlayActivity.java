package com.chao.peakmusic.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.text.TextUtils;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.widget.MusicAlbumView;

import java.io.IOException;

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
    private TextView lyrics;
    private ObjectAnimator albumAnimator;
    private Call lyricsCall;

    @Override
    public int getLayout() {
        return R.layout.activity_play_music;
    }

    @Override
    public void initView() {
        albumMusic = findViewById(R.id.album_music);
        musicName = findViewById(R.id.tv_music_name);
        musicSinger = findViewById(R.id.tv_music_singer);
        lyrics = findViewById(R.id.tv_lyrics);

        MusicModel music = (MusicModel) getIntent().getSerializableExtra(EXTRA_MUSIC);
        String name = music == null ? getIntent().getStringExtra(EXTRA_NAME) : music.getName();
        String singer = music == null ? getIntent().getStringExtra(EXTRA_SINGER) : music.getSinger();
        String image = music == null ? getIntent().getStringExtra(EXTRA_IMAGE) : music.getImg();

        name = TextUtils.isEmpty(name) ? getString(R.string.unknown_music) : name;
        singer = TextUtils.isEmpty(singer) ? getString(R.string.unknown_singer) : singer;
        setTitle(name);
        mToolbar.setTitle(name);
        musicName.setText(name);
        musicSinger.setText(singer);
        if (!TextUtils.isEmpty(image)) {
            ImageLoaderV4.getInstance().load(this, albumMusic, image);
        }

        startAlbumAnimation();
        if (music == null || TextUtils.isEmpty(music.getLrc())) {
            lyrics.setText(R.string.lyrics_empty);
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
        lyrics.setText(R.string.lyrics_loading);
        Request request = new Request.Builder().url(url).build();
        lyricsCall = new OkHttpClient().newCall(request);
        lyricsCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                runOnUiThread(() -> lyrics.setText(R.string.lyrics_empty));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String text = response.isSuccessful() && response.body() != null
                        ? response.body().string() : "";
                String displayLyrics = formatLyrics(text);
                runOnUiThread(() -> lyrics.setText(TextUtils.isEmpty(displayLyrics)
                        ? getString(R.string.lyrics_empty) : displayLyrics));
            }
        });
    }

    private String formatLyrics(String source) {
        StringBuilder result = new StringBuilder();
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            String lyricLine = line
                    .replaceAll("\\[(?:\\d{1,3}:){1,2}\\d{1,2}(?:[.:]\\d{1,3})?]", "")
                    .trim();
            if (lyricLine.matches("^\\[[a-zA-Z]+:.*]$") || lyricLine.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(lyricLine);
        }
        return result.toString();
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
}
