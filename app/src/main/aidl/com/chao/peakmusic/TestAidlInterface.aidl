// TestAidlInterface.aidl
package com.chao.peakmusic;

// Declare any non-default types here with import statements

interface TestAidlInterface {

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
      void getMusicName();

     /**
      * 获取歌曲时长
      */
      void getDuration();

     /**
      * 获取歌曲当前播放位置
      */
      void getCurrentPosition();

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
}
