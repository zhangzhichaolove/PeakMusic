package com.chao.peakmusic.activity;

import android.os.Handler;
import android.os.Looper;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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
        mHandler = new Handler(Looper.getMainLooper());
        mHandler.postDelayed(run, 2000);
    }

    private void toggleHideyBar() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
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
