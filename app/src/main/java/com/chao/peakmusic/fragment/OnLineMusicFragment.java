package com.chao.peakmusic.fragment;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chao.peakmusic.MainActivity;
import com.chao.peakmusic.R;
import com.chao.peakmusic.adapter.OnlineContentMusicAdapter;
import com.chao.peakmusic.base.ApiRequest;
import com.chao.peakmusic.base.ApiUrl;
import com.chao.peakmusic.base.BaseFragment;
import com.chao.peakmusic.base.HttpResult;
import com.chao.peakmusic.base.ServiceFactory;
import com.chao.peakmusic.model.MusicListModel;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.utils.LogUtils;
import com.chao.peakmusic.utils.MusicDataUtils;
import com.chao.peakmusic.utils.MusicActions;
import com.chao.peakmusic.data.MusicTrackEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Created by Chao on 2018-09-23.
 */

public class OnLineMusicFragment extends BaseFragment {
    RecyclerView musicTitle;
    RecyclerView musicContent;
    View stateContainer;
    ProgressBar stateProgress;
    TextView stateMessage;
    Button stateRetry;

    private OnlineContentMusicAdapter contentMusicAdapter;
    private Disposable currentRequest;


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
        musicContent = rootView.findViewById(R.id.rl_content);
        stateContainer = rootView.findViewById(R.id.state_container);
        stateProgress = rootView.findViewById(R.id.state_progress);
        stateMessage = rootView.findViewById(R.id.state_message);
        stateRetry = rootView.findViewById(R.id.state_retry);
        musicContent.setLayoutManager(new LinearLayoutManager(mContext));
        musicContent.setAdapter(contentMusicAdapter = new OnlineContentMusicAdapter());
        contentMusicAdapter.setListener(new OnlineContentMusicAdapter.onItemClick() {
            @Override
            public void itemClickListener(int position) {
                //playMusicWithId(contentMusicAdapter.getData().get(position).getSongid());
                MusicModel musicModel = contentMusicAdapter.getData().get(position);
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    activity.getListener().playMusic(musicModel.getMp3(),
                            musicModel.getName(), musicModel.getSinger(),
                            musicModel.getImg());
                }
                MusicDataUtils.getInstance().setCurrentPosition(position);
            }

            @Override
            public void itemLongClickListener(int position) {
                showMusicDetails(contentMusicAdapter.getData().get(position));
            }
        });
        stateRetry.setOnClickListener(view -> reloadMusic());
    }

    private void showMusicDetails(MusicModel music) {
        MusicActions.show(requireActivity(), MusicTrackEntity.from(music));
    }

    @Override
    public void initData() {
        reloadMusic();
    }

    public void reloadMusic() {
        if (contentMusicAdapter == null) {
            return;
        }
        if (currentRequest != null) {
            currentRequest.dispose();
        }
        showLoading();
        ApiRequest.obtain(ServiceFactory.getInstance().createService(ApiUrl.class).getMusicList(""), new Observer<HttpResult<MusicListModel>>() {
            @Override
            public void onSubscribe(Disposable d) {
                currentRequest = d;
                disposables.add(d);
            }

            @Override
            public void onNext(HttpResult<MusicListModel> objectHttpResult) {
                LogUtils.showTagE(objectHttpResult);
                if (objectHttpResult == null || !objectHttpResult.isSuccess()
                        || objectHttpResult.getResult() == null) {
                    showError(objectHttpResult == null ? null : objectHttpResult.getMsg());
                    return;
                }
                List<MusicModel> records = objectHttpResult.getResult().getRecords();
                contentMusicAdapter.setData(records);
                MusicDataUtils.getInstance().setMusicList(records);
                showContentState(records);
            }

            @Override
            public void onError(Throwable e) {
                LogUtils.showTagE(e);
                showError(e.getLocalizedMessage());
            }

            @Override
            public void onComplete() {

            }
        });
    }

    private void showLoading() {
        stateContainer.setVisibility(View.VISIBLE);
        stateProgress.setVisibility(View.VISIBLE);
        stateRetry.setVisibility(View.GONE);
        stateMessage.setText(R.string.music_loading);
        musicContent.setVisibility(View.GONE);
    }

    private void showContentState(List<MusicModel> records) {
        boolean empty = records == null || records.isEmpty();
        stateContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        stateProgress.setVisibility(View.GONE);
        stateRetry.setVisibility(View.GONE);
        stateMessage.setText(R.string.music_empty);
        musicContent.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        if (contentMusicAdapter != null && contentMusicAdapter.getItemCount() > 0) {
            musicContent.setVisibility(View.VISIBLE);
            stateContainer.setVisibility(View.GONE);
            return;
        }
        stateContainer.setVisibility(View.VISIBLE);
        stateProgress.setVisibility(View.GONE);
        stateRetry.setVisibility(View.VISIBLE);
        stateMessage.setText(getString(R.string.music_load_failed,
                message == null || message.trim().isEmpty()
                        ? getString(R.string.unknown_error) : message));
        musicContent.setVisibility(View.GONE);
    }

    private void playMusicWithId(int id) {
//        ApiRequest.obtain(ServiceFactory.getInstance().createService(ApiUrl.class).getMusicDetails(id), new Observer<HttpResult<MusicDetailsResultModel>>() {
//            @Override
//            public void onSubscribe(Disposable d) {
//                disposables.add(d);
//            }
//
//            @Override
//            public void onNext(HttpResult<MusicDetailsResultModel> objectHttpResult) {
//                LogUtils.showTagE(objectHttpResult.getResult().getSongList().get(0).getSongLink());
//                ((MainActivity) getActivity()).getListener().playMusic(objectHttpResult.getResult().getSongList().get(0).getSongLink(),
//                        objectHttpResult.getResult().getSongList().get(0).getSongName(), objectHttpResult.getResult().getSongList().get(0).getArtistName(),
//                        objectHttpResult.getResult().getSongList().get(0).getSongPicBig());
//            }
//
//            @Override
//            public void onError(Throwable e) {
//                LogUtils.showTagE(e);
//                ToastUtils.showToast("此歌曲飞走了~");
//            }
//
//            @Override
//            public void onComplete() {
//
//            }
//        });
    }
}
