package com.chao.peakmusic.activity;

import android.os.Build;
import android.os.Handler;
import android.view.View;

import com.chao.peakmusic.R;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.utils.NavigationManager;

/**
 * 引导页
 * Created by Chao on 2017-12-18.
 */

public class GuideActivity extends BaseActivity {
    private Handler mHandler;

    @Override
    public int getLayout() {
        return R.layout.activity_guide;
    }

    @Override
    public void initView() {
        toggleHideyBar();
    }

    @Override
    public void initData() {
        mHandler = new Handler();
        mHandler.postDelayed(run, 2000);
    }

    private void toggleHideyBar() {
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;
        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= 16) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            newUiOptions ^= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }


    private Runnable run = new Runnable() {
        @Override
        public void run() {
            NavigationManager.gotoHome(mContext);
            finish();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(run);
        mHandler = null;
        run = null;
    }
}
