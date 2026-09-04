package com.chao.peakmusic.adapter;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import androidx.annotation.NonNull;

/**
 * Created by Chao on 2017/9/3.
 */

public class HomePageAdapter extends FragmentStateAdapter {
    private final Fragment[] fragments;


    public HomePageAdapter(FragmentActivity activity, Fragment[] fragments) {
        super(activity);
        this.fragments = fragments;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments[position];
    }

    @Override
    public int getItemCount() {
        return fragments.length;
    }

}
