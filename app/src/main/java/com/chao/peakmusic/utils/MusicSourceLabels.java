package com.chao.peakmusic.utils;

import android.content.Context;
import com.chao.peakmusic.R;
import com.chao.peakmusic.data.MusicSource;
import com.chao.peakmusic.data.MusicTrackEntity;

public final class MusicSourceLabels {
    private MusicSourceLabels() { }
    public static String label(Context context, MusicTrackEntity track) {
        if (track.local || MusicSource.LOCAL.equals(track.sourceId)) return context.getString(R.string.source_local);
        if (MusicSource.LEGACY.equals(track.sourceId)) return context.getString(R.string.source_legacy);
        return context.getString(R.string.source_api, track.sourceBaseUrl);
    }
}
