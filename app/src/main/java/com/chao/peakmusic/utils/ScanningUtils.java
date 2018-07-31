package com.chao.peakmusic.utils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.chao.peakmusic.model.SongModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描工具类
 * Created by Chao on 2017-12-19.
 */

public class ScanningUtils {

    private static ScanningUtils instance;
    private ArrayList<SongModel> musics;
    private ScanningListener listener;
    private Context mContext;
    private ContentResolver mContentResolver;

    private ScanningUtils(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
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
    public synchronized List<SongModel> scanMusic() {
        Uri MEDIA_URI = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String WHERE = MediaStore.Audio.Media.IS_MUSIC + "=? AND "
                + MediaStore.Audio.Media.SIZE + ">?";
        String[] VALUE = new String[]{
                String.valueOf(1),
                String.valueOf(0)
        };
        String ORDER_BY = MediaStore.Audio.Media.DATE_ADDED + " DESC";
        String[] PROJECTIONS = {
                MediaStore.Audio.Media.DATA, // the real path
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
        musics = new ArrayList<>();
        Cursor c = null;
        try {
            c = mContentResolver.query(MEDIA_URI, PROJECTIONS, WHERE, VALUE, ORDER_BY);

            while (c.moveToNext()) {

                //c.getColumnNames();

                String path = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));// 路径

                if (!isExists(path)) {
                    continue;
                }

//                String name = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)); // 歌曲名
//                String title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)); // 歌曲名
//                String album = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)); // 专辑
//                long albumId = c.getLong(c.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID));// 专辑封面id，根据该id可以获得专辑封面图片
//                String artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)); // 作者
//                long size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));// 大小
//                int duration = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));// 时长

                String name = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)); // 歌曲名
                String title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)); // 歌曲名
                String album = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)); // 专辑
                long albumId = c.getLong(c.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID));// 专辑封面id，根据该id可以获得专辑封面图片
                String artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)); // 作者
                long size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE));// 大小
                int duration = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION));// 时长


                //int id = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));// 歌曲的id
                // int albumId = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID));
                //if (duration > 10 * 1000 && name.endsWith(".mp3")) {
                SongModel music = new SongModel(artist, name, album, albumId, path, duration, size);
                musics.add(music);
                //}
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("ScanningUtils", e.toString());
        } finally {
            if (c != null) {
                c.close();
            }
        }
        if (listener != null) {
            listener.onScanningMusicComplete(musics);
        }
        return musics;
    }

    /**
     * 判断文件是否存在
     *
     * @param path 文件的路径
     * @return
     */
    private boolean isExists(String path) {
        File file = new File(path);
        return file.exists();
    }

    public Uri getMediaStoreAlbumCoverUri(long albumId) {
        Uri artworkUri = Uri.parse("content://media/external/audio/albumart");
        return ContentUris.withAppendedId(artworkUri, albumId);
    }

    public boolean isAudioControlPanelAvailable(Context context) {
        return isIntentAvailable(context, new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL));
    }

    private boolean isIntentAvailable(Context context, Intent intent) {
        return context.getPackageManager().resolveActivity(intent, PackageManager.GET_RESOLVED_FILTER) != null;
    }

    /**
     * 从媒体库加载封面
     */
    public Bitmap loadCoverFromMediaStore(long albumId) {
        ContentResolver resolver = mContext.getContentResolver();
        Uri uri = getMediaStoreAlbumCoverUri(albumId);
        InputStream is;
        try {
            is = resolver.openInputStream(uri);
        } catch (FileNotFoundException ignored) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeStream(is, null, options);
    }

    public Bitmap getAlbumArt(long album_id) {
        Bitmap bm = null;
        String mUriAlbums = "content://media/external/audio/albums";
        String[] projection = new String[]{"album_art"};
        Cursor cur = mContentResolver.query(
                Uri.parse(mUriAlbums + "/" + Long.toString(album_id)),
                projection, null, null, null);
        String album_art = null;
        if (cur.getCount() > 0 && cur.getColumnCount() > 0) {
            cur.moveToNext();
            album_art = cur.getString(0);
            bm = BitmapFactory.decodeFile(album_art);
        }
        cur.close();
        cur = null;
        return bm;
    }


    public ArrayList<SongModel> getMusic() {
        return musics;
    }

    public ScanningUtils setListener(ScanningListener listener) {
        this.listener = listener;
        return this;
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
