package com.chao.peakmusic.fragment;

import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.MainActivity;
import com.chao.peakmusic.R;
import com.chao.peakmusic.adapter.LocalMusicAdapter;
import com.chao.peakmusic.base.BaseFragment;
import com.chao.peakmusic.listener.PlayMusicListener;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.utils.ScanningUtils;

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
        music = music == null ? ScanningUtils.getInstance(mContext).getMusic() : music;
        if (music != null) {
            adapter.setData(music);
        }
        listener = ((MainActivity) getActivity()).getListener();
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
                    listener.playMusic(position, music.get(position).getSong(), music.get(position).getAlbum());
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
}
