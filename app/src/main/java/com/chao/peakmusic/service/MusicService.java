package com.chao.peakmusic.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.RemoteCallbackList;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.chao.peakmusic.ActivityCall;
import com.chao.peakmusic.MainActivity;
import com.chao.peakmusic.MusicAidlInterface;
import com.chao.peakmusic.R;
import com.chao.peakmusic.data.MusicLibraryRepository;
import com.chao.peakmusic.data.MusicTrackEntity;
import com.chao.peakmusic.utils.LogUtils;
import com.cleveroad.audiowidget.AudioWidget;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


/**
 * Music playback service with audio focus, MediaSession and foreground controls.
 */
public class MusicService extends Service {
    public static final String ACTION_PLAY_LIBRARY_QUEUE =
            "com.chao.peakmusic.action.PLAY_LIBRARY_QUEUE";
    public static final String ACTION_SET_SLEEP_TIMER =
            "com.chao.peakmusic.action.SET_SLEEP_TIMER";
    public static final String ACTION_SHOW_FLOATING_CONTROL =
            "com.chao.peakmusic.action.SHOW_FLOATING_CONTROL";
    public static final int QUEUE_PAGE_SIZE = 100;
    public static final String EXTRA_SLEEP_DELAY = "sleep_delay";
    public static final String SLEEP_PREFERENCES = "sleep_timer";
    public static final String KEY_SLEEP_END = "end_time";

    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_PREVIOUS = "com.chao.peakmusic.action.PREVIOUS";
    private static final String ACTION_TOGGLE = "com.chao.peakmusic.action.TOGGLE";
    private static final String ACTION_NEXT = "com.chao.peakmusic.action.NEXT";
    public static final String ACTION_HIDE_FLOATING_CONTROL = "com.chao.peakmusic.HIDE_FLOATING_CONTROL";
    public static final String KEY_FLOATING_ENABLED = "enabled";
    public static final String FLOATING_PREFERENCES = "floating_control";
    private static final String KEY_FLOATING_X = "x";
    private static final String KEY_FLOATING_Y = "y";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ArrayList<QueueItem> activeQueue = new ArrayList<>();
    private final Runnable stateSaver = new Runnable() {
        @Override
        public void run() {
            savePlaybackState();
            mainHandler.postDelayed(this, 5000);
        }
    };
    private final Runnable sleepTimer = () -> {
        pausePlayback(true);
        getSharedPreferences(SLEEP_PREFERENCES, MODE_PRIVATE).edit()
                .remove(KEY_SLEEP_END).apply();
    };
    private final Runnable floatingProgressUpdater = new Runnable() {
        @Override
        public void run() {
            if (audioWidget != null && audioWidget.isShown() && prepared) {
                audioWidget.controller().duration((int) currentDuration());
                audioWidget.controller().position((int) currentPlaybackPosition());
            }
            mainHandler.postDelayed(this, 1000);
        }
    };
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                pausePlayback(true);
            }
        }
    };

    private MediaPlayer mediaPlayer;
    private MediaSessionCompat mediaSession;
    private AudioWidget audioWidget;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private PlaybackStorage storage;
    private boolean restoring = true;
    private boolean destroyed;
    private boolean persistenceFailed;
    private boolean modeChangedDuringRestore;
    private Boolean restorePlayOverride;
    private long loadGeneration;
    private String appliedRequestId;
    private final RemoteCallbackList<ActivityCall> activityCallbacks = new RemoteCallbackList<>();
    private boolean isLocal;
    private boolean prepared;
    private boolean preparing;
    private boolean queuesDirty = true;
    private long queueVersion = System.nanoTime();
    private int playbackState = PlaybackStateCompat.STATE_NONE;
    private String playbackError = "";
    private boolean resumeOnFocusGain;
    private int currentPosition = -1;
    private int playMode = PlaybackModeNavigator.SEQUENTIAL;
    private int pendingSeekPosition;
    private boolean playWhenPrepared = true;
    private String currentSource;
    private String currentTrackName = "PeakMusic";
    private String currentTrackArtist = "";

    private final AudioManager.OnAudioFocusChangeListener focusChangeListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(1f, 1f);
            }
            if (resumeOnFocusGain) {
                resumeOnFocusGain = false;
                playOrRequestDefault();
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(0.2f, 0.2f);
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            boolean shouldResume = isPlaying() || (preparing && playWhenPrepared);
            pausePlayback(true);
            resumeOnFocusGain = shouldResume;
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            resumeOnFocusGain = false;
            pausePlayback(true);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();
        createMediaSession();
        createPlayer();
        storage = PlaybackStorage.get(this);
        loadPlayback(null);
        showFloatingControl();
        registerNoisyReceiver();
        startForeground(NOTIFICATION_ID, buildNotification());
        mainHandler.postDelayed(stateSaver, 5000);
        mainHandler.post(floatingProgressUpdater);
        restoreSleepTimer();
        LogUtils.showTagE("服务创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            handleAction(intent.getAction());
            if (ACTION_PLAY_LIBRARY_QUEUE.equals(intent.getAction())) {
                String id = intent.getStringExtra(PlaybackStorage.EXTRA_QUEUE_ID);
                if (id != null && storage.isCurrent(id)) loadPlayback(id);
            } else if (ACTION_SET_SLEEP_TIMER.equals(intent.getAction())) {
                setSleepTimer(intent.getLongExtra(EXTRA_SLEEP_DELAY, 0));
            }
        }
        return START_STICKY;
    }

    private void handleAction(String action) {
        if (ACTION_PREVIOUS.equals(action)) {
            previousTrack();
        } else if (ACTION_TOGGLE.equals(action)) {
            if (isPlaying()) {
                pausePlayback(true);
            } else {
                playOrRequestDefault();
            }
        } else if (ACTION_NEXT.equals(action)) {
            nextTrack();
        } else if (ACTION_SHOW_FLOATING_CONTROL.equals(action)) {
            showFloatingControl();
        } else if (ACTION_HIDE_FLOATING_CONTROL.equals(action)) {
            if (audioWidget != null) audioWidget.hide();
        }
    }

    private void showFloatingControl() {
        if (!getSharedPreferences(FLOATING_PREFERENCES, MODE_PRIVATE).getBoolean(KEY_FLOATING_ENABLED, false)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            return;
        }
        if (audioWidget == null) {
            audioWidget = new AudioWidget.Builder(this).build();
            audioWidget.controller().onControlsClickListener(createFloatingControlsListener());
            audioWidget.controller().onWidgetStateChangedListener(
                    new AudioWidget.OnWidgetStateChangedListener() {
                        @Override
                        public void onWidgetStateChanged(AudioWidget.State state) {
                            // No additional action is needed when the widget expands or collapses.
                        }

                        @Override
                        public void onWidgetPositionChanged(int cx, int cy) {
                            getSharedPreferences(FLOATING_PREFERENCES, MODE_PRIVATE).edit()
                                    .putInt(KEY_FLOATING_X, cx)
                                    .putInt(KEY_FLOATING_Y, cy)
                                    .apply();
                        }
                    });
        }
        if (!audioWidget.isShown()) {
            SharedPreferences preferences = getSharedPreferences(
                    FLOATING_PREFERENCES, MODE_PRIVATE);
            int defaultX = getResources().getDisplayMetrics().widthPixels;
            int defaultY = getResources().getDisplayMetrics().heightPixels / 2;
            try {
                audioWidget.show(preferences.getInt(KEY_FLOATING_X, defaultX),
                        preferences.getInt(KEY_FLOATING_Y, defaultY));
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to show floating playback control", error);
                audioWidget.hide();
                return;
            }
        }
        syncFloatingControl(isPlaying()
                ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
    }

    private AudioWidget.OnControlsClickListener createFloatingControlsListener() {
        return new AudioWidget.OnControlsClickListener() {
            @Override
            public boolean onPlaylistClicked() {
                return false;
            }

            @Override
            public void onPlaylistLongClicked() {
            }

            @Override
            public void onPreviousClicked() {
                previousTrack();
            }

            @Override
            public void onPreviousLongClicked() {
            }

            @Override
            public boolean onPlayPauseClicked(boolean shouldPlay) {
                if (shouldPlay) {
                    playOrRequestDefault();
                } else {
                    pausePlayback(true);
                }
                return true;
            }

            @Override
            public void onPlayPauseLongClicked() {
            }

            @Override
            public void onNextClicked() {
                nextTrack();
            }

            @Override
            public void onNextLongClicked() {
            }

            @Override
            public void onAlbumClicked() {
                startActivity(new Intent(MusicService.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }

            @Override
            public void onAlbumLongClicked() {
            }
        };
    }

    private void syncFloatingControl(int state) {
        if (audioWidget == null) {
            return;
        }
        audioWidget.controller().duration((int) currentDuration());
        audioWidget.controller().position((int) currentPlaybackPosition());
        if (state == PlaybackStateCompat.STATE_PLAYING) {
            audioWidget.controller().start();
        } else if (state == PlaybackStateCompat.STATE_PAUSED
                || state == PlaybackStateCompat.STATE_BUFFERING) {
            audioWidget.controller().pause();
        } else {
            audioWidget.controller().stop();
        }
    }

    private void createPlayer() {
        mediaPlayer = new MediaPlayer();
        setPlayerAudioAttributes();
        mediaPlayer.setOnPreparedListener(player -> {
            preparing = false;
            prepared = true;
            if (pendingSeekPosition > 0) {
                player.seekTo(Math.min(pendingSeekPosition, Math.max(0, player.getDuration() - 1)));
                pendingSeekPosition = 0;
            }
            updateMetadata();
            if (playWhenPrepared) {
                startPlayback();
            } else {
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
                notifyPlayingState(false);
                savePlaybackState();
            }
        });
        mediaPlayer.setOnCompletionListener(player -> {
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
            nextTrack(true);
        });
        mediaPlayer.setOnErrorListener((player, what, extra) -> {
            reportPlaybackError();
            Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
            return true;
        });
    }

    private void setPlayerAudioAttributes() {
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
    }

    private void createMediaSession() {
        mediaSession = new MediaSessionCompat(this, TAG);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                playOrRequestDefault();
            }

            @Override
            public void onPause() {
                pausePlayback(true);
            }

            @Override
            public void onSkipToNext() {
                nextTrack();
            }

            @Override
            public void onSkipToPrevious() {
                previousTrack();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }
        });
        mediaSession.setActive(true);
        updatePlaybackState(PlaybackStateCompat.STATE_NONE);
    }

    private void prepareSource(String source, boolean shouldPlay, int seekPosition) {
        if (TextUtils.isEmpty(source)) {
            reportPlaybackError();
            return;
        }
        try {
            prepared = false;
            preparing = true;
            playbackError = "";
            currentSource = source;
            playWhenPrepared = shouldPlay;
            pendingSeekPosition = Math.max(0, seekPosition);
            mediaPlayer.reset();
            setPlayerAudioAttributes();
            if (source.startsWith("content://")) {
                mediaPlayer.setDataSource(this, Uri.parse(source));
            } else {
                mediaPlayer.setDataSource(source);
            }
            mediaPlayer.setLooping(false);
            updateMetadata();
            updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING);
            mediaPlayer.prepareAsync();
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException error) {
            reportPlaybackError();
            Log.e(TAG, "Unable to play " + source, error);
        }
    }

    private void startPlayback() {
        if (!prepared) {
            return;
        }
        if (!requestAudioFocus()) {
            pausePlayback(true);
            playbackError = getString(R.string.audio_focus_unavailable);
            return;
        }
        try {
            playWhenPrepared = true;
            playbackError = "";
            mediaPlayer.start();
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            notifyPlayingState(true);
            savePlaybackState();
        } catch (IllegalStateException error) {
            reportPlaybackError();
            Log.e(TAG, "Unable to resume playback", error);
        }
    }

    private void playOrRequestDefault() {
        restorePlayOverride = true;
        storage.cancelPending();
        if (restoring) return;
        if (prepared) {
            startPlayback();
        } else if (preparing) {
            playWhenPrepared = true;
            updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING);
        } else if (!TextUtils.isEmpty(currentSource)) {
            prepareSource(currentSource, true, pendingSeekPosition);
        } else {
            broadcastCallback(ActivityCall::defaultPlay, "request default track");
        }
    }

    private void pausePlayback(boolean notifyActivity) {
        restorePlayOverride = false;
        storage.cancelPending();
        playWhenPrepared = false;
        resumeOnFocusGain = false;
        if (isPlaying()) {
            mediaPlayer.pause();
        }
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
        savePlaybackState();
        if (notifyActivity) {
            notifyPlayingState(false);
        }
    }

    private void reportPlaybackError() {
        prepared = false;
        preparing = false;
        playWhenPrepared = false;
        playbackError = getString(R.string.music_playback_failed);
        updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
        notifyPlayingState(false);
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) {
            return true;
        }
        int result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
                        .build();
            }
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = requestLegacyAudioFocus();
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            abandonLegacyAudioFocus();
        }
    }

    @SuppressWarnings("deprecation")
    private int requestLegacyAudioFocus() {
        return audioManager.requestAudioFocus(focusChangeListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    @SuppressWarnings("deprecation")
    private void abandonLegacyAudioFocus() {
        audioManager.abandonAudioFocus(focusChangeListener);
    }

    private void nextTrack() {
        nextTrack(false);
    }

    private void nextTrack(boolean automatic) {
        if (!automatic) storage.cancelPending();
        int next = PlaybackModeNavigator.next(currentPosition, activeQueue.size(), playMode,
                automatic, random);
        if (next >= 0) {
            openQueueTrack(next, true);
        } else if (!activeQueue.isEmpty() && automatic) {
            playWhenPrepared = false;
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
            notifyPlayingState(false);
            savePlaybackState();
        }
    }

    private void previousTrack() {
        storage.cancelPending();
        int previous = PlaybackModeNavigator.previous(currentPosition, activeQueue.size(), playMode);
        if (previous >= 0) {
            openQueueTrack(previous, true);
        }
    }

    private void openQueueTrack(int position, boolean shouldPlay) {
        if (position < 0 || position >= activeQueue.size()) {
            return;
        }
        currentPosition = position;
        queueVersion++;
        QueueItem item = activeQueue.get(position);
        MusicLibraryRepository.get(this).saveMetadata(item.metadata);
        isLocal = item.local;
        currentTrackName = item.name;
        currentTrackArtist = item.artist;
        currentSource = item.source;
        notifyTrackChanged();
        MusicLibraryRepository.get(this).recordPlayback(item.key(),
                currentTrackName, currentTrackArtist, isLocal);
        prepareSource(currentSource, shouldPlay, 0);
        savePlaybackState();
    }

    private void playLibraryQueue(PlaybackStorage.Request request, boolean shouldPlay) {
        if (request.next) {
            appliedRequestId = request.id;
            enqueueNext(request.tracks.get(0), shouldPlay);
            return;
        }
        ArrayList<QueueItem> replacement = new ArrayList<>();
        for (MusicTrackEntity track : request.tracks) {
            if (track != null && !TextUtils.isEmpty(track.source)) replacement.add(new QueueItem(track));
        }
        if (replacement.isEmpty()) return;
        appliedRequestId = request.id;
        queuesDirty = true;
        queueVersion++;
        activeQueue.clear();
        activeQueue.addAll(replacement);
        openQueueTrack(Math.max(0, Math.min(request.position, activeQueue.size() - 1)), shouldPlay);
    }

    private void enqueueNext(MusicTrackEntity track, boolean shouldPlay) {
        if (track == null || TextUtils.isEmpty(track.source)) return;
        activeQueue.add(Math.min(currentPosition + 1, activeQueue.size()), new QueueItem(track));
        queueVersion++;
        queuesDirty = true;
        if (currentPosition < 0) openQueueTrack(0, shouldPlay);
        else savePlaybackState();
    }

    private Bundle queuePage(int requestedOffset) {
        int lastPage = activeQueue.isEmpty() ? 0 : (activeQueue.size() - 1) / QUEUE_PAGE_SIZE * QUEUE_PAGE_SIZE;
        int offset = Math.max(0, Math.min(requestedOffset, lastPage));
        ArrayList<String> names = new ArrayList<>(), artists = new ArrayList<>();
        for (int i = offset; i < Math.min(offset + QUEUE_PAGE_SIZE, activeQueue.size()); i++) {
            names.add(queueLabel(activeQueue.get(i).name));
            artists.add(queueLabel(activeQueue.get(i).artist));
        }
        Bundle page = new Bundle();
        page.putStringArrayList("names", names); page.putStringArrayList("artists", artists);
        page.putInt("offset", offset); page.putInt("total", activeQueue.size());
        page.putInt("current", currentPosition); page.putLong("version", queueVersion);
        return page;
    }

    private static String queueLabel(String value) {
        // UI pages never ship URLs/artwork/lyrics or arbitrarily large server labels through Binder.
        return value == null ? "" : value.substring(0, Math.min(256, value.length()));
    }

    private boolean validQueueIndex(int index, long version) {
        return version == queueVersion && index >= 0 && index < activeQueue.size();
    }

    private boolean moveQueueItem(int from, int to, long version) {
        if (!validQueueIndex(from, version) || to < 0 || to >= activeQueue.size()) return false;
        if (from == to) return true;
        storage.cancelPending();
        QueueItem current = currentPosition >= 0 ? activeQueue.get(currentPosition) : null;
        activeQueue.add(to, activeQueue.remove(from));
        // Object identity, not URL: the same recording may legitimately appear more than once.
        currentPosition = current == null ? -1 : activeQueue.indexOf(current);
        queueVersion++;
        queuesDirty = true;
        savePlaybackState();
        return true;
    }

    private boolean removeQueueItem(int index, long version) {
        if (!validQueueIndex(index, version)) return false;
        storage.cancelPending();
        boolean shouldPlay = isPlaying() || (preparing && playWhenPrepared);
        activeQueue.remove(index);
        queueVersion++;
        queuesDirty = true;
        if (activeQueue.isEmpty()) {
            clearPlaybackQueue();
        } else if (index == currentPosition) {
            resumeOnFocusGain = false;
            openQueueTrack(Math.min(index, activeQueue.size() - 1), shouldPlay);
        } else {
            if (index < currentPosition) currentPosition--;
            savePlaybackState();
        }
        return true;
    }

    private void clearPlaybackQueue() {
        storage.cancelPending();
        loadGeneration++;
        restoring = false;
        activeQueue.clear();
        currentPosition = -1;
        currentSource = null;
        currentTrackName = getString(R.string.queue_empty);
        currentTrackArtist = "";
        isLocal = false;
        playWhenPrepared = false;
        resumeOnFocusGain = false;
        prepared = false;
        preparing = false;
        pendingSeekPosition = 0;
        playbackError = "";
        mediaPlayer.reset();
        abandonAudioFocus();
        queueVersion++;
        queuesDirty = true;
        updateMetadata();
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
        notifyTrackChanged();
        notifyPlayingState(false);
        savePlaybackState();
    }

    private void setSleepTimer(long delayMs) {
        mainHandler.removeCallbacks(sleepTimer);
        if (delayMs <= 0) {
            getSharedPreferences(SLEEP_PREFERENCES, MODE_PRIVATE).edit()
                    .remove(KEY_SLEEP_END).apply();
            return;
        }
        long endTime = System.currentTimeMillis() + delayMs;
        getSharedPreferences(SLEEP_PREFERENCES, MODE_PRIVATE).edit()
                .putLong(KEY_SLEEP_END, endTime).apply();
        mainHandler.postDelayed(sleepTimer, delayMs);
    }

    private void restoreSleepTimer() {
        long endTime = getSharedPreferences(SLEEP_PREFERENCES, MODE_PRIVATE)
                .getLong(KEY_SLEEP_END, 0);
        long remaining = endTime - System.currentTimeMillis();
        if (remaining > 0) {
            mainHandler.postDelayed(sleepTimer, remaining);
        } else if (endTime > 0) {
            // Initial disk restoration has not started audio yet; a fresh explicit play may follow.
            restorePlayOverride = false;
            getSharedPreferences(SLEEP_PREFERENCES, MODE_PRIVATE).edit()
                    .remove(KEY_SLEEP_END).apply();
        }
    }

    private void seekTo(int position) {
        if (prepared && mediaPlayer != null) {
            mediaPlayer.seekTo(Math.max(0, position));
            updatePlaybackState(isPlaying()
                    ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED);
            savePlaybackState();
        }
    }

    private boolean isPlaying() {
        try {
            return prepared && mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private long currentPlaybackPosition() {
        try {
            return prepared && mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private long currentDuration() {
        try {
            return prepared && mediaPlayer != null ? mediaPlayer.getDuration() : 0;
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    private void notifyPlayingState(boolean playing) {
        broadcastCallback(callback -> callback.call(playing), "notify playback state");
    }

    private void notifyTrackChanged() {
        MusicTrackEntity track = selectedTrack();
        broadcastCallback(callback -> callback.trackChanged(track), "notify track change");
    }

    private MusicTrackEntity selectedTrack() {
        if (currentPosition < 0 || currentPosition >= activeQueue.size()) return null;
        QueueItem item = activeQueue.get(currentPosition);
        if (item.metadata != null) return item.metadata;
        MusicTrackEntity legacy = new MusicTrackEntity();
        legacy.source = item.source; legacy.name = item.name; legacy.artist = item.artist; legacy.local = item.local;
        return legacy;
    }

    private void broadcastCallback(CallbackAction action, String operation) {
        int count = activityCallbacks.beginBroadcast();
        try {
            for (int i = 0; i < count; i++) {
                try {
                    action.run(activityCallbacks.getBroadcastItem(i));
                } catch (RemoteException error) {
                    Log.e(TAG, "Unable to " + operation, error);
                }
            }
        } finally {
            activityCallbacks.finishBroadcast();
        }
    }

    private interface CallbackAction {
        void run(ActivityCall callback) throws RemoteException;
    }

    private void updateMetadata() {
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTrackName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentTrackArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDuration())
                .build());
        updateNotification();
    }

    private void updatePlaybackState(int state) {
        playbackState = state;
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO;
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, currentPlaybackPosition(),
                        state == PlaybackStateCompat.STATE_PLAYING ? 1f : 0f)
                .build());
        syncFloatingControl(state);
        updateNotification();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.playback_notification_channel),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.playback_notification_channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        boolean playing = isPlaying();
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), pendingIntentFlags());
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.peak_music_logo)
                .setContentTitle(currentTrackName)
                .setContentText(TextUtils.isEmpty(currentTrackArtist)
                        ? getString(R.string.playback_ready) : currentTrackArtist)
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(playing)
                .addAction(android.R.drawable.ic_media_previous,
                        getString(R.string.previous_track), actionIntent(ACTION_PREVIOUS, 1))
                .addAction(playing ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play,
                        playing ? getString(R.string.pause) : getString(R.string.play),
                        actionIntent(ACTION_TOGGLE, 2))
                .addAction(android.R.drawable.ic_media_next,
                        getString(R.string.next_track), actionIntent(ACTION_NEXT, 3))
                .setStyle(new MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, MusicService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags());
    }

    private int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private void updateNotification() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && mediaSession != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private void registerNoisyReceiver() {
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            //noinspection UnspecifiedRegisterReceiverFlag
            registerReceiver(noisyReceiver, filter);
        }
    }

    private static String safeText(String value, String fallback) {
        return TextUtils.isEmpty(value) || "<unknown>".equals(value) ? fallback : value;
    }

    private void savePlaybackState() {
        if (restoring) return; // An early stop/pause must not overwrite a queue still being read.
        PlaybackStorage.State state = new PlaybackStorage.State();
        if (queuesDirty) state.queue = new ArrayList<>(activeQueue);
        queuesDirty = false;
        state.key = currentPosition >= 0 && currentPosition < activeQueue.size() ? activeQueue.get(currentPosition).key() : null;
        state.source = currentSource; state.local = isLocal; state.index = currentPosition;
        state.position = prepared ? (int) currentPlaybackPosition() : pendingSeekPosition;
        state.mode = playMode; state.playing = isPlaying() || (preparing && playWhenPrepared);
        storage.save(state, appliedRequestId, saved -> {
            if (!saved) {
                queuesDirty = true;
                if (!destroyed && !persistenceFailed) android.widget.Toast.makeText(this,
                        R.string.playback_save_failed, android.widget.Toast.LENGTH_LONG).show();
            }
            persistenceFailed = !saved;
        });
    }

    private void loadPlayback(String requestId) {
        long generation = ++loadGeneration;
        if (requestId != null) restorePlayOverride = null;
        storage.load(requestId, restoring, (state, request, error) -> {
            if (destroyed || generation != loadGeneration) return;
            boolean initialRestore = restoring;
            restoring = false;
            if (initialRestore && !modeChangedDuringRestore) playMode = PlaybackModeNavigator.normalizeMode(state.mode);
            if (request != null && storage.isCurrent(request.id)) {
                playLibraryQueue(request, restorePlayOverride == null || restorePlayOverride);
            } else if (initialRestore) {
                activeQueue.clear();
                for (PlaybackQueueCodec.Item item : state.queue) {
                    QueueItem restored = new QueueItem(item.source, item.name, item.artist, item.local);
                    restored.metadata = item.metadata;
                    activeQueue.add(restored);
                }
                queueVersion++;
                int index = state.index;
                String identity = state.key != null ? state.key : state.source;
                boolean useKey = state.key != null;
                if (identity != null && (index < 0 || index >= activeQueue.size()
                        || !TextUtils.equals(useKey ? activeQueue.get(index).key() : activeQueue.get(index).source, identity)))
                    index = findQueueIndex(activeQueue, identity, useKey);
                if (index >= 0 && index < activeQueue.size()) {
                    currentPosition = index;
                    QueueItem item = activeQueue.get(index);
                    isLocal = item.local; currentSource = item.source;
                    currentTrackName = item.name; currentTrackArtist = item.artist;
                    MusicLibraryRepository.get(this).saveMetadata(item.metadata);
                    notifyTrackChanged();
                    prepareSource(currentSource, restorePlayOverride != null ? restorePlayOverride : state.playing, state.position);
                }
                savePlaybackState();
            }
            if (error != null) android.widget.Toast.makeText(this, R.string.queue_load_failed,
                    android.widget.Toast.LENGTH_LONG).show();
        });
    }

    private static int findQueueIndex(List<QueueItem> queue, String source, boolean useKey) {
        for (int i = 0; i < queue.size(); i++) {
            if (source.equals(useKey ? queue.get(i).key() : queue.get(i).source)) {
                return i;
            }
        }
        return -1;
    }

    private final class QueueItem extends PlaybackQueueCodec.Item {
        QueueItem(MusicTrackEntity track) {
            this(track.getPlaybackUrl(), safeText(track.name, getString(R.string.unknown_music)),
                    safeText(track.artist, getString(R.string.unknown_singer)), track.local);
            metadata = track;
        }
        QueueItem(String source, String name, String artist, boolean local) {
            super(source, name, artist, local);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        savePlaybackState();
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        unregisterReceiver(noisyReceiver);
        abandonAudioFocus();
        if (audioWidget != null) {
            audioWidget.controller().onControlsClickListener(null);
            audioWidget.controller().onWidgetStateChangedListener(null);
            audioWidget.hide();
            audioWidget = null;
        }
        if (mediaPlayer != null) {
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        activityCallbacks.kill();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForegroundLegacy();
        }
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void stopForegroundLegacy() {
        stopForeground(true);
    }

    private final MusicAidlInterface.Stub stub = new MusicAidlInterface.Stub() {
        @Override public Bundle getQueuePage(int offset) { return queuePage(offset); }
        @Override public long getQueueVersion() { return queueVersion; }
        @Override public boolean playQueueItem(int index, long version) {
            if (!validQueueIndex(index, version)) return false;
            storage.cancelPending();
            openQueueTrack(index, true);
            return true;
        }
        @Override public boolean moveQueueItem(int from, int to, long version) {
            return MusicService.this.moveQueueItem(from, to, version);
        }
        @Override public boolean removeQueueItem(int index, long version) {
            return MusicService.this.removeQueueItem(index, version);
        }
        @Override public boolean clearQueue(long version) {
            if (version != queueVersion) return false;
            clearPlaybackQueue();
            return true;
        }

        @Override
        public void play() {
            playOrRequestDefault();
        }

        @Override
        public void pause() {
            pausePlayback(true);
        }

        @Override
        public String getMusicName() {
            return currentTrackName;
        }

        @Override
        public boolean isPlay() {
            return isPlaying();
        }

        @Override
        public int getPlaybackState() {
            return playbackState;
        }

        @Override
        public String getPlaybackError() {
            return playbackError;
        }

        @Override
        public long getDuration() {
            return currentDuration();
        }

        @Override
        public int getCurrentIndex() {
            return currentPosition;
        }

        @Override
        public int getCurrentPosition() {
            return (int) currentPlaybackPosition();
        }

        @Override
        public void seekTo(int position) {
            MusicService.this.seekTo(position);
        }

        @Override
        public void seekPlayMode(int mode) {
            modeChangedDuringRestore = true;
            playMode = PlaybackModeNavigator.normalizeMode(mode);
            savePlaybackState();
        }

        @Override
        public int getPlayMode() {
            return playMode;
        }

        @Override
        public int getAudioSessionId() {
            return mediaPlayer == null ? 0 : mediaPlayer.getAudioSessionId();
        }

        @Override
        public void pre() {
            previousTrack();
        }

        @Override
        public void next() {
            nextTrack();
        }

        @Override
        public void registerCallback(ActivityCall call) {
            if (call != null) {
                activityCallbacks.register(call);
            }
            notifyTrackChanged();
            notifyPlayingState(isPlaying());
        }

        @Override
        public void unregisterCallback(ActivityCall call) {
            if (call != null) {
                activityCallbacks.unregister(call);
            }
        }

    };
}
