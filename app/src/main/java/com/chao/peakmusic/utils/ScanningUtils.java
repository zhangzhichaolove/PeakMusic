package com.chao.peakmusic.utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import com.chao.peakmusic.model.SongModel;

import java.util.ArrayList;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扫描工具类
 * Created by Chao on 2017-12-19.
 */

public class ScanningUtils {

    private static volatile ScanningUtils instance;
    private final ContentResolver contentResolver;
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile ArrayList<SongModel> musics;
    private WeakReference<ScanningListener> listener = new WeakReference<>(null);

    private ScanningUtils(Context context) {
        contentResolver = context.getApplicationContext().getContentResolver();
    }

    public static ScanningUtils getInstance(Context context) {
        if (instance == null) {
            synchronized (ScanningUtils.class) {
                if (instance == null) {
                    instance = new ScanningUtils(context);
                }
            }
        }
        return instance;
    }

    /**
     * 获取本机音乐列表
     *
     * @return
     */
    public void scanMusic() {
        scanExecutor.execute(() -> {
            ArrayList<SongModel> result = queryMusic();
            musics = result;
            mainHandler.post(() -> {
                ScanningListener callback = listener.get();
                if (callback != null) {
                    callback.onScanningMusicComplete(result);
                }
            });
        });
    }

    private ArrayList<SongModel> queryMusic() {
        Uri MEDIA_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String WHERE = MediaStore.Audio.Media.IS_MUSIC + "=? AND "
                + MediaStore.Audio.Media.SIZE + ">?";
        String[] VALUE = new String[]{
                String.valueOf(1),
                String.valueOf(0)
        };
        String ORDER_BY = MediaStore.Audio.Media.DATE_ADDED + " DESC";
        String[] PROJECTIONS = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.AudioColumns.ALBUM_ID,
                MediaStore.Audio.Media.IS_RINGTONE,
                MediaStore.Audio.Media.IS_MUSIC,
                MediaStore.Audio.Media.IS_NOTIFICATION,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE};
        ArrayList<SongModel> result = new ArrayList<>();
        try (Cursor cursor = contentResolver.query(
                MEDIA_URI, PROJECTIONS, WHERE, VALUE, ORDER_BY)) {
            if (cursor == null) {
                return result;
            }
            while (cursor.moveToNext()) {

                //c.getColumnNames();

                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                String path = ContentUris.withAppendedId(MEDIA_URI, id).toString();
                String filePath = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

//                String name = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)); // 歌曲名
//                String title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)); // 歌曲名
//                String album = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)); // 专辑
//                long albumId = c.getLong(c.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID));// 专辑封面id，根据该id可以获得专辑封面图片
//                String artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)); // 作者
//                long size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));// 大小
//                int duration = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));// 时长

                String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)); // 歌曲名
                String album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)); // 专辑
                long albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID));// 专辑封面id，根据该id可以获得专辑封面图片
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)); // 作者
                long size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));// 大小
                int duration = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));// 时长


                //int id = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));// 歌曲的id
                // int albumId = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
                //if (duration > 10 * 1000 && name.endsWith(".mp3")) {
                SongModel music = new SongModel(artist, name, album, albumId, path, duration, size);
                music.setFilePath(filePath);
                result.add(music);
                //}
            }

        } catch (Exception e) {
            Log.e("ScanningUtils", "Unable to scan local music", e);
        }
        return result;
    }

    public Uri getMediaStoreAlbumCoverUri(long albumId) {
        Uri artworkUri = Uri.parse("content://media/external/audio/albumart");
        return ContentUris.withAppendedId(artworkUri, albumId);
    }

    public ArrayList<SongModel> getMusic() {
        return musics;
    }

    public ScanningUtils setListener(ScanningListener listener) {
        this.listener = new WeakReference<>(listener);
        return this;
    }

    public void clearListener(ScanningListener expected) {
        if (listener.get() == expected) {
            listener.clear();
        }
    }

    public interface ScanningListener {
        /**
         * 扫描完成
         *
         * @param music
         */
        void onScanningMusicComplete(ArrayList<SongModel> music);
    }
}
