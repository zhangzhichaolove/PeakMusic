package com.chao.peakmusic.fragment;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.widget.TextView;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
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

/**
 * 本地音乐
 * Created by Chao on 2017-12-18.
 */

public class LocalMusicFragment extends BaseFragment {
    RecyclerView musicList;
    TextView emptyView;
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
        musicList = rootView.findViewById(R.id.local_music_list);
        emptyView = rootView.findViewById(R.id.local_empty);
        musicList.setLayoutManager(new LinearLayoutManager(mContext));
        musicList.setAdapter(adapter = new LocalMusicAdapter());
        music = music == null ? ScanningUtils.getInstance(mContext).getMusic() : music;
        if (music != null) {
            adapter.setData(music);
        }
        updateEmptyState();
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
                    listener.playMusic(position, music.get(position).getSong(), music.get(position).getSinger());
                }
            }

            @Override
            public void itemLongClickListener(int position) {
                showMusicDetails(music.get(position));
            }
        });
    }

    private void showMusicDetails(SongModel song) {
        String duration = DateUtils.formatElapsedTime(Math.max(0, song.getDuration()) / 1000L);
        String size = Formatter.formatFileSize(mContext, song.getSize());
        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.music_details)
                .setMessage(getString(R.string.local_music_details,
                        song.getSong(), song.getSinger(), song.getAlbum(), duration, size,
                        song.getFilePath()))
                .setPositiveButton(android.R.string.ok, null)
                .show();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextIsSelectable(true);
        }
    }

    public void setMusic(ArrayList<SongModel> music) {
        this.music = music;
        if (adapter != null && music != null) {
            adapter.setData(music);
        }
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (emptyView == null || musicList == null) {
            return;
        }
        boolean empty = music == null || music.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        musicList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
