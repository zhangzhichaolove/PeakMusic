package com.chao.peakmusic.base;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;

import com.chao.peakmusic.R;
import com.chao.peakmusic.utils.BarUtils;
import com.chao.peakmusic.utils.GeneralVar;
import com.chao.peakmusic.widget.CustomToolbar;

import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.disposables.CompositeDisposable;

/**
 * Created by Chao on 2017-12-18.
 */

public abstract class BaseActivity extends AppCompatActivity implements BaseInterFace, View.OnClickListener {
    private Unbinder bind;
    protected Context mContext;
    protected CustomToolbar mToolbar;
    protected CompositeDisposable disposables;
    View statusBarView;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BarUtils.setWindow(this);
        mContext = this;
        disposables = new CompositeDisposable();
        if (getLayout() != 0) {
            setContentView(getLayout());
        }
        bind = ButterKnife.bind(this);
        init();
    }

    private void init() {
        mToolbar = findViewById(R.id.toolbar);
        statusBarView = findViewById(R.id.statusBarView);
        if (statusBarView != null) {
            ViewGroup.LayoutParams statusBarLp = statusBarView.getLayoutParams();
            statusBarLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            statusBarLp.height = GeneralVar.getStatusHeight();
            statusBarView.setLayoutParams(statusBarLp);
        }
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
            mToolbar.setLeftImgOnClickListener(this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mToolbar != null) {
                //mToolbar.setElevation(0);
            }
        }
        initView();
        initData();
        initListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
        bind.unbind();
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
    public void onClick(View view) {
        if (view.getId() == R.id.toolbar_ivb_left) {
            onBackPressed();
        }
    }
}
