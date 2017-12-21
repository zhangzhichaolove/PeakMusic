// TestAidlInterface.aidl
package com.chao.peakmusic;

// Declare any non-default types here with import statements

interface MusicAidlInterface {

  /**
      * 打开一个音频文件
      */
      void openAudio(int position);

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

     /**
      * 上一首
      */
      void pre();

     /**
      * 下一首
      */
      void next();

     /**
      * 显示悬浮控件
      */
      void show();

     /**
      * 隐藏悬浮控件
      */
      void hide();

}
