// ActivityCall.aidl
package com.chao.peakmusic;
import com.chao.peakmusic.data.MusicTrackEntity;

// Declare any non-default types here with import statements

interface ActivityCall {

    void call(boolean isPlay);

    void pre();

    void next();

    void defaultPlay();

    // One selected track, never the full queue. Carries stable identity plus mutable playback URL.
    void trackChanged(in MusicTrackEntity track);
}
