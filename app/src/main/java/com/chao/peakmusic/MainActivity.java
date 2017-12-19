package com.chao.peakmusic;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.chao.peakmusic.activity.MusicPlayActivity;
import com.chao.peakmusic.adapter.HomePageAdapter;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.service.TestService;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.utils.KeyDownUtils;

import butterknife.BindView;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {
    @BindView(R.id.mToolbar)
    Toolbar mToolbar;
    @BindView(R.id.tabs)
    TabLayout tabs;
    @BindView(R.id.dl_left)
    DrawerLayout mDrawerLayout;
    @BindView(R.id.id_nv_menu)
    NavigationView nv_menu;
    @BindView(R.id.vp_content)
    ViewPager vp_content;
    @BindView(R.id.fl_play_bar)
    FrameLayout fl_play_bar;
    @BindView(R.id.iv_play_bar_cover)
    ImageView iv_album_cover;
    private HomePageAdapter pageAdapter;

    @Override
    public int getLayout() {
        return R.layout.activity_main;
    }

    @Override
    public void initView() {
        mToolbar.setTitle("");
        mToolbar.setLogo(R.drawable.menu_setting_icon);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setHomeButtonEnabled(true); //设置返回键可用
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        pageAdapter = new HomePageAdapter(getSupportFragmentManager());
        vp_content.setAdapter(pageAdapter);
        tabs.setupWithViewPager(vp_content);
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, R.drawable.default_cover);
        //getSupportFragmentManager().beginTransaction().add(R.id.fl_content, LocalMusicFragment.newInstance(), LocalMusicFragment.class.getName()).commit();
    }

    @Override
    public void initListener() {
        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar, R.string.open, R.string.close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);

            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);

            }
        };

        mDrawerToggle.syncState();
        //vp_content.addOnPageChangeListener(presenter);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        nv_menu.setNavigationItemSelectedListener(this);
        fl_play_bar.setOnClickListener(this);
        Intent intent = new Intent(this, TestService.class);
        startService(intent);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
        //AudioWidget audioWidget = new AudioWidget.Builder(mContext).build();
        //audioWidget.controller().onControlsClickListener(new ControlsClickListener());
        //audioWidget.show(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight() / 2);
    }

    private TestService audioService;

    //使用ServiceConnection来监听Service状态的变化
    private ServiceConnection conn = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub
            audioService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            //这里我们实例化audioService,通过binder来实现
            audioService = ((TestService.AudioBinder) binder).getService();
            audioService.playMusic();
        }
    };

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_setting:
                break;
            case R.id.action_night:
                break;
            case R.id.action_timer:
                break;
            case R.id.action_exit:
                break;
            case R.id.action_about:
                break;
        }
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
        return false;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onClick(View view) {
        super.onClick(view);
        switch (view.getId()) {
            case R.id.fl_play_bar:
                Intent intent = new Intent(mContext, MusicPlayActivity.class);
                startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this, iv_album_cover, "album").toBundle());
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return KeyDownUtils.BlackBackstage(this, keyCode);
    }
}
