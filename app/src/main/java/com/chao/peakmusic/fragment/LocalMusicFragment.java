package com.chao.peakmusic.fragment;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.adapter.LocalMusicAdapter;
import com.chao.peakmusic.base.BaseFragment;

import butterknife.BindView;

/**
 * 本地音乐
 * Created by Chao on 2017-12-18.
 */

public class LocalMusicFragment extends BaseFragment {
    @BindView(R.id.local_music_list)
    RecyclerView musicList;
    private LocalMusicAdapter adapter;

    public static LocalMusicFragment newInstance() {
        Bundle args = new Bundle();
        LocalMusicFragment fragment = new LocalMusicFragment();
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public int getLayout() {
        return R.layout.fragment_local_music;
    }

    @Override
    public void initView() {
        musicList.setLayoutManager(new LinearLayoutManager(mContext));
        musicList.setAdapter(adapter = new LocalMusicAdapter());
    }

}
