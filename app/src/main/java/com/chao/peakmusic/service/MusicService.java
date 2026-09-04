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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
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
import com.chao.peakmusic.model.SongModel;
import com.chao.peakmusic.utils.LogUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Music playback service with audio focus, MediaSession and foreground controls.
 */
public class MusicService extends Service {
    public static final String EXTRAS_MUSIC = "extras_music";

    private static final String TAG = "MusicService";
    private static final String CHANNEL_ID = "music_playback";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_PREVIOUS = "com.chao.peakmusic.action.PREVIOUS";
    private static final String ACTION_TOGGLE = "com.chao.peakmusic.action.TOGGLE";
    private static final String ACTION_NEXT = "com.chao.peakmusic.action.NEXT";
    private static final String STATE_PREFERENCES = "playback_state";
    private static final String KEY_LOCAL_QUEUE = "local_queue";
    private static final String KEY_ONLINE_QUEUE = "online_queue";
    private static final String KEY_ACTIVE_LOCAL = "active_local";
    private static final String KEY_INDEX = "index";
    private static final String KEY_POSITION = "position";
    private static final String KEY_MODE = "mode";
    private static final String KEY_PLAYING = "playing";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ArrayList<QueueItem> activeQueue = new ArrayList<>();
    private final ArrayList<QueueItem> localQueue = new ArrayList<>();
    private final ArrayList<QueueItem> onlineQueue = new ArrayList<>();
    private final Runnable stateSaver = new Runnable() {
        @Override
        public void run() {
            savePlaybackState();
            mainHandler.postDelayed(this, 5000);
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
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private ArrayList<SongModel> music = new ArrayList<>();
    private SongModel currentMusic;
    private ActivityCall activityCall;
    private boolean isLocal;
    private boolean prepared;
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
                startPlayback();
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(0.2f, 0.2f);
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            resumeOnFocusGain = isPlaying();
            pausePlayback(true);
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
        restorePlaybackState();
        registerNoisyReceiver();
        startForeground(NOTIFICATION_ID, buildNotification());
        mainHandler.postDelayed(stateSaver, 5000);
        LogUtils.showTagE("服务创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Object extra = intent.getSerializableExtra(EXTRAS_MUSIC);
            if (extra instanceof ArrayList) {
                //noinspection unchecked
                music = (ArrayList<SongModel>) extra;
                rebuildLocalQueue();
            }
            handleAction(intent.getAction());
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
                startPlayback();
            }
        } else if (ACTION_NEXT.equals(action)) {
            nextTrack();
        }
    }

    private void createPlayer() {
        mediaPlayer = new MediaPlayer();
        setPlayerAudioAttributes();
        mediaPlayer.setOnPreparedListener(player -> {
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
            prepared = false;
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
            notifyPlayingState(false);
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
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
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

    private void playMusic(String source) {
        prepareSource(source, true, 0);
    }

    private void prepareSource(String source, boolean shouldPlay, int seekPosition) {
        if (TextUtils.isEmpty(source)) {
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
            return;
        }
        try {
            prepared = false;
            currentSource = source;
            playWhenPrepared = shouldPlay;
            pendingSeekPosition = Math.max(0, seekPosition);
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
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
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            prepared = false;
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR);
            notifyPlayingState(false);
            Log.e(TAG, "Unable to play " + source, error);
        }
    }

    private void startPlayback() {
        if (!prepared || !requestAudioFocus()) {
            return;
        }
        try {
            mediaPlayer.start();
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING);
            notifyPlayingState(true);
            savePlaybackState();
        } catch (IllegalStateException error) {
            Log.e(TAG, "Unable to resume playback", error);
        }
    }

    private void playOrRequestDefault() {
        if (prepared) {
            startPlayback();
        } else if (activityCall != null) {
            try {
                activityCall.defaultPlay();
            } catch (RemoteException error) {
                Log.e(TAG, "Unable to request default track", error);
            }
        }
    }

    private void pausePlayback(boolean notifyActivity) {
        if (isPlaying()) {
            mediaPlayer.pause();
        }
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED);
        savePlaybackState();
        if (notifyActivity) {
            notifyPlayingState(false);
        }
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
            //noinspection deprecation
            result = audioManager.requestAudioFocus(focusChangeListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
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
            //noinspection deprecation
            audioManager.abandonAudioFocus(focusChangeListener);
        }
    }

    private void openLocalTrack(int position) {
        if (music == null || music.isEmpty()) {
            return;
        }
        int normalized = (position % music.size() + music.size()) % music.size();
        currentPosition = normalized;
        currentMusic = music.get(normalized);
        isLocal = true;
        currentTrackName = safeText(currentMusic.getSong(), getString(R.string.unknown_music));
        currentTrackArtist = safeText(currentMusic.getSinger(), getString(R.string.unknown_singer));
        rebuildLocalQueue();
        activeQueue.clear();
        activeQueue.addAll(localQueue);
        openQueueTrack(normalized, true);
    }

    private void nextTrack() {
        nextTrack(false);
    }

    private void nextTrack(boolean automatic) {
        int next = PlaybackModeNavigator.next(currentPosition, activeQueue.size(), playMode,
                automatic, random);
        if (next >= 0) {
            openQueueTrack(next, true);
        } else if (activeQueue.isEmpty() && activityCall != null) {
            try {
                activityCall.next();
            } catch (RemoteException error) {
                Log.e(TAG, "Unable to request next track", error);
            }
        } else {
            playWhenPrepared = false;
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED);
            notifyPlayingState(false);
            savePlaybackState();
        }
    }

    private void previousTrack() {
        int previous = PlaybackModeNavigator.previous(currentPosition, activeQueue.size(), playMode);
        if (previous >= 0) {
            openQueueTrack(previous, true);
        } else if (activeQueue.isEmpty() && activityCall != null) {
            try {
                activityCall.pre();
            } catch (RemoteException error) {
                Log.e(TAG, "Unable to request previous track", error);
            }
        }
    }

    private void openQueueTrack(int position, boolean shouldPlay) {
        if (position < 0 || position >= activeQueue.size()) {
            return;
        }
        currentPosition = position;
        QueueItem item = activeQueue.get(position);
        isLocal = item.local;
        currentTrackName = item.name;
        currentTrackArtist = item.artist;
        currentSource = item.source;
        notifyTrackChanged();
        prepareSource(currentSource, shouldPlay, 0);
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
        if (activityCall != null) {
            try {
                activityCall.call(playing);
            } catch (RemoteException error) {
                Log.e(TAG, "Unable to notify playback state", error);
            }
        }
    }

    private void notifyTrackChanged() {
        if (activityCall != null) {
            try {
                activityCall.trackChanged(currentSource, currentTrackName, currentTrackArtist, isLocal);
            } catch (RemoteException error) {
                Log.e(TAG, "Unable to notify track change", error);
            }
        }
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

    private void setOnlineQueue(List<String> urls, List<String> names,
                                List<String> artists, int selectedIndex) {
        onlineQueue.clear();
        if (urls == null) {
            return;
        }
        for (int i = 0; i < urls.size(); i++) {
            String source = urls.get(i);
            if (TextUtils.isEmpty(source)) {
                continue;
            }
            String name = names != null && i < names.size() ? names.get(i) : null;
            String artist = artists != null && i < artists.size() ? artists.get(i) : null;
            onlineQueue.add(new QueueItem(source,
                    safeText(name, getString(R.string.unknown_music)),
                    safeText(artist, getString(R.string.unknown_singer)), false));
        }
        if (!isLocal || TextUtils.isEmpty(currentSource)) {
            activeQueue.clear();
            activeQueue.addAll(onlineQueue);
            if (activeQueue.isEmpty()) {
                currentPosition = -1;
            } else if (TextUtils.isEmpty(currentSource)) {
                currentPosition = Math.max(0, Math.min(selectedIndex, activeQueue.size() - 1));
            } else {
                int matching = findQueueIndex(activeQueue, currentSource);
                currentPosition = matching >= 0 ? matching
                        : Math.max(0, Math.min(selectedIndex, activeQueue.size() - 1));
            }
        }
    }

    private void savePlaybackState() {
        getSharedPreferences(STATE_PREFERENCES, MODE_PRIVATE).edit()
                .putString(KEY_LOCAL_QUEUE, serializeQueue(localQueue))
                .putString(KEY_ONLINE_QUEUE, serializeQueue(onlineQueue))
                .putBoolean(KEY_ACTIVE_LOCAL, isLocal)
                .putInt(KEY_INDEX, currentPosition)
                .putInt(KEY_POSITION, (int) currentPlaybackPosition())
                .putInt(KEY_MODE, playMode)
                .putBoolean(KEY_PLAYING, isPlaying())
                .apply();
    }

    private void restorePlaybackState() {
        SharedPreferences preferences = getSharedPreferences(STATE_PREFERENCES, MODE_PRIVATE);
        playMode = PlaybackModeNavigator.normalizeMode(preferences.getInt(KEY_MODE,
                PlaybackModeNavigator.SEQUENTIAL));
        try {
            deserializeQueue(preferences.getString(KEY_LOCAL_QUEUE, null), localQueue);
            deserializeQueue(preferences.getString(KEY_ONLINE_QUEUE, null), onlineQueue);
            isLocal = preferences.getBoolean(KEY_ACTIVE_LOCAL, false);
            activeQueue.addAll(isLocal ? localQueue : onlineQueue);
            int index = preferences.getInt(KEY_INDEX, -1);
            if (index >= 0 && index < activeQueue.size()) {
                currentPosition = index;
                QueueItem item = activeQueue.get(index);
                currentSource = item.source;
                currentTrackName = item.name;
                currentTrackArtist = item.artist;
                prepareSource(currentSource, preferences.getBoolean(KEY_PLAYING, false),
                        preferences.getInt(KEY_POSITION, 0));
            }
        } catch (JSONException error) {
            Log.w(TAG, "Ignoring invalid saved playback queue", error);
            activeQueue.clear();
        }
    }

    private String serializeQueue(List<QueueItem> items) {
        JSONArray queue = new JSONArray();
        for (QueueItem item : items) {
            try {
                queue.put(new JSONObject()
                        .put("source", item.source)
                        .put("name", item.name)
                        .put("artist", item.artist)
                        .put("local", item.local));
            } catch (JSONException error) {
                Log.e(TAG, "Unable to serialize playback queue", error);
            }
        }
        return queue.toString();
    }

    private static void deserializeQueue(String json, List<QueueItem> output)
            throws JSONException {
        if (TextUtils.isEmpty(json)) {
            return;
        }
        JSONArray queue = new JSONArray(json);
        for (int i = 0; i < queue.length(); i++) {
            JSONObject item = queue.getJSONObject(i);
            output.add(new QueueItem(item.optString("source"), item.optString("name"),
                    item.optString("artist"), item.optBoolean("local")));
        }
    }

    private void rebuildLocalQueue() {
        localQueue.clear();
        if (music == null) {
            return;
        }
        for (SongModel song : music) {
            localQueue.add(new QueueItem(song.getPath(),
                    safeText(song.getSong(), getString(R.string.unknown_music)),
                    safeText(song.getSinger(), getString(R.string.unknown_singer)), true));
        }
        if (isLocal && !TextUtils.isEmpty(currentSource)) {
            activeQueue.clear();
            activeQueue.addAll(localQueue);
            int matching = findQueueIndex(activeQueue, currentSource);
            if (matching >= 0) {
                currentPosition = matching;
            }
        }
        savePlaybackState();
    }

    private static int findQueueIndex(List<QueueItem> queue, String source) {
        for (int i = 0; i < queue.size(); i++) {
            if (source.equals(queue.get(i).source)) {
                return i;
            }
        }
        return -1;
    }

    private static final class QueueItem {
        final String source;
        final String name;
        final String artist;
        final boolean local;

        QueueItem(String source, String name, String artist, boolean local) {
            this.source = source;
            this.name = name;
            this.artist = artist;
            this.local = local;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return stub;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        activityCall = null;
        return true;
    }

    @Override
    public void onDestroy() {
        savePlaybackState();
        mainHandler.removeCallbacksAndMessages(null);
        unregisterReceiver(noisyReceiver);
        abandonAudioFocus();
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
        stopForeground(true);
        super.onDestroy();
    }

    private final MusicAidlInterface.Stub stub = new MusicAidlInterface.Stub() {
        @Override
        public void openAudio(int position) {
            openLocalTrack(position);
        }

        @Override
        public void playAudio(String url, String name, String artist) {
            isLocal = false;
            currentMusic = null;
            currentTrackName = safeText(name, getString(R.string.unknown_music));
            currentTrackArtist = safeText(artist, getString(R.string.unknown_singer));
            currentSource = url;
            activeQueue.clear();
            activeQueue.addAll(onlineQueue);
            currentPosition = findQueueIndex(activeQueue, url);
            if (currentPosition < 0) {
                QueueItem item = new QueueItem(url, currentTrackName, currentTrackArtist, false);
                onlineQueue.add(item);
                activeQueue.add(item);
                currentPosition = activeQueue.size() - 1;
            }
            notifyTrackChanged();
            playMusic(url);
        }

        @Override
        public void setOnlineQueue(List<String> urls, List<String> names,
                                   List<String> artists, int currentIndex) {
            MusicService.this.setOnlineQueue(urls, names, artists, currentIndex);
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
            playMode = PlaybackModeNavigator.normalizeMode(mode);
            savePlaybackState();
        }

        @Override
        public int getPlayMode() {
            return playMode;
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
            activityCall = call;
            notifyTrackChanged();
            notifyPlayingState(isPlaying());
        }

    };
}
