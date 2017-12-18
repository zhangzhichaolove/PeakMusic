package com.chao.peakmusic.activity;

import com.chao.peakmusic.R;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.utils.NavigationManager;

/**
 * 引导页
 * Created by Chao on 2017-12-18.
 */

public class GuideActivity extends BaseActivity {

    @Override
    public int getLayout() {
        return R.layout.activity_guide;
    }

    @Override
    public void initData() {
        NavigationManager.gotoHome(mContext);
        finish();
    }
}
