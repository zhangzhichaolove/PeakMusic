package com.chao.peakmusic;

import android.Manifest;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.ServiceConnection;
import android.media.audiofx.AudioEffect;
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
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.chao.peakmusic.activity.MusicPlayActivity;
import com.chao.peakmusic.activity.ApiLogActivity;
import com.chao.peakmusic.activity.MusicLibraryActivity;
import com.chao.peakmusic.activity.MusicSearchActivity;
import com.chao.peakmusic.adapter.HomePageAdapter;
import com.chao.peakmusic.base.BaseActivity;
import com.chao.peakmusic.base.ApiAddressManager;
import com.chao.peakmusic.base.ApiUrl;
import com.chao.peakmusic.fragment.LocalMusicFragment;
import com.chao.peakmusic.fragment.OnLineMusicFragment;
import com.chao.peakmusic.listener.PlayMusicListener;
import com.chao.peakmusic.model.MusicModel;
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.data.MusicLibraryRepository;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.service.PlaybackModeNavigator;
import com.chao.peakmusic.utils.ImageLoaderV4;
import com.chao.peakmusic.utils.KeyDownUtils;
import com.chao.peakmusic.utils.MusicDataUtils;
import com.chao.peakmusic.utils.ScanningUtils;
import com.chao.peakmusic.utils.ToastUtils;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener, ScanningUtils.ScanningListener, PlayMusicListener {
    private static final String TAG = "MainActivity";
    private static final long UPDATE_INTERVAL = 500;
    Toolbar mToolbar;
    TabLayout tabs;
    DrawerLayout mDrawerLayout;
    NavigationView nv_menu;
    ViewPager2 vp_content;
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
    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    loadMusic();
                } else {
                    onScanningMusicComplete(new ArrayList<>());
                }
                requestNotificationPermission();
            });
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // Playback remains available when notifications are declined.
            });
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
        pageAdapter = new HomePageAdapter(this, fragments);
        vp_content.setAdapter(pageAdapter);
        new TabLayoutMediator(tabs, vp_content,
                (tab, position) -> tab.setText(position == 0
                        ? R.string.online_music : R.string.local_music)).attach();
        handler = new Handler(Looper.getMainLooper());
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, R.drawable.default_cover);
    }

    @Override
    public void initData() {
        if (hasMusicPermission()) {
            loadMusic();
            requestNotificationPermission();
        } else {
            audioPermissionLauncher.launch(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? Manifest.permission.READ_MEDIA_AUDIO
                    : Manifest.permission.READ_EXTERNAL_STORAGE);
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
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
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
        if (item.getItemId() == R.id.action_api_logs) {
            startActivity(new Intent(this, ApiLogActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_play_mode) {
            showPlayModeDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_music_favorites) {
            startActivity(MusicLibraryActivity.intent(this, MusicLibraryActivity.MODE_FAVORITES));
            return true;
        }
        if (item.getItemId() == R.id.action_recently_played) {
            startActivity(MusicLibraryActivity.intent(this, MusicLibraryActivity.MODE_HISTORY));
            return true;
        }
        if (item.getItemId() == R.id.action_playlists) {
            startActivity(MusicLibraryActivity.intent(this, MusicLibraryActivity.MODE_PLAYLISTS));
            return true;
        }
        if (item.getItemId() == R.id.action_search_music) {
            startActivity(new Intent(this, MusicSearchActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_timer) {
            showSleepTimerDialog();
            return true;
        }
        if (item.getItemId() == R.id.action_setting) {
            openEqualizer();
            return true;
        }
        if (item.getItemId() == R.id.action_night) {
            int current = getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            AppCompatDelegate.setDefaultNightMode(current
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
            return true;
        }
        if (item.getItemId() == R.id.action_exit) {
            stopService(new Intent(this, MusicService.class));
            finishAffinity();
            return true;
        }
        if (item.getItemId() == R.id.action_about) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.about_content)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        }
        return false;
    }

    private void showSleepTimerDialog() {
        int[] minutes = {0, 15, 30, 60, 90};
        String[] labels = getResources().getStringArray(R.array.sleep_timer_options);
        new AlertDialog.Builder(this)
                .setTitle(R.string.sleep_timer)
                .setItems(labels, (dialog, which) -> {
                    long delay = minutes[which] * 60_000L;
                    Intent intent = new Intent(this, MusicService.class)
                            .setAction(MusicService.ACTION_SET_SLEEP_TIMER)
                            .putExtra(MusicService.EXTRA_SLEEP_DELAY, delay);
                    ContextCompat.startForegroundService(this, intent);
                    ToastUtils.showToast(getString(minutes[which] == 0
                            ? R.string.sleep_timer_cancelled : R.string.sleep_timer_set,
                            minutes[which]));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openEqualizer() {
        if (mService == null) {
            ToastUtils.showToast(getString(R.string.playback_service_unavailable));
            return;
        }
        try {
            Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mService.getAudioSessionId())
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName())
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException error) {
                ToastUtils.showToast(getString(R.string.equalizer_unavailable));
            }
        } catch (RemoteException error) {
            ToastUtils.showToast(getString(R.string.equalizer_unavailable));
        }
    }

    private void showPlayModeDialog() {
        if (mService == null) {
            ToastUtils.showToast(getString(R.string.playback_service_unavailable));
            return;
        }
        String[] modes = getResources().getStringArray(R.array.play_modes);
        int currentMode = PlaybackModeNavigator.SEQUENTIAL;
        try {
            currentMode = mService.getPlayMode();
        } catch (RemoteException error) {
            Log.e("MainActivity", "Unable to read play mode", error);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.play_mode)
                .setSingleChoiceItems(modes, currentMode, (dialog, which) -> {
                    try {
                        mService.seekPlayMode(which);
                        ToastUtils.showToast(getString(R.string.play_mode_changed, modes[which]));
                    } catch (RemoteException error) {
                        Log.e("MainActivity", "Unable to set play mode", error);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showApiAddressDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(ApiAddressManager.getBaseUrl());
        input.setSelection(input.length());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        content.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout actions = new LinearLayout(this);
        Button restore = new Button(this);
        restore.setText(R.string.restore_default);
        Button test = new Button(this);
        test.setText(R.string.test_connection);
        actions.addView(restore, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        actions.addView(test, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        content.addView(actions);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.menu_api_address)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
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
                    if (ApiAddressManager.getBaseUrl().startsWith("http://")) {
                        ToastUtils.showToast(getString(R.string.cleartext_api_warning));
                    }
                    ((OnLineMusicFragment) fragments[0]).reloadMusic();
                });
            restore.setOnClickListener(view -> {
                input.setText(ApiUrl.BASE_URL);
                input.setSelection(input.length());
            });
            test.setOnClickListener(view -> {
                test.setEnabled(false);
                test.setText(R.string.testing_connection);
                ApiAddressManager.testConnection(input.getText().toString(), (reachable, detail) -> {
                    test.setEnabled(true);
                    test.setText(R.string.test_connection);
                    ToastUtils.showToast(getString(reachable
                                    ? R.string.connection_success : R.string.connection_failed,
                            detail == null ? getString(R.string.unknown_error) : detail));
                });
            });
        });
        dialog.show();
    }

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
                    Log.e(TAG, "Unable to pause playback", e);
                }
            } else {//当前是三角图标，点击播放
                try {
                    mService.play();
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to resume playback", e);
                }
            }
        } else if (id == R.id.iv_next && mService != null) {
            try {
                mService.next();
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to skip playback", e);
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
            Log.e(TAG, "Unable to read playback progress", e);
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
            mService = MusicAidlInterface.Stub.asInterface(binder);
            try {
                mService.registerCallback(mCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to register playback callback", e);
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
        public void trackChanged(String source, String name, String artist, boolean local) {
            runOnUiThread(() -> {
                if (local) {
                    currentOnlineMusic = null;
                    currentTrackImage = findLocalCover(source);
                    ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover,
                            currentTrackImage == null ? R.drawable.default_cover : currentTrackImage);
                } else {
                    currentOnlineMusic = findOnlineMusic(source);
                    currentTrackImage = currentOnlineMusic == null ? null
                            : currentOnlineMusic.getImg();
                    ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover,
                            currentTrackImage == null ? R.drawable.default_cover : currentTrackImage);
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
            try {
                if (mService != null) {
                    mService.unregisterCallback(mCallback);
                }
            } catch (RemoteException error) {
                Log.w("MainActivity", "Unable to unregister playback callback", error);
            }
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
            intent.putParcelableArrayListExtra(MusicService.EXTRAS_MUSIC, songs);
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
        currentTrackImage = music != null && position >= 0 && position < music.size()
                ? findLocalCover(music.get(position).getPath()) : null;
        iv_play.setSelected(true);
        tv_title.setText(name);
        tv_artist.setText(artist);
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover,
                currentTrackImage == null ? R.drawable.default_cover : currentTrackImage);
        if (music != null && position >= 0 && position < music.size()) {
            MusicLibraryRepository.get(this).saveMetadata(
                    MusicTrackEntity.from(music.get(position)));
        }
        try {
            if (mService != null) {
                mService.openAudio(position);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to play local track", e);
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
        if (currentOnlineMusic != null) {
            MusicLibraryRepository.get(this).saveMetadata(
                    MusicTrackEntity.from(currentOnlineMusic));
        }
        try {
            if (mService != null) {
                List<MusicModel> queue = MusicDataUtils.getInstance().getMusicList();
                if (queue != null && !queue.isEmpty()) {
                    ArrayList<String> urls = new ArrayList<>();
                    ArrayList<String> names = new ArrayList<>();
                    ArrayList<String> artists = new ArrayList<>();
                    for (MusicModel item : queue) {
                        urls.add(item.getMp3());
                        names.add(item.getName());
                        artists.add(item.getSinger());
                    }
                    mService.setOnlineQueue(urls, names, artists,
                            MusicDataUtils.getInstance().getCurrentPosition());
                }
                mService.playAudio(url, name, artist);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to play online track", e);
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

    private String findLocalCover(String source) {
        if (music == null || source == null) {
            return null;
        }
        for (SongModel song : music) {
            if (source.equals(song.getPath()) && song.getAlbumId() > 0) {
                return ScanningUtils.getInstance(this)
                        .getMediaStoreAlbumCoverUri(song.getAlbumId()).toString();
            }
        }
        return null;
    }
}
