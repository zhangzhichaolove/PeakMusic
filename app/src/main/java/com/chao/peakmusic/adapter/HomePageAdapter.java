package com.chao.peakmusic.adapter;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

/**
 * Created by Chao on 2017/9/3.
 */

public class HomePageAdapter extends FragmentPagerAdapter {
    private String[] mTitles = new String[]{"本地音乐", "在线音乐"};
    private Fragment[] fragments;


    public HomePageAdapter(FragmentManager fm, Fragment[] fragments) {
        super(fm);
        this.fragments = fragments;
    }

    @Override
    public Fragment getItem(int position) {
        return fragments[position];
    }

    @Override
    public int getCount() {
        return fragments.length;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return mTitles[position];
    }

}
