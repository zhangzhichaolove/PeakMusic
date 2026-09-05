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
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
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
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.service.MusicService;
import com.chao.peakmusic.service.PlaybackModeNavigator;
import com.chao.peakmusic.utils.ImageLoaderV4;
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
    private MusicModel currentOnlineMusic;
    private String currentTrackName;
    private String currentTrackArtist;
    private String currentTrackImage;
    private boolean serviceBound;
    private boolean started;
    private boolean observingMedia;
    private final Handler mediaHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshChangedMedia = () -> {
        if (!started) return;
        ScanningUtils.getInstance(this).invalidate();
        if (vp_content.getCurrentItem() == 1) loadMusic();
    };
    private final android.database.ContentObserver mediaObserver = new android.database.ContentObserver(mediaHandler) {
        @Override public void onChange(boolean selfChange) {
            mediaHandler.removeCallbacks(refreshChangedMedia);
            mediaHandler.postDelayed(refreshChangedMedia, 400);
        }
    };
    private final ViewPager2.OnPageChangeCallback localPageCallback = new ViewPager2.OnPageChangeCallback() {
        @Override public void onPageSelected(int position) {
            if (position == 1) loadMusic();
        }
    };
    private final ActivityResultLauncher<String> audioPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                ScanningUtils.getInstance(this).invalidate();
                if (granted) {
                    observeMedia();
                    loadMusic();
                }
            });
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    ToastUtils.showToast(getString(granted ? R.string.notification_enabled : R.string.notification_declined)));
    private final ActivityResultLauncher<Intent> localSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                ScanningUtils.getInstance(this).invalidate();
                observeMedia();
                loadMusic();
            });
    private final ActivityResultLauncher<Intent> overlayPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (canDrawOverlays()) setFloatingControl(true);
                else ToastUtils.showToast(getString(R.string.floating_permission_declined));
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
        com.chao.peakmusic.utils.BarUtils.applyBottomInsets(findViewById(android.R.id.content));
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
        vp_content.registerOnPageChangeCallback(localPageCallback);
        new TabLayoutMediator(tabs, vp_content,
                (tab, position) -> tab.setText(position == 0
                        ? R.string.online_music : R.string.local_music)).attach();
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) mDrawerLayout.closeDrawer(GravityCompat.START);
                else if (vp_content.getCurrentItem() != 1 || !((LocalMusicFragment) homeFragment(1)).navigateUp()) moveTaskToBack(true);
            }
        });
        handler = new Handler(Looper.getMainLooper());
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, R.drawable.default_cover);
    }

    @Override
    public void initData() {
        ScanningUtils.getInstance(this).setListener(this);
        startPlaybackService(); // Online playback never depends on granting local media access.
        startTrackingPosition();
    }

    private void loadMusic() {
        ScanningUtils scanner = ScanningUtils.getInstance(this).setListener(this);
        onScanStateChanged(scanner.getState());
        if (!scanner.hasPermission()) return;
        if (scanner.getMusic() != null && scanner.getState() == ScanningUtils.State.READY) {
            onScanningMusicComplete(scanner.getMusic());
        } else scanner.scanMusic();
    }

    public void requestLocalMusic() {
        ScanningUtils scanner = ScanningUtils.getInstance(this);
        if (scanner.hasPermission()) {
            scanner.invalidate();
            loadMusic();
            return;
        }
        String permission = ScanningUtils.musicPermission();
        boolean asked = getPreferences(MODE_PRIVATE).getBoolean("audio_permission_asked", false);
        if (asked && !shouldShowRequestPermissionRationale(permission)) {
            new AlertDialog.Builder(this).setMessage(R.string.local_permission_settings)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.open_app_settings, (dialog, which) -> localSettingsLauncher.launch(
                            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))).show();
        } else {
            getPreferences(MODE_PRIVATE).edit().putBoolean("audio_permission_asked", true).apply();
            audioPermissionLauncher.launch(permission);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && (!getPreferences(MODE_PRIVATE).getBoolean("notification_permission_asked", false)
                    || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_permission_asked", true).apply();
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            Intent settings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivity(settings);
        }
    }

    private void requestFloatingControlPermission() {
        boolean enabled = getSharedPreferences(MusicService.FLOATING_PREFERENCES, MODE_PRIVATE)
                .getBoolean(MusicService.KEY_FLOATING_ENABLED, false);
        if (enabled && canDrawOverlays()) { setFloatingControl(false); return; }
        if (canDrawOverlays()) { setFloatingControl(true); return; }
        new AlertDialog.Builder(this).setMessage(R.string.floating_permission_explanation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.open_app_settings, (dialog, which) -> overlayPermissionLauncher.launch(
                        new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))).show();
    }

    private void setFloatingControl(boolean enabled) {
        getSharedPreferences(MusicService.FLOATING_PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(MusicService.KEY_FLOATING_ENABLED, enabled).apply();
        nv_menu.getMenu().findItem(R.id.action_floating_control).setChecked(enabled);
        ContextCompat.startForegroundService(this, new Intent(this, MusicService.class).setAction(enabled
                ? MusicService.ACTION_SHOW_FLOATING_CONTROL : MusicService.ACTION_HIDE_FLOATING_CONTROL));
    }

    private void observeMedia() {
        if (!started || observingMedia || !ScanningUtils.getInstance(this).hasPermission()) return;
        getContentResolver().registerContentObserver(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true, mediaObserver);
        observingMedia = true;
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        ScanningUtils.getInstance(this).invalidate();
        observeMedia();
        if (vp_content.getCurrentItem() == 1) loadMusic();
        nv_menu.getMenu().findItem(R.id.action_floating_control).setChecked(
                getSharedPreferences(MusicService.FLOATING_PREFERENCES, MODE_PRIVATE)
                        .getBoolean(MusicService.KEY_FLOATING_ENABLED, false) && canDrawOverlays());
    }

    @Override protected void onStop() {
        started = false;
        mediaHandler.removeCallbacks(refreshChangedMedia);
        if (observingMedia) {
            getContentResolver().unregisterContentObserver(mediaObserver);
            observingMedia = false;
        }
        super.onStop();
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
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
        if (item.getItemId() == R.id.action_floating_control) {
            requestFloatingControlPermission();
            return true;
        }
        if (item.getItemId() == R.id.action_notification_permission) {
            requestNotificationPermission();
            return true;
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

    private okhttp3.Call connectionTest;

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
        Button restore = new androidx.appcompat.widget.AppCompatButton(this);
        restore.setText(R.string.restore_default);
        Button test = new androidx.appcompat.widget.AppCompatButton(this);
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
        dialog.setOnDismissListener(ignored -> {
            if (connectionTest != null) connectionTest.cancel();
            connectionTest = null;
        });
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
                    ((OnLineMusicFragment) homeFragment(0)).reloadMusic();
                });
            restore.setOnClickListener(view -> {
                input.setText(ApiUrl.BASE_URL);
                input.setSelection(input.length());
            });
            test.setOnClickListener(view -> {
                test.setEnabled(false);
                test.setText(R.string.testing_connection);
                String testedAddress = input.getText().toString();
                connectionTest = ApiAddressManager.testConnection(testedAddress, (reachable, detail) -> {
                    if (isDestroyed() || !dialog.isShowing()) return;
                    test.setEnabled(true);
                    test.setText(R.string.test_connection);
                    if (!testedAddress.equals(input.getText().toString())) return;
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
                    playMusic(musicModel);
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
                    playMusic(musicModel);
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
                    playMusic(musicModel);
                } else {
                    ToastUtils.showToast("你的曲库没有歌曲呢~");
                }
            });
        }

        @Override
        public void trackChanged(MusicTrackEntity track) {
            runOnUiThread(() -> {
                currentOnlineMusic = track == null || track.local ? null : track.toOnlineMusic();
                currentTrackImage = track == null ? null : track.imageUrl;
                ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover,
                        currentTrackImage == null ? R.drawable.default_cover : currentTrackImage);
                currentTrackName = track == null ? "" : track.name;
                currentTrackArtist = track == null ? "" : track.artist;
                tv_title.setText(currentTrackName);
                tv_artist.setText(currentTrackArtist);
            });
        }
    };

    @Override
    protected void onDestroy() {
        if (connectionTest != null) connectionTest.cancel();
        handler.removeCallbacks(positionUpdater);
        ScanningUtils.getInstance(this).clearListener(this);
        vp_content.unregisterOnPageChangeCallback(localPageCallback);
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

    private Fragment homeFragment(int position) {
        // FragmentStateAdapter restores its own instances; the constructor array may be detached.
        Fragment restored = getSupportFragmentManager().findFragmentByTag("f" + pageAdapter.getItemId(position));
        return restored == null ? fragments[position] : restored;
    }

    @Override
    public void onScanningMusicComplete(ArrayList<SongModel> music) {
        ((LocalMusicFragment) homeFragment(1)).setMusic(music);
    }

    @Override public void onScanStateChanged(ScanningUtils.State state) {
        ((LocalMusicFragment) homeFragment(1)).setScanState(state);
    }

    private void startPlaybackService() {
        Intent intent = new Intent(mContext, MusicService.class);
        ContextCompat.startForegroundService(mContext, intent);
        if (!serviceBound) {
            serviceBound = mContext.bindService(intent, conn, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void playMusic(MusicModel music) {
        currentOnlineMusic = music;
        currentTrackName = music.getName(); currentTrackArtist = music.getSinger(); currentTrackImage = music.getImg();
        iv_play.setSelected(true);
        tv_title.setText(currentTrackName); tv_artist.setText(currentTrackArtist);
        ImageLoaderV4.getInstance().loadCircle(mContext, iv_album_cover, currentTrackImage);
        List<MusicModel> catalogue = MusicDataUtils.getInstance().getMusicList();
        ArrayList<MusicTrackEntity> queue = new ArrayList<>();
        String key = MusicTrackEntity.keyOf(music);
        int selected = -1;
        if (catalogue != null) for (MusicModel item : catalogue) {
            if (key.equals(MusicTrackEntity.keyOf(item))) selected = queue.size();
            queue.add(MusicTrackEntity.from(item));
        }
        if (selected < 0) { queue.clear(); queue.add(MusicTrackEntity.from(music)); selected = 0; }
        com.chao.peakmusic.service.PlaybackStorage.get(this).play(queue, selected);
    }

}
