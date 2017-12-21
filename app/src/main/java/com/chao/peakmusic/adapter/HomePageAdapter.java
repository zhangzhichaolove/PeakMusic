package com.chao.peakmusic.adapter;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import com.chao.peakmusic.fragment.LocalMusicFragment;

/**
 * Created by Chao on 2017/9/3.
 */

public class HomePageAdapter extends FragmentPagerAdapter {
    private String[] mTitles = new String[]{"本地音乐", "在线音乐"};
    private Fragment[] fragments;
    private LocalMusicFragment localMusicFragment;

    public LocalMusicFragment getLocalMusicFragment() {
        return localMusicFragment;
    }

    public HomePageAdapter(FragmentManager fm) {
        super(fm);
        localMusicFragment = LocalMusicFragment.newInstance();
        fragments = new Fragment[]{localMusicFragment, LocalMusicFragment.newInstance()};
    }

    @Override
    public Fragment getItem(int position) {
        return fragments[position];
    }

    @Override
    public int getCount() {
        return mTitles.length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mTitles[position];
    }
}
