package com.chao.peakmusic.base;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.reactivex.disposables.CompositeDisposable;

/**
 * Created by Chao on 2017-12-18.
 */

public abstract class BaseFragment extends Fragment implements BaseInterFace {
    protected Context mContext;
    protected View rootView;
    protected CompositeDisposable disposables;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getContext();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(getLayout(), container, false);
        disposables = new CompositeDisposable();
        init();
        return rootView;
    }

    private void init() {
        initView();
        initData();
        initListener();
    }

    @Override
    public void initView() {
    }

    @Override
    public void initData() {
    }

    @Override
    public void initListener() {
    }

    @Override
    public void onDestroyView() {
        if (disposables != null) {
            disposables.clear();
        }
        rootView = null;
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        mContext = null;
        super.onDetach();
    }
}
