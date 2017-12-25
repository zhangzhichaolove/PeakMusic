package com.chao.peakmusic.base;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import com.chao.peakmusic.R;
import com.chao.peakmusic.utils.BarUtils;
import com.chao.peakmusic.widget.CustomToolbar;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by Chao on 2017-12-18.
 */

public abstract class BaseActivity extends AppCompatActivity implements BaseInterFace, View.OnClickListener {
    private Unbinder bind;
    protected Context mContext;
    protected CustomToolbar mToolbar;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BarUtils.setWindow(this);
        mContext = this;
        if (getLayout() != 0) {
            setContentView(getLayout());
        }
        bind = ButterKnife.bind(this);
        init();
    }

    private void init() {
        mToolbar = findViewById(R.id.toolbar);
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
