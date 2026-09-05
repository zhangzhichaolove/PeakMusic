// TestAidlInterface.aidl
package com.chao.peakmusic;
import com.chao.peakmusic.ActivityCall;
import android.os.Bundle;
// Declare any non-default types here with import statements

interface MusicAidlInterface {

      // Bounded page: names/artists (at most 100), offset, total, current, version.
      Bundle getQueuePage(int offset);
      long getQueueVersion();
      boolean playQueueItem(int index, long version);
      boolean moveQueueItem(int from, int to, long version);
      boolean removeQueueItem(int index, long version);
      boolean clearQueue(long version);

     /**
      * 播放
      */
      void play();

     /**
      * 暂停
      */
      void pause();

     /**
      * 获取歌曲名称
      */
      String getMusicName();

      /**
      * 是否播放中
      */
      boolean isPlay();

      int getPlaybackState();

      String getPlaybackError();

     /**
      * 获取歌曲时长
      */
      long getDuration();

     /**
      * 获取歌曲当前播放索引
      */

      int getCurrentIndex();

     /**
      * 获取歌曲当前播放位置
      */
      int getCurrentPosition();

     /**
      * 播放指定进度
      */
      void seekTo(int position);

     /**
      * 设置播放模式
      */
      void seekPlayMode(int mode);

      int getPlayMode();

      int getAudioSessionId();

     /**
      * 上一首
      */
      void pre();

     /**
      * 下一首
      */
      void next();

      void registerCallback(ActivityCall call);

      void unregisterCallback(ActivityCall call);

}
