package com.chao.peakmusic;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import com.chao.peakmusic.activity.MusicPlayActivity;
import com.chao.peakmusic.adapter.HomePageAdapter;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.fragment.LocalMusicFragment;
import com.chao.peakmusic.listener.PlayMusicListener;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.service.TestService;
import com.chao.peakmusic.utils.AppStatusListener;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.utils.KeyDownUtils;
import com.chao.peakmusic.utils.ScanningUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener, ScanningUtils.ScanningListener {
    private static final long UPDATE_INTERVAL = 1000;
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
    @BindView(R.id.iv_play)
    ImageView iv_play;
    @BindView(R.id.iv_next)
    ImageView iv_next;
    @BindView(R.id.pb_play_bar)
    ProgressBar pb_play_bar;
    @BindView(R.id.tv_title)
    TextView tv_title;
    @BindView(R.id.tv_artist)
    TextView tv_artist;
    private Handler handler;
    private HomePageAdapter pageAdapter;
    private ScheduledExecutorService timer;
    private LocalMusicFragment localMusicFragment;
    private ArrayList<SongModel> music;

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
        localMusicFragment = pageAdapter.getLocalMusicFragment();
        vp_content.setAdapter(pageAdapter);
        tabs.setupWithViewPager(vp_content);
        handler = new Handler(Looper.getMainLooper());
        timer = Executors.newScheduledThreadPool(1);
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, R.drawable.default_cover);
        //getSupportFragmentManager().beginTransaction().add(R.id.fl_content, LocalMusicFragment.newInstance(), LocalMusicFragment.class.getName()).commit();
    }

    @Override
    public void initData() {
        if (ScanningUtils.getInstance(mContext).getMusic() == null) {
            ScanningUtils.getInstance(mContext).setListener(this).scanMusic();
        } else {
            onScanningMusicComplete(ScanningUtils.getInstance(mContext).getMusic());
        }
        startTrackingPosition();
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
        iv_play.setOnClickListener(this);
        iv_next.setOnClickListener(this);

        Intent intent = new Intent("com.chao.peakmusic.action.MUSIC_SERVICE");
        intent.setPackage(getPackageName());
        startService(intent);
        bindService(intent, conns, Context.BIND_AUTO_CREATE);
        //unbindService(connection);

        AppStatusListener.getInstance().setAppLifecycle(new AppStatusListener.AppLifecycle() {
            @Override
            public void AppBackstage(boolean isBackstage) {
                try {
                    if (isBackstage && mService != null) {
                        mService.hide();
                    } else {
                        mService.show();
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        localMusicFragment.setPlayMusicListener(new PlayMusicListener() {
            @Override
            public void playMusic(int position) {
                try {
                    mService.openAudio(position);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private TestService audioService;

    //使用ServiceConnection来监听Service状态的变化
    private ServiceConnection conns = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
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
            case R.id.iv_play:
                break;
            case R.id.iv_next:
                break;
        }
    }

    private void startTrackingPosition() {
        timer.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(new Date()));
                        try {
                            if (music == null || mService == null || mService.getCurrentIndex() == -1) {
                                return;
                            }
                            SongModel model = music.get(mService.getCurrentIndex());
                            tv_title.setText(model.getSong());
                            tv_artist.setText(model.getSinger());
                            pb_play_bar.setMax(model.getDuration());
                            pb_play_bar.setProgress(mService.getCurrentPosition());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }, UPDATE_INTERVAL, UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private MusicAidlInterface mService;

    //使用ServiceConnection来监听Service状态的变化
    private ServiceConnection conn = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            //这里我们实例化audioService,通过binder来实现
            mService = MusicAidlInterface.Stub.asInterface(binder);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.shutdown();
        mContext.unbindService(conn);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }
        return KeyDownUtils.BlackBackstage(this, keyCode);
    }

    @Override
    public void onScanningMusicComplete(ArrayList<SongModel> music) {
        localMusicFragment.setMusic(music);
        Intent intent = new Intent(mContext, MusicService.class);
        intent.putExtra(MusicService.EXTRAS_MUSIC, music);
        mContext.startService(intent);
        mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        this.music = music;
    }
}
