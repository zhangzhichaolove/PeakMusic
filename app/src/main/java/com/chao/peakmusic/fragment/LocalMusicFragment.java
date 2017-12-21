package com.chao.peakmusic.fragment;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.adapter.LocalMusicAdapter;
import com.chao.peakmusic.base.BaseFragment;
import com.chao.peakmusic.listener.PlayMusicListener;
import com.chao.peakmusic.model.SongModel;

import java.util.ArrayList;

import butterknife.BindView;

/**
 * 本地音乐
 * Created by Chao on 2017-12-18.
 */

public class LocalMusicFragment extends BaseFragment {
    @BindView(R.id.local_music_list)
    RecyclerView musicList;
    private LocalMusicAdapter adapter;
    private PlayMusicListener listener;
    private ArrayList<SongModel> music;

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
        if (music != null) {
            adapter.setData(music);
        }
    }

    @Override
    public void initData() {

    }

    @Override
    public void initListener() {
        adapter.setListener(new LocalMusicAdapter.onItemClick() {
            @Override
            public void itemClickListener(int position) {
                if (listener != null) {
                    listener.playMusic(position);
                }
            }
        });
    }

    public void setMusic(ArrayList<SongModel> music) {
        this.music = music;
        if (adapter != null && music != null) {
            adapter.setData(music);
        }
    }

    public void setPlayMusicListener(PlayMusicListener listener) {
        this.listener = listener;
    }


}
