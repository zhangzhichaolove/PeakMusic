package com.chao.peakmusic;

import android.support.annotation.NonNull;
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

import com.chao.peakmusic.adapter.HomePageAdapter;
import com.chao.peakmusic.base.BaseActivity;
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
    }

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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return KeyDownUtils.BlackBackstage(this, keyCode);
    }
}
