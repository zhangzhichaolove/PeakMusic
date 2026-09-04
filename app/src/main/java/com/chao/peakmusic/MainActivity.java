package com.chao.peakmusic;

import android.Manifest;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.chao.peakmusic.activity.MusicPlayActivity;
import com.chao.peakmusic.adapter.HomePageAdapter;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.fragment.LocalMusicFragment;
import com.chao.peakmusic.fragment.OnLineMusicFragment;
import com.chao.peakmusic.listener.PlayMusicListener;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.service.TestService;
import com.chao.peakmusic.utils.AppStatusListener;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.utils.KeyDownUtils;
import com.chao.peakmusic.utils.MusicDataUtils;
import com.chao.peakmusic.utils.ScanningUtils;
import com.chao.peakmusic.utils.ToastUtils;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener, ScanningUtils.ScanningListener, PlayMusicListener {
    private static final long UPDATE_INTERVAL = 500;
    private static final int OVERLAY_PERMISSION_REQ_CODE = 66;
    private static final int AUDIO_PERMISSION_REQ_CODE = 67;
    Toolbar mToolbar;
    TabLayout tabs;
    DrawerLayout mDrawerLayout;
    NavigationView nv_menu;
    ViewPager vp_content;
    FrameLayout fl_play_bar;
    ImageView iv_album_cover;
    ImageView iv_play;
    ImageView iv_next;
    ProgressBar pb_play_bar;
    TextView tv_title;
    TextView tv_artist;
    private Handler handler;
    private Fragment[] fragments;
    private HomePageAdapter pageAdapter;
    private ScheduledExecutorService timer;
    private ArrayList<SongModel> music;

    @Override
    public int getLayout() {
        return R.layout.activity_main;
    }

    @Override
    public void initView() {
        mToolbar = findViewById(R.id.mToolbar);
        tabs = findViewById(R.id.tabs);
        mDrawerLayout = findViewById(R.id.dl_left);
        nv_menu = findViewById(R.id.id_nv_menu);
        vp_content = findViewById(R.id.vp_content);
        fl_play_bar = findViewById(R.id.fl_play_bar);
        iv_album_cover = findViewById(R.id.iv_play_bar_cover);
        iv_play = findViewById(R.id.iv_play);
        iv_next = findViewById(R.id.iv_next);
        pb_play_bar = findViewById(R.id.pb_play_bar);
        tv_title = findViewById(R.id.tv_title);
        tv_artist = findViewById(R.id.tv_artist);
        mToolbar.setTitle("");
        mToolbar.setLogo(R.drawable.menu_setting_icon);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setHomeButtonEnabled(true); //设置返回键可用
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        fragments = new Fragment[]{OnLineMusicFragment.newInstance(), LocalMusicFragment.newInstance()};
        pageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
        vp_content.setAdapter(pageAdapter);
        tabs.setupWithViewPager(vp_content);
        handler = new Handler(Looper.getMainLooper());
        timer = Executors.newScheduledThreadPool(1);
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, R.drawable.default_cover);
        //getSupportFragmentManager().beginTransaction().add(R.id.fl_content, LocalMusicFragment.newInstance(), LocalMusicFragment.class.getName()).commit();
    }

    @Override
    public void initData() {
        if (hasMusicPermission()) {
            loadMusic();
            requestOverlayPermission();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ? Manifest.permission.READ_MEDIA_AUDIO
                            : Manifest.permission.READ_EXTERNAL_STORAGE},
                    AUDIO_PERMISSION_REQ_CODE);
        }
        startTrackingPosition();
    }

    private void loadMusic() {
        if (ScanningUtils.getInstance(mContext).getMusic() == null) {
            ScanningUtils.getInstance(mContext).setListener(this).scanMusic();
        } else {
            onScanningMusicComplete(ScanningUtils.getInstance(mContext).getMusic());
        }
    }

    private boolean hasMusicPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 请求悬浮权限
     */
    void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMusic();
            } else {
                onScanningMusicComplete(new ArrayList<>());
            }
            requestOverlayPermission();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {

            }
        }
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

//        Intent intent = new Intent("com.chao.peakmusic.action.MUSIC_SERVICE");
//        intent.setPackage(getPackageName());
//        startService(intent);
        //测试Service
//        bindService(intent, conns, Context.BIND_AUTO_CREATE);
//        unbindService(connection);
//        AppStatusListener.getInstance().setAppLifecycle(new AppStatusListener.AppLifecycle() {
//            @Override
//            public void AppBackstage(boolean isBackstage) {
//                handler.post(() -> {
//                    try {
//                        if (isBackstage && mService != null) {//App切换到前台隐藏悬浮。这里会崩溃，原因待查。
//                            mService.hide();
//                        } else if (mService != null) {
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                                if (Settings.canDrawOverlays(MainActivity.this)) {//已有悬浮权限
//                                    mService.show();
//                                }
//                            } else {//低版本直接显示
//                                mService.show();
//                            }
//                        }
//                    } catch (RemoteException e) {
//                        e.printStackTrace();
//                    }
//                });
//            }
//        });
    }

    public PlayMusicListener getListener() {
        return this;
    }

//    private TestService audioService;
//
//    //使用ServiceConnection来监听Service状态的变化
//    private ServiceConnection conns = new ServiceConnection() {
//
//        @Override
//        public void onServiceDisconnected(ComponentName name) {
//            audioService = null;
//        }
//
//        @Override
//        public void onServiceConnected(ComponentName name, IBinder binder) {
//            //这里我们实例化audioService,通过binder来实现
//            audioService = ((TestService.AudioBinder) binder).getService();
//            audioService.playMusic();
//        }
//    };

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onClick(View view) {
        super.onClick(view);
        int id = view.getId();
        if (id == R.id.fl_play_bar) {
            Intent intent = new Intent(mContext, MusicPlayActivity.class);
            startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this, iv_album_cover, "album").toBundle());
        } else if (id == R.id.iv_play && mService != null) {
            if (iv_play.isSelected()) {//当前是暂停图标
                try {
                    mService.pause();
                    mService.clickButton(false);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else {//当前是三角图标，点击播放
                try {
                    mService.play();
                    mService.clickButton(true);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        } else if (id == R.id.iv_next && mService != null) {
            try {
                mService.next();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 开启定时器，动态获取服务中的歌曲信息。
     */
    private void startTrackingPosition() {
        timer.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        //System.out.println(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(new Date()));
                        try {
                            if (mService == null || !mService.isPlay()) {
                                return;
                            }
//                            if (music == null || mService == null || mService.getCurrentIndex() == -1) {
//                                return;
//                            }
//                            SongModel model = music.get(mService.getCurrentIndex());
//                            tv_title.setText(model.getSong());
//                            tv_artist.setText(model.getSinger());
                            pb_play_bar.setMax((int) mService.getDuration());
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
            try {
                //注册回调，服务状态同步到UI按钮。
                mService.registerCallback(mCallback);
                mService.show();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    private ActivityCall.Stub mCallback = new ActivityCall.Stub() {
        @Override
        public void call(boolean isPlay) throws RemoteException {
            Log.e("TAG", "ActivityCall" + isPlay);
            runOnUiThread(() -> iv_play.setSelected(isPlay));
        }

        @Override
        public void pre() throws RemoteException {
            runOnUiThread(() -> {
                int currentPosition = MusicDataUtils.getInstance().getCurrentPosition();
                if (currentPosition <= 0) {
                    ToastUtils.showToast("没有更多歌曲了~");
                } else {
                    List<MusicModel> musicList = MusicDataUtils.getInstance().getMusicList();
                    currentPosition -= 1;
                    MusicDataUtils.getInstance().setCurrentPosition(currentPosition);
                    MusicModel musicModel = musicList.get(currentPosition);
                    playMusic(musicModel.getMp3(),
                            musicModel.getName(), musicModel.getSinger(),
                            musicModel.getImg());
                }
            });
        }

        @Override
        public void next() throws RemoteException {
            runOnUiThread(() -> {
                List<MusicModel> musicList = MusicDataUtils.getInstance().getMusicList();
                int currentPosition = MusicDataUtils.getInstance().getCurrentPosition();
                if (currentPosition >= musicList.size() - 1) {
                    ToastUtils.showToast("没有更多歌曲了~");
                } else {
                    currentPosition += 1;
                    MusicDataUtils.getInstance().setCurrentPosition(currentPosition);
                    MusicModel musicModel = musicList.get(currentPosition);
                    playMusic(musicModel.getMp3(),
                            musicModel.getName(), musicModel.getSinger(),
                            musicModel.getImg());
                }
            });
        }

        @Override
        public void defaultPlay() throws RemoteException {
            runOnUiThread(() -> {
                List<MusicModel> musicList = MusicDataUtils.getInstance().getMusicList();
                if (musicList != null && musicList.size() > 0) {
                    MusicDataUtils.getInstance().setCurrentPosition(0);
                    MusicModel musicModel = musicList.get(0);
                    playMusic(musicModel.getMp3(),
                            musicModel.getName(), musicModel.getSinger(),
                            musicModel.getImg());
                } else {
                    ToastUtils.showToast("你的曲库没有歌曲呢~");
                }
            });
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
        ((LocalMusicFragment) fragments[1]).setMusic(music);
        Intent intent = new Intent(mContext, MusicService.class);
        intent.putExtra(MusicService.EXTRAS_MUSIC, music);
        mContext.startService(intent);
        mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        this.music = music;
    }

    @Override
    public void playMusic(int position, String name, String artist) {
        iv_play.setSelected(true);
        tv_title.setText(name);
        tv_artist.setText(artist);
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, R.drawable.default_cover);
        try {
            if (mService != null) {
                mService.openAudio(position);
                mService.clickButton(true);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void playMusic(String url, String name, String artist, String img) {
        iv_play.setSelected(true);
        tv_title.setText(name);
        tv_artist.setText(artist);
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, img);
        try {
            if (mService != null) {
                mService.playAudio(url);
                mService.clickButton(true);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
