package com.chao.peakmusic;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
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
import com.chao.peakmusic.base.ApiAddressManager;
import com.chao.peakmusic.base.ApiUrl;
import com.chao.peakmusic.fragment.LocalMusicFragment;
import com.chao.peakmusic.fragment.OnLineMusicFragment;
import com.chao.peakmusic.listener.PlayMusicListener;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.utils.KeyDownUtils;
import com.chao.peakmusic.utils.MusicDataUtils;
import com.chao.peakmusic.utils.ScanningUtils;
import com.chao.peakmusic.utils.ToastUtils;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener, ScanningUtils.ScanningListener, PlayMusicListener {
    private static final long UPDATE_INTERVAL = 500;
    private static final int AUDIO_PERMISSION_REQ_CODE = 67;
    private static final int NOTIFICATION_PERMISSION_REQ_CODE = 68;
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
    private ArrayList<SongModel> music;
    private MusicModel currentOnlineMusic;
    private String currentTrackName;
    private String currentTrackArtist;
    private String currentTrackImage;
    private boolean serviceBound;
    private final Runnable positionUpdater = new Runnable() {
        @Override
        public void run() {
            updatePlaybackProgress();
            handler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

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
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, R.drawable.default_cover);
        //getSupportFragmentManager().beginTransaction().add(R.id.fl_content, LocalMusicFragment.newInstance(), LocalMusicFragment.class.getName()).commit();
    }

    @Override
    public void initData() {
        if (hasMusicPermission()) {
            loadMusic();
            requestNotificationPermission();
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

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQ_CODE);
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
            requestNotificationPermission();
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

    }

    public PlayMusicListener getListener() {
        return this;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
        if (item.getItemId() == R.id.action_api_address) {
            showApiAddressDialog();
            return true;
        }
        return false;
    }

    private void showApiAddressDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(ApiAddressManager.getBaseUrl());
        input.setSelection(input.length());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.menu_api_address)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.restore_default, null)
                .setPositiveButton(R.string.save, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    if (!ApiAddressManager.saveBaseUrl(input.getText().toString())) {
                        input.setError(getString(R.string.api_address_invalid));
                        return;
                    }
                    dialog.dismiss();
                    ToastUtils.showToast(getString(R.string.api_address_saved));
                    ((OnLineMusicFragment) fragments[0]).reloadMusic();
                });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                input.setText(ApiUrl.BASE_URL);
                input.setSelection(input.length());
            });
        });
        dialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onClick(View view) {
        super.onClick(view);
        int id = view.getId();
        if (id == R.id.fl_play_bar) {
            Intent intent = new Intent(mContext, MusicPlayActivity.class);
            intent.putExtra(MusicPlayActivity.EXTRA_MUSIC, currentOnlineMusic);
            intent.putExtra(MusicPlayActivity.EXTRA_NAME, currentTrackName);
            intent.putExtra(MusicPlayActivity.EXTRA_SINGER, currentTrackArtist);
            intent.putExtra(MusicPlayActivity.EXTRA_IMAGE, currentTrackImage);
            startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this, iv_album_cover, "album").toBundle());
        } else if (id == R.id.iv_play && mService != null) {
            if (iv_play.isSelected()) {//当前是暂停图标
                try {
                    mService.pause();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else {//当前是三角图标，点击播放
                try {
                    mService.play();
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
        handler.post(positionUpdater);
    }

    private void updatePlaybackProgress() {
        try {
            if (mService == null || !mService.isPlay()) {
                return;
            }
            pb_play_bar.setMax((int) mService.getDuration());
            pb_play_bar.setProgress(mService.getCurrentPosition());
        } catch (RemoteException e) {
            Log.e("MainActivity", "Unable to read playback progress", e);
        }
    }

    private MusicAidlInterface mService;

    //使用ServiceConnection来监听Service状态的变化
    private ServiceConnection conn = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            serviceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            serviceBound = true;
            //这里我们实例化audioService,通过binder来实现
            mService = MusicAidlInterface.Stub.asInterface(binder);
            try {
                //注册回调，服务状态同步到UI按钮。
                mService.registerCallback(mCallback);
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
                List<MusicModel> musicList = MusicDataUtils.getInstance().getMusicList();
                if (musicList == null || musicList.isEmpty()) {
                    ToastUtils.showToast("你的曲库没有歌曲呢~");
                    return;
                }
                int currentPosition = MusicDataUtils.getInstance().getCurrentPosition();
                if (currentPosition <= 0) {
                    ToastUtils.showToast("没有更多歌曲了~");
                } else {
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
                if (musicList == null || musicList.isEmpty()) {
                    ToastUtils.showToast("你的曲库没有歌曲呢~");
                    return;
                }
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

        @Override
        public void trackChanged(String name, String artist, boolean local) {
            runOnUiThread(() -> {
                if (local) {
                    currentOnlineMusic = null;
                    currentTrackImage = null;
                    ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover,
                            R.drawable.default_cover);
                }
                currentTrackName = name;
                currentTrackArtist = artist;
                tv_title.setText(name);
                tv_artist.setText(artist);
            });
        }
    };

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(positionUpdater);
        if (serviceBound) {
            mContext.unbindService(conn);
            serviceBound = false;
        }
        super.onDestroy();
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
        startPlaybackService(music);
        this.music = music;
    }

    private void startPlaybackService(ArrayList<SongModel> songs) {
        Intent intent = new Intent(mContext, MusicService.class);
        if (songs != null) {
            intent.putExtra(MusicService.EXTRAS_MUSIC, songs);
        }
        ContextCompat.startForegroundService(mContext, intent);
        if (!serviceBound) {
            serviceBound = mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void playMusic(int position, String name, String artist) {
        currentOnlineMusic = null;
        currentTrackName = name;
        currentTrackArtist = artist;
        currentTrackImage = null;
        iv_play.setSelected(true);
        tv_title.setText(name);
        tv_artist.setText(artist);
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, R.drawable.default_cover);
        try {
            if (mService != null) {
                mService.openAudio(position);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void playMusic(String url, String name, String artist, String img) {
        currentOnlineMusic = findOnlineMusic(url);
        currentTrackName = name;
        currentTrackArtist = artist;
        currentTrackImage = img;
        iv_play.setSelected(true);
        tv_title.setText(name);
        tv_artist.setText(artist);
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, img);
        try {
            if (mService != null) {
                mService.playAudio(url, name, artist);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private MusicModel findOnlineMusic(String url) {
        List<MusicModel> musicList = MusicDataUtils.getInstance().getMusicList();
        if (musicList == null) {
            return null;
        }
        for (MusicModel musicModel : musicList) {
            if (musicModel != null && url != null && url.equals(musicModel.getMp3())) {
                return musicModel;
            }
        }
        return null;
    }
}
