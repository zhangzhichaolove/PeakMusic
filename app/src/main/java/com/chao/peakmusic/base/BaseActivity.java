package com.chao.peakmusic.base;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.chao.peakmusic.R;
import com.chao.peakmusic.utils.BarUtils;
import com.chao.peakmusic.widget.CustomToolbar;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Created by Chao on 2017-12-18.
 */

public abstract class BaseActivity extends AppCompatActivity implements BaseInterFace, View.OnClickListener {
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
        init();
    }

    private void init() {
        mToolbar = findViewById(R.id.toolbar);
        statusBarView = findViewById(R.id.statusBarView);
        if (statusBarView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(statusBarView, (view, insets) -> {
                ViewGroup.LayoutParams statusBarLp = view.getLayoutParams();
                statusBarLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                statusBarLp.height = insets.getInsets(
                        WindowInsetsCompat.Type.statusBars()).top;
                view.setLayoutParams(statusBarLp);
                return insets;
            });
            ViewCompat.requestApplyInsets(statusBarView);
        }
        if (mToolbar != null) {
            setSupportActionBar(mToolbar);
            mToolbar.setLeftImgOnClickListener(this);
        }
        initView();
        initData();
        initListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
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
            getOnBackPressedDispatcher().onBackPressed();
        }
    }
}
