package com.chao.peakmusic.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

import com.chao.peakmusic.R;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.widget.MusicAlbumView;

/**
 * Created by Chao on 2017-12-19.
 */

public class MusicPlayActivity extends BaseActivity {
    MusicAlbumView album_music;

    @Override
    public int getLayout() {
        return R.layout.activity_play_music;
    }

    @Override
    public void initView() {
        album_music = findViewById(R.id.album_music);
        ObjectAnimator animator = ObjectAnimator.ofFloat(album_music, "rotation", 0, 360);
        animator.setDuration(8000);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.start();
    }
}
