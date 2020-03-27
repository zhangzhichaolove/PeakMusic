package com.chao.peakmusic.fragment;

import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.MainActivity;
import com.chao.peakmusic.R;
import com.chao.peakmusic.adapter.OnlineContentMusicAdapter;
import com.chao.peakmusic.adapter.OnlineTitleMusicAdapter;
import com.chao.peakmusic.base.ApiRequest;
import com.chao.peakmusic.base.ApiUrl;
import com.chao.peakmusic.base.BaseFragment;
import com.chao.peakmusic.base.HttpResult;
import com.chao.peakmusic.base.ServiceFactory;
import com.chao.peakmusic.model.MusicDetailsResultModel;
import com.chao.peakmusic.model.MusicListModel;
import com.chao.peakmusic.utils.LogUtils;
import com.chao.peakmusic.utils.ToastUtils;

import butterknife.BindView;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

/**
 * Created by Chao on 2018-09-23.
 */

public class OnLineMusicFragment extends BaseFragment {
    @BindView(R.id.rl_title)
    RecyclerView musicTitle;
    @BindView(R.id.rl_content)
    RecyclerView musicContent;

    private OnlineTitleMusicAdapter titleMusicAdapter;
    private OnlineContentMusicAdapter contentMusicAdapter;


    public static OnLineMusicFragment newInstance() {
        Bundle args = new Bundle();
        OnLineMusicFragment fragment = new OnLineMusicFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_online_music;
    }

    @Override
    public void initView() {
        //musicTitle.setLayoutManager(new GridLayoutManager(mContext, 3));
        //musicTitle.setAdapter(titleMusicAdapter = new OnlineTitleMusicAdapter());
        musicContent.setLayoutManager(new LinearLayoutManager(mContext));
        musicContent.setAdapter(contentMusicAdapter = new OnlineContentMusicAdapter());
        contentMusicAdapter.setListener(new OnlineContentMusicAdapter.onItemClick() {
            @Override
            public void itemClickListener(int position) {
                playMusicWithId(contentMusicAdapter.getData().get(position).getSongid());
            }
        });
    }

    @Override
    public void initData() {
        ApiRequest.obtain(ServiceFactory.getInstance().createService(ApiUrl.class).getMusicHome(), new Observer<HttpResult<MusicListModel>>() {
            @Override
            public void onSubscribe(Disposable d) {
                disposables.add(d);
            }

            @Override
            public void onNext(HttpResult<MusicListModel> objectHttpResult) {
                LogUtils.showTagE(objectHttpResult);
                contentMusicAdapter.setData(objectHttpResult.getResult().getSonglist());
                //titleMusicAdapter.setData(objectHttpResult.getResult().getSonglist());
            }

            @Override
            public void onError(Throwable e) {
                LogUtils.showTagE(e);
            }

            @Override
            public void onComplete() {

            }
        });
    }

    private void playMusicWithId(int id) {
        ApiRequest.obtain(ServiceFactory.getInstance().createService(ApiUrl.class).getMusicDetails(id), new Observer<HttpResult<MusicDetailsResultModel>>() {
            @Override
            public void onSubscribe(Disposable d) {
                disposables.add(d);
            }

            @Override
            public void onNext(HttpResult<MusicDetailsResultModel> objectHttpResult) {
                LogUtils.showTagE(objectHttpResult.getResult().getSongList().get(0).getSongLink());
                ((MainActivity) getActivity()).getListener().playMusic(objectHttpResult.getResult().getSongList().get(0).getSongLink(),
                        objectHttpResult.getResult().getSongList().get(0).getSongName(), objectHttpResult.getResult().getSongList().get(0).getArtistName(),
                        objectHttpResult.getResult().getSongList().get(0).getSongPicBig());
            }

            @Override
            public void onError(Throwable e) {
                LogUtils.showTagE(e);
                ToastUtils.showToast("此歌曲飞走了~");
            }

            @Override
            public void onComplete() {

            }
        });
    }
}
