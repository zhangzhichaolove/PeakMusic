package com.chao.peakmusic.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import com.chao.peakmusic.R;
import com.chao.peakmusic.TestAidlInterface;
import com.chao.peakmusic.adapter.LocalMusicAdapter;
import com.chao.peakmusic.base.BaseFragment;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.service.TestAidlService;
import com.chao.peakmusic.utils.ScanningUtils;

import java.util.ArrayList;

import butterknife.BindView;

/**
 * 本地音乐
 * Created by Chao on 2017-12-18.
 */

public class LocalMusicFragment extends BaseFragment implements ScanningUtils.ScanningListener {
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

    @Override
    public void initData() {
        if (ScanningUtils.getInstance(mContext).getMusic() == null) {
            ScanningUtils.getInstance(mContext).setListener(this).scanMusic();
        } else {
            onScanningMusicComplete(ScanningUtils.getInstance(mContext).getMusic());
        }

        Intent intent = new Intent(mContext, TestAidlService.class);
        //startService(intent);
        mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE);

    }

    @Override
    public void initListener() {
        adapter.setListener(new LocalMusicAdapter.onItemClick() {
            @Override
            public void itemClickListener(int position) {
                if (mService != null) {
                    try {
                        mService.openAudio(0);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private TestAidlInterface mService;

    //使用ServiceConnection来监听Service状态的变化
    private ServiceConnection conn = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            //这里我们实例化audioService,通过binder来实现
            //audioService = ((TestService.AudioBinder) binder).getService();
            //audioService.playMusic();
            mService = TestAidlInterface.Stub.asInterface(binder);
        }
    };

    @Override
    public void onScanningMusicComplete(ArrayList<SongModel> music) {
        adapter.setData(music);
    }
}
