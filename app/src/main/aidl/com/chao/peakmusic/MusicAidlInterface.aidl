// TestAidlInterface.aidl
package com.chao.peakmusic;
import com.chao.peakmusic.ActivityCall;
// Declare any non-default types here with import statements

interface MusicAidlInterface {

  /**
      * 打开一个音频文件
      */
      void openAudio(int position);
  /**
      * 打开一个音频文件
      */
      void playAudio(String url, String name, String artist);

      void setOnlineQueue(in List<String> urls, in List<String> names,
                          in List<String> artists, int currentIndex);

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

     /**
      * 上一首
      */
      void pre();

     /**
      * 下一首
      */
      void next();

      void registerCallback(ActivityCall call);

}
