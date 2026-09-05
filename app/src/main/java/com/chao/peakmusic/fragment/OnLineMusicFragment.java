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
import com.chao.peakmusic.base.ApiUrl;
import com.chao.peakmusic.base.BaseFragment;
import com.chao.peakmusic.base.ServiceFactory;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.utils.MusicDataUtils;
import com.chao.peakmusic.utils.MusicActions;
import com.chao.peakmusic.data.MusicTrackEntity;

import java.util.List;
import com.chao.peakmusic.catalog.MusicPageController;
import com.chao.peakmusic.catalog.MusicPageFooter;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;


/**
 * Created by Chao on 2018-09-23.
 */

public class OnLineMusicFragment extends BaseFragment {
    RecyclerView musicContent;
    View stateContainer;
    ProgressBar stateProgress;
    TextView stateMessage;
    Button stateRetry;

    private OnlineContentMusicAdapter contentMusicAdapter;
    private MusicPageController pages;
    private MusicPageFooter footer;


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
                MusicModel musicModel = contentMusicAdapter.getData().get(position);
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    activity.getListener().playMusic(musicModel);
                }
                MusicDataUtils.getInstance().setCurrentPosition(position);
            }

            @Override
            public void itemLongClickListener(int position) {
                showMusicDetails(contentMusicAdapter.getData().get(position));
            }
        });
        stateRetry.setOnClickListener(view -> reloadMusic());
        footer = new MusicPageFooter(rootView, () -> { if (pages != null) pages.loadNext(); });
        musicContent.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView recycler, int dx, int dy) {
                if (dy > 0 && ((LinearLayoutManager) recycler.getLayoutManager()).findLastVisibleItemPosition()
                        >= contentMusicAdapter.getItemCount() - 4 && pages != null
                        && pages.state() != MusicPageController.State.MORE_ERROR) pages.loadNext();
            }
        });
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
        if (pages != null) pages.close();
        pages = new MusicPageController(ServiceFactory.getInstance().createService(ApiUrl.class),
                Schedulers.io(), AndroidSchedulers.mainThread(), (state, songs) -> {
                    contentMusicAdapter.setData(songs);
                    MusicDataUtils.getInstance().setMusicList(songs);
                    if (state == MusicPageController.State.LOADING) showLoading();
                    else if (state == MusicPageController.State.ERROR) showError(null);
                    else showContentState(songs);
                    footer.render(pages);
                });
        pages.refresh("", 0);
    }

    @Override public void onDestroyView() {
        if (pages != null) pages.close();
        pages = null;
        musicContent.setAdapter(null);
        contentMusicAdapter = null;
        footer = null;
        super.onDestroyView();
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

}
